package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BlundersTest {

    private fun rec(player: Int, error: Float?, hinted: Boolean = false) = DecisionRecord(
        player = player, kind = DecisionKind.CHECKER, board = Board.INITIAL, cube = CubeState(),
        score = listOf(0, 0), crawford = false, error = error, hinted = hinted,
    )

    @Test
    fun onlyTheHumansUnhintedBlundersAreKept() {
        val game = GameState(
            decisions = listOf(
                rec(Player.HUMAN, 0.081f), // blunder
                rec(Player.HUMAN, 0.080f), // error (blunder is *more than* 0.080)
                rec(Player.CPU, 0.300f), // the computer's
                rec(Player.HUMAN, 0.200f, hinted = true), // after a hint
                rec(Player.HUMAN, null), // not analyzed
                rec(Player.HUMAN, 0.500f),
            ),
        )
        assertEquals(listOf(0 to 0.081f, 5 to 0.500f), Blunders.of(game, matchLength = 5))
    }

    @Test
    fun onePointMatchFactorApplies() {
        // 0.060 x 1.5 = 0.090: a blunder in a 1-point match only.
        val game = GameState(decisions = listOf(rec(Player.HUMAN, 0.060f)))
        assertEquals(emptyList<Pair<Int, Float>>(), Blunders.of(game, matchLength = 3))
        assertEquals(1, Blunders.of(game, matchLength = 1).size)
        assertEquals(0.090f, Blunders.of(game, matchLength = 1).single().second, 1e-6f)
    }
}
