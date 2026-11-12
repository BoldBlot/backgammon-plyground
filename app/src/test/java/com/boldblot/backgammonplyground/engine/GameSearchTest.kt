package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.CpuLevel
import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.GameSearch
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.PositionIds
import com.boldblot.backgammonplyground.core.SearchMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The searches by played-out games ([GameSearch]): their depth codes, and on the real engine their results, stopping and fallbacks. */
class GameSearchTest {
    private class Pos(xgid: String) {
        val s = PositionIds.parseXgid(xgid)
        val board = s.board
        val ctx = CubeContext(s.matchLength, s.score, s.crawford, s.jacoby, s.cube, s.onRoll)
        val money = ctx.copy(matchLength = 0, score = listOf(0, 0), crawford = false)
        val d1 get() = s.dice!!.d1
        val d2 get() = s.dice!!.d2
    }

    /** A close double at 2-away... 3-3 in a match to 11 (XG's rollout: no double 0.865, double/take 0.851). */
    private val cube = Pos("-dAadBCBBBA-------Babb--a-:0:0:1:00:8:0:0:11:10")
    private val play = Pos("a--BBdB-B---AB-AcbAcBb----:0:0:1:64:2:2:0:11:10")

    @Test
    fun `depth codes of the game searches`() {
        for (g in GameSearch.entries) {
            val d = Depth.of(g.code)
            assertEquals(g, d.search)
            assertEquals(g.code, d.code)
            assertEquals(g.label, d.label())
            assertEquals(g.plies, d.plies)
            assertEquals("${g.label}, incomplete", Depth.of(g.code + Depth.INCOMPLETE).label())
        }
        assertEquals("3/144/7", Depth.of(53).label())
        assertEquals("3/44/7", Depth.of(61).label(cube = true))
        // The codes analyses already carry read as before.
        assertEquals(Depth(1), Depth.of(0))
        assertEquals(Depth(3, MoveFilter.WIDE), Depth.of(3))
        assertEquals(Depth(3, MoveFilter.ALL), Depth.of(4))
        for (plies in 1..4) for (f in MoveFilter.entries) assertEquals(Depth(plies, f), Depth.of(10 * plies + f.ordinal))
        assertEquals("3-ply, pruned, incomplete", Depth.of(132).label())
        assertTrue(Depth.of(132).incomplete)
        assertEquals(Depth(3, MoveFilter.WIDE), Depth.of(53).plain)
    }

    @Test
    fun `one number for money and match`() {
        val dual = Depth.dual(Depth(3, MoveFilter.WIDE).code, GameSearch.G144.code)
        assertEquals(53, Depth.resolve(dual, match = true))
        assertEquals(32, Depth.resolve(dual, match = false))
        assertEquals(32, Depth.dual(32, 32))
        assertEquals(43, Depth.resolve(43, match = true))
        // A dual code of 1-ply money (11) and short codes survive.
        val low = Depth.dual(0, 61)
        assertEquals(0, Depth.resolve(low, match = false))
        assertEquals(61, Depth.resolve(low, match = true))
    }

    @Test
    fun `the same position gives the same values`() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        e!!
        val a = e.cubeDecision(cube.board, cube.ctx, GameSearch.C44.code)
        e.cubeDecision(cube.board, cube.ctx, 0) // evicts the one-decision cache
        val b = e.cubeDecision(cube.board, cube.ctx, GameSearch.C44.code)
        assertEquals(GameSearch.C44.code, a.plies)
        assertNotNull("the games were played", a.spread)
        assertEquals(a.noDouble, b.noDouble, 0f)
        assertEquals(a.doubleTake, b.doubleTake, 0f)
        // Near XG's rollout (0.865 and 0.851): the games, not the plies before them.
        assertEquals(0.865f, a.noDouble, 0.08f)
        assertEquals(0.851f, a.doubleTake, 0.08f)

        val r = e.rankMoves(play.board, play.ctx, play.d1, play.d2, GameSearch.G36.code, null)
        e.rankMoves(play.board, play.ctx, 1, 2, 0, null)
        val r2 = e.rankMoves(play.board, play.ctx, play.d1, play.d2, GameSearch.G36.code, null)
        assertEquals(GameSearch.G36.code, r.candidates[0].plies)
        assertEquals(r.candidates.map { it.move to it.equity }, r2.candidates.map { it.move to it.equity })
        // Searched plays first: the games, then the 3-ply stage, then the rest at 1-ply.
        assertEquals(r.candidates.map { it.plies }.sortedDescending(), r.candidates.map { it.plies })
        assertTrue(r.candidates.count { it.plies == GameSearch.G36.code } in 1..GameSearch.G36.plays)
    }

    @Test
    fun `money games and clear decisions get the plies alone`() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        e!!
        val r = e.rankMoves(play.board, play.money, play.d1, play.d2, GameSearch.G144.code, null)
        assertEquals(Depth(3, MoveFilter.WIDE).code, r.candidates[0].plies)
        val c = e.cubeDecision(cube.board, cube.money, GameSearch.C48.code)
        assertEquals(3, Depth.of(c.plies!!).plies)
        assertNull(Depth.of(c.plies!!).search)
        assertNull(c.spread)
        // A dual code: each kind of game takes its half.
        val dual = Depth.dual(Depth(2).code, GameSearch.C44.code)
        assertEquals(Depth(2).code, e.cubeDecision(cube.board, cube.money, dual).plies)
        assertEquals(GameSearch.C44.code, e.cubeDecision(cube.board, cube.ctx, dual).plies)
        // The level made of game searches plays money games as Maximum.
        assertNotNull(e.chooseMove(play.board, play.money, play.d1, play.d2, CpuLevel.NEW_BEST))
    }

    @Test
    fun `whole-game analysis plays games out only for errors`() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        e!!
        val full = e.rankMoves(play.board, play.ctx, play.d1, play.d2, GameSearch.G36.code, null)
        val quick = e.rankMoves(play.board, play.ctx, play.d1, play.d2, Depth(3, MoveFilter.WIDE).code, null)
        e.gamesOnErrorsOnly = true
        try {
            // The 3-ply best play, played: no games.
            val best = e.rankMoves(play.board, play.ctx, play.d1, play.d2, GameSearch.G36.code, quick.candidates[0].move)
            assertEquals(Depth(3, MoveFilter.WIDE).code, best.candidates[best.playedIndex].plies)
            // A clear error: played out, and the move played goes through the games.
            val bad = e.rankMoves(play.board, play.ctx, play.d1, play.d2, GameSearch.G36.code, full.candidates.last().move)
            assertEquals(GameSearch.G36.code, bad.candidates[bad.playedIndex].plies)
            assertEquals(GameSearch.G36.code, bad.candidates[0].plies)
        } finally { e.gamesOnErrorsOnly = false }
    }

    @Test
    fun `a stopped search returns what it has, marked incomplete`() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        e!!
        try {
            // Stopped before it starts: the 1-ply values.
            val early = SearchMonitor().also { it.requestStop() }
            e.monitor = early
            val r0 = e.rankMoves(play.board, play.ctx, play.d1, play.d2, GameSearch.G144.code, null)
            assertTrue(Depth.of(r0.candidates[0].plies).incomplete)
            assertEquals(1, Depth.of(r0.candidates[0].plies).plies)
            val c0 = e.cubeDecision(cube.board, cube.ctx, Depth(4).code)
            assertTrue(Depth.of(c0.plies!!).incomplete)
            assertEquals(1, Depth.of(c0.plies!!).plies)

            // Stopped during the games: the games played so far, or the 3-ply stage before them.
            val mon = SearchMonitor()
            e.monitor = mon
            val stopper = Thread {
                while (!mon.what.startsWith("Played")) Thread.sleep(5)
                Thread.sleep(300)
                mon.requestStop()
            }.also { it.isDaemon = true; it.start() }
            val t0 = System.nanoTime()
            val r = e.rankMoves(play.board, play.ctx, play.d1, play.d2, GameSearch.G288.code, null)
            stopper.join()
            assertTrue("stops soon after it is asked", (System.nanoTime() - t0) / 1e9 < 15.0)
            val top = Depth.of(r.candidates[0].plies)
            assertTrue(top.incomplete)
            assertTrue(top.search == GameSearch.G288 || top == Depth(3, MoveFilter.WIDE, incomplete = true))
            assertTrue(mon.total == GameSearch.G288.games && mon.done < mon.total)

            // A watched search that is left alone finishes, 4-ply after 3-ply.
            val calm = SearchMonitor()
            e.monitor = calm
            val c = e.cubeDecision(cube.board, cube.ctx, Depth(4).code)
            assertEquals(Depth(4).code, c.plies)
            assertFalse(calm.stop)
        } finally { e.monitor = null }
        // The stop is not left behind: the next search runs in full and is not marked.
        val after = e.cubeDecision(play.board, play.ctx.copy(), Depth(3).code)
        assertEquals(Depth(3).code, after.plies)
        val again = e.rankMoves(play.board, play.ctx, play.d1, play.d2, GameSearch.G36.code, null)
        assertEquals(GameSearch.G36.code, again.candidates[0].plies)
    }
}
