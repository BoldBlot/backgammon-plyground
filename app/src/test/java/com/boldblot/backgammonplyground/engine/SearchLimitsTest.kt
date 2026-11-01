package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.MAX_SEARCHED
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.PositionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The checker search's limits: decided games stop at 1-ply, a pruned search takes at most MAX_SEARCHED deeper. */
class SearchLimitsTest {
    private fun ctx(xgid: String) = PositionIds.parseXgid(xgid).let { s ->
        s to CubeContext(s.matchLength, s.score, s.crawford, s.jacoby, s.cube, s.onRoll)
    }

    @Test
    fun aLostGameIsNotSearchedDeeper() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        // 9 checkers left (6 off, so no gammon) against two on the ace point: lost whatever is played.
        // Every 1-1 play at 4-ply took over a minute on a phone (log 2026-11-01).
        val (s, c) = ctx("------BBBAAA------------b-:1:1:1:11:4:3:0:7:10")
        val t = System.nanoTime()
        val r = e!!.rankMoves(s.board, c, 1, 1, Depth(4, MoveFilter.WIDE).code, null)
        val ms = (System.nanoTime() - t) / 1_000_000
        assertTrue("many plays", r.candidates.size > 20)
        assertTrue("no play searched deeper", r.candidates.all { it.plies == 0 })
        val spread = r.candidates.maxOf { it.equity } - r.candidates.minOf { it.equity }
        assertTrue("all plays equal ($spread)", spread < 1e-3f)
        assertTrue("fast ($ms ms)", ms < 2_000)
    }

    @Test
    fun aPrunedSearchTakesAtMostMaxSearchedDeeper() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        // Bringing checkers home with 1-1: 208 plays, most of them within the window.
        val (s, c) = ctx("-BBBBBAAAB---------aabbaa-:1:1:1:11:4:3:0:7:10")
        val pruned = Depth(3, MoveFilter.WIDE).code
        val r = e!!.rankMoves(s.board, c, 1, 1, pruned, null)
        val deep = r.candidates.count { it.plies == pruned }
        assertTrue("searched $deep", deep in 5..MAX_SEARCHED)
        // The move played is searched too, even past the limit.
        val last = r.candidates.last().move
        val again = e.rankMoves(s.board, c.copy(cube = c.cube.copy()), 1, 1, pruned, last)
        assertEquals(pruned, again.candidates[again.playedIndex].plies)
    }
}
