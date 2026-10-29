package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.CubeEval
import com.boldblot.backgammonplyground.core.CubeView
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.MatchCube
import com.boldblot.backgammonplyground.core.MatchEquityTable
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.cubeLife
import com.boldblot.backgammonplyground.core.invertProbs
import com.boldblot.backgammonplyground.core.moneyJanowski

/**
 * [AnalysisEngine] on PureTD's networks ([PureTdNative]). Depth `plies` is a [Depth] code: XG plies
 * 1-4 and, for checker play, the root filter (moves within a 1-ply equity window of the best go to
 * the full depth). Money cube decisions come from the cube-aware search with PureTD's cubeful
 * network; match cube decisions from the cube-aware match search ([MatchCubeSearch]: our match
 * Janowski and our own match equity table at its leaves), or that Janowski alone at 1-ply. The checker search, levels and luck are [SearchEngine]'s.
 */
class PureTdEngine(private val handle: Long, private val met: MatchEquityTable = MatchEquityTable.DEFAULT) : SearchEngine() {

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

    override fun evaluate(board: Board, ctx: CubeContext, posts: List<Board>, plies: Int): List<PostEval> {
        val probs = postProbs(ctx.onRoll, posts, plies)
        return posts.indices.map { PostEval(postEquity(posts[it], ctx, probs[it]), probs[it]) }
    }

    // ---- cube -----------------------------------------------------------------------------------

    override fun computeCube(board: Board, ctx: CubeContext, plies: Int): CubeEval {
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
            // Deeper than 1-ply: the cube-aware search, with cube decisions at every node of the tree.
            val search = if (depth >= 2) matchSearch(board, ctx, depth) else null
            probs = search?.probs() ?: preProbs(board, me, depth)
            val mc = MatchCube(met, probs, away(ctx, me), away(ctx, Player.other(me)), ctx.crawford, cubeLife(board))
            val (a, b, c) = search?.root(owner, v) ?: mc.decision(owner, v)
            nd = mc.toEmg(a, v); dt = mc.toEmg(b, v); dp = mc.toEmg(c, v)
            available = mc.canDoubleNow(owner, v)
        }
        return evalOf(nd, dt, dp, available, probs)
    }

    /** The match cube search for the player on roll, [depth] XG plies (depth - 1 rolls deep). */
    private fun matchSearch(board: Board, ctx: CubeContext, depth: Int): MatchCubeSearch {
        val me = ctx.onRoll
        val levels = depth - 1
        val tree = PureTdNative.nativeCubeTree(handle, state(board, me, 0), levels) ?: error("PureTD cube search failed")
        return MatchCubeSearch(met, tree, levels, away(ctx, me), away(ctx, Player.other(me)), ctx.crawford)
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
}
