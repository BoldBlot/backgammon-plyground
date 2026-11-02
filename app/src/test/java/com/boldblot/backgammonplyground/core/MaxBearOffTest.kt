package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Max Bearoff (feedback 2026-11-02 #17). */
class MaxBearOffTest {
    private fun board(human: Map<Int, Int>, cpu: Map<Int, Int>): Board {
        val h = IntArray(25); human.forEach { (p, n) -> h[p - 1] = n }
        val c = IntArray(25); cpu.forEach { (p, n) -> c[p - 1] = n }
        return Board.fromSides(h, c)
    }

    @Test
    fun takesOffAsManyAsPossible() {
        // 6-5 with checkers on the 6, 5 and 1 points: 6/off 5/off bears off two; 6/1 5/off only one.
        val b = board(mapOf(6 to 1, 5 to 1, 1 to 1), mapOf(1 to 2))
        val plays = MaxBearOff.plays(b, Player.HUMAN, 6, 5)
        assertEquals(1, plays.size)
        assertEquals(2, b.applyMove(Player.HUMAN, plays[0]).off(Player.HUMAN) - b.off(Player.HUMAN))
    }

    @Test
    fun leavesTheChoiceWhenSeveralBearOffTheMost() {
        // 2-1 with checkers on the 4, 3 and 2 points: nothing comes off, every play is a candidate (1-ply decides).
        val b = board(mapOf(4 to 1, 3 to 1, 2 to 1), mapOf(1 to 2))
        assertTrue(MaxBearOff.plays(b, Player.HUMAN, 2, 1).size > 1)
    }

    @Test
    fun onlyInARaceWithAllCheckersHome() {
        assertTrue(MaxBearOff.applies(board(mapOf(6 to 3), mapOf(1 to 2)), Player.HUMAN))
        assertFalse(MaxBearOff.applies(board(mapOf(6 to 2, 8 to 1), mapOf(1 to 2)), Player.HUMAN))
        // Contact: a computer checker behind the human's.
        assertFalse(MaxBearOff.applies(board(mapOf(6 to 3), mapOf(20 to 1)), Player.HUMAN))
    }
}
