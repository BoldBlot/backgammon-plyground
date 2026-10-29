package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.CubeEval
import com.boldblot.backgammonplyground.core.Depth

/**
 * [SearchEngine] on GNU Backgammon's networks, bear-off database, cubeful evaluation and match
 * equity table (Kazaross XG2) ([GnubgNative]). XG plies are GNU's plies + 1.
 */
class GnubgEngine : SearchEngine() {

    override fun evaluate(board: Board, ctx: CubeContext, posts: List<Board>, plies: Int): List<PostEval> {
        if (posts.isEmpty()) return emptyList()
        val boards = IntArray(50 * posts.size)
        posts.forEachIndexed { k, b -> b.toEngine(ctx.onRoll).copyInto(boards, 50 * k) }
        val r = GnubgNative.nativeScoreBoards(boards, ctx.toEngine(), plies - 1) ?: error("GNU Backgammon evaluation failed")
        return List(posts.size) { k -> PostEval(r[6 * k], List(5) { r[6 * k + 1 + it] }) }
    }

    override fun computeCube(board: Board, ctx: CubeContext, plies: Int): CubeEval {
        val r = GnubgNative.nativeCubeDecision(board.toEngine(ctx.onRoll), ctx.toEngine(), Depth.of(plies).plies - 1)
            ?: error("GNU Backgammon cube evaluation failed")
        return CubeEval(
            noDouble = r[0], doubleTake = r[1], doublePass = r[2], optimal = r[3],
            shouldDouble = r[5] != 0f, shouldTake = r[6] != 0f, optional = r[7] != 0f,
            cubeAvailable = r[8] != 0f, probs = List(5) { r[9 + it] },
        )
    }

    /** GNU's own luck (its 0-ply cubeful evaluation of the best play for each roll). */
    override fun luck(board: Board, ctx: CubeContext, d1: Int, d2: Int, firstRoll: Boolean): Float =
        GnubgNative.nativeLuck(board.toEngine(ctx.onRoll), d1, d2, ctx.toEngine(), firstRoll)

    companion object {
        /** Initializes the engine with data files in [dataDir] (gnubg.wd, gnubg_os0.bd, met/). */
        fun open(dataDir: String): GnubgEngine {
            check(GnubgNative.nativeInit(dataDir) == 0) { "GNU Backgammon engine failed to load its networks" }
            return GnubgEngine()
        }
    }
}
