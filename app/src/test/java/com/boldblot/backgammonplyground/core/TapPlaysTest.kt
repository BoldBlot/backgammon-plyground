package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The plays behind a tap on the bear-off tray and a long press on a point (feedback 2026-11-07 #2, #3). */
class TapPlaysTest {
    private fun board(human: Map<Int, Int>, cpu: Map<Int, Int>): Board {
        val h = IntArray(25); human.forEach { (p, n) -> h[p - 1] = n }
        val c = IntArray(25); cpu.forEach { (p, n) -> c[p - 1] = n }
        return Board.fromSides(h, c)
    }

    private fun off(b: Board, m: Move) = b.applyMove(Player.HUMAN, m).off(Player.HUMAN) - b.off(Player.HUMAN)

    @Test
    fun trayBearsOffTwoNotMore() {
        // 6-5 with checkers on the 6, 5, 4 and 3 points: 6/off 5/off takes two, other plays take one or none.
        val b = board(mapOf(6 to 1, 5 to 1, 4 to 1, 3 to 1), mapOf(1 to 2))
        val plays = TapPlays.bearOffTwo(b, Player.HUMAN, MoveGenerator.legalMoves(b, Player.HUMAN, 6, 5))
        assertTrue(plays.isNotEmpty())
        plays.forEach { assertEquals(2, off(b, it)) }
    }

    @Test
    fun trayTakesTheFewestAboveTwoWhenTheRollForcesMore() {
        // 6-6 with five checkers on the 6 point: every play bears off four.
        val b = board(mapOf(6 to 5), mapOf(1 to 2))
        val plays = TapPlays.bearOffTwo(b, Player.HUMAN, MoveGenerator.legalMoves(b, Player.HUMAN, 6, 6))
        assertTrue(plays.isNotEmpty())
        plays.forEach { assertEquals(4, off(b, it)) }
    }

    @Test
    fun trayTakesTheMostWhenTwoIsImpossible() {
        // 6-1 with checkers on the 6, 4 and 3 points: only the six comes off (1 checker).
        val b = board(mapOf(6 to 1, 4 to 1, 3 to 1), mapOf(1 to 2))
        val plays = TapPlays.bearOffTwo(b, Player.HUMAN, MoveGenerator.legalMoves(b, Player.HUMAN, 6, 1))
        assertTrue(plays.isNotEmpty())
        plays.forEach { assertEquals(1, off(b, it)) }
    }

    @Test
    fun trayDoesNothingWithoutABearOff() {
        val b = Board.INITIAL
        assertTrue(TapPlays.bearOffTwo(b, Player.HUMAN, MoveGenerator.legalMoves(b, Player.HUMAN, 3, 1)).isEmpty())
    }

    @Test
    fun longPressMakesThePoint() {
        // Opening 3-1: the 5 point (index 4) is made by 8/5 6/5.
        val b = Board.INITIAL
        val plays = TapPlays.makePoint(b, Player.HUMAN, MoveGenerator.legalMoves(b, Player.HUMAN, 3, 1), 4)
        assertEquals(1, plays.size)
        assertEquals(2, b.applyMove(Player.HUMAN, plays[0]).count(Player.HUMAN, 4))
    }

    @Test
    fun longPressWithNoWayToMakeItFindsNothing() {
        val b = Board.INITIAL
        assertTrue(TapPlays.makePoint(b, Player.HUMAN, MoveGenerator.legalMoves(b, Player.HUMAN, 6, 5), 2).isEmpty())
    }
}
