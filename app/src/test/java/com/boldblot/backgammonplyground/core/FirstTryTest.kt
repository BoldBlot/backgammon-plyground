package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FirstTryTest {

    private fun game(vararg decisions: DecisionRecord) = GameState(
        phase = Phase.OVER, decisions = decisions.toList(),
        result = GameResult(Player.HUMAN, WinType.SINGLE, 1),
    )

    private fun rec(kind: DecisionKind, error: Float, firstTry: FirstTry? = null) = DecisionRecord(
        Player.HUMAN, kind, Board.INITIAL, CubeState(), listOf(0, 0), false, error = error, counted = true, firstTry = firstTry,
    )

    @Test
    fun aDecisionTriedAgainCountsItsFirstTry() {
        // Rolled when a double was right (a 0.10 blunder), then doubled on the second try.
        val retried = rec(DecisionKind.DOUBLE, 0f, FirstTry(DecisionKind.NO_DOUBLE, error = 0.10f, counted = true))
        assertEquals(0.10f, retried.scoredError)
        val p = Summaries.of(game(retried), matchLength = 0).performance[Player.HUMAN]
        assertEquals(1, p.doubleDecisions)
        assertEquals(1, p.doubleBlunders)
        assertEquals(0.10, p.doubleError, 1e-6)
    }

    @Test
    fun aDecisionPlayedOnceCountsAsBefore() {
        val p = Summaries.of(game(rec(DecisionKind.TAKE, 0.03f)), matchLength = 0).performance[Player.HUMAN]
        assertEquals(1, p.takeErrors)
        assertEquals(0.03, p.takeError, 1e-6)
    }
}
