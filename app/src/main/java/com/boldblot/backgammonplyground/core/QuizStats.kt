package com.boldblot.backgammonplyground.core

import java.time.LocalDate
import java.time.ZoneId

/** One kept quiz answer (see [com.boldblot.backgammonplyground.data.QuizAnswerEntity]). */
data class QuizAnswer(val at: Long, val positionId: Long, val verdict: Verdict, val error: Float, val cube: Boolean, val match: Boolean) {
    enum class Verdict { RIGHT, NEUTRAL, WRONG }
}

/** Answers counted: [answered] in all, [right] of them right. */
data class QuizTally(val answered: Int = 0, val right: Int = 0, val neutral: Int = 0) {
    val wrong: Int get() = answered - right - neutral
    /** Share right, 0..1 (null with no answers). */
    val rate: Double? get() = if (answered == 0) null else right.toDouble() / answered
}

/** One day of the quiz: [day] as an epoch day. */
data class QuizDay(val day: Long, val tally: QuizTally)

data class QuizFolderStat(val name: String, val tally: QuizTally, val id: Long? = null)

/** A label: the positions in My positions that carry it ([inLibrary]) and how the quiz answers to those went ([tally]). */
data class QuizLabelStat(val label: String, val tally: QuizTally, val inLibrary: Int)

data class QuizMissed(val positionId: Long, val missed: Int, val answered: Int)

data class QuizStatsReport(
    val total: QuizTally,
    val checker: QuizTally,
    val cube: QuizTally,
    val match: QuizTally,
    val money: QuizTally,
    /** Average equity lost per answer. */
    val averageError: Double?,
    /** The days with answers, oldest first. */
    val days: List<QuizDay>,
    /** Days in a row with a quiz, up to today (or up to yesterday while today has none yet). */
    val streak: Int,
    val bestStreak: Int,
    /** Folders with at least [QuizStats.MIN_FOLDER] answers, lowest right-rate first. */
    val folders: List<QuizFolderStat>,
    /** Positions missed most (at least once), most first. */
    val missed: List<QuizMissed>,
    /** Labels with at least [QuizStats.MIN_FOLDER] answers, lowest right-rate first. */
    val labels: List<QuizLabelStat> = emptyList(),
)

/** The quiz page's numbers from the kept answers. */
object QuizStats {
    const val MIN_FOLDER = 3
    const val MISSED_SHOWN = 10

    private fun tally(a: List<QuizAnswer>) = QuizTally(
        a.size, a.count { it.verdict == QuizAnswer.Verdict.RIGHT }, a.count { it.verdict == QuizAnswer.Verdict.NEUTRAL },
    )

    private fun dayOf(at: Long, zone: ZoneId): Long = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toEpochDay()

    /**
     * The report for answers [all] since [since] (epoch millis; 0 = all time); [folders] links positions to folder names.
     * The streak is always over all answers.
     */
    fun report(
        all: List<QuizAnswer>, folders: List<Pair<Long, String>>, since: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault(),
        /** Folder name to its id, to open it from the list. */
        folderIds: Map<String, Long> = emptyMap(),
        /** Every position in My positions with its labels (the frequency in the library, and the answers' labels). */
        labels: List<Pair<Long, Set<String>>> = emptyList(),
    ): QuizStatsReport {
        val a = all.filter { it.at >= since }
        val days = a.groupBy { dayOf(it.at, zone) }.toSortedMap().map { (d, v) -> QuizDay(d, tally(v)) }
        val byFolder = folders.groupBy({ it.first }, { it.second })
        val folderStats = a.flatMap { ans -> byFolder[ans.positionId].orEmpty().map { it to ans } }
            .groupBy({ it.first }, { it.second })
            .map { (name, v) -> QuizFolderStat(name, tally(v), folderIds[name]) }
            .filter { it.tally.answered >= MIN_FOLDER }
            .sortedWith(compareBy<QuizFolderStat> { it.tally.rate ?: 1.0 }.thenByDescending { it.tally.answered })
        val missed = a.groupBy { it.positionId }
            .map { (id, v) -> QuizMissed(id, v.count { it.verdict == QuizAnswer.Verdict.WRONG }, v.size) }
            .filter { it.missed > 0 }
            .sortedWith(compareByDescending<QuizMissed> { it.missed }.thenBy { it.answered })
            .take(MISSED_SHOWN)
        // Quiz trend labels and one-position ID tags say nothing about what is hard.
        fun shown(k: String) = !Labels.isTrend(k) && !Labels.isId(k)
        val byPosition = labels.toMap()
        val inLibrary = labels.flatMap { (_, ls) -> ls.filter(::shown) }.groupingBy { it }.eachCount()
        val labelStats = a.flatMap { ans -> byPosition[ans.positionId].orEmpty().filter(::shown).map { it to ans } }
            .groupBy({ it.first }, { it.second })
            .map { (label, v) -> QuizLabelStat(label, tally(v), inLibrary[label] ?: 0) }
            .filter { it.tally.answered >= MIN_FOLDER }
            .sortedWith(compareBy<QuizLabelStat> { it.tally.rate ?: 1.0 }.thenByDescending { it.tally.answered })
        val (streak, best) = streaks(all.map { dayOf(it.at, zone) }.toSortedSet(), LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now), zone).toEpochDay())
        return QuizStatsReport(
            total = tally(a), checker = tally(a.filter { !it.cube }), cube = tally(a.filter { it.cube }),
            match = tally(a.filter { it.match }), money = tally(a.filter { !it.match }),
            averageError = a.takeIf { it.isNotEmpty() }?.map { it.error.toDouble() }?.average(),
            days = days, streak = streak, bestStreak = best, folders = folderStats, missed = missed, labels = labelStats,
        )
    }

    /** The current run of days with a quiz ending [today] (or yesterday), and the longest run. */
    fun streaks(days: Set<Long>, today: Long): Pair<Int, Int> {
        if (days.isEmpty()) return 0 to 0
        var best = 0
        var run = 0
        var prev: Long? = null
        for (d in days.sorted()) {
            run = if (prev != null && d == prev + 1) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        var cur = 0
        var d = if (today in days) today else today - 1
        while (d in days) { cur++; d-- }
        return cur to best
    }
}
