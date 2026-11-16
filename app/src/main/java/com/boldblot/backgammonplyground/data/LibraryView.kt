package com.boldblot.backgammonplyground.data

import com.boldblot.backgammonplyground.core.Opponent
import com.boldblot.backgammonplyground.core.PerformanceTotals
import com.boldblot.backgammonplyground.core.XgRules

/** The order of the Match Library's list. PR orders put matches without an analysis last. */
enum class LibrarySort(val label: String) {
    NEWEST("Newest first"), OLDEST("Oldest first"),
    MY_PR_BEST("My PR: best first"), MY_PR_WORST("My PR: worst first"),
    OPP_PR_BEST("Opponent's PR: best first"), OPP_PR_WORST("Opponent's PR: worst first"),
    RATING("Rating after the match"), RATING_CHANGE("Rating change"),
}

/** How a match ended, for the library's filter. */
enum class LibraryOutcome(val label: String) { WON("Won"), LOST("Lost") }

/** Matches or money sessions, for the library's filter. */
enum class LibraryKind(val label: String) { MATCH("Matches"), MONEY("Money games") }

/** When a match was played, for the library's filter. */
enum class LibraryPeriod(val label: String, val days: Int) {
    ANY("Any time", 0), WEEK("Last 7 days", 7), MONTH("Last 30 days", 30), QUARTER("Last 90 days", 90), YEAR("Last 365 days", 365),
}

/**
 * What the Match Library shows: within a group any of the ticked values, across groups all of them (an
 * empty group lets everything through). [grades] are XG's names for the player's PR ([XgRules.ratingName]);
 * [opponents] the opponents' names as the list shows them; [analyzed]: true = only analyzed matches,
 * false = only those without an analysis, null = both.
 */
data class LibraryFilter(
    val outcomes: Set<LibraryOutcome> = emptySet(),
    val kinds: Set<LibraryKind> = emptySet(),
    val lengths: Set<Int> = emptySet(),
    val opponents: Set<String> = emptySet(),
    val grades: Set<String> = emptySet(),
    val favourites: Boolean = false,
    val analyzed: Boolean? = null,
    val period: LibraryPeriod = LibraryPeriod.ANY,
) {
    val active: Boolean get() = this != NONE

    /** One line per ticked group, for the chips above the list. */
    fun parts(): List<String> = buildList {
        if (outcomes.isNotEmpty()) add(outcomes.joinToString(" or ") { it.label })
        if (kinds.isNotEmpty()) add(kinds.joinToString(" or ") { it.label })
        if (lengths.isNotEmpty()) add(lengths.sorted().joinToString(", ") { "$it-pt" })
        if (opponents.isNotEmpty()) add(if (opponents.size == 1) "vs ${opponents.first()}" else "${opponents.size} opponents")
        if (grades.isNotEmpty()) add(if (grades.size == 1) grades.first() else "${grades.size} PR grades")
        if (favourites) add("Favourites")
        analyzed?.let { add(if (it) "Analyzed" else "Not analyzed") }
        if (period != LibraryPeriod.ANY) add(period.label)
    }

    fun passes(m: MatchEntity, now: Long = System.currentTimeMillis()): Boolean {
        val pr = LibraryView.pr(m.humanPerformance)
        if (outcomes.isNotEmpty() && LibraryView.outcome(m) !in outcomes) return false
        if (kinds.isNotEmpty() && (if (m.length == 0) LibraryKind.MONEY else LibraryKind.MATCH) !in kinds) return false
        if (lengths.isNotEmpty() && m.length !in lengths) return false
        if (opponents.isNotEmpty() && Opponent.name(m.cpuLevel) !in opponents) return false
        if (grades.isNotEmpty() && (pr == null || XgRules.ratingName(pr) !in grades)) return false
        if (favourites && !m.favourite) return false
        if (analyzed != null && (pr != null) != analyzed) return false
        if (period.days > 0 && m.startedAt < now - period.days * 86_400_000L) return false
        return true
    }

    /** For [Settings.libraryFilter]. */
    fun encode(): String = listOf(
        outcomes.joinToString(",") { it.name }, kinds.joinToString(",") { it.name }, lengths.joinToString(","),
        opponents.joinToString("\n"), grades.joinToString("\n"), if (favourites) "1" else "",
        analyzed?.let { if (it) "1" else "0" } ?: "", period.name,
    ).joinToString(SEP)

    companion object {
        val NONE = LibraryFilter()
        private const val SEP = "\u001f"

        fun decode(text: String): LibraryFilter {
            if (text.isBlank()) return NONE
            val p = text.split(SEP)
            fun list(i: Int, sep: String) = p.getOrNull(i).orEmpty().split(sep).filter { it.isNotBlank() }
            return LibraryFilter(
                outcomes = list(0, ",").mapNotNull { n -> LibraryOutcome.entries.firstOrNull { it.name == n } }.toSet(),
                kinds = list(1, ",").mapNotNull { n -> LibraryKind.entries.firstOrNull { it.name == n } }.toSet(),
                lengths = list(2, ",").mapNotNull { it.toIntOrNull() }.toSet(),
                opponents = list(3, "\n").toSet(), grades = list(4, "\n").toSet(),
                favourites = p.getOrNull(5) == "1",
                analyzed = when (p.getOrNull(6)) { "1" -> true; "0" -> false; else -> null },
                period = LibraryPeriod.entries.firstOrNull { it.name == p.getOrNull(7) } ?: LibraryPeriod.ANY,
            )
        }
    }
}

/** The Match Library's list: [LibraryFilter] and [LibrarySort] applied to the stored matches. */
object LibraryView {
    /** XG's grade names, best first. */
    val GRADES = listOf("World Champ", "World Class", "Expert", "Advanced", "Intermediate", "Casual Player", "Beginner", "Distracted")

    fun pr(performance: String?): Double? =
        performance?.let { runCatching { AppJson.decodeFromString<PerformanceTotals>(it) }.getOrNull() }?.pr

    /** Won or lost; null for a match in progress or abandoned, and for a level money session. */
    fun outcome(m: MatchEntity): LibraryOutcome? = when {
        m.status != MatchStatus.FINISHED -> null
        m.length == 0 -> if (m.humanScore > m.cpuScore) LibraryOutcome.WON else if (m.humanScore < m.cpuScore) LibraryOutcome.LOST else null
        m.winner == 0 -> LibraryOutcome.WON
        m.winner == 1 -> LibraryOutcome.LOST
        else -> null
    }

    /** [all] (newest first, as stored) that pass [filter], in [sort]'s order. */
    fun list(all: List<MatchEntity>, filter: LibraryFilter, sort: LibrarySort, now: Long = System.currentTimeMillis()): List<MatchEntity> {
        val kept = all.filter { filter.passes(it, now) }
        // Matches without the value sorted by go last, newest first.
        fun <T : Comparable<T>> by(descending: Boolean, value: (MatchEntity) -> T?): List<MatchEntity> {
            val (known, unknown) = kept.partition { value(it) != null }
            val sorted = if (descending) known.sortedByDescending { value(it)!! } else known.sortedBy { value(it)!! }
            return sorted + unknown.sortedByDescending { it.startedAt }
        }
        return when (sort) {
            LibrarySort.NEWEST -> kept.sortedByDescending { it.startedAt }
            LibrarySort.OLDEST -> kept.sortedBy { it.startedAt }
            LibrarySort.MY_PR_BEST -> by(false) { pr(it.humanPerformance) }
            LibrarySort.MY_PR_WORST -> by(true) { pr(it.humanPerformance) }
            LibrarySort.OPP_PR_BEST -> by(false) { pr(it.cpuPerformance) }
            LibrarySort.OPP_PR_WORST -> by(true) { pr(it.cpuPerformance) }
            LibrarySort.RATING -> by(true) { it.eloAfter }
            LibrarySort.RATING_CHANGE -> by(true) { m -> m.eloAfter?.let { a -> m.eloBefore?.let { b -> a - b } } }
        }
    }
}
