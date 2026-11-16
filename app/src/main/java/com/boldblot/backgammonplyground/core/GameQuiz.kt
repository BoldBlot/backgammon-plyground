package com.boldblot.backgammonplyground.core

/** Which decisions of a saved match a game quiz stops at (any of the ticked ones). */
enum class GameQuizStop(val label: String) {
    /** The two best plays (or cube actions) are within the close level of each other. */
    CLOSE("Close decisions"),
    /** What was played in the game cost more than the error level. */
    WRONG("Errors in the game"),
    /** Every decision that counts (not forced, not obvious). */
    ANY("Any decision"),
}

/** A level of a game quiz: XG's error or blunder threshold, or the player's own value. */
enum class GameQuizLevel(val label: String) {
    ERROR("Error"), BLUNDER("Blunder"), CUSTOM("Custom");

    fun value(custom: Float): Float = when (this) {
        ERROR -> XgRules.ERROR_THRESHOLD
        BLUNDER -> XgRules.BLUNDER_THRESHOLD
        CUSTOM -> custom
    }
}

/** Whose decisions a game quiz asks. */
enum class GameQuizSide(val label: String) { BOTH("Both players"), MINE("Mine"), THEIRS("Opponent's") }

/** Which decisions stay when the match has more than the quiz asks. */
enum class GameQuizPriority(val label: String) { BIGGEST("Biggest errors"), FIRST("First in the match"), LAST("Last in the match"), RANDOM("Random") }

/** The order of a game quiz's questions. */
enum class GameQuizOrder(val label: String) { GAME("As in the game"), RANDOM("Random") }

/** What a game quiz picks from a match. Levels are errors after the 1-point factor, as the review's badges. */
data class GameQuizRules(
    val stops: Set<GameQuizStop> = setOf(GameQuizStop.WRONG),
    val closeLevel: GameQuizLevel = GameQuizLevel.ERROR,
    val closeCustom: Float = 0.03f,
    val wrongLevel: GameQuizLevel = GameQuizLevel.ERROR,
    val wrongCustom: Float = 0.05f,
    val side: GameQuizSide = GameQuizSide.BOTH,
    val cube: Boolean = true,
    /** How many questions (0 = all that match). */
    val limit: Int = 10,
    val priority: GameQuizPriority = GameQuizPriority.BIGGEST,
    val order: GameQuizOrder = GameQuizOrder.GAME,
)

/** One decision of a match that a game quiz can ask: decision [index] of game [game] (in the match's order). */
data class GameQuizPick(
    val game: Int, val index: Int, val decision: DecisionRecord,
    /** What the game's play cost, after the 1-point factor (0 when it was the best). */
    val error: Float,
    /** How far the second best play or cube action is behind the best, after the 1-point factor; null with a single choice. */
    val gap: Float?,
)

object GameQuiz {
    /** The analyzed decisions of [games] (each game's decisions, in the match's order) that count, with their error and gap. */
    fun decisions(games: List<List<DecisionRecord>>, matchLength: Int): List<GameQuizPick> {
        val factor = XgRules.errorFactor(matchLength)
        return games.flatMapIndexed { g, ds ->
            ds.mapIndexedNotNull { i, d ->
                if (!d.counted || d.auto || d.pending) return@mapIndexedNotNull null
                val gap = gap(d) ?: return@mapIndexedNotNull null
                GameQuizPick(g, i, d, (d.scoredError ?: 0f).coerceAtLeast(0f) * factor, gap.takeIf { it.isFinite() }?.times(factor))
            }
        }
    }

    /** Second best minus best for [d] (infinite with a single choice); null when [d] is not an analyzed checker or cube decision. */
    private fun gap(d: DecisionRecord): Float? = when (d.reviewKind) {
        DecisionKind.CHECKER -> d.checker?.takeIf { d.dice != null && it.candidates.isNotEmpty() }?.let { c ->
            c.candidates.getOrNull(1)?.let { (c.bestEquity - it.equity).coerceAtLeast(0f) } ?: Float.POSITIVE_INFINITY
        }
        DecisionKind.DOUBLE, DecisionKind.NO_DOUBLE -> d.cubeEval?.let { e ->
            listOf(true, false).map { DecisionMath.doubleError(e, it, d.beavers) }.sorted()[1]
        }
        DecisionKind.TAKE, DecisionKind.PASS, DecisionKind.BEAVER -> d.cubeEval?.let { e ->
            (listOf(DecisionKind.TAKE, DecisionKind.PASS) + if (d.beavers) listOf(DecisionKind.BEAVER) else emptyList())
                .map { DecisionMath.takeError(e, it, d.beavers) }.sorted()[1]
        }
        else -> null
    }

    /** Whether [p] is one of the decisions [r] stops at. */
    fun matches(p: GameQuizPick, r: GameQuizRules): Boolean {
        val d = p.decision
        if (!r.cube && d.reviewKind != DecisionKind.CHECKER) return false
        when (r.side) {
            GameQuizSide.MINE -> if (d.player != Player.HUMAN) return false
            GameQuizSide.THEIRS -> if (d.player != Player.CPU) return false
            GameQuizSide.BOTH -> Unit
        }
        return r.stops.any { stop ->
            when (stop) {
                GameQuizStop.CLOSE -> p.gap != null && p.gap <= r.closeLevel.value(r.closeCustom)
                GameQuizStop.WRONG -> p.error > r.wrongLevel.value(r.wrongCustom)
                GameQuizStop.ANY -> true
            }
        }
    }

    /** The decisions of [all] (in the match's order) that [r] stops at, before its limit. */
    fun matching(all: List<GameQuizPick>, r: GameQuizRules): List<GameQuizPick> = all.filter { matches(it, r) }

    /** The quiz's questions: [r]'s priority keeps at most its limit of the matching decisions, then they go in its order. */
    fun pick(all: List<GameQuizPick>, r: GameQuizRules, random: kotlin.random.Random = kotlin.random.Random.Default): List<GameQuizPick> {
        val matching = matching(all, r)
        val ranked = when (r.priority) {
            // Equal errors: the closer decision first, then the earlier one.
            GameQuizPriority.BIGGEST -> matching.sortedWith(compareByDescending<GameQuizPick> { it.error }.thenBy { it.gap ?: Float.MAX_VALUE })
            GameQuizPriority.FIRST -> matching
            GameQuizPriority.LAST -> matching.reversed()
            GameQuizPriority.RANDOM -> matching.shuffled(random)
        }
        val kept = if (r.limit > 0) ranked.take(r.limit) else ranked
        return when (r.order) {
            GameQuizOrder.GAME -> kept.sortedWith(compareBy<GameQuizPick> { it.game }.thenBy { it.index })
            GameQuizOrder.RANDOM -> kept.shuffled(random)
        }
    }
}
