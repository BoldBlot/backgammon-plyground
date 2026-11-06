package com.boldblot.backgammonplyground.core

/**
 * The ID tag of a saved position, "AB012": two letters (no O) and three digits, counted up with the
 * last symbol cycling first, like an odometer. Never-reused row numbers give the numbers, so a position
 * has its tag from the moment it is saved, and an old one has the one it always would have had.
 *
 * Beyond ZZ999 symbols are added at the front: first two digits, then two letters, then two digits again,
 * so the part before the last three digits always alternates two letters / two digits and cannot spell
 * anything unwelcome.
 */
object PositionTags {
    private const val LETTERS = "ABCDEFGHIJKLMNPQRSTUVWXYZ"

    /** The tag of row [id] (rows start at 1). */
    fun of(id: Long): String {
        var n = (id - 1).coerceAtLeast(0)
        val out = StringBuilder()
        var i = 0
        while (i < 5 || n > 0) {
            val letter = i >= 3 && ((i - 3) / 2) % 2 == 0
            val radix = if (letter) LETTERS.length else 10
            val v = (n % radix).toInt()
            out.append(if (letter) LETTERS[v] else '0' + v)
            n /= radix
            i++
        }
        return out.reverse().toString()
    }
}
