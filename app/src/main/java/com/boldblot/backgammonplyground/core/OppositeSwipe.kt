package com.boldblot.backgammonplyground.core

/** Analyze Position's "Opposite swipe": a swipe on the board acts on the point across it, in the reverse direction. */
object OppositeSwipe {
    /**
     * The point (human orientation, 0..23) and the change of checkers a swipe of [delta] on [point] makes. With
     * [opposite] it is the point across the board (23 - [point]), and the change is the reverse: a swipe that would
     * have added checkers removes them there.
     */
    fun of(point: Int, delta: Int, opposite: Boolean): Pair<Int, Int> =
        if (opposite) (23 - point) to -delta else point to delta
}
