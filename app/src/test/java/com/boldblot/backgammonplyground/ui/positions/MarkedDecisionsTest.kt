package com.boldblot.backgammonplyground.ui.positions

import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.Candidate
import com.boldblot.backgammonplyground.core.CheckerAnalysis
import com.boldblot.backgammonplyground.core.CubeEval
import com.boldblot.backgammonplyground.core.DecisionKind
import com.boldblot.backgammonplyground.core.Dice
import com.boldblot.backgammonplyground.core.MoveGenerator
import com.boldblot.backgammonplyground.core.MyAnswer
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.PositionAnalysis
import com.boldblot.backgammonplyground.core.PositionSetup
import com.boldblot.backgammonplyground.core.Severity
import com.boldblot.backgammonplyground.core.StudyPosition
import com.boldblot.backgammonplyground.ui.review.PositionShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** My positions: the plays and cube answers set by hand (played in the game, my answer) count as in Analyze Position. */
class MarkedDecisionsTest {
    private val cubePos = StudyPosition(PositionSetup(Board.INITIAL, Player.HUMAN, format = "XGID"), doubled = false, beavers = false)
    private val cube = PositionAnalysis(
        cube = CubeEval(0.80f, 1.20f, 1.00f, 1.00f, shouldDouble = true, shouldTake = false, optional = false, cubeAvailable = true, probs = emptyList()),
    )

    @Test
    fun `a whole cube answer costs the doubler's error and the answer's`() {
        val choices = cubeAnswerChoices(cubePos)
        assertEquals(listOf("No double", "Double, take", "Double, pass"), choices.map { answerText(cubePos, it) })
        assertEquals(0.20f, answerError(cubePos, cube, MyAnswer(cube = DecisionKind.DOUBLE, expect = DecisionKind.TAKE))!!, 1e-6f)
        assertEquals(0f, answerError(cubePos, cube, MyAnswer(cube = DecisionKind.DOUBLE, expect = DecisionKind.PASS))!!, 1e-6f)
        // An older answer, the double alone: only the doubler's part.
        assertEquals(0f, answerError(cubePos, cube, MyAnswer(cube = DecisionKind.DOUBLE))!!, 1e-6f)
        assertEquals(listOf("Take", "Pass"), cubeAnswerChoices(cubePos.copy(doubled = true)).map { answerText(cubePos, it) })
    }

    @Test
    fun `the picture tags what was played and my answer, each at its level`() {
        val played = PositionShare.marked(cubePos, cube, MyAnswer(cube = DecisionKind.NO_DOUBLE), MyAnswer(cube = DecisionKind.DOUBLE, expect = DecisionKind.TAKE))
        assertEquals(listOf("played", "my answer", null), played.map { it.tag })
        assertEquals(Severity.BLUNDER, played[0].severity)
        assertEquals(Severity.BLUNDER, played[1].severity)
        val same = PositionShare.marked(cubePos, cube, MyAnswer(cube = DecisionKind.DOUBLE, expect = DecisionKind.PASS), MyAnswer(cube = DecisionKind.DOUBLE, expect = DecisionKind.PASS))
        assertEquals("played, my answer", same[2].tag)
    }

    private val dice = Dice(3, 1)
    private val checkerPos = StudyPosition(PositionSetup(Board.INITIAL, Player.HUMAN, dice = dice, format = "XGID"), doubled = false, beavers = false)
    private val moves = MoveGenerator.legalMoves(Board.INITIAL, Player.HUMAN, 3, 1)

    private fun analysis(): CheckerAnalysis {
        // Six plays searched at "3-ply" (code 30), the rest at 1-ply (code 10).
        val cands = moves.mapIndexed { i, m -> Candidate(m, 0.2f - 0.05f * i, emptyList(), if (i < 6) 30 else 10) }
        return CheckerAnalysis(cands, -1, cands[0].equity, cands[0].equity, cands.last().equity, moves.size, 30)
    }

    @Test
    fun `a play searched apart joins the deep plays by its equity`() {
        val a = analysis()
        val late = moves.last()
        assertFalse(searchedDeeply(checkerPos, a, late))
        val b = a.withCandidate(Board.INITIAL, Player.HUMAN, Candidate(late, 0.08f, emptyList(), 30))
        assertTrue(searchedDeeply(checkerPos, b, late))
        assertEquals(a.candidates.size, b.candidates.size)
        // Between 0.10 (third) and 0.05 (fourth).
        assertEquals(late, b.candidates[3].move)
        assertEquals(List(7) { 30 } + List(b.candidates.size - 7) { 10 }, b.candidates.map { it.plies })
        // A better one becomes the best.
        val c = a.withCandidate(Board.INITIAL, Player.HUMAN, Candidate(late, 0.5f, emptyList(), 30))
        assertEquals(0.5f, c.bestEquity, 0f)
        assertEquals(late, c.candidates[0].move)
    }
}
