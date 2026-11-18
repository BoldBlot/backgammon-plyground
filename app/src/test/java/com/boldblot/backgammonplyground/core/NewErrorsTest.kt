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

    @Test
    fun averageBasisCombinesTheOriginalAndTheLastAnswer() {
        val both = AverageBasis.ORIGINAL_AND_LAST
        assertEquals(0.1f, both.of(0.2f, 0f)!!, 1e-6f)
        assertEquals(0.15f, both.of(0.2f, 0.1f)!!, 1e-6f)
        // Never answered: the original alone. No game play: the last answer alone.
        assertEquals(0.2f, both.of(0.2f, null)!!, 1e-6f)
        assertEquals(0.3f, both.of(null, 0.3f)!!, 1e-6f)
        assertNull(both.of(null, null))
        // As it was before: the original only.
        assertEquals(0.2f, AverageBasis.ORIGINAL.of(0.2f, 0f)!!, 1e-6f)
        assertNull(AverageBasis.ORIGINAL.of(null, 0.3f))
        // Last answer only, falling back to the original for a position never answered.
        assertEquals(0f, AverageBasis.LAST.of(0.2f, 0f)!!, 1e-6f)
        assertEquals(0.2f, AverageBasis.LAST.of(0.2f, null)!!, 1e-6f)
    }
}

