package com.boldblot.backgammonplyground.core

/**
 * The speed test's search for the strongest level that fits a time budget, without timing every level: start at the level
 * predicted to fit ([start]), then one step at a time, up while the levels fit, down while they do not. [fits] times level i
 * (true: within the budget; null: the test was stopped).
 *
 * Returns the index of the strongest level that fits (the weakest, 0, when none does), or null when stopped.
 */
suspend fun climbLadder(size: Int, start: Int, fits: suspend (Int) -> Boolean?): Int? {
    require(size > 0)
    val i = start.coerceIn(0, size - 1)
    val first = fits(i) ?: return null
    if (first) {
        var best = i
        while (best + 1 < size) {
            val next = fits(best + 1) ?: return null
            if (!next) break
            best++
        }
        return best
    }
    var j = i - 1
    while (j >= 0) {
        if (fits(j) ?: return null) return j
        j--
    }
    return 0
}

/**
 * Where [climbLadder] starts: the strongest level whose predicted time ([baseMs] x [ratios], the levels' rough cost against
 * 1-ply measured on a PC) is within [budgetMs]; 0 when none is.
 */
fun ladderStart(baseMs: Double, ratios: List<Double>, budgetMs: Int): Int =
    ratios.indexOfLast { baseMs * it <= budgetMs }.coerceAtLeast(0)
