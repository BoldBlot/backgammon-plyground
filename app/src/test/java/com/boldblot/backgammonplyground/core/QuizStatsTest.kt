package com.boldblot.backgammonplyground.core

import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class QuizStatsTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val day = 86_400_000L
    private val t0 = 20_000L * day // midnight UTC, an epoch day

    private fun a(dayOffset: Int, id: Long, v: QuizAnswer.Verdict, cube: Boolean = false, match: Boolean = true, err: Float = 0f) =
        QuizAnswer(t0 + dayOffset * day + 1000, id, v, err, cube, match)

    private val R = QuizAnswer.Verdict.RIGHT
    private val W = QuizAnswer.Verdict.WRONG
    private val N = QuizAnswer.Verdict.NEUTRAL

    @Test fun tallies_and_rates() {
        val all = listOf(a(0, 1, R), a(0, 2, W, cube = true, err = 0.1f), a(1, 1, N, match = false), a(1, 3, R, match = false))
        val r = QuizStats.report(all, emptyList(), 0, now = t0 + 1 * day + 5000, zone = zone)
        assertEquals(4, r.total.answered)
        assertEquals(2, r.total.right)
        assertEquals(1, r.total.neutral)
        assertEquals(1, r.total.wrong)
        assertEquals(0.5, r.total.rate)
        assertEquals(1, r.cube.answered)
        assertEquals(2, r.money.answered)
        assertEquals(0.025, r.averageError!!, 1e-9)
        assertEquals(2, r.days.size)
    }

    @Test fun empty_has_no_rate() {
        val r = QuizStats.report(emptyList(), emptyList(), 0, now = t0, zone = zone)
        assertNull(r.total.rate)
        assertNull(r.averageError)
        assertEquals(0 to 0, QuizStats.streaks(emptySet(), 5))
    }

    @Test fun since_filters_but_streak_counts_all() {
        val all = listOf(a(0, 1, R), a(1, 1, R), a(2, 1, W))
        val r = QuizStats.report(all, emptyList(), since = t0 + 2 * day, now = t0 + 2 * day + 5000, zone = zone)
        assertEquals(1, r.total.answered)
        assertEquals(3, r.streak)
        assertEquals(3, r.bestStreak)
    }

    @Test fun streak_runs_through_yesterday_and_breaks_on_a_gap() {
        assertEquals(2 to 3, QuizStats.streaks(setOf(1L, 2L, 3L, 7L, 8L), today = 9L))
        assertEquals(0 to 3, QuizStats.streaks(setOf(1L, 2L, 3L), today = 9L))
        assertEquals(1 to 1, QuizStats.streaks(setOf(9L), today = 9L))
    }

    @Test fun folders_need_three_answers_and_sort_worst_first() {
        val all = (1..3).map { a(0, 1, W) } + (1..3).map { a(0, 2, R) } + listOf(a(0, 3, W))
        val r = QuizStats.report(all, listOf(1L to "Hard", 2L to "Easy", 3L to "Few"), 0, now = t0 + 5000, zone = zone)
        assertEquals(listOf("Hard", "Easy"), r.folders.map { it.name })
    }

    @Test fun missed_most_first() {
        val all = listOf(a(0, 1, W), a(0, 1, W), a(0, 2, W), a(0, 2, R), a(0, 3, R))
        val r = QuizStats.report(all, emptyList(), 0, now = t0 + 5000, zone = zone)
        assertEquals(listOf(1L, 2L), r.missed.map { it.positionId })
        assertEquals(2, r.missed[0].missed)
    }
}
