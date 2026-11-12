package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.AnalysisEngine
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.Candidate
import com.boldblot.backgammonplyground.core.CpuLevel
import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.CubeEval
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.GameSearch
import com.boldblot.backgammonplyground.core.SearchMonitor
import com.boldblot.backgammonplyground.core.MAX_PLIES
import com.boldblot.backgammonplyground.core.Move
import com.boldblot.backgammonplyground.core.MIN_SEARCHED
import com.boldblot.backgammonplyground.core.MAX_SEARCHED
import com.boldblot.backgammonplyground.core.DECIDED_CHANCE
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.MoveGenerator
import com.boldblot.backgammonplyground.core.RankResult
import java.util.Random
import kotlin.math.abs
import kotlin.math.min

/** A position's value after the mover's play: cubeful equity (normalized, EMG in matches) and cubeless probabilities. */
class PostEval(val equity: Float, val probs: List<Float>)

/**
 * What every engine shares: the checker search over an engine's [evaluate] (every legal play at
 * 1-ply, then the [MIN_SEARCHED] best and the plays within the [Depth]'s window of the best at full
 * depth), the computer's
 * levels as depth plus noise, and luck at 1-ply. Engines are not thread-safe: use one thread
 * ([EngineService]).
 */
abstract class SearchEngine : AnalysisEngine {

    protected val rng = Random()

    /**
     * Set around a search the user waits for: the search reports its progress, goes stage by stage (4-ply
     * after 3-ply) and stops when asked, returning the last stage that finished ([Depth.incomplete]).
     */
    override var monitor: SearchMonitor? = null
        set(m) { field?.onStop = null; field = m; clearStop(); m?.onStop = { requestStop() } }

    /**
     * Everything but the analysis of one position (play, whole games, the background): played-out games
     * ([GameSearch]) only for the checker plays that the plies before them call an error of [ERROR_FLOOR] or
     * more (a setting). A roll ranked without a move played (a hint, the computer's choice) is played out when
     * someone waits for it ([monitor]), and not when it is only ranked ahead.
     */
    @Volatile var gamesOnErrorsOnly: Boolean = false

    /** Makes a running native search return at once ([requestStop]), and allows searches again ([clearStop]). */
    protected open fun requestStop() {}
    protected open fun clearStop() {}

    /**
     * Equity for [ctx].onRoll of each of [posts] (positions after its play, none of them a finished game) by
     * [search]'s played-out games on common dice, and the games played; null when the engine has no such
     * search, or it was stopped too early to count.
     */
    protected open fun gameValues(ctx: CubeContext, posts: List<Board>, search: GameSearch): Pair<List<Float>, Int>? = null

    /** Whether [ctx] is something this engine can play games out for (PureTD, match play). */
    protected open fun playsGames(ctx: CubeContext): Boolean = false

    /**
     * [base] ([search]'s plies) replaced by played-out games when it leaves the decision closer than
     * [GameSearch.CUBE_CLEAR]: the evaluation and whether the games were all played. Null: [base] stands.
     */
    protected open fun gamesCube(board: Board, ctx: CubeContext, search: GameSearch, base: CubeEval): Pair<CubeEval, Boolean>? = null

    /** The plain code a search runs with: the half of a dual code for [ctx], without games where they cannot be played. */
    private fun codeFor(plies: Int, ctx: CubeContext, cube: Boolean): Int {
        val code = Depth.resolve(plies, ctx.matchLength > 0)
        val d = Depth.of(code)
        val gs = d.search ?: return code
        return if (gs.cube != cube || (gs.games > 0 && !playsGames(ctx))) d.plain.copy(incomplete = false).code else code
    }

    /** Positions right after [ctx].onRoll's play from [board], at XG depth [plies] (1-4), for the mover. */
    protected abstract fun evaluate(board: Board, ctx: CubeContext, posts: List<Board>, plies: Int): List<PostEval>

    // ---- checker play ---------------------------------------------------------------------------

    /** Anything besides the position that changes the ranking (an engine's setting): part of the cache key. */
    protected open val rankingMode: Any? get() = null

    /** The last ranking (a hint, the computer's own choice and the analysis often rank the same roll). */
    private var cacheKey: Any? = null
    private var cached: RankResult? = null
    /** The depth code [cached] was ranked with (its searched candidates carry it). */
    private var cachedPlies: Int = 0

    override fun rankMoves(board: Board, ctx: CubeContext, d1: Int, d2: Int, plies: Int, played: Move?): RankResult {
        val code = codeFor(plies, ctx, cube = false)
        if (monitor?.stop != true) clearStop()
        val games = Depth.of(code).search?.takeIf { it.games > 0 }
        if (games != null && gamesOnErrorsOnly && cacheKey != keyOf(board, ctx, d1, d2, code)) {
            val plain = Depth.of(code).plain.code
            if (played == null) {
                if (monitor == null) return ranked(board, ctx, d1, d2, plain, null)
            } else {
                val quick = ranked(board, ctx, d1, d2, plain, played)
                val i = quick.playedIndex
                if (i <= 0 || quick.candidates[0].equity - quick.candidates[i].equity < ERROR_FLOOR) return quick
            }
        }
        return ranked(board, ctx, d1, d2, code, played)
    }

    private fun keyOf(board: Board, ctx: CubeContext, d1: Int, d2: Int, plies: Int): Any =
        listOf(board, ctx, minOf(d1, d2), maxOf(d1, d2), Depth.of(plies), rankingMode)

    private fun ranked(board: Board, ctx: CubeContext, d1: Int, d2: Int, plies: Int, played: Move?): RankResult {
        // The ranking depends on the played move only when a filter would leave it unsearched, so a
        // ranking of the same roll is reused when the played move is among its fully searched
        // candidates (the computer's choice, then the analysis of that choice; a hint, then the move).
        // The depth by what it searches: a level's short code (Grandmaster's 3) and the analysis's long one
        // (3-ply pruned, 32) are the same search, done once.
        val key = keyOf(board, ctx, d1, d2, plies)
        val hit = cached?.takeIf { cacheKey == key }?.let { h ->
            // Candidates searched at the cached ranking's depth carry the code asked for now.
            if (cachedPlies == plies) h
            else h.copy(candidates = h.candidates.map { if (it.plies == cachedPlies) it.copy(plies = plies) else it })
        }
        if (hit != null) {
            val mover = ctx.onRoll
            val target = played?.let { board.applyMove(mover, it) }
            val idx = if (target == null) -1 else hit.candidates.indexOfFirst { board.applyMove(mover, it.move) == target }
            val searched = idx >= 0 && (Depth.of(plies).plies <= 1 || hit.candidates[idx].plies == plies)
            if (target == null || searched) return hit.copy(playedIndex = idx)
            // (Played-out games value the plays together, on the same dice: the roll is ranked again.)
            if (idx >= 0 && Depth.of(plies).search == null) {
                // The roll was ranked ahead (while the player thought) but the move played was left at
                // 1-ply by the filter: search it alone, as a ranking with it would have.
                val e = evaluate(board, ctx, listOf(target), Depth.of(plies).plies).first()
                val moved = hit.candidates[idx].copy(equity = e.equity, probs = e.probs, plies = plies)
                val list = (hit.candidates.filterIndexed { i, _ -> i != idx } + moved)
                    .sortedWith(compareByDescending<Candidate> { it.plies }.thenByDescending { it.equity })
                return RankResult(list, list.indexOf(moved)).also { cached = it; cachedPlies = plies }
            }
        }
        return rank(board, ctx, d1, d2, plies, played).also {
            // A stopped search is not kept: asked again, it runs again.
            if (it.candidates.any { c -> c.plies >= Depth.INCOMPLETE }) { cacheKey = null; cached = null }
            else { cacheKey = key; cached = it; cachedPlies = plies }
        }
    }

    /** [posts] at [plies]: under a [monitor] one at a time, reporting [what]; null when it was stopped. */
    private fun deeper(board: Board, ctx: CubeContext, posts: List<Board>, plies: Int, what: String): List<PostEval>? {
        val mon = monitor ?: return evaluate(board, ctx, posts, plies)
        val out = ArrayList<PostEval>(posts.size)
        for ((i, p) in posts.withIndex()) {
            mon.report(what, i, posts.size)
            if (mon.stop) return null
            out += evaluate(board, ctx, listOf(p), plies)
        }
        if (mon.stop) return null // the last one was abandoned: its values mean nothing
        mon.report(what, posts.size, posts.size)
        return out
    }

    private fun plays(n: Int) = if (n == 1) "1 play" else "$n plays"

    /** Leaves the ranking cache alone: the bar must not cost the deep ranking done ahead of a move. */
    override fun bestPlayChances(board: Board, ctx: CubeContext, d1: Int, d2: Int): List<Float>? =
        rank(board, ctx, d1, d2, 0, null).candidates.firstOrNull()?.probs

    private fun rank(board: Board, ctx: CubeContext, d1: Int, d2: Int, plies: Int, played: Move?): RankResult {
        val mover = ctx.onRoll
        val moves = MoveGenerator.legalMoves(board, mover, d1, d2)
        if (moves.isEmpty()) return RankResult(emptyList(), -1)
        val posts = moves.map { board.applyMove(mover, it) }
        val first = evaluate(board, ctx, posts, 1)
        val probs = first.map { it.probs }.toMutableList()
        val eq = first.map { it.equity }.toMutableList()
        val depth = IntArray(posts.size)
        val playedIdx = played?.let { posts.indexOf(board.applyMove(mover, it)) } ?: -1
        val depthOf = Depth.of(plies)
        var stopped = false
        val search = depthOf.search
        if (depthOf.plies >= 2 && posts.size > 1 && !decided(probs) && !exactAtOnePly(ctx, posts) && search == null) {
            val within = depthOf.filter.within
            val best = eq.max()
            // Every move whose 1-ply equity is within the window of the best is searched deeper, and
            // always the best few: a window alone can drop the right move by a hair (FlashBack Boot
            // Camp 6-2, 16/10 5/3: 0.164 behind at 1-ply, best by 0.29 at 3-ply). A pruned search
            // takes at most MAX_SEARCHED of them, the best first.
            val byEq = eq.indices.sortedByDescending { eq[it] }
            val inWindow = byEq.filter { eq[it] >= best - within }
            val deep = (if (within.isInfinite()) inWindow else inWindow.take(MAX_SEARCHED)).toMutableSet()
            deep += byEq.take(MIN_SEARCHED)
            if (playedIdx >= 0) deep += playedIdx
            val list = deep.toList()
            // Watched, 4-ply comes after 3-ply: stopped, it still has the 3-ply values to show.
            val steps = if (monitor != null && depthOf.plies == 4) listOf(3, 4) else listOf(depthOf.plies)
            for (n in steps) {
                val evals = deeper(board, ctx, list.map { posts[it] }, n, "$n-ply on ${plays(list.size)}")
                if (evals == null) { stopped = true; break }
                val code = if (n == depthOf.plies) plies else Depth(n, depthOf.filter).code
                list.forEachIndexed { j, i ->
                    probs[i] = evals[j].probs
                    eq[i] = evals[j].equity
                    depth[i] = code
                }
            }
        } else if (search != null && posts.size > 1 && !decided(probs) && !exactAtOnePly(ctx, posts)) {
            // A funnel: fewer and fewer plays at each deeper stage, the last of them by played-out games.
            // The move played goes through every stage. (plies, window, most plays; plies 0 = the games.)
            val stages = if (search.games == 0) listOf(Triple(2, 0.16f, 16), Triple(3, 0.08f, 8), Triple(search.plies, search.within, search.plays))
            else listOf(Triple(search.plies, 0.16f, 16), Triple(0, search.within, search.plays))
            var alive = posts.indices.toList()
            var last = alive
            for ((n, st) in stages.withIndex()) {
                val best = alive.maxOf { eq[it] }
                val byEq = alive.sortedByDescending { eq[it] }
                // The first stage always takes the best few, as the plain search does: a window alone can drop the right move by a hair.
                val keep = (byEq.filter { eq[it] >= best - st.second }.take(st.third) + (if (n == 0) byEq.take(MIN_SEARCHED) else emptyList()) +
                    listOfNotNull(playedIdx.takeIf { it >= 0 })).distinct()
                last = keep
                if (keep.size <= 1) continue
                val final = n == stages.lastIndex
                val code = if (final) plies else Depth(st.first, MoveFilter.WIDE).code
                if (st.first > 0) {
                    val evals = deeper(board, ctx, keep.map { posts[it] }, st.first, "${st.first}-ply on ${plays(keep.size)}")
                    if (evals == null) { stopped = true; break }
                    keep.forEachIndexed { j, i -> probs[i] = evals[j].probs; eq[i] = evals[j].equity; depth[i] = code }
                } else {
                    // A play that ends the game needs no games: its value is exact, and it is the best there is.
                    val live = keep.filter { posts[it].off(mover) < 15 }
                    val got = if (live.size == keep.size) gameValues(ctx, live.map { posts[it] }, search) else null
                    if (got == null) { stopped = monitor?.stop == true; if (stopped) break else continue }
                    val partial = got.second < search.games
                    keep.forEachIndexed { j, i -> eq[i] = got.first[j]; depth[i] = if (partial) code + Depth.INCOMPLETE else code }
                    if (partial) { stopped = false; alive = keep; last = emptyList(); break }
                }
                alive = keep
            }
            // The plays left standing when the funnel ran dry (one play in the window) carry the depth asked for.
            if (!stopped) last.forEach { if (depth[it] < Depth.INCOMPLETE) depth[it] = plies }
        }
        if (stopped) {
            // The stage that was running is dropped: the plays of the last finished one say so.
            val top = depth.max()
            for (i in depth.indices) if (depth[i] == top) depth[i] = top + Depth.INCOMPLETE
        }
        // Searched candidates first (their values are on the deeper scale), each group by equity.
        val order = posts.indices.sortedWith(compareByDescending<Int> { depth[it] }.thenByDescending { eq[it] })
        val candidates = order.map { Candidate(moves[it], eq[it], probs[it], depth[it]) }
        return RankResult(candidates, if (playedIdx >= 0) order.indexOf(playedIdx) else -1)
    }

    /** True when the 1-ply values of these plays are already exact, so a deeper search finds the same (see PureTD's bear-off table). */
    protected open fun exactAtOnePly(ctx: CubeContext, posts: List<Board>): Boolean = false

    /**
     * True when no play changes anything: the mover wins (or loses) whatever they play, and the
     * gammon and backgammon chances are the same after every play (1-ply chances, for the mover).
     * A deeper search could only find the same value for each play, so it is skipped.
     */
    private fun decided(probs: List<List<Float>>): Boolean {
        fun same(i: Int) = probs.maxOf { it[i] } - probs.minOf { it[i] } < DECIDED_CHANCE
        val lost = probs.all { it[0] < DECIDED_CHANCE }
        val won = probs.all { it[0] > 1 - DECIDED_CHANCE }
        return (lost || won) && (1..4).all { same(it) }
    }

    override fun chooseMove(board: Board, ctx: CubeContext, d1: Int, d2: Int, level: CpuLevel): Move {
        val r = rankMoves(board, ctx, d1, d2, levelPlies(if (level == CpuLevel.NEW_BEST && !playsGames(ctx)) CpuLevel.MAXIMUM else level), null)
        if (r.candidates.isEmpty()) return Move.NONE
        val noise = levelNoise(level)
        if (noise == 0.0) return r.candidates[0].move
        return r.candidates.maxBy { it.equity + noise * rng.nextGaussian() }.move
    }

    // ---- cube -----------------------------------------------------------------------------------

    protected fun evalOf(nd: Double, dt: Double, dp: Double, available: Boolean, probs: List<Float>): CubeEval {
        val after = min(dt, dp)
        val double = available && after > nd
        return CubeEval(
            noDouble = nd.toFloat(), doubleTake = dt.toFloat(), doublePass = dp.toFloat(),
            optimal = (if (double) after else nd).toFloat(), shouldDouble = double, shouldTake = dt <= dp,
            optional = available && abs(nd - after) < 1e-4, cubeAvailable = available, probs = probs,
        )
    }

    /** The engine's cube decision for [ctx].onRoll at XG depth [plies] ([cubeDecision] caches it). */
    protected abstract fun computeCube(board: Board, ctx: CubeContext, plies: Int): CubeEval

    /** The last cube decision: the computer's own and the analysis of it are often the same evaluation. */
    private var cubeKey: Any? = null
    private var cubeCached: CubeEval? = null

    private fun cubeKeyOf(board: Board, ctx: CubeContext, plies: Int) =
        listOf(board, ctx, Depth.of(codeFor(plies, ctx, cube = true)).let { it.search ?: it.plies }, rankingMode)

    final override fun cubeDecision(board: Board, ctx: CubeContext, plies: Int): CubeEval {
        cachedCube(board, ctx, plies)?.let { return it }
        val code = codeFor(plies, ctx, cube = true)
        val mon = monitor
        if (mon?.stop != true) clearStop()
        val d = Depth.of(code)
        // Watched, 4-ply comes after 3-ply: stopped, it still has the 3-ply decision to show.
        val steps = if (mon != null && d.plies == 4) listOf(3, 4) else listOf(d.plies)
        var res: CubeEval? = null
        var at = 0
        var stopped = false
        for (n in steps) {
            mon?.report("Cube decision, $n-ply", 0, 0)
            val e = computeCube(board, ctx, Depth(n).code)
            if (mon?.stop == true) { stopped = true; break }
            res = e
            at = if (n == d.plies) code else Depth(n).code
        }
        val search = d.search
        if (!stopped && search != null && search.games > 0) {
            gamesCube(board, ctx, search, requireNotNull(res))?.let { (e, complete) ->
                res = e
                if (!complete) { at = code; stopped = true }
            } ?: run { if (mon?.stop == true) { stopped = true; at = Depth(search.plies).code } }
        }
        if (res == null) {
            // Stopped before anything finished: the network's own view, which takes no time.
            clearStop()
            res = computeCube(board, ctx, Depth(1).code)
            at = Depth(1).code
        }
        val out = requireNotNull(res).copy(engine = name.ifEmpty { null }, plies = if (stopped) at + Depth.INCOMPLETE else at)
        if (!stopped) { cubeKey = cubeKeyOf(board, ctx, plies); cubeCached = out }
        return out
    }

    override fun cachedCube(board: Board, ctx: CubeContext, plies: Int): CubeEval? =
        cubeCached?.takeIf { cubeKey == cubeKeyOf(board, ctx, plies) }

    /** The level's cube decision; [CpuLevel.NEW_BEST] as [CpuLevel.MAXIMUM] where games cannot be played out. */
    private fun cubeLevel(level: CpuLevel, ctx: CubeContext) = if (level == CpuLevel.NEW_BEST && !playsGames(ctx)) CpuLevel.MAXIMUM else level

    override fun cubeDecisionAtLevel(board: Board, ctx: CubeContext, level: CpuLevel): CubeEval {
        val e = cubeDecision(board, ctx, levelCubePlies(cubeLevel(level, ctx)))
        val noise = levelNoise(level)
        if (noise == 0.0) return e
        return evalOf(
            e.noDouble + noise * rng.nextGaussian(), e.doubleTake + noise * rng.nextGaussian(), e.doublePass.toDouble(),
            e.cubeAvailable, e.probs,
        )
    }

    // ---- luck -----------------------------------------------------------------------------------

    /** Luck of roll (d1, d2): its best 1-ply equity minus the average over all rolls. */
    override fun luck(board: Board, ctx: CubeContext, d1: Int, d2: Int, firstRoll: Boolean): Float {
        val mover = ctx.onRoll
        val rolls = ArrayList<Triple<Int, Int, Double>>()
        for (a in 1..6) for (b in a..6) {
            if (firstRoll && a == b) continue
            rolls += Triple(a, b, if (firstRoll) 1.0 / 15 else if (a == b) 1.0 / 36 else 2.0 / 36)
        }
        val groups = rolls.map { (a, b, _) ->
            MoveGenerator.legalMoves(board, mover, a, b).map { board.applyMove(mover, it) }.ifEmpty { listOf(board) }
        }
        val evals = evaluate(board, ctx, groups.flatten(), 1)
        var k = 0
        var mean = 0.0
        var actual = 0.0
        groups.forEachIndexed { i, g ->
            var best = -1e9
            repeat(g.size) { best = maxOf(best, evals[k++].equity.toDouble()) }
            mean += rolls[i].third * best
            val (a, b, _) = rolls[i]
            if (a == min(d1, d2) && b == maxOf(d1, d2)) actual = best
        }
        return (actual - mean).toFloat()
    }

    // GNU Backgammon's position and match IDs come from core/PositionIds, not from the engines.
    override fun positionId(board: Board, onRoll: Int): String = ""
    override fun matchId(ctx: CubeContext, d1: Int, d2: Int): String = ""

    companion object {
        fun levelPlies(level: CpuLevel): Int = when (level) {
            CpuLevel.WORLD_CLASS -> 1
            CpuLevel.SUPREMO -> 2
            CpuLevel.GRANDMASTER, CpuLevel.MISTAKES -> 3
            CpuLevel.STRONG -> Depth(2, MoveFilter.ALL).code
            CpuLevel.MAXIMUM -> MAX_PLIES
            CpuLevel.NEW_BEST -> GameSearch.G36.code
            else -> 0
        }

        /** The depth code of a level's cube decisions. */
        fun levelCubePlies(level: CpuLevel): Int = if (level == CpuLevel.NEW_BEST) GameSearch.C44.code else levelPlies(level)

        /** With [gamesOnErrorsOnly]: a checker play this far behind the best (normalized equity) is played out. */
        const val ERROR_FLOOR = 0.005f

        /** Standard deviation of the noise added to each equity (weaker levels). */
        fun levelNoise(level: CpuLevel): Double = when (level) {
            CpuLevel.BEGINNER -> 0.060
            CpuLevel.CASUAL -> 0.050
            CpuLevel.INTERMEDIATE -> 0.040
            CpuLevel.ADVANCED -> 0.015
            else -> 0.0
        }
    }
}
