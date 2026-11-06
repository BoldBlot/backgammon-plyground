package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PositionTagsTest {
    @Test
    fun countsLikeAnOdometerWithTheLastSymbolFirst() {
        assertEquals("AA000", PositionTags.of(1))
        assertEquals("AA001", PositionTags.of(2))
        assertEquals("AA009", PositionTags.of(10))
        assertEquals("AA010", PositionTags.of(11))
        assertEquals("AB000", PositionTags.of(1001))
        assertEquals("BA000", PositionTags.of(25_001))
    }

    @Test
    fun skipsTheLetterO() {
        // After N comes P: AN999 is followed by AP000.
        assertEquals("AN999", PositionTags.of(14_000))
        assertEquals("AP000", PositionTags.of(14_001))
    }

    @Test
    fun addsDigitsThenLettersInFront() {
        assertEquals("ZZ999", PositionTags.of(625_000))
        assertEquals("1AA000", PositionTags.of(625_001))
        assertEquals("9ZZ999", PositionTags.of(625_000L * 10))
        assertEquals("10AA000", PositionTags.of(625_000L * 10 + 1))
        assertEquals("99ZZ999", PositionTags.of(625_000L * 100))
        // Two digits used up: a letter, then another one in front.
        assertEquals("B00AA000", PositionTags.of(62_500_001))
    }

    @Test
    fun neverUsesO() {
        for (id in 1L..30_000L step 7) assertFalse(PositionTags.of(id).contains('O'))
    }
}
