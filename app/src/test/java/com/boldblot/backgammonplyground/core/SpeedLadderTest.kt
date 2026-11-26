package com.boldblot.backgammonplyground.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedLadderTest {
    /** Levels 0..[n-1] that take [ms] each; the budget is 100. Returns the pick and the levels timed. */
    private fun climb(ms: List<Int>, start: Int): Pair<Int?, List<Int>> {
        val timed = mutableListOf<Int>()
        val pick = runBlocking { climbLadder(ms.size, start) { i -> timed += i; ms[i] <= 100 } }
        return pick to timed
    }

    private val times = listOf(5, 20, 60, 90, 150, 400)

    @Test fun startsBelowAndClimbsToTheLastFit() = assertEquals(3 to listOf(1, 2, 3, 4), climb(times, 1))
    @Test fun startsAboveAndStepsDown() = assertEquals(3 to listOf(5, 4, 3), climb(times, 5))
    @Test fun startsRightOnTheBoundary() = assertEquals(3 to listOf(3, 4), climb(times, 3))
    @Test fun noneFitsGivesTheWeakest() = assertEquals(0 to listOf(2, 1, 0), climb(listOf(200, 300, 400), 2))
    @Test fun allFitGivesTheStrongest() = assertEquals(2 to listOf(0, 1, 2), climb(listOf(1, 2, 3), 0))

    @Test fun stoppedGivesNull() {
        val pick = runBlocking { climbLadder(4, 1) { i -> if (i == 2) null else true } }
        assertNull(pick)
    }

    @Test fun startFromPrediction() {
        val ratios = listOf(1.0, 6.0, 20.0, 50.0, 120.0)
        assertEquals(2, ladderStart(4.0, ratios, 100))   // 80 fits, 200 does not
        assertEquals(0, ladderStart(500.0, ratios, 100))
        assertEquals(4, ladderStart(0.5, ratios, 100))
    }

    @Test fun referencePositionsAreWhatTheySay() {
        assertEquals(6, ReferencePositions.all.size)
        for (cube in listOf(false, true)) assertEquals(RefDifficulty.entries.toList(), ReferencePositions.of(cube).map { it.difficulty })
        for (r in ReferencePositions.all) {
            val p = r.setup
            assertTrue(r.title, p.matchLength > 0)
            assertEquals(r.title, r.cube, p.dice == null)
            if (!r.cube) {
                val n = MoveGenerator.legalMoves(p.board, p.onRoll, p.dice!!.d1, p.dice!!.d2).size
                assertTrue("${r.title}: $n plays", when (r.difficulty) { RefDifficulty.EASY -> n <= 10; RefDifficulty.AVERAGE -> n in 11..40; RefDifficulty.HARD -> n > 40 })
            }
        }
    }
}
