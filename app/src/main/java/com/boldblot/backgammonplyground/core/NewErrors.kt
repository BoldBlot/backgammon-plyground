package com.boldblot.backgammonplyground.core

import kotlin.math.max

/** What a position's error counts as in My positions' average error (Settings → My positions). */
enum class AverageBasis(val label: String) {
    ORIGINAL("Original error"), ORIGINAL_AND_LAST("Original error and last answer"), LAST("Last answer only");

    /**
     * The error of one position under this basis: its [original] (the game play's) and the [last] answer's, by the
     * analysis it has now. Original only is as it was before build 86; the others fall back to the one that is
     * known, and a position with neither is left out (null).
     */
    fun of(original: Float?, last: Float?): Float? = when (this) {
        ORIGINAL -> original
        ORIGINAL_AND_LAST -> if (original != null && last != null) (original + last) / 2f else original ?: last
        LAST -> last ?: original
    }
}

/**
 * Which new errors go straight to My positions (Settings → My positions → New errors). Errors are weighted
 * (after the 1-point-match factor), as PR counts them.
 */
object NewErrors {
    /** The slider's range: from the error threshold to enormous blunders only. */
    const val MIN_ERROR = XgRules.ERROR_THRESHOLD
    const val MAX_ERROR = 0.5f

    /** Adaptive: errors above [ratio] of the library's [average] error, never below the slider's [min]. */
    fun adaptiveThreshold(min: Float, ratio: Float, average: Float?): Float = max(min, (average ?: 0f) * ratio)

    /** The library's average error: the mean of [errors] that are errors at all (more than 0.0005). */
    fun average(errors: List<Float>): Float? = errors.filter { it > 0.0005f }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

    /**
     * Top X per match: of a match's waiting errors ([errors], id to weighted error) the biggest of at least
     * [min], so that [x] are filed in all with the [already] filed before. Returns the ids to file.
     */
    fun topX(errors: List<Pair<Long, Float>>, x: Int, min: Float, already: Int = 0): List<Long> =
        errors.filter { it.second >= min }.sortedByDescending { it.second }.take((x - already).coerceAtLeast(0)).map { it.first }

    /** The rolling New errors of the automatic methods: of [ids] (newest first) the ones beyond [keep] go. */
    fun overflow(ids: List<Long>, keep: Int): List<Long> = ids.drop(keep.coerceAtLeast(0))
}
