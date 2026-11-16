package com.boldblot.backgammonplyground.data

import com.boldblot.backgammonplyground.core.PerformanceTotals
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Match Library's filter and order, and My positions' balance order. */
class LibraryViewTest {
    private val day = 86_400_000L
    private val now = 100 * day

    /** PR = 500 * error / decisions. */
    private fun perf(pr: Double?) = pr?.let { AppJson.encodeToString(PerformanceTotals(checkerDecisions = 100, checkerError = it / 5)) }

    private fun match(
        id: Long, length: Int = 5, winner: Int? = 0, pr: Double? = null, oppPr: Double? = null, daysAgo: Int = 1, favourite: Boolean = false,
        level: String = "x", before: Double? = null, after: Double? = null, human: Int = 0, cpu: Int = 0,
    ) = MatchEntity(
        id = id, startedAt = now - daysAgo * day, length = length, cpuLevel = level, useCube = true, jacoby = false, crawford = true,
        status = MatchStatus.FINISHED, winner = winner, humanPerformance = perf(pr), cpuPerformance = perf(oppPr), favourite = favourite,
        eloBefore = before, eloAfter = after, humanScore = human, cpuScore = cpu,
    )

    private val all = listOf(
        match(1, pr = 4.0, oppPr = 9.0, daysAgo = 1, before = 1500.0, after = 1510.0),
        match(2, winner = 1, pr = 9.0, oppPr = 3.0, daysAgo = 3, favourite = true, before = 1510.0, after = 1495.0),
        match(3, length = 0, winner = null, daysAgo = 40, human = 3, cpu = 5),
        match(4, length = 7, winner = 0, pr = 13.0, daysAgo = 200, before = 1495.0, after = 1520.0),
    )

    private fun ids(f: LibraryFilter = LibraryFilter.NONE, sort: LibrarySort = LibrarySort.NEWEST) = LibraryView.list(all, f, sort, now).map { it.id }

    @Test
    fun groupsNarrowAndTickedValuesWiden() {
        assertEquals(listOf(1L, 2L, 3L, 4L), ids())
        assertEquals(listOf(1L, 4L), ids(LibraryFilter(outcomes = setOf(LibraryOutcome.WON))))
        // A money session behind counts as lost.
        assertEquals(listOf(2L, 3L), ids(LibraryFilter(outcomes = setOf(LibraryOutcome.LOST))))
        assertEquals(listOf(3L), ids(LibraryFilter(kinds = setOf(LibraryKind.MONEY))))
        assertEquals(listOf(1L, 2L, 4L), ids(LibraryFilter(lengths = setOf(5, 7))))
        assertEquals(listOf(2L), ids(LibraryFilter(favourites = true)))
        assertEquals(listOf(1L, 4L), ids(LibraryFilter(grades = setOf("World Class", "Intermediate"))))
        assertEquals(listOf(3L), ids(LibraryFilter(analyzed = false)))
        assertEquals(listOf(1L, 2L), ids(LibraryFilter(period = LibraryPeriod.MONTH)))
        assertEquals(listOf(1L), ids(LibraryFilter(outcomes = setOf(LibraryOutcome.WON), lengths = setOf(5))))
    }

    @Test
    fun ordersPutMatchesWithoutTheValueLast() {
        assertEquals(listOf(4L, 3L, 2L, 1L), ids(sort = LibrarySort.OLDEST))
        assertEquals(listOf(1L, 2L, 4L, 3L), ids(sort = LibrarySort.MY_PR_BEST))
        assertEquals(listOf(4L, 2L, 1L, 3L), ids(sort = LibrarySort.MY_PR_WORST))
        assertEquals(listOf(2L, 1L, 3L, 4L), ids(sort = LibrarySort.OPP_PR_BEST))
        assertEquals(listOf(4L, 1L, 2L, 3L), ids(sort = LibrarySort.RATING))
        assertEquals(listOf(4L, 1L, 2L, 3L), ids(sort = LibrarySort.RATING_CHANGE))
    }

    @Test
    fun theFilterSurvivesItsStoredForm() {
        val f = LibraryFilter(
            outcomes = setOf(LibraryOutcome.LOST), kinds = setOf(LibraryKind.MATCH), lengths = setOf(5, 11), opponents = setOf("PureTD PR <1", "Anna, B."),
            grades = setOf("Expert"), favourites = true, analyzed = false, period = LibraryPeriod.QUARTER,
        )
        assertEquals(f, LibraryFilter.decode(f.encode()))
        assertEquals(LibraryFilter.NONE, LibraryFilter.decode(""))
        assertFalse(LibraryFilter.NONE.active)
        assertTrue(f.active)
    }

    @Test
    fun positionsByBalancePutTheNeverAnsweredLast() {
        // (balance, answered), newest first.
        val ps = listOf("a" to (2 to true), "b" to (0 to false), "c" to (-3 to true), "d" to (0 to true))
        fun order(s: PositionsSort) = s.sort(ps, error = { null }, balance = { it.second.first }, answered = { it.second.second }).map { it.first }
        assertEquals(listOf("c", "d", "a", "b"), order(PositionsSort.BALANCE_LOW))
        assertEquals(listOf("a", "d", "c", "b"), order(PositionsSort.BALANCE_HIGH))
        assertEquals(listOf("a", "b", "c", "d"), order(PositionsSort.NEWEST))
    }
}
