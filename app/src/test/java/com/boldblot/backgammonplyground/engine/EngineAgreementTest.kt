package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.CubeState
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.MoveNotation
import com.boldblot.backgammonplyground.core.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.abs

/** The three engines through the same interface: well-known answers, and units that agree. */
class EngineAgreementTest {

    private val engines: List<Pair<String, SearchEngine>> by lazy {
        listOfNotNull(TestEngine.engine?.let { "PureTD" to it }, TestEngine.gnubg?.let { "GNU" to it }, TestEngine.sage?.let { "Sage" to it })
    }

    private fun money(onRoll: Int = Player.HUMAN) = CubeContext(0, listOf(0, 0), false, false, CubeState(), onRoll)

    @Test
    fun openingMovesAndEquities() {
        assumeTrue(TestEngine.engine != null)
        val start = Board.INITIAL
        for ((name, e) in engines) {
            for (plies in listOf(1, 2, 3)) {
                val r = e.rankMoves(start, money(), 3, 1, Depth(plies, MoveFilter.NARROW).code, null)
                val best = MoveNotation.format(start, Player.HUMAN, r.candidates[0].move)
                println("$name $plies-ply 31: $best ${r.candidates[0].equity}")
                assertEquals("$name $plies-ply", "8/5 6/5", best)
                // After the best 31 the mover is a little ahead.
                assertTrue("$name $plies-ply equity ${r.candidates[0].equity}", r.candidates[0].equity in 0.05f..0.3f)
            }
        }
    }

    @Test
    fun cubeAtTheStartAndInARace() {
        assumeTrue(TestEngine.engine != null)
        val start = Board.INITIAL
        for ((name, e) in engines) {
            for (plies in listOf(1, 2, 3)) {
                val c = e.cubeDecision(start, money(), Depth(plies).code)
                println("$name $plies-ply start cube: nd ${c.noDouble} dt ${c.doubleTake} dp ${c.doublePass}")
                assertFalse(name, c.shouldDouble)
                assertTrue(name, c.shouldTake)
                assertTrue(name, abs(c.noDouble) < 0.15f)
                assertEquals(name, 1f, c.doublePass, 1e-3f)
                assertEquals(name, 0.5f, c.probs[0], 0.06f)
            }
        }
    }

    @Test
    fun matchEquitiesAreEmg() {
        assumeTrue(TestEngine.engine != null)
        // 5-point match at 0-0: doubling and passing is worth 1.0 EMG-normalized at every engine.
        val ctx = CubeContext(5, listOf(0, 0), false, false, CubeState(), Player.HUMAN)
        for ((name, e) in engines) {
            val c = e.cubeDecision(Board.INITIAL, ctx, Depth(2).code)
            println("$name 5pt start cube: nd ${c.noDouble} dt ${c.doubleTake} dp ${c.doublePass}")
            assertEquals(name, 1f, c.doublePass, 0.05f)
            assertTrue(name, abs(c.noDouble) < 0.15f)
            val r = e.rankMoves(Board.INITIAL, ctx, 3, 1, Depth(2, MoveFilter.NARROW).code, null)
            assertTrue("$name ${r.candidates[0].equity}", r.candidates[0].equity in 0.05f..0.3f)
        }
    }

    @Test
    fun luckOfTheOpeningRoll() {
        assumeTrue(TestEngine.engine != null)
        for ((name, e) in engines) {
            val l65 = e.luck(Board.INITIAL, money(), 6, 5, firstRoll = true)
            val l21 = e.luck(Board.INITIAL, money(), 2, 1, firstRoll = true)
            println("$name luck 65 $l65, 21 $l21")
            assertTrue(name, l65 > 0f && l21 < 0f)
        }
    }
}
