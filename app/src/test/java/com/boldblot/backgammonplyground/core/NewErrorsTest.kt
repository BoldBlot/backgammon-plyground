package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** New errors methods (feedback 2026-11-02 #7-#10). */
class NewErrorsTest {
    @Test
    fun adaptiveFollowsTheLibraryButNotBelowTheSlider() {
        assertEquals(0.09f, NewErrors.adaptiveThreshold(0.02f, 0.9f, 0.1f), 1e-6f)
        assertEquals(0.15f, NewErrors.adaptiveThreshold(0.15f, 0.9f, 0.1f), 1e-6f)
        assertEquals(0.05f, NewErrors.adaptiveThreshold(0.05f, 0.9f, null), 1e-6f)
    }

    @Test
    fun averageLeavesOutRightAnswers() {
        assertEquals(0.1f, NewErrors.average(listOf(0f, 0.05f, 0.15f))!!, 1e-6f)
        assertNull(NewErrors.average(listOf(0f)))
    }

    @Test
    fun topXTakesTheBiggestAboveTheMinimum() {
        val errors = listOf(1L to 0.03f, 2L to 0.2f, 3L to 0.01f, 4L to 0.09f, 5L to 0.12f)
        assertEquals(listOf(2L, 5L, 4L), NewErrors.topX(errors, 3, 0.02f))
        assertEquals(listOf(2L), NewErrors.topX(errors, 3, 0.15f))
        // Two filed already (errors analyzed late): one more.
        assertEquals(listOf(2L), NewErrors.topX(errors, 3, 0.02f, already = 2))
        assertEquals(emptyList<Long>(), NewErrors.topX(errors, 3, 0.02f, already = 3))
    }

    @Test
    fun rollingBufferDropsTheOldest() {
        assertEquals(listOf(4L, 5L), NewErrors.overflow(listOf(1L, 2L, 3L, 4L, 5L), 3))
        assertEquals(emptyList<Long>(), NewErrors.overflow(listOf(1L, 2L), 50))
    }
}
