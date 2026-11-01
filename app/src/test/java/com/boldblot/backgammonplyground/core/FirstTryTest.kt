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

    @Test
    fun theReviewShowsTheFirstTryAndMarksTheTriesAndTheLastPlay() {
        val moves = MoveGenerator.legalMoves(Board.INITIAL, Player.HUMAN, 5, 1)
        val analysis = CheckerAnalysis(
            candidates = moves.take(4).mapIndexed { i, m -> Candidate(m, -0.01f * i, listOf(0.5f, 0.1f, 0f, 0.1f, 0f), 2) },
            playedIndex = 0, playedEquity = 0f, bestEquity = 0f, worstEquity = -0.03f, legalMoves = moves.size, plies = 2,
        )
        // First try: the 4th play; then the 3rd, taken back too; the best play at last.
        val d = DecisionRecord(
            Player.HUMAN, DecisionKind.CHECKER, Board.INITIAL, CubeState(), listOf(0, 0), false,
            dice = Dice(5, 1), move = moves[0], checker = analysis, error = 0f, counted = true,
            firstTry = FirstTry(DecisionKind.CHECKER, moves[3], 0.03f, true, retries = 2, tried = listOf(moves[2])),
        )
        assertEquals(moves[3], d.reviewMove)
        assertEquals(3, d.reviewIndex)
        assertEquals(setOf(3, 2), d.triedIndices)
        assertEquals(0.03f, d.scoredError)
        // Played once: as before.
        val once = d.copy(firstTry = null)
        assertEquals(moves[0], once.reviewMove)
        assertEquals(0, once.reviewIndex)
        assertEquals(emptySet<Int>(), once.triedIndices)
    }
}
