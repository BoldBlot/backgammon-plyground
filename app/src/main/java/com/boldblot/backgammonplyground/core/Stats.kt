package com.boldblot.backgammonplyground.core

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.sqrt

/** Dice statistics for one player: counts of each ordered roll (6x6) plus totals. */
@Serializable
data class DiceTotals(
    /** counts[(d1-1)*6 + (d2-1)] for the higher die first; doubles on the diagonal. */
    val counts: List<Int> = List(36) { 0 },
) {
    val rolls: Int get() = counts.sum()
    val doubles: Int get() = (0 until 6).sumOf { counts[it * 6 + it] }
    val pips: Int get() = counts.indices.sumOf { i ->
        val a = i / 6 + 1
        val b = i % 6 + 1
        counts[i] * if (a == b) 4 * a else a + b
    }

    /** Number of rolls containing each face 1..6 (a double counts once). */
    fun faceCounts(): List<Int> = (1..6).map { f ->
        counts.indices.filter { i -> i / 6 + 1 == f || i % 6 + 1 == f }.sumOf { counts[it] }
    }

    fun count(a: Int, b: Int): Int = counts[(maxOf(a, b) - 1) * 6 + (minOf(a, b) - 1)]

    fun add(d: Dice): DiceTotals {
        val i = (maxOf(d.d1, d.d2) - 1) * 6 + (minOf(d.d1, d.d2) - 1)
        return DiceTotals(counts.toMutableList().also { it[i]++ })
    }

    operator fun plus(o: DiceTotals) = DiceTotals(counts.zip(o.counts) { a, b -> a + b })
}

/** Everything the statistics screen needs from one finished game. */
@Serializable
data class GameSummary(
    val result: GameResult,
    val performance: List<PerformanceTotals>,
    /** Dice rolled by each player, excluding the opening roll (which is shared). */
    val dice: List<DiceTotals>,
    val crawford: Boolean,
    val finalCube: Int,
)

object Summaries {

    fun of(game: GameState, matchLength: Int): GameSummary {
        val factor = XgRules.errorFactor(matchLength)
        val perf = MutableList(2) { PerformanceTotals() }
        for (d in game.decisions) {
            // A decision tried again counts as first played.
            val t = d.firstTry
            if (t != null) perf[d.player] = perf[d.player].add(t.kind, t.counted, (t.error ?: 0f) * factor)
            else perf[d.player] = perf[d.player].add(d.kind, d.counted, (d.error ?: 0f) * factor)
        }
        val dice = MutableList(2) { DiceTotals() }
        for (r in game.rolls) {
            perf[r.player] = perf[r.player].addRoll(r.luck)
            if (!r.opening) dice[r.player] = dice[r.player].add(r.dice)
        }
        return GameSummary(requireNotNull(game.result), perf, dice, game.crawford, game.cube.value)
    }
}

/**
 * FIBS rating formula (as used by NJ Backgammon and most servers):
 *   P(underdog wins) = 1 / (10^(D * sqrt(N) / 2000) + 1)
 *   winner gains 4 * sqrt(N) * P(winner would have lost); the loser loses the same.
 * New players move faster: the change is multiplied by max(1, 5 - experience / 100),
 * where experience is the total length of matches played so far.
 */
object Elo {
    const val START = 1500.0

    /** Fixed ratings used for the computer levels (estimates, see docs). */
    /** A mistake-rate opponent: Grandmaster's rating less about 15 points per point of estimated PR. */
    fun cpuRating(level: CpuLevel, rate: MistakeRate?): Double =
        if (level == CpuLevel.MISTAKES && rate != null) (2050.0 - 15.0 * rate.estimatedPr).coerceAtLeast(1300.0) else cpuRating(level)

    fun cpuRating(level: CpuLevel): Double = when (level) {
        CpuLevel.BEGINNER -> 1400.0
        CpuLevel.CASUAL -> 1500.0
        CpuLevel.INTERMEDIATE -> 1600.0
        CpuLevel.ADVANCED -> 1750.0
        CpuLevel.EXPERT -> 1850.0
        CpuLevel.WORLD_CLASS -> 2000.0
        CpuLevel.SUPREMO -> 2025.0
        CpuLevel.GRANDMASTER -> 2050.0
        CpuLevel.STRONG -> 2060.0
        CpuLevel.MAXIMUM -> 2075.0
        CpuLevel.MISTAKES -> 2050.0
    }

    fun winProbability(rating: Double, opponent: Double, length: Int): Double {
        val d = rating - opponent
        return 1.0 - 1.0 / (10.0.pow(d * sqrt(length.toDouble()) / 2000.0) + 1.0)
    }

    /** New rating after a match of [length] points against [opponent]. */
    fun update(rating: Double, experience: Int, opponent: Double, length: Int, won: Boolean): Double {
        val p = winProbability(rating, opponent, length)
        val k = maxOf(1.0, 5.0 - experience / 100.0)
        val change = 4.0 * sqrt(length.toDouble()) * (if (won) 1.0 - p else -p)
        return rating + k * change
    }

    fun title(rating: Double): String = when {
        rating > 2000 -> "World Class"
        rating >= 1850 -> "Expert"
        rating >= 1700 -> "Advanced"
        rating >= 1500 -> "Intermediate"
        else -> "Beginner"
    }
}

/** The human's blunders, kept in a list for later study. */
object Blunders {

    /** True if [d] is one of the human's blunders (hinted decisions excluded). [weightedError] includes the 1-point factor. */
    fun isBlunder(d: DecisionRecord, weightedError: Float): Boolean =
        d.player == Player.HUMAN && !d.hinted && XgRules.severity(weightedError) == Severity.BLUNDER

    /** Indices into [game]'s decisions of the human's blunders, with their weighted errors. */
    fun of(game: GameState, matchLength: Int): List<Pair<Int, Float>> {
        val factor = XgRules.errorFactor(matchLength)
        return game.decisions.mapIndexedNotNull { i, d ->
            val w = (d.error ?: return@mapIndexedNotNull null) * factor
            if (isBlunder(d, w)) i to w else null
        }
    }
}
