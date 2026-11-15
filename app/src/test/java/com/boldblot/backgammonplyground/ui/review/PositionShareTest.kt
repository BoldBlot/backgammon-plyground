package com.boldblot.backgammonplyground.ui.review

import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.CubeEval
import com.boldblot.backgammonplyground.core.DecisionKind
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.Severity
import org.junit.Assert.assertEquals
import org.junit.Test

/** The shared picture: the network named in its footer, and the decision made marked at its error level. */
class PositionShareTest {

    @Test
    fun `the footer names PureTD's network either way`() {
        assertEquals("PureTD 5L 3-ply, pruned", PositionShare.netLabel("PureTD 3-ply, pruned"))
        assertEquals("PureTD 5L 3/36/7", PositionShare.netLabel("PureTD 3/36/7"))
        assertEquals("PureTD 4L 3/36/7", PositionShare.netLabel("PureTD 4L 3/36/7"))
        assertEquals("Open Sage 3-ply", PositionShare.netLabel("Open Sage 3-ply"))
        assertEquals("eXtreme Gammon XG Roller++", PositionShare.netLabel("eXtreme Gammon XG Roller++"))
    }

    private val eval = CubeEval(
        0.80f, 1.20f, 1.00f, 1.00f, shouldDouble = true, shouldTake = false, optional = false, cubeAvailable = true, probs = emptyList(),
    )

    private fun lines(kind: DecisionKind?, answer: DecisionKind? = null, response: Boolean = false) = PositionShare.analysisLines(
        Board.INITIAL, Player.HUMAN, null, eval, response = response, beavers = false,
        playedIndex = null, playedKind = kind, factor = 1f, playedAnswer = answer,
    )

    @Test
    fun `a whole cube decision marks the answer chosen at the whole decision's level`() {
        // Double, take where it is a pass: the take row, an error of 0.20 (a blunder).
        val dt = lines(DecisionKind.DOUBLE, DecisionKind.TAKE)
        assertEquals(listOf(false, true, false), dt.map { it.played })
        assertEquals(Severity.BLUNDER, dt[1].severity)
        // Double, pass: right.
        val dp = lines(DecisionKind.DOUBLE, DecisionKind.PASS)
        assertEquals(listOf(false, false, true), dp.map { it.played })
        assertEquals(Severity.NONE, dp[2].severity)
        // A double without an answer goes on the row of the best answer, as in a review.
        assertEquals(listOf(false, false, true), lines(DecisionKind.DOUBLE).map { it.played })
        // No double: the doubler's error.
        assertEquals(Severity.BLUNDER, lines(DecisionKind.NO_DOUBLE)[0].severity)
        // With the double offered: take and pass only.
        val take = lines(DecisionKind.TAKE, response = true)
        assertEquals(listOf("Take", "Pass"), take.map { it.text })
        assertEquals(listOf(true, false), take.map { it.played })
        assertEquals(Severity.BLUNDER, take[0].severity)
    }
}
