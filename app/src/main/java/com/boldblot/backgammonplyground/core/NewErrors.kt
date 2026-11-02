package com.boldblot.backgammonplyground.core

import kotlin.math.max

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
