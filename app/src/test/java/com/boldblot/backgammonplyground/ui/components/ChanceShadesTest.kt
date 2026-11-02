package com.boldblot.backgammonplyground.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChanceShadesTest {

    /** Single wins are the checker colour; gammons and backgammons get brighter, step by step. */
    @Test
    fun darkAndMidCheckersGetBrighterForGammons() {
        for (c in listOf(Color(0xFF2A2C2F), Color(0xFFC8372D), Color(0xFF3F6FB5))) {
            val (single, gammon, backgammon) = chanceShades(c)
            assertEquals(c, single)
            assertTrue(gammon.luminance() > single.luminance())
            assertTrue(backgammon.luminance() > gammon.luminance())
        }
    }

    /** Light checkers cannot get brighter, so their gammons and backgammons get darker. */
    @Test
    fun lightCheckersGetDarkerForGammons() {
        val c = Color(0xFFF4F1E8)
        val (single, gammon, backgammon) = chanceShades(c)
        assertEquals(c, single)
        assertTrue(gammon.luminance() < single.luminance())
        assertTrue(backgammon.luminance() < gammon.luminance())
    }
}
