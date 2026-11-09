package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.MoveNotation
import com.boldblot.backgammonplyground.core.PositionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** In matches, each side picks its moves inside PureTD's search by its match-winning chance, not money equity. */
class MatchAwareSearchTest {
    private fun ctx(xgid: String) = PositionIds.parseXgid(xgid).let { s ->
        s to CubeContext(s.matchLength, s.score, s.crawford, s.jacoby, s.cube, s.onRoll)
    }

    /**
     * Karol's position (2026-11-08), 0-4 Crawford in a 5-point match: the leader's gammons are worth nothing.
     * When the search let the leader play for gammons, it left shots and PureTD picked 6/3(2) 5/2 4/1
     * (12% wins at 3-ply), a 0.032 error by XG's rollout; XG's best, 13/10 8/2 5/2, wins about 9.3%.
     */
    @Test
    fun theCrawfordLeaderDoesNotPlayForGammons() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        val (s, c) = ctx("XGID=----BCCCA-A--A---bbcdAb-b-:0:0:1:33:0:4:1:5:10")
        val r = e!!.rankMoves(s.board, c, 3, 3, Depth(3, MoveFilter.WIDE).code, null)
        val best = r.candidates.first()
        assertNotEquals("6/3(2) 5/2 4/1", MoveNotation.format(s.board, s.onRoll, best.move))
        assertTrue("wins ${best.probs[0]}", best.probs[0] < 0.105f)
        // XG's best is close to PureTD's.
        val xg = r.candidates.first { MoveNotation.format(s.board, s.onRoll, it.move) == "13/10 8/2 5/2" }
        assertTrue("13/10 8/2 5/2 ${best.equity - xg.equity} behind", best.equity - xg.equity < 0.01f)
    }

    /** Money games are searched as before (money equity is what each side maximizes there). */
    @Test
    fun moneyPlayIsUnchanged() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        val (s, c) = ctx("XGID=----BCCCA-A--A---bbcdAb-b-:0:0:1:33:0:0:0:0:10")
        val code = Depth(2, MoveFilter.ALL).code
        e!!.matchAwareSearch = false
        val before = try { e.rankMoves(s.board, c, 3, 3, code, null) } finally { e.matchAwareSearch = true }
        val after = e.rankMoves(s.board, c, 3, 3, code, null)
        assertEquals(before.candidates.map { it.move }, after.candidates.map { it.move })
        assertEquals(before.candidates.map { it.equity }, after.candidates.map { it.equity })
    }
}
