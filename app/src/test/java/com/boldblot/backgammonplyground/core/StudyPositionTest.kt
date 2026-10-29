package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPositionTest {

    // Figure 2 of the PureTD article: X to play 3-1, O owns the cube at 2, money.
    private val xToPlay = "XGID=-CBaCBD-----a----a-bbbbca-:1:-1:1:31:0:0:3:0:10"

    @Test
    fun keepsThePlayerOnRollAtTheBottom() {
        val p = StudyPosition.parse(xToPlay)
        assertEquals(Dice(3, 1), p.dice)
        assertFalse(p.doubled)
        assertEquals(Player.HUMAN, p.setup.onRoll)
        assertEquals(CubeState(2, Player.CPU), p.setup.cube)
        assertEquals(PositionIds.parse(xToPlay).board, p.board)
    }

    @Test
    fun turnsAPositionWithTheTopPlayerOnRoll() {
        val o = PositionIds.parse(xToPlay).let { s -> PositionIds.toXgid(StudyPosition.turned(s, Player.CPU).copy(onRoll = Player.CPU)) }
        val p = StudyPosition.parse(o)
        // Turned back: the same position as the original, the decider at the bottom.
        assertEquals(PositionIds.parse(xToPlay).board, p.board)
        assertEquals(Player.HUMAN, p.setup.onRoll)
        assertEquals(CubeState(2, Player.CPU), p.setup.cube)
    }

    @Test
    fun aDoubleOnOfferIsTheAnswerersDecision() {
        // O on roll has doubled X (dice field "D"): X decides take or pass.
        val doubled = "XGID=-CBaCBD-----a----a-bbbbca-:1:0:-1:D:0:0:3:0:10"
        val p = StudyPosition.parse(doubled)
        assertTrue(p.doubled)
        assertEquals(null, p.dice)
        assertEquals(Player.CPU, p.setup.onRoll)
        assertEquals(PositionIds.parse(doubled).board, p.board)
        // Stored as the doubler's cube decision, and read back the same.
        val back = StudyPosition.stored(p.xgid, p.doubled, p.beavers)
        assertEquals(p.board, back.board)
        assertEquals(p.setup.onRoll, back.setup.onRoll)
        assertTrue(back.doubled)
    }

    @Test
    fun theComputersTakeIsSavedFromItsSide() {
        val s = PositionIds.parse(xToPlay)
        val d = DecisionRecord(
            player = Player.CPU, kind = DecisionKind.TAKE, board = s.board, cube = CubeState(), score = listOf(0, 0), crawford = false,
        )
        val p = StudyPosition.of(d, matchLength = 0, jacoby = true)!!
        assertTrue(p.doubled)
        // The computer (who answers) is now at the bottom; the human doubled, from the top.
        assertEquals(s.board.count(Player.CPU, 5), p.board.count(Player.HUMAN, 5))
        assertEquals(Player.CPU, p.setup.onRoll)
    }
}
