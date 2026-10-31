package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizFilterTest {
    private fun keys(vararg f: QuizFilter) = f.map { it.key }.toSet()

    @Test
    fun anyOfAGroupAllOfTheGroups() {
        assertTrue(QuizFilter.matches(0, 0, emptySet()))
        assertTrue(QuizFilter.matches(0, 0, setOf("CHECKER"))) // labels are not the quiz filter's business
        assertTrue(QuizFilter.matches(0, 0, keys(QuizFilter.NOT_TRIED)))
        assertFalse(QuizFilter.matches(1, 0, keys(QuizFilter.NOT_TRIED)))
        assertTrue(QuizFilter.matches(1, 3, keys(QuizFilter.NOT_TRIED, QuizFilter.SOME_RIGHT)))
        assertTrue(QuizFilter.matches(1, 3, keys(QuizFilter.SOME_RIGHT, QuizFilter.BALANCE_BELOW)))
        assertFalse(QuizFilter.matches(3, 1, keys(QuizFilter.SOME_RIGHT, QuizFilter.BALANCE_BELOW)))
        assertTrue(QuizFilter.matches(2, 2, keys(QuizFilter.SOME_WRONG, QuizFilter.BALANCE_ZERO, QuizFilter.BALANCE_ABOVE)))
    }

    @Test
    fun keysAndText() {
        QuizFilter.entries.forEach { assertEquals(it, QuizFilter.of(it.key)) }
        assertEquals(null, QuizFilter.of("BALANCE_ZERO"))
        assertEquals("Quiz answers: Missed · Quiz balance (right − missed): Below 0 or 0",
            QuizFilter.text(keys(QuizFilter.BALANCE_ZERO, QuizFilter.SOME_WRONG, QuizFilter.BALANCE_BELOW) + "CHECKER"))
    }
}
