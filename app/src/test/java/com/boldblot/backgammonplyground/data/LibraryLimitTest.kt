package com.boldblot.backgammonplyground.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryLimitTest {
    private fun match(id: Long, status: Int = MatchStatus.FINISHED) =
        MatchEntity(id = id, startedAt = id * 1000, length = 5, cpuLevel = "x", useCube = true, jacoby = false, crawford = true, status = status)

    @Test
    fun nothingGoesWhileWithinTheLimit() {
        // Crashed in 0.8.3: 26 games and a limit of 100 asked for take(-74).
        assertTrue(matchesOverLimit((1L..26L).map { match(it) }, 100).isEmpty())
        assertTrue(matchesOverLimit((1L..100L).map { match(it) }, 100).isEmpty())
        assertTrue(matchesOverLimit(emptyList(), 100).isEmpty())
    }

    @Test
    fun theOldestFinishedGoFirstAndAMatchInProgressStays() {
        val all = listOf(match(1, MatchStatus.IN_PROGRESS), match(2), match(3), match(4))
        assertEquals(listOf(2L, 3L), matchesOverLimit(all, 2).map { it.id })
    }
}
