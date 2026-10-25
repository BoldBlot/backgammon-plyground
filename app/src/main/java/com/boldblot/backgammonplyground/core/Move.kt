package com.boldblot.backgammonplyground.core

import kotlinx.serialization.Serializable

/** One checker movement, in the mover's orientation: from 0..23 or [BAR], to 0..23 or [OFF]. */
@Serializable
data class Step(val from: Int, val to: Int) {
    init {
        require(from in 0..24) { "bad from $from" }
        require(to in -1..23) { "bad to $to" }
        require(to < from) { "a step must move forward: $from -> $to" }
    }

    /** Distance moved (bearing off from the 6-point counts as 6, since OFF = -1). */
    val pips: Int get() = from - to
}

/** A complete (or partial) checker play for one turn. */
@Serializable
data class Move(val steps: List<Step>) {

    val isEmpty: Boolean get() = steps.isEmpty()

    /** Engine representation: 8 ints (from, to) x 4, unused = -1. */
    fun toEngine(): IntArray {
        val out = IntArray(8) { -1 }
        steps.forEachIndexed { i, s ->
            out[2 * i] = s.from
            out[2 * i + 1] = s.to
        }
        return out
    }

    companion object {
        val NONE = Move(emptyList())

        fun fromEngine(arr: IntArray, offset: Int = 0): Move {
            val steps = ArrayList<Step>(4)
            for (i in 0 until 4) {
                val from = arr[offset + 2 * i]
                if (from < 0) break
                steps += Step(from, arr[offset + 2 * i + 1])
            }
            return Move(steps)
        }
    }
}

/**
 * Formats moves in GNU Backgammon / XG style notation, e.g. "13/7 8/7",
 * "8/5(2) 6/5(2)", "bar/22", "6/off"; hits are marked with an asterisk after the
 * point. Points are shown from the mover's view.
 */
object MoveNotation {

    private fun pointName(index: Int): String = when (index) {
        BAR -> "bar"
        OFF -> "off"
        else -> (index + 1).toString()
    }

    /** [board] is the position before the move, used to mark hits with '*'. */
    fun format(board: Board, player: Int, move: Move): String {
        if (move.isEmpty) return "Cannot move"

        // Replay the move to find which steps hit.
        data class S(val from: Int, val to: Int, val hit: Boolean)
        var b = board
        val steps = move.steps.map { s ->
            val hit = b.isHit(player, s)
            b = b.applyStep(player, s)
            S(s.from, s.to, hit)
        }

        // Chain consecutive steps of the same checker (to == next from) when the
        // intermediate point is not a hit; hits on the way are still printed.
        val remaining = steps.sortedWith(compareByDescending<S> { it.from }.thenByDescending { it.to }).toMutableList()
        val chains = mutableListOf<List<S>>()
        while (remaining.isNotEmpty()) {
            val first = remaining.removeAt(0)
            val chain = mutableListOf(first)
            while (true) {
                val last = chain.last()
                if (last.to == OFF) break
                val idx = remaining.indexOfFirst { it.from == last.to }
                if (idx < 0) break
                chain += remaining.removeAt(idx)
            }
            chains += chain
        }

        val parts = chains.map { chain ->
            buildString {
                append(pointName(chain.first().from))
                // Intermediate points are only shown when a hit happens there.
                chain.forEachIndexed { i, s ->
                    if (s.hit || i == chain.lastIndex) {
                        append('/')
                        append(pointName(s.to))
                        if (s.hit) append('*')
                    }
                }
            }
        }

        // Group identical parts: "8/5(2)"
        val grouped = LinkedHashMap<String, Int>()
        for (p in parts) grouped[p] = (grouped[p] ?: 0) + 1
        return grouped.entries.joinToString(" ") { (p, n) -> if (n > 1) "$p($n)" else p }
    }
}
