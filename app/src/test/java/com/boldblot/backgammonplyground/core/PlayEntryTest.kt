package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Analyze Position's "Your decision": entering either side's play by taps and drags, and the error of a cube decision. */
class PlayEntryTest {

    private fun text(e: PlayEntry, steps: List<Step>) = MoveNotation.format(e.board, e.player, Move(steps))

    @Test
    fun `taps enter your play, the higher die first`() {
        val e = PlayEntry(Board.INITIAL, Player.HUMAN, 3, 1)
        val a = e.tap(emptyList(), 7)!!          // the 8-point: 8/5
        val b = e.tap(a, 5)!!                     // the 6-point: 6/5
        assertTrue(e.isComplete(b))
        assertEquals("8/5 6/5", text(e, b))
        assertEquals(Move(b), e.move(b))
        assertNull("nothing moves once the play is complete", e.tap(b, 12))
    }

    @Test
    fun `taps and drags enter the opponent's play in their own numbering`() {
        val e = PlayEntry(Board.INITIAL, Player.CPU, 3, 1)
        // Their 8-point is the 17th from the bottom right (human orientation 16).
        assertTrue(16 in e.movable(emptyList()))
        val a = e.tap(emptyList(), 16)!!
        assertEquals(listOf(Step(7, 4)), a)
        // A drag from their mid-point (human 11) to their 10-point (human 14): 13/10.
        val b = e.drag(emptyList(), 11, 14)!!
        assertEquals(listOf(Step(12, 9)), b)
        assertNull("13/7 is not a 3-1", e.drag(emptyList(), 11, 17))
        assertEquals("13/9 takes both dice", 2, e.drag(emptyList(), 11, 15)!!.size)
        assertTrue(e.isPointTap(emptyList(), 23 - 4))
        val point = e.pointPlays(emptyList(), 23 - 4)
        assertEquals(listOf("8/5 6/5"), point.map { text(e, it.steps) })
        assertEquals(e.boardAfter(point.single().steps), Board.INITIAL.applyMove(Player.CPU, point.single().move))
    }

    @Test
    fun `a roll that cannot be played is the decision`() {
        // Two checkers on the bar against a closed board.
        val me = IntArray(25).also { it[BAR] = 2; it[5] = 13 }
        val opp = IntArray(25).also { for (i in 0..5) it[i] = 2; it[18] = 3 }
        val e = PlayEntry(Board.fromSides(me, opp), Player.HUMAN, 6, 5)
        assertEquals(Move.NONE, e.move(emptyList()))
    }

    private fun eval(nd: Float, dt: Float, dp: Float) = CubeEval(
        nd, dt, dp, maxOf(nd, minOf(dt, dp)), shouldDouble = minOf(dt, dp) > nd, shouldTake = dt <= dp,
        optional = false, cubeAvailable = true, probs = emptyList(),
    )

    @Test
    fun `the whole cube decision adds the answer's error`() {
        // Double, pass: no double 0.80, double/take 1.20, double/pass 1.00.
        val e = eval(0.80f, 1.20f, 1.00f)
        assertEquals(0f, CubeChoice.DOUBLE_PASS.error(e), 1e-6f)
        assertEquals(0.20f, CubeChoice.NO_DOUBLE.error(e), 1e-6f)
        // The doubler is right to double; the take costs the taker 0.20.
        assertEquals(0.20f, CubeChoice.DOUBLE_TAKE.error(e), 1e-6f)
        // With the double offered only the answer counts.
        assertEquals(0.20f, CubeChoice.TAKE.error(e), 1e-6f)
        assertEquals(0f, CubeChoice.PASS.error(e), 1e-6f)
        // No double, take: no double 0.50, double/take 0.40 — doubling costs 0.10, the take is right.
        val early = eval(0.50f, 0.40f, 1.00f)
        assertEquals(0.10f, CubeChoice.DOUBLE_TAKE.error(early), 1e-6f)
        assertEquals(0.10f + 0.60f, CubeChoice.DOUBLE_PASS.error(early), 1e-6f)
    }
}
