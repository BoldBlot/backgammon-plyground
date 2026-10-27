package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.CubeEval
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.Player

/**
 * [SearchEngine] on Open Sage's production networks (stage 11), bear-off database, cubeful search
 * and match equity table ([SageNative]).
 */
class SageEngine(private val handle: Long) : SearchEngine() {

    /** [board] from [me]'s point of view, in Open Sage's layout. */
    private fun sage(board: Board, me: Int, out: IntArray = IntArray(26), at: Int = 0): IntArray {
        val opp = Player.other(me)
        for (i in 0 until 24) out[at + 1 + i] = board.count(me, i) - board.count(opp, 23 - i)
        out[at + 25] = board.bar(me)
        out[at] = board.bar(opp)
        return out
    }

    private fun cube(ctx: CubeContext): IntArray {
        val me = ctx.onRoll
        val owner = when {
            ctx.cube.centred -> 0
            ctx.cube.owner == me -> 1
            else -> 2
        }
        val money = ctx.matchLength == 0
        return intArrayOf(
            ctx.cube.value, owner,
            if (money) 0 else ctx.matchLength - ctx.score[me], if (money) 0 else ctx.matchLength - ctx.score[Player.other(me)],
            if (ctx.crawford) 1 else 0, if (ctx.jacoby) 1 else 0, 0,
        )
    }

    override fun evaluate(board: Board, ctx: CubeContext, posts: List<Board>, plies: Int): List<PostEval> {
        if (posts.isEmpty()) return emptyList()
        val me = ctx.onRoll
        val s = IntArray(26 * posts.size)
        posts.forEachIndexed { k, b -> sage(b, me, s, 26 * k) }
        val r = SageNative.nativeScore(handle, sage(board, me), s, cube(ctx), plies) ?: error("Open Sage evaluation failed")
        return List(posts.size) { k -> PostEval(r[6 * k], List(5) { r[6 * k + 1 + it] }) }
    }

    override fun cubeDecision(board: Board, ctx: CubeContext, plies: Int): CubeEval {
        val r = SageNative.nativeCube(handle, sage(board, ctx.onRoll), cube(ctx), Depth.of(plies).plies)
            ?: error("Open Sage cube evaluation failed")
        return evalOf(r[0].toDouble(), r[1].toDouble(), r[2].toDouble(), r[7] != 0f, List(5) { r[8 + it] })
    }
}
