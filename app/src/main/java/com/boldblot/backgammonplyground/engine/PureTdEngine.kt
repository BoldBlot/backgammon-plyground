package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.AnalysisEngine
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.Candidate
import com.boldblot.backgammonplyground.core.CpuLevel
import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.CubeEval
import com.boldblot.backgammonplyground.core.CubeView
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.MAX_PLIES
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.MatchCube
import com.boldblot.backgammonplyground.core.MatchEquityTable
import com.boldblot.backgammonplyground.core.Move
import com.boldblot.backgammonplyground.core.MoveGenerator
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.RankResult
import com.boldblot.backgammonplyground.core.cubeLife
import com.boldblot.backgammonplyground.core.invertProbs
import com.boldblot.backgammonplyground.core.moneyJanowski
import java.util.Random
import kotlin.math.abs
import kotlin.math.min

/**
 * [AnalysisEngine] on PureTD's networks ([PureTdNative]). Depth `plies` is a [Depth] code: XG plies
 * 1-4 and, for checker play, the root filter (moves within a 1-ply equity window of the best go to
 * the full depth). Money cube decisions come from the cube-aware search with PureTD's cubeful
 * network; match cube decisions from our match Janowski and our own match equity table on
 * probabilities at the same depth. Not thread-safe: use one thread ([EngineService]).
 */
class PureTdEngine(private val handle: Long, private val met: MatchEquityTable = MatchEquityTable.DEFAULT) : AnalysisEngine {

    private val rng = Random()

    // ---- positions ------------------------------------------------------------------------------

    /** Engine position with [white] as WHITE; [turn] 0 = WHITE to act next, 1 = the other side. */
    private fun state(board: Board, white: Int, turn: Int, out: IntArray = IntArray(29), at: Int = 0): IntArray {
        val black = Player.other(white)
        for (i in 0 until 24) {
            val m = board.count(white, i)
            out[at + i] = if (m > 0) m else -board.count(black, 23 - i)
        }
        out[at + 24] = board.bar(white); out[at + 25] = board.bar(black)
        out[at + 26] = board.off(white); out[at + 27] = board.off(black)
        out[at + 28] = turn
        return out
    }

    /** Probabilities for [mover] of positions right after its move (the opponent to roll). */
    private fun postProbs(mover: Int, boards: List<Board>, plies: Int): List<List<Float>> {
        if (boards.isEmpty()) return emptyList()
        val s = IntArray(29 * boards.size)
        boards.forEachIndexed { k, b -> state(b, mover, 1, s, 29 * k) }
        val r = PureTdNative.nativePostProbs(handle, s, plies) ?: error("PureTD evaluation failed")
        return List(boards.size) { k -> List(5) { r[5 * k + it] } }
    }

    /** Pre-roll probabilities for the player on roll. */
    private fun preProbs(board: Board, onRoll: Int, plies: Int): List<Float> {
        val r = PureTdNative.nativePostProbs(handle, state(board, onRoll, 0), plies) ?: error("PureTD evaluation failed")
        return invertProbs(List(5) { r[it] })
    }

    private fun view(ctx: CubeContext, player: Int): CubeView = when {
        ctx.cube.centred -> CubeView.CENTRED
        ctx.cube.owner == player -> CubeView.MINE
        else -> CubeView.THEIRS
    }

    private fun away(ctx: CubeContext, player: Int) = ctx.matchLength - ctx.score[player]

    /** Cubeful equity (normalized, EMG in matches) for the mover of [post], from its probabilities. */
    private fun postEquity(post: Board, ctx: CubeContext, p: List<Float>): Float {
        val mover = ctx.onRoll
        val opp = Player.other(mover)
        val v = ctx.cube.value
        val finished = post.off(mover) == 15
        if (ctx.matchLength == 0) {
            if (finished) return if (ctx.jacoby && ctx.cube.centred) 1f else 1 + p[1] + p[2]
            return (-moneyJanowski(invertProbs(p), cubeLife(post), view(ctx, opp), ctx.jacoby && ctx.cube.centred)).toFloat()
        }
        val mine = MatchCube(met, p, away(ctx, mover), away(ctx, opp), ctx.crawford, cubeLife(post))
        if (finished) {
            val pts = (1 + p[1] + p[2]).toInt()
            return mine.toEmg(met.after(away(ctx, mover), away(ctx, opp), ctx.crawford, pts * v, 0), v).toFloat()
        }
        val theirs = MatchCube(met, invertProbs(p), away(ctx, opp), away(ctx, mover), ctx.crawford, cubeLife(post))
        return mine.toEmg(1 - theirs.cubeful(view(ctx, opp), v), v).toFloat()
    }

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
        }
        return rank(board, ctx, d1, d2, plies, played).also { cacheKey = key; cached = it }
    }

    private fun rank(board: Board, ctx: CubeContext, d1: Int, d2: Int, plies: Int, played: Move?): RankResult {
        val mover = ctx.onRoll
        val moves = MoveGenerator.legalMoves(board, mover, d1, d2)
        if (moves.isEmpty()) return RankResult(emptyList(), -1)
        val posts = moves.map { board.applyMove(mover, it) }
        val probs = postProbs(mover, posts, 1).toMutableList()
        val eq = posts.indices.map { postEquity(posts[it], ctx, probs[it]) }.toMutableList()
        val depth = IntArray(posts.size)
        val playedIdx = played?.let { posts.indexOf(board.applyMove(mover, it)) } ?: -1
        val depthOf = Depth.of(plies)
        if (depthOf.plies >= 2 && posts.size > 1) {
            val within = depthOf.filter.within
            val best = eq.max()
            // Every move whose 1-ply equity is within the window of the best is searched deeper.
            val deep = eq.indices.filter { eq[it] >= best - within }.toMutableSet()
            if (playedIdx >= 0) deep += playedIdx
            val list = deep.toList()
            val p3 = postProbs(mover, list.map { posts[it] }, depthOf.plies)
            list.forEachIndexed { j, i ->
                probs[i] = p3[j]
                eq[i] = postEquity(posts[i], ctx, p3[j])
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

    override fun cubeDecision(board: Board, ctx: CubeContext, plies: Int): CubeEval {
        val me = ctx.onRoll
        val v = ctx.cube.value
        val owner = view(ctx, me)
        val depth = Depth.of(plies).plies
        val nd: Double
        val dt: Double
        val dp: Double
        val probs: List<Float>
        val available: Boolean
        if (ctx.matchLength == 0) {
            val r = PureTdNative.nativeCube(handle, state(board, me, 0), depth, owner.ordinal, ctx.jacoby && ctx.cube.centred)
                ?: error("PureTD cube evaluation failed")
            nd = r[0].toDouble(); dt = r[1].toDouble(); dp = 1.0
            probs = List(5) { r[2 + it] }
            available = owner != CubeView.THEIRS
        } else {
            probs = preProbs(board, me, depth)
            val mc = MatchCube(met, probs, away(ctx, me), away(ctx, Player.other(me)), ctx.crawford, cubeLife(board))
            val (a, b, c) = mc.decision(owner, v)
            nd = mc.toEmg(a, v); dt = mc.toEmg(b, v); dp = mc.toEmg(c, v)
            available = mc.canDoubleNow(owner, v)
        }
        return evalOf(nd, dt, dp, available, probs)
    }

    private fun evalOf(nd: Double, dt: Double, dp: Double, available: Boolean, probs: List<Float>): CubeEval {
        val after = min(dt, dp)
        val double = available && after > nd
        return CubeEval(
            noDouble = nd.toFloat(), doubleTake = dt.toFloat(), doublePass = dp.toFloat(),
            optimal = (if (double) after else nd).toFloat(), shouldDouble = double, shouldTake = dt <= dp,
            optional = available && abs(nd - after) < 1e-4, cubeAvailable = available, probs = probs,
        )
    }

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
        val all = groups.flatten()
        val probs = postProbs(mover, all, 1)
        var k = 0
        var mean = 0.0
        var actual = 0.0
        groups.forEachIndexed { i, g ->
            var best = -1e9
            for (post in g) best = maxOf(best, postEquity(post, ctx, probs[k++]).toDouble())
            mean += rolls[i].third * best
            val (a, b, _) = rolls[i]
            if (a == min(d1, d2) && b == maxOf(d1, d2)) actual = best
        }
        return (actual - mean).toFloat()
    }

    /** PureTD's own move generator, as resulting boards (used to cross-check the Kotlin one in tests). */
    fun legalBoards(board: Board, player: Int, d1: Int, d2: Int): Set<Board> {
        val r = PureTdNative.nativeLegalStates(state(board, player, 0), d1, d2) ?: return emptySet()
        return List(r.size / 29) { k ->
            val o = 29 * k
            val me = IntArray(25)
            val them = IntArray(25)
            for (i in 0 until 24) {
                val v = r[o + i]
                if (v > 0) me[i] = v else if (v < 0) them[23 - i] = -v
            }
            me[24] = r[o + 24]; them[24] = r[o + 25]
            if (player == Player.HUMAN) Board.fromSides(me, them) else Board.fromSides(them, me)
        }.toSet()
    }

    // GNU Backgammon's position and match IDs are not produced by this engine.
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
