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
class PureTdEngine(
    private val handle: Long,
    private val met: MatchEquityTable = MatchEquityTable.DEFAULT,
    /** What an analysis records as its engine: "PureTD" for the wide net, "PureTD 4L" for the compact one. */
    private val netName: String = com.boldblot.backgammonplyground.core.EngineKind.PURETD.label,
) : SearchEngine() {
    /**
     * In matches, each side picks its moves inside the search by its match-winning chance (true), or by money
     * equity as up to 0.8.8 (false: for benchmarks only).
     */
    internal var matchAwareSearch: Boolean = true

    override val name: String get() = netName


    /**
     * Money checker plays (a setting, on by default; off here for tests) scored with the cubeful network. Each
     * position after a play is the opponent's turn, valued by the cube-aware money search (the
     * money cube decisions' search, cubeful network at its leaves) as deep as the prob5 search
     * would look; the mover's equity is minus the opponent's best (double or not).
     */
    @Volatile var cubefulCheckers: Boolean = false

    override val rankingMode: Any? get() = cubefulCheckers to matchAwareSearch

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
    private fun postProbs(mover: Int, boards: List<Board>, plies: Int, weights: FloatArray?): List<List<Float>> {
        if (boards.isEmpty()) return emptyList()
        val s = IntArray(29 * boards.size)
        boards.forEachIndexed { k, b -> state(b, mover, 1, s, 29 * k) }
        val r = PureTdNative.nativePostProbs(handle, s, plies, weights) ?: error("PureTD evaluation failed")
        return List(boards.size) { k -> List(5) { r[5 * k + it] } }
    }

    /** Pre-roll probabilities for the player on roll. */
    private fun preProbs(board: Board, onRoll: Int, plies: Int, weights: FloatArray?): List<Float> {
        val r = PureTdNative.nativePostProbs(handle, state(board, onRoll, 0), plies, weights) ?: error("PureTD evaluation failed")
        return invertProbs(List(5) { r[it] })
    }

    private fun view(ctx: CubeContext, player: Int): CubeView = when {
        ctx.cube.centred -> CubeView.CENTRED
        ctx.cube.owner == player -> CubeView.MINE
        else -> CubeView.THEIRS
    }

    private fun away(ctx: CubeContext, player: Int) = ctx.matchLength - ctx.score[player]

    /**
     * Weights of a side's 5 probabilities (win, gammon, backgammon, lose gammon, lose backgammon) in its
     * match-winning chance, for [white] then the other side: what each side maximizes when it picks its
     * move inside the search. Null for money games (the search then uses money equity).
     */
    internal fun matchWeights(ctx: CubeContext, white: Int): FloatArray? {
        if (ctx.matchLength == 0 || !matchAwareSearch) return null
        val v = ctx.cube.value
        fun side(me: Int): List<Float> {
            val a = away(ctx, me); val b = away(ctx, Player.other(me))
            fun after(won: Int, lost: Int) = met.after(a, b, ctx.crawford, won, lost)
            val l1 = after(0, v); val lg = after(0, 2 * v); val lb = after(0, 3 * v)
            val w1 = after(v, 0); val wg = after(2 * v, 0); val wb = after(3 * v, 0)
            return listOf(w1 - l1, wg - w1, wb - wg, lg - l1, lb - lg).map { it.toFloat() }
        }
        return (side(white) + side(Player.other(white))).toFloatArray()
    }

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

    /**
     * Both sides bearing off with a checker off each: PureTD's bear-off table gives exact winning chances at 1-ply (no
     * gammons left), so the best play is found there. Not with the cubeful network (money cubeful checker play).
     */
    override fun exactAtOnePly(ctx: CubeContext, posts: List<Board>): Boolean =
        !(cubefulCheckers && ctx.matchLength == 0) && hasBearoff &&
            posts.all { b -> (0..1).all { p -> b.allHome(p) && b.off(p) > 0 } }

    private val hasBearoff: Boolean by lazy { PureTdNative.nativeHasBearoff(handle) }

    override fun evaluate(board: Board, ctx: CubeContext, posts: List<Board>, plies: Int): List<PostEval> {
        if (cubefulCheckers && ctx.matchLength == 0) return posts.map { cubefulPost(it, ctx, plies) }
        val probs = postProbs(ctx.onRoll, posts, plies, matchWeights(ctx, ctx.onRoll))
        return posts.indices.map { PostEval(postEquity(posts[it], ctx, probs[it]), probs[it]) }
    }

    /** [post] (after [ctx].onRoll's play) valued for the mover by the opponent's cube-aware money search. */
    private fun cubefulPost(post: Board, ctx: CubeContext, plies: Int): PostEval {
        val mover = ctx.onRoll
        val opp = Player.other(mover)
        if (post.off(mover) == 15) {
            val probs = postProbs(mover, listOf(post), 1, null)[0]
            return PostEval(postEquity(post, ctx, probs), probs)
        }
        val owner = view(ctx, opp)
        val r = PureTdNative.nativeCube(handle, state(post, opp, 0), plies, owner.ordinal, ctx.jacoby && ctx.cube.centred)
            ?: error("PureTD cube evaluation failed")
        val nd = r[0]
        val dt = r[1]
        val theirs = if (owner != CubeView.THEIRS) maxOf(nd, minOf(dt, 1f)) else nd
        return PostEval(-theirs, invertProbs(List(5) { r[2 + it] }))
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
            probs = search?.probs() ?: preProbs(board, me, depth, matchWeights(ctx, me))
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
        val tree = PureTdNative.nativeCubeTree(handle, state(board, me, 0), levels, matchWeights(ctx, me)) ?: error("PureTD cube search failed")
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
