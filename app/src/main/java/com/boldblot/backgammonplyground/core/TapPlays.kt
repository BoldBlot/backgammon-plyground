package com.boldblot.backgammonplyground.core

/** The plays a tap on the bear-off tray and a tap on an empty point or an opponent's blot ask for (the engine's 1-ply picks between them). */
object TapPlays {
    /** [moves] that bear off the most checkers (empty when none bears off). */
    fun bearOffMost(board: Board, player: Int, moves: List<Move>): List<Move> {
        val off = { mv: Move -> board.applyMove(player, mv).off(player) - board.off(player) }
        val most = moves.maxOfOrNull(off) ?: 0
        return if (most == 0) emptyList() else moves.filter { off(it) == most }
    }

    /** A play that makes a point: the complete [move] it comes from (for ranking) and the steps to put on the board. */
    class PointPlay(val move: Move, val steps: List<Step>)

    /**
     * Plays that keep the [done] steps already on the board and add only the steps that land on [point], so
     * that two or more of [player]'s checkers are there; other checkers stay where they are. [board] is the
     * position at the start of the turn, [moves] its complete plays.
     */
    fun makePoint(board: Board, player: Int, moves: List<Move>, point: Int, done: List<Step>): List<PointPlay> =
        moves.mapNotNull { mv ->
            val rest = mv.steps.toMutableList()
            for (s in done) if (!rest.remove(s)) return@mapNotNull null
            val have = runCatching { done.fold(board) { b, s -> b.applyStep(player, s) } }.getOrNull()?.count(player, point) ?: return@mapNotNull null
            val extra = rest.filter { it.to == point }.take(maxOf(2 - have, 1))   // two checkers make it; more is not asked for
            if (extra.isEmpty()) return@mapNotNull null
            val steps = done + extra
            val after = runCatching { steps.fold(board) { b, s -> b.applyStep(player, s) } }.getOrNull() ?: return@mapNotNull null
            if (after.count(player, point) >= 2) PointPlay(mv, steps) else null
        }.distinctBy { it.steps }
}
