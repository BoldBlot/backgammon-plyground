package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.MAX_PLIES
import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.CubeState
import com.boldblot.backgammonplyground.core.MoveGenerator
import com.boldblot.backgammonplyground.core.MoveNotation
import com.boldblot.backgammonplyground.core.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Tests against the real PureTD engine, using a host build of the JNI
 * library (run scripts/host-tests.sh). Skipped when the library is not available.
 */
class EngineCrossCheckTest {

    companion object {
        private val engine: PureTdEngine? get() = TestEngine.engine
    }

    private fun ctx(onRoll: Int) = CubeContext(0, listOf(0, 0), false, true, CubeState(), onRoll)

    @Test
    fun kotlinMoveGeneratorMatchesPureTd() {
        val e = engine
        assumeTrue("host engine library not provided", e != null)
        val rnd = Random(12345)
        var positions = 0
        repeat(300) {
            var board = Board.INITIAL
            var player = rnd.nextInt(2)
            for (turn in 0 until 200) {
                val d1 = rnd.nextInt(1, 7)
                val d2 = rnd.nextInt(1, 7)
                val ours = MoveGenerator.legalMoves(board, player, d1, d2)
                    .map { board.applyMove(player, it) }.toSet()
                val theirs = e!!.legalBoards(board, player, d1, d2)
                val expected = if (theirs.isEmpty()) setOf(board) else theirs
                if (expected != ours) {
                    throw AssertionError(
                        "position $board player $player dice $d1$d2\n" +
                            "only PureTD: ${expected - ours}\nonly ours: ${ours - expected}",
                    )
                }
                positions++
                val next = ours.toList()[rnd.nextInt(ours.size)]
                board = next
                if (board.off(player) == 15) break
                player = Player.other(player)
            }
        }
        assertTrue(positions > 10_000)
    }

    @Test
    fun engineFindsStandardOpeningMoves() {
        val e = engine
        assumeTrue(e != null)
        fun best(d1: Int, d2: Int): String {
            val r = e!!.rankMoves(Board.INITIAL, ctx(Player.HUMAN), d1, d2, 2, null)
            return MoveNotation.format(Board.INITIAL, Player.HUMAN, r.candidates[0].move)
        }
        assertEquals("8/5 6/5", best(3, 1))
        assertEquals("13/7 8/7", best(6, 1))
        assertEquals("24/13", best(6, 5))
        assertEquals("8/3 6/3", best(5, 3))
    }

    @Test
    fun rankingFromCpuPerspectiveIsMirrored() {
        val e = engine
        assumeTrue(e != null)
        val r = e!!.rankMoves(Board.INITIAL, ctx(Player.CPU), 3, 1, 0, null)
        assertEquals("8/5 6/5", MoveNotation.format(Board.INITIAL, Player.CPU, r.candidates[0].move))
    }

    @Test
    fun playedMoveIsLocatedAndScored() {
        val e = engine
        assumeTrue(e != null)
        // The worst 1-ply play for an opening 6-4 is a clear blunder at 3-ply too.
        val worst = e!!.rankMoves(Board.INITIAL, ctx(Player.HUMAN), 6, 4, 0, null).candidates.last().move
        val r = e.rankMoves(Board.INITIAL, ctx(Player.HUMAN), 6, 4, 2, worst)
        assertTrue(r.playedIndex > 0)
        val err = r.candidates[0].equity - r.candidates[r.playedIndex].equity
        assertTrue("worst move should be a blunder, was $err", err > 0.08f)
        assertEquals(r.candidates[0].plies, r.candidates[r.playedIndex].plies)
    }

    @Test
    fun filteredRankingIsReusedForItsOwnChoiceButNotForAnUnsearchedMove() {
        val e = engine
        assumeTrue(e != null)
        val code = 32 // 3-ply, moves within 0.16
        val first = e!!.rankMoves(Board.INITIAL, ctx(Player.HUMAN), 6, 4, code, null)
        // The engine's own choice: the same ranking object comes back.
        val again = e.rankMoves(Board.INITIAL, ctx(Player.HUMAN), 6, 4, code, first.candidates[0].move)
        assertTrue(again.candidates === first.candidates)
        assertEquals(0, again.playedIndex)
        // A move left out of the deep search: ranked again, with the played move searched too.
        val worst = first.candidates.last().move
        val r = e.rankMoves(Board.INITIAL, ctx(Player.HUMAN), 6, 4, code, worst)
        assertEquals(code, r.candidates[r.playedIndex].plies)
    }

    @Test
    fun luckOfOpeningRolls() {
        val e = engine
        assumeTrue(e != null)
        val l65 = e!!.luck(Board.INITIAL, ctx(Player.HUMAN), 6, 5, firstRoll = true)
        val l21 = e.luck(Board.INITIAL, ctx(Player.HUMAN), 2, 1, firstRoll = true)
        assertTrue(l65 > l21)
    }

    @Test
    fun threePlyAnalysis() {
        val e = engine
        assumeTrue(e != null)
        val rnd = Random(31)
        // A few middle-game positions reached by random play.
        var board = Board.INITIAL
        var player = Player.HUMAN
        var total1 = 0L
        var total3 = 0L
        var totalWide = 0L
        var slowest = 0L
        var mostSearched = 0
        var deep = 0
        repeat(20) { turn ->
            val d1 = rnd.nextInt(1, 7)
            val d2 = rnd.nextInt(1, 7)
            if (turn >= 6) {
                var t = System.nanoTime()
                val r1 = e!!.rankMoves(board, ctx(player), d1, d2, 0, null)
                total1 += System.nanoTime() - t
                t = System.nanoTime()
                val r3 = e.rankMoves(board, ctx(player), d1, d2, 2, null)
                total3 += System.nanoTime() - t
                t = System.nanoTime()
                val rw = e.rankMoves(board, ctx(player), d1, d2, 3, null)
                val dt = System.nanoTime() - t
                totalWide += dt
                slowest = maxOf(slowest, dt)
                mostSearched = maxOf(mostSearched, rw.candidates.count { it.plies == 3 })
                assertEquals(r1.candidates.size, r3.candidates.size)
                // Every move within the window (by 1-ply equity) is searched.
                val bestOne = r1.candidates[0].equity
                val inWindow = r1.candidates.count { it.equity >= bestOne - 0.08f }
                if (r1.candidates.size > 1) assertEquals(inWindow, r3.candidates.count { it.plies == 2 })
                // With more than one legal play, the best candidate has been searched at 3-ply.
                if (r3.candidates.size > 1) { assertEquals(2, r3.candidates[0].plies); deep++ }
                // Maximum searches every legal play, and its best is at least as good at 3-ply as the window's.
                val rm = e.rankMoves(board, ctx(player), d1, d2, MAX_PLIES, null)
                if (rm.candidates.size > 1) {
                    assertTrue(rm.candidates.all { it.plies == MAX_PLIES })
                    assertTrue(rm.candidates[0].equity >= rw.candidates[0].equity - 1e-5f)
                }
            }
            val moves = MoveGenerator.legalMoves(board, player, d1, d2)
            board = board.applyMove(player, moves.random(rnd))
            player = Player.other(player)
        }
        assertTrue("some positions are analyzed at full depth", deep > 0)
        println("rank moves, 14 positions ($deep deep): 1-ply ${total1 / 1_000_000} ms, 3-ply ±0.08 ${total3 / 1_000_000} ms, " +
            "3-ply ±0.16 ${totalWide / 1_000_000} ms (slowest ${slowest / 1_000_000} ms, at most $mostSearched moves searched)")
    }

    @Test
    fun cubeDecisionsMoneyAndMatch() {
        val e = engine
        assumeTrue(e != null)
        // Opening position: no double, for money and at 5-away / 5-away.
        val money = e!!.cubeDecision(Board.INITIAL, ctx(Player.HUMAN), 2)
        assertTrue(!money.shouldDouble && money.cubeAvailable)
        val match = e.cubeDecision(Board.INITIAL, CubeContext(5, listOf(0, 0), false, false, CubeState(), Player.HUMAN), 2)
        assertTrue(!match.shouldDouble && match.cubeAvailable)
        assertEquals(1f, match.doublePass, 1e-4f)
        // Crawford game: no doubling.
        val crawford = e.cubeDecision(Board.INITIAL, CubeContext(5, listOf(4, 2), true, false, CubeState(), Player.HUMAN), 0)
        assertTrue(!crawford.cubeAvailable && !crawford.shouldDouble)
    }
}
