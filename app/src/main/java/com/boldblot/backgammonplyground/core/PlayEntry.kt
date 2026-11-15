package com.boldblot.backgammonplyground.core

/**
 * Entering the play of [player] (either side) with dice [d1], [d2] on [board], by taps and drags as in a game.
 * Targets are in human orientation (as the board reports them: 0..23, [BAR], [OFF]); steps are in [player]'s own.
 */
class PlayEntry(val board: Board, val player: Int, val d1: Int, val d2: Int) {
    val options = MoveOptions(board, player, d1, d2)

    /** A board target (human orientation) in [player]'s own numbering, and back (the same mapping). */
    fun own(target: Int): Int = if (player == Player.HUMAN || target == BAR || target == OFF) target else 23 - target

    /** The dice, high first: the order a tap tries them in. */
    val order: List<Int> = listOf(maxOf(d1, d2), minOf(d1, d2))

    fun boardAfter(steps: List<Step>): Board = steps.fold(board) { b, s -> b.applyStep(player, s) }

    fun isComplete(steps: List<Step>): Boolean = options.isComplete(steps)

    /** Points (human orientation, or [BAR]) whose checkers can move next. */
    fun movable(steps: List<Step>): Set<Int> =
        if (isComplete(steps)) emptySet() else options.movableFrom(steps).mapTo(LinkedHashSet()) { own(it) }

    /**
     * A tap on [target] (human orientation) after [steps]: the checker there moves by the higher die, or the other
     * if that one cannot. Returns the steps after it, or null when nothing moves. An empty point or an opponent's
     * blot asks for the plays that make it: see [pointPlays].
     */
    fun tap(steps: List<Step>, target: Int): List<Step>? {
        if (isComplete(steps) || target == OFF) return null
        val from = own(target)
        val ownChecker = if (from == BAR) boardAfter(steps).bar(player) > 0 else boardAfter(steps).count(player, from) > 0
        if (!ownChecker) return null
        return options.tapStep(steps, from, order)?.let { steps + it }
    }

    /** Plays that make point [target] (human orientation) on top of [steps]; the caller picks one when there are several. */
    fun pointPlays(steps: List<Step>, target: Int): List<TapPlays.PointPlay> {
        if (isComplete(steps) || target == OFF || target == BAR) return emptyList()
        val point = own(target)
        if (boardAfter(steps).count(player, point) > 0) return emptyList()
        return TapPlays.makePoint(board, player, options.distinctMoves, point, steps)
    }

    /** Whether a tap on [target] means "make this point" (no checker of [player] there). */
    fun isPointTap(steps: List<Step>, target: Int): Boolean =
        target != BAR && target != OFF && boardAfter(steps).count(player, own(target)) == 0

    /** A drag from [from] to [to] (human orientation): the steps after it, or null when it is not legal. */
    fun drag(steps: List<Step>, from: Int, to: Int): List<Step>? {
        if (isComplete(steps)) return null
        return options.destinations(steps, own(from))[own(to)]?.let { steps + it }
    }

    /** The play [steps] make once complete ([Move.NONE] when the roll cannot be played). */
    fun move(steps: List<Step>): Move? = when {
        !options.canMove -> Move.NONE
        isComplete(steps) -> Move(steps)
        else -> null
    }
}

/** The decision entered in Analyze Position for a cube decision. */
enum class CubeChoice(val label: String) {
    /** The player on roll's whole decision: theirs and the answer they expect. */
    NO_DOUBLE("No double"), DOUBLE_TAKE("Double, take"), DOUBLE_PASS("Double, pass"),
    /** With a double offered: the doubled player's answer. */
    TAKE("Take"), PASS("Pass");

    val isResponse: Boolean get() = this == TAKE || this == PASS

    /** The decision kind of the player who makes it (DOUBLE for both double choices). */
    val kind: DecisionKind get() = when (this) {
        NO_DOUBLE -> DecisionKind.NO_DOUBLE
        DOUBLE_TAKE, DOUBLE_PASS -> DecisionKind.DOUBLE
        TAKE -> DecisionKind.TAKE
        PASS -> DecisionKind.PASS
    }

    /** For a double: the answer it expects (TAKE or PASS). */
    val answer: DecisionKind? get() = when (this) {
        DOUBLE_TAKE -> DecisionKind.TAKE
        DOUBLE_PASS -> DecisionKind.PASS
        else -> null
    }

    /**
     * The error of this choice in [eval] (normalized equity, >= 0). A whole cube decision adds the doubler's error
     * and, after a double, the error of the answer expected (Double, take when it is a pass costs the taker's error).
     */
    fun error(eval: CubeEval): Float = when (this) {
        TAKE, PASS -> DecisionMath.takeError(eval, kind)
        NO_DOUBLE -> DecisionMath.doubleError(eval, doubled = false)
        DOUBLE_TAKE, DOUBLE_PASS -> DecisionMath.doubleError(eval, doubled = true) + DecisionMath.takeError(eval, answer!!)
    }
}
