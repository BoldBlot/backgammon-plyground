package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The plays behind a tap on the bear-off tray and a tap on an empty point (feedback 2026-11-07 #2, #3, 20261108). */
class TapPlaysTest {
    private fun board(human: Map<Int, Int>, cpu: Map<Int, Int>): Board {
        val h = IntArray(25); human.forEach { (p, n) -> h[p - 1] = n }
        val c = IntArray(25); cpu.forEach { (p, n) -> c[p - 1] = n }
        return Board.fromSides(h, c)
    }

    private fun off(b: Board, m: Move) = b.applyMove(Player.HUMAN, m).off(Player.HUMAN) - b.off(Player.HUMAN)

    @Test
    fun trayBearsOffTheMost() {
        // 6-5 with checkers on the 6, 5, 4 and 3 points: 6/off 5/off is the most there is.
        val b = board(mapOf(6 to 1, 5 to 1, 4 to 1, 3 to 1), mapOf(1 to 2))
        val plays = TapPlays.bearOffMost(b, Player.HUMAN, MoveGenerator.legalMoves(b, Player.HUMAN, 6, 5))
        assertTrue(plays.isNotEmpty())
        plays.forEach { assertEquals(2, off(b, it)) }
    }

    @Test
    fun trayBearsOffFourWithADouble() {
        // 3-3 with checkers on the 3 point: four come off, not two.
        val b = board(mapOf(3 to 5), mapOf(1 to 2))
        val plays = TapPlays.bearOffMost(b, Player.HUMAN, MoveGenerator.legalMoves(b, Player.HUMAN, 3, 3))
        assertTrue(plays.isNotEmpty())
        plays.forEach { assertEquals(4, off(b, it)) }
    }

    @Test
    fun trayDoesNothingWithoutABearOff() {
        val b = Board.INITIAL
        assertTrue(TapPlays.bearOffMost(b, Player.HUMAN, MoveGenerator.legalMoves(b, Player.HUMAN, 3, 1)).isEmpty())
    }

    @Test
    fun tapMakesThePoint() {
        // Opening 3-1: the 5 point (index 4) is made by 8/5 6/5.
        val b = Board.INITIAL
        val plays = TapPlays.makePoint(b, Player.HUMAN, MoveGenerator.legalMoves(b, Player.HUMAN, 3, 1), 4, emptyList())
        assertEquals(1, plays.size)
        assertEquals(2, plays[0].steps.size)
        assertEquals(2, plays[0].steps.fold(b) { x, s -> x.applyStep(Player.HUMAN, s) }.count(Player.HUMAN, 4))
    }

    @Test
    fun tapWithNoWayToMakeItFindsNothing() {
        val b = Board.INITIAL
        assertTrue(TapPlays.makePoint(b, Player.HUMAN, MoveGenerator.legalMoves(b, Player.HUMAN, 6, 5), 2, emptyList()).isEmpty())
    }

    @Test
    fun tapKeepsTheMovesAlreadyMadeAndTouchesNothingElse() {
        // 3-3 after 24/21 is on the board: tapping the 5 point adds 8/5 8/5 only, the other 3 is left alone.
        val b = Board.INITIAL
        val done = listOf(Step(23, 20))
        val plays = TapPlays.makePoint(b, Player.HUMAN, MoveGenerator.legalMoves(b, Player.HUMAN, 3, 3), 4, done)
        assertTrue(plays.isNotEmpty())
        plays.forEach { assertEquals(done + listOf(Step(7, 4), Step(7, 4)), it.steps) }
    }
}
