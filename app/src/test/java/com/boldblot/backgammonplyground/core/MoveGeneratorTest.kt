package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveGeneratorTest {

    private fun side(vararg pairs: Pair<Int, Int>): IntArray {
        val s = IntArray(25)
        for ((point, n) in pairs) s[if (point == 25) BAR else point - 1] = n
        return s
    }

    /** Board with the human (player on roll) and cpu given as point(1..24, 25 = bar) -> count. */
    private fun board(human: IntArray, cpu: IntArray) = Board.fromSides(human, cpu)

    private fun moves(b: Board, d1: Int, d2: Int, player: Int = Player.HUMAN) =
        MoveGenerator.legalMoves(b, player, d1, d2).map { MoveNotation.format(b, player, it) }.toSet()

    @Test
    fun openingRollsHaveExpectedMoveCounts() {
        // Distinct resulting positions, as counted by gnubg (e.g. 16 for 3-1).
        assertEquals(16, MoveGenerator.legalMoves(Board.INITIAL, 0, 3, 1).size)
        assertTrue("8/5 6/5" in moves(Board.INITIAL, 3, 1))
        assertTrue("24/13" in moves(Board.INITIAL, 6, 5))
    }

    @Test
    fun forcedMovesAreDetected() {
        // Two checkers on the 1 point: every roll bears both off, one way only.
        val bearOff = board(side(1 to 2), side(24 to 15))
        assertTrue(MoveOptions(bearOff, Player.HUMAN, 6, 5).let { it.canMove && it.isForced })
        // On the bar with only the 22 point open (cpu holds its 1, 2, 4, 5, 6 points): 3-1
        // must enter with the 3, and the 1 cannot be played.
        val bar = board(side(25 to 1, 1 to 14), side(1 to 2, 2 to 2, 4 to 2, 5 to 2, 6 to 2, 24 to 5))
        val opts = MoveOptions(bar, Player.HUMAN, 3, 1)
        assertTrue(opts.canMove)
        assertEquals(1, opts.distinctMoves.size)
        assertTrue(opts.isForced)
        // The opening 3-1 is not forced.
        assertFalse(MoveOptions(Board.INITIAL, Player.HUMAN, 3, 1).isForced)
    }

    @Test
    fun mustEnterFromBarFirst() {
        val b = board(side(25 to 1, 6 to 14), side(1 to 2, 2 to 13))
        // Opponent holds our 24 and 23 points (its 1 and 2 points). A 1 and 2 cannot enter.
        assertEquals(setOf("Cannot move"), moves(b, 1, 2).ifEmpty { setOf("Cannot move") })
        val withThree = moves(b, 3, 1)
        assertTrue(withThree.isNotEmpty() && withThree.all { it.startsWith("bar/") })
        assertTrue("bar/22 6/5" in withThree)
    }

    @Test
    fun mustUseBothDiceWhenPossible() {
        // Human: one checker on 13; the 6 is blocked from 13 (cpu owns our 7-point), but
        // 13/11 then 11/5 uses both dice, so 13/11 alone (or any single-die play) is illegal.
        val b = board(side(13 to 1, 1 to 14), side(18 to 2, 2 to 13))
        val ms = MoveGenerator.legalMoves(b, 0, 6, 2)
        assertTrue(ms.all { it.steps.size == 2 })
    }

    @Test
    fun largerDieRuleKnownPosition() {
        // Human: one checker on 24, the rest on the 1-point (so no bear-offs).
        // From 24 a 6 reaches 18 and a 5 reaches 19; the cpu blocks 13 and 14 so that
        // after either play the other die cannot be used. The larger die must be played.
        val cpu = IntArray(25)
        // our point p is cpu index 24 - p
        fun blockOurPoint(p: Int, n: Int = 2) { cpu[24 - p] = n }
        blockOurPoint(14); blockOurPoint(13); blockOurPoint(12, 11)
        val b = Board.fromSides(side(24 to 1, 1 to 14), cpu)
        val ms = moves(b, 6, 5)
        assertEquals(setOf("24/18"), ms)
    }

    @Test
    fun bearOffWithLargerDieOnlyFromHighestPoint() {
        val b = board(side(4 to 1, 2 to 1, 1 to 13), side(1 to 15))
        val ms = moves(b, 6, 1)
        assertTrue("4/off 2/1" in ms || "4/off 1/off" in ms)
        // A 6 may not bear off from the 2-point while the 4-point is occupied.
        assertFalse(ms.any { it.contains("2/off") && !it.contains("4/") })
    }

    @Test
    fun cannotBearOffWithCheckerOutsideHome() {
        val b = board(side(7 to 1, 1 to 14), side(1 to 15))
        val ms = moves(b, 6, 1)
        assertTrue(ms.none { it.startsWith("1/off") && !it.contains("7/") })
    }

    @Test
    fun doublesUseUpToFourSteps() {
        val ms = MoveGenerator.legalMoves(Board.INITIAL, 0, 6, 6)
        assertTrue(ms.all { it.steps.size == 4 })
        assertTrue("24/18(2) 13/7(2)" in moves(Board.INITIAL, 6, 6))
    }

    @Test
    fun oneTapUsesLeftDieThenOtherDie() {
        val opts = MoveOptions(Board.INITIAL, 0, 3, 1)
        // 8-point checker with dice shown as [3, 1]: moves 8/5.
        assertEquals(Step(7, 4), opts.tapStep(emptyList(), 7, listOf(3, 1)))
        // Same tap with the dice swapped: 8/7.
        assertEquals(Step(7, 6), opts.tapStep(emptyList(), 7, listOf(1, 3)))
        // After 8/5 the 3 is used, so the left die [3] is skipped and the 1 is used.
        assertEquals(Step(5, 4), opts.tapStep(listOf(Step(7, 4)), 5, listOf(3, 1)))
        val o65 = MoveOptions(Board.INITIAL, 0, 6, 5)
        assertEquals(Step(23, 17), o65.tapStep(emptyList(), 23, listOf(6, 5)))   // 24/18
        // 6-point checker: a 6 would bear off (not all home) and 6/1 is blocked -> no step.
        assertEquals(null, o65.tapStep(emptyList(), 5, listOf(6, 5)))
        assertEquals(Step(12, 6), o65.tapStep(emptyList(), 12, listOf(6, 5)))   // 13/7
        assertEquals(listOf(6), o65.usedDice(listOf(Step(12, 6))))
    }

    @Test
    fun oneTapOnDoublesUsesSameValue() {
        val opts = MoveOptions(Board.INITIAL, 0, 6, 6)
        var steps = emptyList<Step>()
        repeat(4) {
            val s = opts.tapStep(steps, 12, listOf(6, 6)) ?: opts.tapStep(steps, 23, listOf(6, 6))
            steps = steps + s!!
        }
        assertTrue(opts.isComplete(steps))
        assertEquals(listOf(6, 6, 6, 6), opts.usedDice(steps))
    }

    @Test
    fun moveOptionsSupportsStepByStepEntry() {
        val opts = MoveOptions(Board.INITIAL, 0, 3, 1)
        assertEquals(2, opts.stepsRequired)
        val dest = opts.destinations(emptyList(), 7) // checker on our 8-point
        assertTrue(4 in dest) // 8/5 with the 3
        assertTrue(6 in dest) // 8/7 with the 1
        assertTrue(3 in dest) // 8/4 using both dice
        assertEquals(2, dest.getValue(3).size)
        val after = listOf(Step(7, 4))
        assertTrue(Step(5, 4) in opts.nextSteps(after))
        assertTrue(opts.isComplete(after + Step(5, 4)))
        assertEquals(listOf(1), opts.remainingDice(after))
    }
}
