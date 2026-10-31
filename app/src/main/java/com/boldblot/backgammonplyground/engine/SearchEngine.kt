package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.AnalysisEngine
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.Candidate
import com.boldblot.backgammonplyground.core.CpuLevel
import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.CubeEval
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.MAX_PLIES
import com.boldblot.backgammonplyground.core.Move
import com.boldblot.backgammonplyground.core.MIN_SEARCHED
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

    /** Positions right after [ctx].onRoll's play from [board], at XG depth [plies] (1-4), for the mover. */
    protected abstract fun evaluate(board: Board, ctx: CubeContext, posts: List<Board>, plies: Int): List<PostEval>

    // ---- checker play ---------------------------------------------------------------------------

    /** The last ranking (a hint, the computer's own choice and the analysis often rank the same roll). */
    private var cacheKey: Any? = null
    private var cached: RankResult? = null

    override fun rankMoves(board: Board, ctx: CubeContext, d1: Int, d2: Int, plies: Int, played: Move?): RankResult {
        // The ranking depends on the played move only when a filter would leave it unsearched, so a
        // ranking of the same roll is reused when the played move is among its fully searched
        // candidates (the computer's choice, then the analysis of that choice; a hint, then the move).
        val key = listOf(board, ctx, minOf(d1, d2), maxOf(d1, d2), plies)
        val hit = cached?.takeIf { cacheKey == key }
        if (hit != null) {
            val mover = ctx.onRoll
            val target = played?.let { board.applyMove(mover, it) }
            val idx = if (target == null) -1 else hit.candidates.indexOfFirst { board.applyMove(mover, it.move) == target }
            val searched = idx >= 0 && (Depth.of(plies).plies <= 1 || hit.candidates[idx].plies == plies)
            if (target == null || searched) return hit.copy(playedIndex = idx)
            if (idx >= 0) {
                // The roll was ranked ahead (while the player thought) but the move played was left at
                // 1-ply by the filter: search it alone, as a ranking with it would have.
                val e = evaluate(board, ctx, listOf(target), Depth.of(plies).plies).first()
                val moved = hit.candidates[idx].copy(equity = e.equity, probs = e.probs, plies = plies)
                val list = (hit.candidates.filterIndexed { i, _ -> i != idx } + moved)
                    .sortedWith(compareByDescending<Candidate> { it.plies }.thenByDescending { it.equity })
                return RankResult(list, list.indexOf(moved)).also { cached = it }
            }
        }
        return rank(board, ctx, d1, d2, plies, played).also { cacheKey = key; cached = it }
    }

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
        if (depthOf.plies >= 2 && posts.size > 1) {
            val within = depthOf.filter.within
            val best = eq.max()
            // Every move whose 1-ply equity is within the window of the best is searched deeper, and
            // always the best few: a window alone can drop the right move by a hair (FlashBack Boot
            // Camp 6-2, 16/10 5/3: 0.164 behind at 1-ply, best by 0.29 at 3-ply).
            val deep = eq.indices.filter { eq[it] >= best - within }.toMutableSet()
            deep += eq.indices.sortedByDescending { eq[it] }.take(MIN_SEARCHED)
            if (playedIdx >= 0) deep += playedIdx
            val list = deep.toList()
            val evals = evaluate(board, ctx, list.map { posts[it] }, depthOf.plies)
            list.forEachIndexed { j, i ->
                probs[i] = evals[j].probs
                eq[i] = evals[j].equity
                depth[i] = plies
            }
        }
        // Searched candidates first (their values are on the deeper scale), each group by equity.
        val order = posts.indices.sortedWith(compareByDescending<Int> { depth[it] }.thenByDescending { eq[it] })
        val candidates = order.map { Candidate(moves[it], eq[it], probs[it], depth[it]) }
        return RankResult(candidates, if (playedIdx >= 0) order.indexOf(playedIdx) else -1)
    }

    override fun chooseMove(board: Board, ctx: CubeContext, d1: Int, d2: Int, level: CpuLevel): Move {
        val r = rankMoves(board, ctx, d1, d2, levelPlies(level), null)
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

    private fun cubeKeyOf(board: Board, ctx: CubeContext, plies: Int) = listOf(board, ctx, Depth.of(plies).plies)

    final override fun cubeDecision(board: Board, ctx: CubeContext, plies: Int): CubeEval {
        cachedCube(board, ctx, plies)?.let { return it }
        return computeCube(board, ctx, plies).also { cubeKey = cubeKeyOf(board, ctx, plies); cubeCached = it }
    }

    override fun cachedCube(board: Board, ctx: CubeContext, plies: Int): CubeEval? =
        cubeCached?.takeIf { cubeKey == cubeKeyOf(board, ctx, plies) }

    override fun cubeDecisionAtLevel(board: Board, ctx: CubeContext, level: CpuLevel): CubeEval {
        val e = cubeDecision(board, ctx, levelPlies(level))
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
            else -> 0
        }

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
