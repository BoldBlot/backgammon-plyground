package com.boldblot.backgammonplyground.ui.board

import androidx.compose.ui.graphics.Color
import com.boldblot.backgammonplyground.data.BoardTheme
import com.boldblot.backgammonplyground.data.CheckerSide
import com.boldblot.backgammonplyground.data.CheckerStyle
import com.boldblot.backgammonplyground.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BoardThemesTest {

    @Test
    fun mattsBoardIsUnchanged() {
        val c = BoardThemes.matts.colors(CheckerSide.FIRST, CheckerStyle.RIMMED)
        assertEquals(Color(0xFFF2C14E), c.human)
        assertEquals(Color(0xFFA9781A), c.humanRim)
        assertEquals(Color(0xFFC8372D), c.cpu)
        assertEquals(Color(0xFF2A2C2F), c.tray)
    }

    @Test
    fun everyBoardLetsYouSwapCheckers() {
        for (t in BoardTheme.entries) {
            val p = BoardThemes.palette(t)
            val a = p.colors(CheckerSide.FIRST, CheckerStyle.FLAT)
            val b = p.colors(CheckerSide.SECOND, CheckerStyle.FLAT)
            assertNotEquals(t.name, a.human, a.cpu)
            assertEquals(t.name, a.human, b.cpu)
            assertEquals(t.name, a.cpu, b.human)
        }
    }

    @Test
    fun designsKeepTheirColoursAndSwatchNames() {
        val d = BoardThemes.toCustom(BoardThemes.claudes, "Mine")
        val p = BoardThemes.palette(d)
        assertEquals(BoardThemes.claudes.frame, p.frame)
        assertEquals(BoardThemes.claudes.first, p.first)
        val red = Swatches.all.first { it.first == "Red" }.second
        assertEquals("Red", BoardThemes.palette(d.copy(theirs = red)).secondName)
        for ((_, v) in Swatches.all) assertEquals(v, argb(v).argbLong())
        assertEquals("Charcoal", p.secondName)
    }

    @Test
    fun anEmptyDesignSlotFallsBackToMatts() {
        val s = Settings(boardTheme = BoardTheme.CUSTOM, customSlot = 2)
        assertEquals(BoardThemes.matts, BoardThemes.palette(s))
    }
}
