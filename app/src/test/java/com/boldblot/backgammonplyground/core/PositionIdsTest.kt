package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PositionIdsTest {

    @Test
    fun startingPositionInBothFormats() {
        val xg = PositionIds.parse("XGID=-b----E-C---eE---c-e----B-:0:0:1:00:0:0:0:0:10")
        assertEquals(Board.INITIAL, xg.board)
        assertEquals(Player.HUMAN, xg.onRoll)
        assertNull(xg.dice)
        assertEquals(0, xg.matchLength)
        val gnu = PositionIds.parse("Position ID: 4HPwATDgc/ABMA  Match ID: cAkAAAAAAAAA")
        assertEquals(Board.INITIAL, gnu.board)
        assertEquals(Player.HUMAN, gnu.onRoll) // gnubg's player 1, the bottom player
        assertEquals(CubeState(), gnu.cube)
        assertEquals(0, gnu.matchLength)
        assertTrue(gnu.jacoby)
    }

    @Test
    fun xgidFields() {
        // O on roll with 6-3, cube 2 owned by X, 7-point match at 4-3, not Crawford.
        val s = PositionIds.parse("XGID=-a--B-E-C--adD---c-e--A---:1:1:-1:63:4:3:0:7:10")
        assertEquals(Player.CPU, s.onRoll)
        assertEquals(Dice(6, 3), s.dice)
        assertEquals(CubeState(2, Player.HUMAN), s.cube)
        assertEquals(7, s.matchLength)
        assertEquals(listOf(4, 3), s.score)
        assertFalse(s.crawford)
        assertEquals(2, s.board.count(Player.HUMAN, 3)) // B on X's 4-point
        assertEquals(1, s.board.count(Player.CPU, 23)) // a on X's 1-point = O's 24-point
    }

    @Test
    fun doubledXgidIsACubeDecision() {
        val s = PositionIds.parse("XGID=-b----E-C---eE---c-e----B-:0:0:1:D:0:0:0:0:10")
        assertTrue(s.doubled)
        assertNull(s.dice)
    }

    private fun randomSetup(rnd: Random): PositionSetup {
        val human = IntArray(25)
        val cpu = IntArray(25)
        repeat(rnd.nextInt(1, 16)) { human[rnd.nextInt(25)]++ }
        repeat(rnd.nextInt(1, 16)) {
            val p = rnd.nextInt(25)
            if (p == BAR || human[23 - p] == 0) cpu[p]++
        }
        val length = if (rnd.nextBoolean()) 0 else rnd.nextInt(1, 26)
        val cube = 1 shl rnd.nextInt(0, 6)
        return PositionSetup(
            board = Board.fromSides(human, cpu), onRoll = rnd.nextInt(2),
            dice = if (rnd.nextBoolean()) Dice(rnd.nextInt(1, 7), rnd.nextInt(1, 7)) else null,
            cube = if (cube == 1) CubeState() else CubeState(cube, rnd.nextInt(-1, 2)),
            matchLength = length,
            score = if (length == 0) listOf(0, 0) else listOf(rnd.nextInt(length), rnd.nextInt(length)),
            crawford = length > 0 && rnd.nextBoolean(), jacoby = length == 0 && rnd.nextBoolean(),
            format = "",
        )
    }

    @Test
    fun roundTrips() {
        val rnd = Random(7)
        repeat(300) {
            val s = randomSetup(rnd)
            assertEquals(s.copy(format = "XGID"), PositionIds.parse(PositionIds.toXgid(s)))
            val gnu = PositionIds.toGnu(s)
            assertEquals(gnu, PositionIds.toGnu(PositionIds.parse(gnu)))
            val back = PositionIds.parse(gnu)
            assertEquals(s.board, back.board)
            assertEquals(s.onRoll, back.onRoll)
            assertEquals(s.dice, back.dice)
            assertEquals(s.cube, back.cube)
            assertEquals(s.score, back.score)
            assertEquals(s.matchLength, back.matchLength)
        }
    }

    @Test
    fun startingPositionEncodesAsGnubgDoes() {
        val s = PositionSetup(Board.INITIAL, Player.HUMAN, format = "")
        assertEquals("4HPwATDgc/ABMA:cAkAAAAAAAAA", PositionIds.toGnu(s))
        assertEquals("XGID=-b----E-C---eE---c-e----B-:0:0:1:00:0:0:1:0:10", PositionIds.toXgid(s))
    }

    @Test
    fun rejectsImpossiblePositions() {
        val e = runCatching { PositionIds.parse("XGID=-p----E-C---eE---c-e----B-:0:0:1:00:0:0:0:0:10") }.exceptionOrNull()
        assertTrue(e?.message ?: "", e is IllegalArgumentException)
        assertTrue(runCatching { PositionIds.parse("hello") }.isFailure)
    }
}
