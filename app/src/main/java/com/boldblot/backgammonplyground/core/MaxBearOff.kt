package com.boldblot.backgammonplyground.core

/**
 * Max Bearoff (the game chip): once contact is broken and all of the player's checkers are home, each
 * turn plays a play that bears off as many checkers as possible. When several do, 1-ply picks between them.
 */
object MaxBearOff {
    /** Whether Max Bearoff plays [player]'s turn on [board]: no contact, all checkers home. */
    fun applies(board: Board, player: Int): Boolean = board.isRace() && board.allHome(player)

    /** [player]'s legal plays with [d1]-[d2] that bear off the most checkers, one per resulting position. */
    fun plays(board: Board, player: Int, d1: Int, d2: Int): List<Move> {
        val moves = MoveGenerator.legalMoves(board, player, d1, d2)
        if (moves.isEmpty()) return emptyList()
        val most = moves.maxOf { board.applyMove(player, it).off(player) }
        return moves.filter { board.applyMove(player, it).off(player) == most }.distinctBy { board.applyMove(player, it) }
    }
}
