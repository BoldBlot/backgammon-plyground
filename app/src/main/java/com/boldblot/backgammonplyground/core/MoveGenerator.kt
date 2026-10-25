package com.boldblot.backgammonplyground.core

/**
 * Legal move generation following the standard rules (as implemented by GNU Backgammon):
 *  - checkers on the bar must enter first;
 *  - a player must use as many dice as possible;
 *  - if either die can be played but not both, the larger one must be played if possible;
 *  - bearing off is allowed once all checkers are home; a die larger than the
 *    highest occupied point may bear off from the highest point.
 */
object MoveGenerator {

    /** Single legal steps for one die value, ignoring the "use both dice" rule. */
    fun singleSteps(board: Board, player: Int, die: Int): List<Step> {
        val out = ArrayList<Step>(15)
        val opp = Player.other(player)
        fun open(to: Int) = to == OFF || board.count(opp, 23 - to) < 2

        if (board.bar(player) > 0) {
            val to = BAR - die
            if (open(to)) out += Step(BAR, to)
            return out
        }
        val allHome = board.allHome(player)
        val highest = board.highestPoint(player)
        for (from in 23 downTo 0) {
            if (board.count(player, from) == 0) continue
            val to = from - die
            if (to >= 0) {
                if (open(to)) out += Step(from, to)
            } else if (allHome) {
                // Exact bear-off, or a larger die from the highest occupied point.
                if (to == -1 || from == highest) out += Step(from, OFF)
            }
        }
        return out
    }

    /** A legal way to play the roll: the steps and the die value each step used. */
    data class Sequence(val steps: List<Step>, val dice: List<Int>)

    /** All legal step sequences for the roll (every order), each of maximal length. */
    fun sequences(board: Board, player: Int, d1: Int, d2: Int): List<Sequence> {
        val dieOrders: List<List<Int>> =
            if (d1 == d2) listOf(List(4) { d1 }) else listOf(listOf(d1, d2), listOf(d2, d1))
        val all = ArrayList<Sequence>()
        for (dice in dieOrders) {
            fun rec(b: Board, i: Int, steps: List<Step>) {
                val options = if (i < dice.size) singleSteps(b, player, dice[i]) else emptyList()
                if (options.isEmpty()) {
                    all += Sequence(steps, dice.subList(0, i))
                    return
                }
                for (s in options) rec(b.applyStep(player, s), i + 1, steps + s)
            }
            rec(board, 0, emptyList())
        }
        val maxLen = all.maxOf { it.steps.size }
        var legal = all.filter { it.steps.size == maxLen }
        if (d1 != d2 && maxLen == 1) {
            // Either die can be played but not both: the larger die must be used if possible.
            val big = maxOf(d1, d2)
            val withBig = legal.filter { it.dice[0] == big }
            if (withBig.isNotEmpty()) legal = withBig
        }
        return legal.distinctBy { it.steps }
    }

    /** Distinct complete moves, one per resulting position. */
    fun legalMoves(board: Board, player: Int, d1: Int, d2: Int): List<Move> {
        val seen = LinkedHashMap<Board, Move>()
        for (seq in sequences(board, player, d1, d2)) {
            val m = Move(seq.steps)
            seen.putIfAbsent(board.applyMove(player, m), m)
        }
        return seen.values.toList()
    }
}

/**
 * Tracks a move being entered step by step in the UI. All complete legal
 * sequences are precomputed (in every order); a partial input is valid while it
 * is a prefix of one of them.
 */
class MoveOptions(val board: Board, val player: Int, val d1: Int, val d2: Int) {

    val sequences: List<MoveGenerator.Sequence> = MoveGenerator.sequences(board, player, d1, d2)

    /** Number of steps in a complete move (0 when the player cannot move). */
    val stepsRequired: Int = sequences.first().steps.size

    val canMove: Boolean get() = stepsRequired > 0

    /** Distinct complete moves (by resulting position). */
    val distinctMoves: List<Move> by lazy { MoveGenerator.legalMoves(board, player, d1, d2) }

    /** True when there is exactly one way to play the roll (a forced move). */
    val isForced: Boolean get() = distinctMoves.size <= 1

    private fun matching(prefix: List<Step>): List<MoveGenerator.Sequence> =
        sequences.filter { it.steps.size >= prefix.size && it.steps.subList(0, prefix.size) == prefix }

    /** Legal next steps after [prefix]. */
    fun nextSteps(prefix: List<Step>): Set<Step> =
        matching(prefix).mapNotNullTo(LinkedHashSet()) { it.steps.getOrNull(prefix.size) }

    /** Points (or [BAR]) whose checkers can move next. */
    fun movableFrom(prefix: List<Step>): Set<Int> = nextSteps(prefix).mapTo(LinkedHashSet()) { it.from }

    /**
     * Destinations reachable by moving the checker on [from] one or more steps,
     * each mapped to the chain of steps that gets there (shortest chain first).
     */
    fun destinations(prefix: List<Step>, from: Int): Map<Int, List<Step>> {
        val result = LinkedHashMap<Int, List<Step>>()
        fun rec(acc: List<Step>, at: Int) {
            for (s in nextSteps(prefix + acc)) {
                if (s.from != at) continue
                val chain = acc + s
                val existing = result[s.to]
                if (existing == null || existing.size > chain.size) result[s.to] = chain
                if (s.to != OFF) rec(chain, s.to)
            }
        }
        rec(emptyList(), from)
        return result
    }

    fun isComplete(steps: List<Step>): Boolean = steps.size == stepsRequired && matching(steps).isNotEmpty()

    /** The step moving the checker on [from] by exactly [die] after [prefix], if that is legal. */
    fun stepWithDie(prefix: List<Step>, from: Int, die: Int): Step? = matching(prefix).firstOrNull {
        it.steps.getOrNull(prefix.size)?.from == from && it.dice.getOrNull(prefix.size) == die
    }?.steps?.get(prefix.size)

    /** Die values used so far by [prefix], in order. */
    fun usedDice(prefix: List<Step>): List<Int> = matching(prefix).firstOrNull()?.dice?.take(prefix.size) ?: emptyList()

    /**
     * One-tap move: the checker on [from] moves by the first die in [order] that is still
     * unused and gives a legal step; otherwise by the other die. Null if neither works.
     */
    fun tapStep(prefix: List<Step>, from: Int, order: List<Int>): Step? {
        val remaining = remainingDice(prefix)
        for (die in order.distinct()) {
            if (die !in remaining) continue
            stepWithDie(prefix, from, die)?.let { return it }
        }
        return null
    }

    /** Dice not yet used after [prefix], for display (unplayable dice included). */
    fun remainingDice(prefix: List<Step>): List<Int> {
        val all = if (d1 == d2) List(4) { d1 } else listOf(maxOf(d1, d2), minOf(d1, d2))
        val seq = matching(prefix).firstOrNull() ?: return all
        val left = all.toMutableList()
        for (d in seq.dice.take(prefix.size)) left.remove(d)
        return left
    }
}
