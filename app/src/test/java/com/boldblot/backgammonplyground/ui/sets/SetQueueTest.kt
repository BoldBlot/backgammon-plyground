package com.boldblot.backgammonplyground.ui.sets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetQueueTest {

    @Test
    fun positionsGoInTheOrderAskedAndOnlyOnce() {
        val q = SetQueue()
        assertEquals(3, q.add(1, listOf(10, 11, 12)))
        // Asked again (Analyze on a selection while the import's analysis runs): no duplicates, the place kept.
        assertEquals(1, q.add(1, listOf(11, 13)))
        assertEquals(1, q.add(2, listOf(20)))
        assertEquals(mapOf(1L to SetProgress(4, 4), 2L to SetProgress(1, 1)), q.progress())
        val order = generateSequence { q.next()?.also { q.finished(it.first) } }.map { it.first }.toList()
        assertEquals(listOf(10L, 11, 12, 13, 20), order)
        assertEquals(emptyMap<Long, SetProgress>(), q.progress())
    }

    @Test
    fun progressCountsDownAndEndsWithTheSet() {
        val q = SetQueue()
        q.add(1, listOf(10, 11))
        q.add(2, listOf(20))
        q.finished(q.next()!!.first)
        assertEquals(mapOf(1L to SetProgress(1, 2), 2L to SetProgress(1, 1)), q.progress())
        q.finished(q.next()!!.first)
        assertEquals(mapOf(2L to SetProgress(1, 1)), q.progress())
    }

    @Test
    fun stopDropsOnlyThatSet() {
        val q = SetQueue()
        q.add(1, listOf(10, 11))
        q.add(2, listOf(20))
        q.stop(1)
        assertEquals(20L, q.next()!!.first)
        assertEquals(mapOf(2L to SetProgress(1, 1)), q.progress())
        q.finished(20)
        assertNull(q.next())
        // Finishing what was dropped (the one being analyzed when Stop came) changes nothing.
        q.finished(10)
        assertNull(q.next())
    }
}
