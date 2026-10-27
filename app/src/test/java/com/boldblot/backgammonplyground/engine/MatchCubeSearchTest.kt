package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.PositionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** PureTD's cube-aware match cube search ([MatchCubeSearch]). */
class MatchCubeSearchTest {

    private fun ctx(xgid: String) = PositionIds.parseXgid(xgid).let { s ->
        s to CubeContext(s.matchLength, s.score, s.crawford, s.jacoby, s.cube, s.onRoll)
    }

    @Test
    fun probabilitiesFromTheTreeMatchTheNPlyProbabilities() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        val (s, match) = ctx("aBaB--C-A---dE---d-e----B-:0:0:1:00:0:1:0:3:10")
        val money = match.copy(matchLength = 0, score = listOf(0, 0))
        for (p in 2..3) {
            val a = e!!.cubeDecision(s.board, match, Depth(p).code).probs
            val b = e.cubeDecision(s.board, money, Depth(p).code).probs
            for (i in 0 until 5) assertEquals("$p-ply probability $i", b[i], a[i], 1e-4f)
        }
    }

    @Test
    fun shortRacesAtTwoAway() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        // Two checkers against one or two, where only a search with the cube in the tree gets the
        // last rolls right (1-ply Janowski gets all three wrong; XG: double/take, double/pass, double/take).
        val cases = listOf(
            Triple("---A--A------------a------:0:0:1:00:5:5:0:7:10", true, true),
            Triple("--AA--------------------b-:0:0:1:00:5:4:0:7:10", true, false),
            Triple("---A-A------------------a-:0:0:1:00:3:1:0:5:10", true, true),
        )
        for ((xgid, double, take) in cases) {
            val (s, c) = ctx(xgid)
            val one = e!!.cubeDecision(s.board, c, Depth(1).code)
            val three = e.cubeDecision(s.board, c, Depth(3).code)
            assertFalse(xgid, one.shouldDouble == double && one.shouldTake == take)
            assertEquals(xgid, double, three.shouldDouble)
            assertEquals(xgid, take, three.shouldTake)
            assertTrue(xgid, three.doublePass > 0.99f)
        }
    }
}
