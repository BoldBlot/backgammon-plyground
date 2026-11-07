package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.PositionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Money checker plays with the cubeful network (PureTdEngine.cubefulCheckers, the "Cubeful checker
 * play" setting): Alex Strehl's position scored like cubeless + Janowski although the cubeful mode
 * was meant; the ranking cache must not carry one mode's result into the other.
 */
class CubefulCheckersTest {
    private val xgid = "XGID=---aBBCbB---cD---c-bBca---:0:0:1:11:0:0:3:0:10"

    private fun ranking(e: PureTdEngine, plies: Int): List<Float> {
        val s = PositionIds.parseXgid(xgid.removePrefix("XGID="))
        val dice = requireNotNull(s.dice)
        val ctx = CubeContext(s.matchLength, s.score, s.crawford, s.jacoby, s.cube, s.onRoll)
        return e.rankMoves(s.board, ctx, dice.d1, dice.d2, Depth(plies, MoveFilter.ALL).code, null).candidates.map { it.equity }
    }

    @Test
    fun cubefulModeChangesTheEquitiesAndTheCacheFollowsTheFlag() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        e!!
        try {
            e.cubefulCheckers = false
            val janowski = ranking(e, 2)
            e.cubefulCheckers = true
            val cubeful = ranking(e, 2)       // same roll straight after: a stale cache would repeat the first ranking
            assertNotEquals(janowski, cubeful)
            e.cubefulCheckers = false
            assertEquals(janowski, ranking(e, 2))
        } finally {
            e.cubefulCheckers = false
        }
    }

    @Test
    fun compactNetworkLoadsAndPlays() {
        val e = TestEngine.compact
        assumeTrue(e != null)
        assertEquals("PureTD 4L", e!!.name)
        assertEquals(true, ranking(e, 1).isNotEmpty())
    }
}
