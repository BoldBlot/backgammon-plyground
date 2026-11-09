package com.boldblot.backgammonplyground.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The error report lists every setting, one per line, without the player's name. */
class SettingsReportTest {
    @Test
    fun everySettingOnItsOwnLine() {
        val s = Settings(playerName = "Somebody", blunderFilter = setOf("a, b", "c"))
        val lines = settingsReport(s).trim().lines()
        val fields = Settings::class.java.declaredFields.count { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(fields - 1, lines.size)
        assertTrue(lines.all { Regex("""^\w+ = .*""").matches(it) })
        assertFalse(lines.any { "Somebody" in it })
        assertTrue(lines.any { it.startsWith("blunderFilter = ") && "a, b" in it })
    }
}
