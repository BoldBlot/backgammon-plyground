package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.CpuLevel
import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.PositionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Searches not done twice, or not at all where 1-ply is already exact (the computer's endgame turns). */
class SearchShortcutsTest {
    private fun ctx(xgid: String) = PositionIds.parseXgid(xgid).let { s ->
        s to CubeContext(s.matchLength, s.score, s.crawford, s.jacoby, s.cube, s.onRoll)
    }

    /** Both sides bearing off with a checker off each: PureTD's bear-off table is exact, so the plays stay at 1-ply. */
    @Test
    fun bearOffInsideTheTableStaysAtOnePly() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        val (s, c) = ctx("XGID=-BBBBCB------------bbbbcb-:0:0:1:52:0:0:0:0:10")
        val deep = e!!.rankMoves(s.board, c, 5, 2, Depth(3, MoveFilter.WIDE).code, null)
        val one = e.rankMoves(s.board, c, 5, 2, Depth(1).code, null)
        assertTrue(deep.candidates.size > 1)
        assertTrue("not searched deeper", deep.candidates.all { it.plies == 0 })
        assertEquals(one.candidates.map { it.move }, deep.candidates.map { it.move })
        // With contact (a checker still back), the search goes deeper as before.
        val (s2, c2) = ctx("XGID=-BBBBCB-----------Abbbbcb-:0:0:1:52:0:0:0:0:10")
        val contact = e.rankMoves(s2.board, c2, 5, 2, Depth(3, MoveFilter.WIDE).code, null)
        assertTrue(contact.candidates.first().plies > 0)
    }

    /** Grandmaster's choice (short code 3) and the analysis of it (3-ply pruned, 32) are one search. */
    @Test
    fun grandmasterSearchIsReusedByItsAnalysis() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        val (s, c) = ctx("XGID=-b----E-C---eE---c-e----B-:0:0:1:31:0:0:0:0:10")
        val t0 = System.nanoTime()
        val chosen = e!!.rankMoves(s.board, c, 3, 1, SearchEngine.levelPlies(CpuLevel.GRANDMASTER), null)
        val t1 = System.nanoTime()
        val code = Depth(3, MoveFilter.WIDE).code
        val analysis = e.rankMoves(s.board, c, 3, 1, code, chosen.candidates.first().move)
        val t2 = System.nanoTime()
        assertEquals(chosen.candidates.map { it.move }, analysis.candidates.map { it.move })
        assertEquals(0, analysis.playedIndex)
        assertEquals("searched candidates carry the analysis's depth", code, analysis.candidates.first().plies)
        assertTrue("reused: ${(t2 - t1) / 1e6} ms vs ${(t1 - t0) / 1e6} ms", t2 - t1 < (t1 - t0) / 5)
    }
}
