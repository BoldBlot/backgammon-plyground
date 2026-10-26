package com.boldblot.backgammonplyground.core

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Error classification and Performance Rating exactly as defined by eXtreme Gammon
 * (eXtreme Gammon 2 manual, "Analyze > Categories" and "Performance"):
 *
 *  - All errors are measured in normalized equity (per unit cube; in match play
 *    match-winning-chance losses are converted to "equivalent to money" equity).
 *  - An error of more than 0.020 is an Error, more than 0.080 a Blunder.
 *  - Performance = total equity lost / number of decisions; PR = performance x 500.
 *  - A decision is a checker play or cube action that is not obvious. Obvious are:
 *      * forced moves;
 *      * checker plays where the best and the worst choice differ by less than 0.001;
 *      * no-double decisions where doubling and not doubling have the same equity
 *        (optional double);
 *      * no-double decisions where the equity before doubling exceeds the equity after
 *        the double by 0.200 or more (obvious no double);
 *      * no-double decisions where the equity before doubling exceeds the equity after
 *        a drop by 0.200 or more (obvious too good to double);
 *      * no-double decisions where the equity before and after a double is very negative
 *        (trailing far behind in a match).
 *    Never obvious: a decision that results in a double, and any take/pass decision.
 *  - In 1-point matches every error is multiplied by 1.5.
 *
 * XG does not publish the threshold for "very negative"; [VERY_NEGATIVE] is our choice.
 */
object XgRules {
    const val ERROR_THRESHOLD = 0.020f
    const val BLUNDER_THRESHOLD = 0.080f
    const val OBVIOUS_CHECKER_SPREAD = 0.001f
    const val OBVIOUS_CUBE_MARGIN = 0.200f
    const val OPTIONAL_EPSILON = 0.0005f
    const val VERY_NEGATIVE = -0.900f
    const val ONE_POINT_MATCH_FACTOR = 1.5f
    const val PR_SCALE = 500f

    /** Factor applied to every error (1.5 in 1-point matches, else 1). */
    fun errorFactor(matchLength: Int): Float = if (matchLength == 1) ONE_POINT_MATCH_FACTOR else 1f

    fun severity(weightedError: Float): Severity = when {
        weightedError > BLUNDER_THRESHOLD -> Severity.BLUNDER
        weightedError > ERROR_THRESHOLD -> Severity.ERROR
        weightedError > 0.0005f -> Severity.INACCURACY
        else -> Severity.NONE
    }

    /** XG's rating categories for a PR value. */
    fun ratingName(pr: Double): String = when {
        pr < 0.0 -> "Wow!"
        pr < 2.5 -> "World Champ"
        pr < 5.0 -> "World Class"
        pr < 7.5 -> "Expert"
        pr < 12.5 -> "Advanced"
        pr < 17.5 -> "Intermediate"
        pr < 22.5 -> "Casual Player"
        pr < 30.0 -> "Beginner"
        else -> "Distracted"
    }

    /**
     * GNU Backgammon's luck rating for a player's [luck] over [rolls] rolls: the average luck
     * per roll × 10, against gnubg's levels ±0.3 and ±0.6. Null when there were no rolls.
     */
    fun luckRating(luck: Double, rolls: Int): String? {
        if (rolls == 0) return null
        val r = luck / rolls * 10
        return when {
            r > 0.6 -> "Go to Las Vegas"
            r > 0.3 -> "Good dice, man!"
            r < -0.6 -> "Go to bed"
            r < -0.3 -> "Bad dice, man!"
            else -> "None"
        }
    }

    /** XG's approximate FIBS/GammonSite-equivalent Elo for a performance (PR / 500). */
    fun eloEquivalent(pr: Double): Double {
        val performance = -pr / PR_SCALE
        return 1.0 / (1.0 + kotlin.math.exp(-performance * 40.0 - 1.12)) * 2000.0 + 732.0
    }
}

/**
 * How bad a decision was. INACCURACY is XG's "Different" (not best, but below the
 * error threshold); it does not count as an error in statistics.
 */
enum class Severity(val label: String) {
    NONE("Best"),
    INACCURACY("Inaccuracy"),
    ERROR("Error"),
    BLUNDER("Blunder");

    fun atLeast(other: Severity) = ordinal >= other.ordinal
}

enum class DecisionKind {
    CHECKER, DOUBLE, NO_DOUBLE, TAKE, PASS,
    /** Money: took and redoubled at once (a take decision). */
    BEAVER,
    /** Money: the doubler answers a beaver by redoubling again, or plays on (cube decisions). */
    RACCOON, NO_RACCOON;

    /** The doubled player's answer (take, pass or beaver). */
    val isResponse: Boolean get() = this == TAKE || this == PASS || this == BEAVER

    /** The doubler's answer to a beaver. */
    val isRaccoonDecision: Boolean get() = this == RACCOON || this == NO_RACCOON
}

/** Analysis of a checker play. Candidates are best first; [playedIndex] indexes into them. */
@Serializable
data class CheckerAnalysis(
    val candidates: List<Candidate>,
    val playedIndex: Int,
    val playedEquity: Float,
    val bestEquity: Float,
    val worstEquity: Float,
    val legalMoves: Int,
    val plies: Int,
) {
    val error: Float get() = (bestEquity - playedEquity).coerceAtLeast(0f)

    /** XG: forced moves and plays with a best/worst spread under 0.001 are obvious. */
    val isDecision: Boolean get() = legalMoves > 1 && bestEquity - worstEquity >= XgRules.OBVIOUS_CHECKER_SPREAD
}

object DecisionMath {

    /** Equity after doubling given the best answer, which with [beavers] includes beavering (cube 4×). */
    fun afterDouble(eval: CubeEval, beavers: Boolean): Float =
        if (beavers) minOf(eval.afterDouble, 2 * eval.doubleTake) else eval.afterDouble

    /** Error of the player on roll's cube action (normalized equity, >= 0). */
    fun doubleError(eval: CubeEval, doubled: Boolean, beavers: Boolean = false): Float {
        val after = afterDouble(eval, beavers)
        val chosen = if (doubled) after else eval.noDouble
        val best = maxOf(eval.noDouble, after)
        return (best - chosen).coerceAtLeast(0f)
    }

    /** The doubled player's equities, taker's view: take, pass, and beaver (null when not allowed). */
    fun responseEquities(eval: CubeEval, beavers: Boolean): Triple<Float, Float, Float?> =
        Triple(-eval.doubleTake, -eval.doublePass, if (beavers) -2 * eval.doubleTake else null)

    /** Error of the opponent's response ([kind]: take, pass or beaver); equities from the taker's view. */
    fun takeError(eval: CubeEval, kind: DecisionKind, beavers: Boolean = false): Float {
        val (takeEq, passEq, beaverEq) = responseEquities(eval, beavers || kind == DecisionKind.BEAVER)
        val chosen = when (kind) {
            DecisionKind.PASS -> passEq
            DecisionKind.BEAVER -> beaverEq!!
            else -> takeEq
        }
        return (maxOf(takeEq, passEq, beaverEq ?: takeEq) - chosen).coerceAtLeast(0f)
    }

    fun takeError(eval: CubeEval, took: Boolean): Float = takeError(eval, if (took) DecisionKind.TAKE else DecisionKind.PASS)

    /**
     * Error of the doubler's answer to a beaver, doubler's view in units of the cube before the
     * double: playing on at 4× is worth 2 × double/take, raccooning to 8× is worth 4 × double/take.
     */
    fun raccoonError(eval: CubeEval, raccooned: Boolean): Float {
        val accept = 2 * eval.doubleTake
        val raccoon = 4 * eval.doubleTake
        return (maxOf(accept, raccoon) - if (raccooned) raccoon else accept).coerceAtLeast(0f)
    }

    /** XG's "non-obvious" test for a cube action by the player on roll. */
    fun isDoubleDecision(eval: CubeEval, doubled: Boolean): Boolean {
        if (!eval.cubeAvailable) return false
        if (doubled) return true
        val before = eval.noDouble
        val after = eval.afterDouble
        if (eval.optional || abs(before - after) <= XgRules.OPTIONAL_EPSILON) return false
        if (before - after >= XgRules.OBVIOUS_CUBE_MARGIN) return false
        if (before - eval.doublePass >= XgRules.OBVIOUS_CUBE_MARGIN) return false
        if (before <= XgRules.VERY_NEGATIVE && after <= XgRules.VERY_NEGATIVE) return false
        return true
    }
}

/**
 * Running totals for one player, from which PR and the other statistics are derived.
 * Errors are stored already multiplied by the 1-point-match factor.
 */
@Serializable
data class PerformanceTotals(
    val checkerDecisions: Int = 0,
    val checkerError: Double = 0.0,
    val checkerErrors: Int = 0,
    val checkerBlunders: Int = 0,
    val doubleDecisions: Int = 0,
    val doubleError: Double = 0.0,
    val doubleErrors: Int = 0,
    val doubleBlunders: Int = 0,
    val takeDecisions: Int = 0,
    val takeError: Double = 0.0,
    val takeErrors: Int = 0,
    val takeBlunders: Int = 0,
    /** All checker plays including forced ones (for gnubg/NJ style error rate). */
    val moves: Int = 0,
    val luck: Double = 0.0,
    val rolls: Int = 0,
) {
    val decisions: Int get() = checkerDecisions + doubleDecisions + takeDecisions
    /** Errors and blunders, checker and cube. */
    val mistakes: Int get() = checkerErrors + checkerBlunders + doubleErrors + doubleBlunders + takeErrors + takeBlunders
    val cubeDecisions: Int get() = doubleDecisions + takeDecisions
    val totalError: Double get() = checkerError + doubleError + takeError
    val cubeError: Double get() = doubleError + takeError

    /** XG Performance Rating; null when there were no decisions. */
    val pr: Double? get() = if (decisions == 0) null else XgRules.PR_SCALE * totalError / decisions
    val checkerPr: Double? get() = if (checkerDecisions == 0) null else XgRules.PR_SCALE * checkerError / checkerDecisions
    val cubePr: Double? get() = if (cubeDecisions == 0) null else XgRules.PR_SCALE * cubeError / cubeDecisions

    operator fun plus(o: PerformanceTotals) = PerformanceTotals(
        checkerDecisions + o.checkerDecisions, checkerError + o.checkerError,
        checkerErrors + o.checkerErrors, checkerBlunders + o.checkerBlunders,
        doubleDecisions + o.doubleDecisions, doubleError + o.doubleError,
        doubleErrors + o.doubleErrors, doubleBlunders + o.doubleBlunders,
        takeDecisions + o.takeDecisions, takeError + o.takeError,
        takeErrors + o.takeErrors, takeBlunders + o.takeBlunders,
        moves + o.moves, luck + o.luck, rolls + o.rolls,
    )

    /** Adds one decision. [weightedError] already includes the 1-point factor. */
    fun add(kind: DecisionKind, counted: Boolean, weightedError: Float): PerformanceTotals {
        val sev = XgRules.severity(weightedError)
        val e = if (sev.atLeast(Severity.ERROR) && sev != Severity.BLUNDER) 1 else 0
        val b = if (sev == Severity.BLUNDER) 1 else 0
        val err = weightedError.toDouble()
        return when (kind) {
            DecisionKind.CHECKER -> copy(
                moves = moves + 1,
                checkerDecisions = checkerDecisions + if (counted) 1 else 0,
                checkerError = checkerError + if (counted) err else 0.0,
                checkerErrors = checkerErrors + if (counted) e else 0,
                checkerBlunders = checkerBlunders + if (counted) b else 0,
            )
            DecisionKind.DOUBLE, DecisionKind.NO_DOUBLE, DecisionKind.RACCOON, DecisionKind.NO_RACCOON -> if (!counted) this else copy(
                doubleDecisions = doubleDecisions + 1,
                doubleError = doubleError + err,
                doubleErrors = doubleErrors + e,
                doubleBlunders = doubleBlunders + b,
            )
            // Never obvious (XG), but not counted when unanalyzed, hinted or autofinished.
            DecisionKind.TAKE, DecisionKind.PASS, DecisionKind.BEAVER -> if (!counted) this else copy(
                takeDecisions = takeDecisions + 1,
                takeError = takeError + err,
                takeErrors = takeErrors + e,
                takeBlunders = takeBlunders + b,
            )
        }
    }

    fun addRoll(luck: Float?): PerformanceTotals =
        copy(rolls = rolls + 1, luck = this.luck + (luck?.toDouble() ?: 0.0))
}
