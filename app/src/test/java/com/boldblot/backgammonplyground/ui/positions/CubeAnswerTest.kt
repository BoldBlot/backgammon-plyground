package com.boldblot.backgammonplyground.ui.positions

import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.CubeEval
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.PositionAnalysis
import com.boldblot.backgammonplyground.core.PositionSetup
import com.boldblot.backgammonplyground.core.StudyPosition
import com.boldblot.backgammonplyground.ui.components.err
import com.boldblot.backgammonplyground.ui.components.errWithUnit
import com.boldblot.backgammonplyground.ui.components.factorNote
import com.boldblot.backgammonplyground.data.ErrorUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class CubeAnswerTest {
    private val money = StudyPosition(PositionSetup(Board.INITIAL, Player.HUMAN, format = "XGID"), doubled = false, beavers = false)

    private fun eval(noDouble: Float, doubleTake: Float, doublePass: Float = 1f) = PositionAnalysis(
        cube = CubeEval(
            noDouble = noDouble, doubleTake = doubleTake, doublePass = doublePass, optimal = maxOf(noDouble, minOf(doubleTake, doublePass)),
            shouldDouble = minOf(doubleTake, doublePass) > noDouble, shouldTake = doubleTake <= doublePass,
            optional = false, cubeAvailable = true, probs = listOf(0.7f, 0.2f, 0f, 0.05f, 0f),
        ),
    )

    @Test fun doubleTakeIsBestWhenDoublingWinsAndTheTakeIsRight() {
        val a = eval(noDouble = 0.60f, doubleTake = 0.80f)
        assertEquals(CubeAnswer.DOUBLE_TAKE, bestCubeAnswer(money, a))
        assertEquals(0f, cubeAnswerError(money, a, CubeAnswer.DOUBLE_TAKE)!!, 1e-6f)
        // Right cube action, wrong guess of the answer: the taker's error (pass 1.0 vs take 0.8 from the doubler's side).
        assertEquals(0.20f, cubeAnswerError(money, a, CubeAnswer.DOUBLE_PASS)!!, 1e-6f)
        // No double: the doubling error only (the take guess is right).
        assertEquals(0.20f, cubeAnswerError(money, a, CubeAnswer.NO_DOUBLE)!!, 1e-6f)
        assertEquals(0.40f, cubeAnswerError(money, a, CubeAnswer.TOO_GOOD)!!, 1e-6f)
    }

    @Test fun tooGoodWhenPlayingOnBeatsACashAndTheOpponentShouldPass() {
        val a = eval(noDouble = 1.20f, doubleTake = 1.50f, doublePass = 1.0f)
        assertEquals(CubeAnswer.TOO_GOOD, bestCubeAnswer(money, a))
        assertEquals(0.20f, cubeAnswerError(money, a, CubeAnswer.DOUBLE_PASS)!!, 1e-6f)
        assertEquals(0.50f, cubeAnswerError(money, a, CubeAnswer.NO_DOUBLE)!!, 1e-6f)
    }

    @Test fun doublePassAndNoDouble() {
        assertEquals(CubeAnswer.DOUBLE_PASS, bestCubeAnswer(money, eval(noDouble = 0.80f, doubleTake = 1.2f, doublePass = 1.0f)))
        assertEquals(CubeAnswer.NO_DOUBLE, bestCubeAnswer(money, eval(noDouble = 0.30f, doubleTake = 0.10f)))
    }

    @Test fun errorUnits() {
        assertEquals("-0.026", err(-0.026f, ErrorUnit.EQ))
        assertEquals("-26.0", err(-0.026f, ErrorUnit.MEQ))
        assertEquals("0.0124 Eq", errWithUnit(0.0124, ErrorUnit.EQ))
        assertEquals("12.4 mEq", errWithUnit(0.0124, ErrorUnit.MEQ))
        assertEquals(" (×1.5 in PR)", factorNote(1.5f))
        assertEquals("", factorNote(1f))
    }
}
