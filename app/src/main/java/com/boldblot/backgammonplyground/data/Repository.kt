package com.boldblot.backgammonplyground.data

import com.boldblot.backgammonplyground.core.Blunders
import com.boldblot.backgammonplyground.core.CpuLevel
import com.boldblot.backgammonplyground.core.DecisionRecord
import com.boldblot.backgammonplyground.core.DiceTotals
import com.boldblot.backgammonplyground.core.Elo
import com.boldblot.backgammonplyground.core.MistakeRate
import com.boldblot.backgammonplyground.core.Opponent
import com.boldblot.backgammonplyground.core.GameState
import com.boldblot.backgammonplyground.core.GameSummary
import com.boldblot.backgammonplyground.core.MatchRules
import com.boldblot.backgammonplyground.core.MatchState
import com.boldblot.backgammonplyground.core.PerformanceTotals
import com.boldblot.backgammonplyground.core.Summaries
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.WinType
import com.boldblot.backgammonplyground.core.XgRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

enum class MatchTypeFilter(val label: String) { ALL("All"), MATCHES("Matches"), MONEY("Money") }

enum class PeriodFilter(val label: String, val days: Int?) {
    ALL("All time", null), DAYS_30("30 days", 30), DAYS_7("7 days", 7)
}

data class StatsFilter(
    val type: MatchTypeFilter = MatchTypeFilter.ALL,
    val period: PeriodFilter = PeriodFilter.ALL,
)

/** PR of one finished match / session: total, checker play and cube (null without such decisions). */
data class PrPoint(val time: Long, val pr: Double, val checkerPr: Double?, val cubePr: Double?, val length: Int)

data class StatsReport(
    val matchesPlayed: Int,
    val matchesWon: Int,
    val gamesPlayed: Int,
    val gamesWon: Int,
    val gammonsWon: Int,
    val gammonsLost: Int,
    val backgammonsWon: Int,
    val backgammonsLost: Int,
    val moneyGames: Int,
    val moneyPointsWon: Int,
    val moneyPointsLost: Int,
    val human: PerformanceTotals,
    val cpu: PerformanceTotals,
    val humanDice: DiceTotals,
    val cpuDice: DiceTotals,
    /** Rating is global (not filtered). */
    val elo: Double,
    val eloHistory: List<Pair<Long, Double>>,
    val experience: Int,
    /** PR of each finished match/session in the filter, oldest first. */
    val prHistory: List<PrPoint>,
) {
    /** NJ Backgammon / gnubg style error rate: millipoints lost per move of both players. */
    val njErrorRate: Double?
        get() {
            val moves = human.moves + cpu.moves
            return if (moves == 0) null else 1000.0 * human.totalError / moves
        }
}

/**
 * Totals of the games of one match (or money session) finished so far. [eloBefore] and
 * [eloAfter] are set once a match has been finished and rated.
 */
data class MatchReport(
    val games: Int,
    /** Games won: [human, computer]. */
    val gamesWon: List<Int>,
    val human: PerformanceTotals,
    val cpu: PerformanceTotals,
    val eloBefore: Double? = null,
    val eloAfter: Double? = null,
)

/** A saved blunder with its decision decoded. */
data class SavedBlunder(val entity: BlunderEntity, val decision: DecisionRecord)

class Repository(private val db: AppDatabase) {

    private val dao = db.matches()

    suspend fun createMatch(rules: MatchRules, level: CpuLevel, mistakes: MistakeRate?): Long = dao.insertMatch(
        MatchEntity(
            startedAt = System.currentTimeMillis(), length = rules.length, cpuLevel = Opponent.key(level, mistakes),
            useCube = rules.useCube, jacoby = rules.jacoby, crawford = rules.crawford,
        ),
    )

    // ---- match in progress -----------------------------------------------------

    suspend fun saveCurrent(m: MatchState) = dao.saveCurrent(CurrentMatchEntity(json = AppJson.encodeToString(m)))

    suspend fun loadCurrent(): MatchState? = dao.current()?.let {
        runCatching { AppJson.decodeFromString<MatchState>(it.json) }.getOrNull()
    }

    suspend fun clearCurrent() = dao.clearCurrent()

    // ---- results ---------------------------------------------------------------------

    /** Stores a finished game. [m] still contains the finished game and the score before it. */
    suspend fun saveFinishedGame(m: MatchState, summary: GameSummary) {
        val g = m.game
        val r = requireNotNull(g.result)
        dao.insertGame(
            GameEntity(
                matchId = m.id, number = m.gameNumber, finishedAt = System.currentTimeMillis(),
                winner = r.winner, winType = r.type.name, points = r.points,
                summary = AppJson.encodeToString(summary), record = AppJson.encodeToString(g),
                scoreBeforeHuman = m.score[0], scoreBeforeCpu = m.score[1],
            ),
        )
        val entity = dao.match(m.id) ?: return
        val newScore = m.score.toMutableList().also { it[r.winner] += r.points }
        dao.upsertMatch(entity.copy(humanScore = newScore[0], cpuScore = newScore[1], games = entity.games + 1))
    }

    /** Marks the match as finished (or a money session as ended) and updates the rating. */
    suspend fun finishMatch(m: MatchState, abandoned: Boolean = false) {
        val entity = dao.match(m.id) ?: return
        val games = dao.games(m.id).map { AppJson.decodeFromString<GameSummary>(it.summary) }
        if (games.isEmpty()) {
            dao.deleteMatch(m.id)
            return
        }
        val human = games.fold(PerformanceTotals()) { acc, s -> acc + s.performance[0] }
        val cpu = games.fold(PerformanceTotals()) { acc, s -> acc + s.performance[1] }
        var updated = entity.copy(
            finishedAt = System.currentTimeMillis(),
            status = if (abandoned) MatchStatus.ABANDONED else MatchStatus.FINISHED,
            winner = m.winner,
            humanPerformance = AppJson.encodeToString(human),
            cpuPerformance = AppJson.encodeToString(cpu),
            humanScore = m.score[0], cpuScore = m.score[1],
        )
        if (!abandoned && m.rules.length > 0 && m.winner != null && entity.inStats) {
            val previous = dao.ratedMatchesChronological()
            val rating = previous.lastOrNull()?.eloAfter ?: Elo.START
            val experience = previous.sumOf { it.length }
            val after = Elo.update(
                rating, experience, Elo.cpuRating(m.cpuLevel, m.mistakes), m.rules.length, won = m.winner == Player.HUMAN,
            )
            updated = updated.copy(eloBefore = rating, eloAfter = after)
        }
        dao.upsertMatch(updated)
    }

    /** Report of the games of match [matchId] finished so far. */
    suspend fun matchReport(matchId: Long): MatchReport {
        val games = dao.games(matchId)
        val summaries = games.mapNotNull { runCatching { AppJson.decodeFromString<GameSummary>(it.summary) }.getOrNull() }
        val entity = dao.match(matchId)
        return MatchReport(
            games = games.size,
            gamesWon = listOf(games.count { it.winner == Player.HUMAN }, games.count { it.winner == Player.CPU }),
            human = summaries.fold(PerformanceTotals()) { acc, s -> acc + s.performance[0] },
            cpu = summaries.fold(PerformanceTotals()) { acc, s -> acc + s.performance[1] },
            eloBefore = entity?.eloBefore,
            eloAfter = entity?.eloAfter,
        )
    }

    suspend fun history(): List<MatchEntity> = dao.history()

    suspend fun games(matchId: Long): List<Pair<GameEntity, GameState>> =
        dao.games(matchId).mapNotNull { e ->
            runCatching { e to AppJson.decodeFromString<GameState>(e.record) }.getOrNull()
        }

    suspend fun match(id: Long): MatchEntity? = dao.match(id)

    /** The human's errors and blunders in match [matchId]'s finished games, and the first game that has any. */
    suspend fun humanMistakes(matchId: Long): Pair<Int, Int?> {
        val games = gameSummaries(matchId)
        return games.sumOf { it.second.performance[0].mistakes } to games.firstOrNull { it.second.performance[0].mistakes > 0 }?.first?.number
    }

    /** Each finished game of match [matchId] with its stored summary (also for games whose records were cleared). */
    suspend fun gameSummaries(matchId: Long): List<Pair<GameEntity, GameSummary>> =
        dao.games(matchId).mapNotNull { e -> runCatching { e to AppJson.decodeFromString<GameSummary>(e.summary) }.getOrNull() }

    /**
     * Stores game [e] analyzed again ([g]): its record and summary, and the match's totals if the
     * match is over (statistics and PR history read both).
     */
    suspend fun saveReanalyzedGame(e: GameEntity, g: GameState, matchLength: Int): GameEntity {
        val updated = e.copy(record = AppJson.encodeToString(g), summary = AppJson.encodeToString(Summaries.of(g, matchLength)))
        dao.updateGame(updated)
        val m = dao.match(e.matchId) ?: return updated
        if (m.status != MatchStatus.IN_PROGRESS) {
            val summaries = dao.games(m.id).map { AppJson.decodeFromString<GameSummary>(it.summary) }
            dao.upsertMatch(
                m.copy(
                    humanPerformance = AppJson.encodeToString(summaries.fold(PerformanceTotals()) { acc, s -> acc + s.performance[0] }),
                    cpuPerformance = AppJson.encodeToString(summaries.fold(PerformanceTotals()) { acc, s -> acc + s.performance[1] }),
                ),
            )
        }
        return updated
    }

    /** Stores a saved blunder analyzed again ([d]); its error is the new one. */
    suspend fun saveReanalyzedBlunder(b: BlunderEntity, d: DecisionRecord): BlunderEntity {
        val updated = b.copy(error = (d.error ?: 0f) * XgRules.errorFactor(b.matchLength), record = AppJson.encodeToString(d))
        dao.updateBlunder(updated)
        return updated
    }

    suspend fun deleteMatch(id: Long) = dao.deleteMatchWithGames(id)

    /** Deletes all finished matches and games, and with them the statistics and rating (not the blunder list). */
    suspend fun clearMatchHistory() {
        dao.deleteAllGames()
        dao.deleteFinishedMatches()
    }

    /**
     * Deletes the move-by-move records (review, export) of the finished matches started more
     * than [olderThanDays] days ago (null = all); their results and statistics are kept and
     * they leave the History list. The blunder list keeps its own copies.
     */
    suspend fun clearGameRecords(olderThanDays: Int?) {
        val before = olderThanDays?.let { System.currentTimeMillis() - it * 86_400_000L } ?: Long.MAX_VALUE
        dao.clearRecords(before)
        withContext(Dispatchers.IO) { db.openHelper.writableDatabase.execSQL("VACUUM") }
    }

    /** Statistics and rating start again: every finished match is left out (they stay in History). */
    suspend fun clearStatistics() {
        dao.excludeFinishedFromStats()
        recomputeRatings()
    }

    /** Counts match [id] in the statistics and rating, or leaves it out. */
    suspend fun setInStats(id: Long, inStats: Boolean) {
        dao.setInStats(id, inStats)
        recomputeRatings()
    }

    /**
     * Recomputes the FIBS-formula rating along the finished matches that count, oldest first
     * (the same formula as when each match finished); matches left out get no rating.
     */
    suspend fun recomputeRatings() {
        var rating = Elo.START
        var experience = 0
        for (m in dao.finishedMatchesChronological()) {
            if (!m.inStats || m.winner == null) {
                if (m.eloBefore != null || m.eloAfter != null) dao.setRating(m.id, null, null)
                continue
            }
            val after = Elo.update(rating, experience, Opponent.rating(m.cpuLevel), m.length, won = m.winner == Player.HUMAN)
            if (m.eloBefore != rating || m.eloAfter != after) dao.setRating(m.id, rating, after)
            rating = after
            experience += m.length
        }
    }

    suspend fun clearBlunders() = dao.deleteAllBlunders()

    // ---- blunder list ----------------------------------------------------------------

    /** Saves the decision at [index] of [m]'s current game if it is one of the human's blunders. */
    suspend fun saveIfBlunder(m: MatchState, index: Int) {
        val d = m.game.decisions.getOrNull(index) ?: return
        val error = (d.error ?: return) * XgRules.errorFactor(m.rules.length)
        if (!Blunders.isBlunder(d, error)) return
        dao.insertBlunder(
            BlunderEntity(
                matchId = m.id, gameNumber = m.gameNumber, decisionIndex = index,
                playedAt = System.currentTimeMillis(), matchLength = m.rules.length, cpuLevel = m.opponentKey,
                error = error, record = AppJson.encodeToString(d),
            ),
        )
    }

    /** The saved blunders in [sort] order (newest first within equal errors). */
    suspend fun blunders(sort: BlunderSort): List<SavedBlunder> {
        val list = dao.blunders().mapNotNull { e ->
            runCatching { SavedBlunder(e, AppJson.decodeFromString<DecisionRecord>(e.record)) }.getOrNull()
        }
        return when (sort) {
            BlunderSort.NEWEST -> list
            BlunderSort.BIGGEST -> list.sortedByDescending { it.entity.error }
        }
    }

    suspend fun removeBlunder(id: Long) = dao.deleteBlunder(id)

    /** Puts back a removed blunder (undo). */
    suspend fun restoreBlunder(e: BlunderEntity) {
        dao.insertBlunder(e)
    }

    // ---- statistics ------------------------------------------------------------------

    suspend fun stats(filter: StatsFilter): StatsReport {
        val since = filter.period.days?.let { System.currentTimeMillis() - it * 86_400_000L }
        val matches = dao.allMatches().associateBy { it.id }
        fun included(m: MatchEntity): Boolean {
            if (!m.inStats) return false
            if (filter.type == MatchTypeFilter.MATCHES && m.length == 0) return false
            if (filter.type == MatchTypeFilter.MONEY && m.length != 0) return false
            return true
        }

        var gamesPlayed = 0
        var gamesWon = 0
        var gammonsWon = 0
        var gammonsLost = 0
        var bgWon = 0
        var bgLost = 0
        var moneyGames = 0
        var moneyWon = 0
        var moneyLost = 0
        var human = PerformanceTotals()
        var cpu = PerformanceTotals()
        var humanDice = DiceTotals()
        var cpuDice = DiceTotals()

        for (g in dao.allGameSummaries()) {
            val m = matches[g.matchId] ?: continue
            if (!included(m)) continue
            if (since != null && g.finishedAt < since) continue
            val s = runCatching { AppJson.decodeFromString<GameSummary>(g.summary) }.getOrNull() ?: continue
            gamesPlayed++
            val won = s.result.winner == Player.HUMAN
            if (won) gamesWon++
            when (s.result.type) {
                WinType.GAMMON -> if (won) gammonsWon++ else gammonsLost++
                WinType.BACKGAMMON -> if (won) bgWon++ else bgLost++
                WinType.SINGLE -> Unit
            }
            if (m.length == 0) {
                moneyGames++
                if (won) moneyWon += s.result.points else moneyLost += s.result.points
            }
            human += s.performance[0]
            cpu += s.performance[1]
            humanDice += s.dice[0]
            cpuDice += s.dice[1]
        }

        val finished = matches.values.filter {
            it.status == MatchStatus.FINISHED && included(it) && (since == null || (it.finishedAt ?: 0) >= since)
        }.sortedBy { it.finishedAt }
        val realMatches = finished.filter { it.length > 0 }
        val prHistory = finished.mapNotNull { m ->
            val p = m.humanPerformance?.let { runCatching { AppJson.decodeFromString<PerformanceTotals>(it) }.getOrNull() }
            val pr = p?.pr ?: return@mapNotNull null
            PrPoint(m.finishedAt ?: m.startedAt, pr, p.checkerPr, p.cubePr, m.length)
        }

        val rated = dao.ratedMatchesChronological().filter { it.eloAfter != null }
        val eloHistory = rated.map { (it.finishedAt ?: it.startedAt) to it.eloAfter!! }

        return StatsReport(
            matchesPlayed = realMatches.size,
            matchesWon = realMatches.count { it.winner == Player.HUMAN },
            gamesPlayed = gamesPlayed, gamesWon = gamesWon,
            gammonsWon = gammonsWon, gammonsLost = gammonsLost,
            backgammonsWon = bgWon, backgammonsLost = bgLost,
            moneyGames = moneyGames, moneyPointsWon = moneyWon, moneyPointsLost = moneyLost,
            human = human, cpu = cpu, humanDice = humanDice, cpuDice = cpuDice,
            elo = eloHistory.lastOrNull()?.second ?: Elo.START,
            eloHistory = eloHistory,
            experience = rated.sumOf { it.length },
            prHistory = prHistory,
        )
    }
}
