package com.boldblot.backgammonplyground.core

/** The plays a tap on the bear-off tray and a long press on a point ask for (the engine's 1-ply picks between them). */
object TapPlays {
    /**
     * [moves] that bear off two checkers; else the fewest above two (a roll that must take more off,
     * such as 6-6), else the most there is below two. Empty when none bears off.
     */
    fun bearOffTwo(board: Board, player: Int, moves: List<Move>): List<Move> {
        val off = { mv: Move -> board.applyMove(player, mv).off(player) - board.off(player) }
        val counts = moves.map(off).toSet()
        val want = when {
            2 in counts -> 2
            else -> counts.filter { it > 2 }.minOrNull() ?: counts.maxOrNull() ?: 0
        }
        return if (want == 0) emptyList() else moves.filter { off(it) == want }
    }

    /** [moves] that land a checker on [point] and leave two or more of [player]'s there. */
    fun makePoint(board: Board, player: Int, moves: List<Move>, point: Int): List<Move> =
        moves.filter { mv -> mv.steps.any { it.to == point } && board.applyMove(player, mv).count(player, point) >= 2 }
}
