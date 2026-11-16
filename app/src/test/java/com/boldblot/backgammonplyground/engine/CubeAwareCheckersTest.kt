package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.PositionIds
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Match checker plays deeper than 1-ply are valued by the cube-aware match search: cube decisions at every node
 * of the tree, the match cube formula at each leaf under the cube state there.
 */
class CubeAwareCheckersTest {
    private val id = "-b-A-CC-C---bD---cabbAb-a-:0:0:1:66:0:2:0:11:10"
    private val better = "-bAB-CC-B---bD---cabb-b-a-"
    private val worse = "-b-A-CCBCA--bB---cabb-b-a-"

    /** Equity of [better] minus [worse] at 3-ply. */
    private fun gap(e: PureTdEngine): Float {
        val s = PositionIds.parseXgid(id)
        val c = CubeContext(s.matchLength, s.score, s.crawford, s.jacoby, s.cube, s.onRoll)
        val r = e.rankMoves(s.board, c, 6, 6, Depth(3, MoveFilter.ALL).code, null)
        fun eq(pos: String): Float {
            val b = PositionIds.parseXgid(pos + id.substring(26)).board
            return r.candidates.first { s.board.applyMove(s.onRoll, it.move) == b }.equity
        }
        return eq(better) - eq(worse)
    }

    /**
     * 0-2 in an 11-point match, centred cube, 6-6 (a PureTD-Sage test match, 2026-11): XG's rollout has the
     * first play 0.059 ahead. Valued without the cube inside the tree, the search had it 0.09 behind.
     */
    @Test
    fun theCubeInsideTheTreeFindsTheBetterPlay() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        e!!
        val now = gap(e)
        assertTrue("ahead by $now", now > 0.03f && now < 0.09f)
        e.cubeAwareMatchCheckers = false
        val before = try { gap(e) } finally { e.cubeAwareMatchCheckers = true }
        assertTrue("was $before", before < -0.05f)
    }
}
