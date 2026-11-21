package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionSeriesTest {

    private val board = "-CBaCBD-----a----a-bbbbca-"
    // 7-pt match, X (bottom) 2, O (top) 4.
    private val xToPlay = "XGID=$board:1:-1:1:31:2:4:0:7:10"
    private val oToPlay = "XGID=$board:1:-1:-1:31:2:4:0:7:10"
    private val oDoubledX = "XGID=$board:1:0:-1:D:2:4:0:7:10"
    private val xDoubledO = "XGID=$board:1:0:1:D:2:4:0:7:10"

    @Test
    fun keepsTheOrderAndCountsUnreadableLines() {
        val p = PositionSeries.read("$oToPlay\n\nnot a position\n$xToPlay\n  \n$xDoubledO\n")
        assertEquals(3, p.items.size)
        assertEquals(1, p.skipped)
        assertEquals(listOf(false, true, false), p.items.map { it.bottomDecides })
        assertEquals(Dice(3, 1), p.items[0].position.dice)
        assertTrue(p.items[2].position.doubled)
    }

    @Test
    fun theDeciderIsStoredAtTheBottom() {
        for (id in listOf(xToPlay, oToPlay, oDoubledX, xDoubledO)) {
            val item = PositionSeries.item(id)
            // As My positions stores it: the same position as StudyPosition.parse.
            assertEquals(StudyPosition.parse(id), item.position)
        }
        assertTrue(PositionSeries.item(xToPlay).bottomDecides)
        assertFalse(PositionSeries.item(oToPlay).bottomDecides)
        // A double on offer: the player who answers it decides.
        assertTrue(PositionSeries.item(oDoubledX).bottomDecides)
        assertFalse(PositionSeries.item(xDoubledO).bottomDecides)
    }

    @Test
    fun theScoreIsTheImportedIdsWhoeverDecides() {
        for (id in listOf(xToPlay, oToPlay, oDoubledX, xDoubledO)) {
            val item = PositionSeries.item(id)
            assertEquals(id, 2 to 4, PositionSeries.score(item.position, item.bottomDecides))
        }
    }

    @Test
    fun theMatchLengthIsSharedOrNone() {
        assertEquals(7, PositionSeries.read("$xToPlay\n$oToPlay").matchLength)
        assertNull(PositionSeries.read("$xToPlay\nXGID=$board:1:-1:1:31:0:0:0:0:10").matchLength)
        assertEquals(0, PositionSeries.read("XGID=$board:1:-1:1:31:0:0:0:0:10").matchLength)
        assertNull(PositionSeries.read("nothing").matchLength)
    }

    @Test
    fun linesNameWhoDecidesInASetFromOneMatch() {
        fun line(id: String, n: Int) = PositionSeries.item(id).let { PositionSeries.line(n, it.position, it.bottomDecides, true, "Anna", "Bob") }
        assertEquals("1 · Anna to play · Anna 2, Bob 4 (7 pt)", line(xToPlay, 1))
        assertEquals("2 · Bob to play · Anna 2, Bob 4 (7 pt)", line(oToPlay, 2))
        assertEquals("3 · Anna to take or pass · Anna 2, Bob 4 (7 pt)", line(oDoubledX, 3))
        assertEquals("4 · Bob to take or pass · Anna 2, Bob 4 (7 pt)", line(xDoubledO, 4))
        val plain = PositionSeries.item(xToPlay)
        assertEquals("5", PositionSeries.line(5, plain.position, plain.bottomDecides, false, "Anna", "Bob"))
    }

    @Test
    fun theAnalyzeIdIsTheSamePosition() {
        for (id in listOf(xToPlay, oToPlay, oDoubledX, xDoubledO)) {
            val p = PositionSeries.item(id).position
            // Stored and read back, as a set keeps it, then sent to Analyze Position.
            val stored = StudyPosition.stored(p.xgid, p.doubled, p.beavers)
            assertEquals(id, p, StudyPosition.parse(PositionSeries.analyzeId(stored)))
        }
    }
}
