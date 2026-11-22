package com.boldblot.backgammonplyground.core

/**
 * Deep dive: one position analyzed in versions that differ in one thing (where some checkers stand, the score, the
 * cube), following one decision (the tracked play or cube action) through them. The player who decides is at the
 * bottom ([Player.HUMAN]), as in every [StudyPosition].
 */
object DeepDive {
    /** The most versions one deep dive analyzes. */
    const val MAX_VERSIONS = 60

    /** Below this a loss counts as none: the tracked decision is (equal) best. */
    const val TIE = 0.0005f

    /** The decision followed through the versions. */
    sealed interface Tracked {
        /** A play of the bottom player (their numbering). */
        data class Play(val move: Move) : Tracked

        /** A cube action: [DecisionKind.DOUBLE], [DecisionKind.NO_DOUBLE], [DecisionKind.TAKE] or [DecisionKind.PASS]. */
        data class Cube(val kind: DecisionKind) : Tracked
    }

    /** One checker that moves: [player]'s checker on [from] (that player's numbering, 0..23) tried on each of [targets]. */
    data class Slot(val player: Int, val from: Int, val targets: List<Int>)

    /** What changes between the versions. */
    sealed interface Change {
        data class Checkers(val slots: List<Slot>) : Change

        /** [player]'s score, from the match length away down to 1-away (Crawford and post-Crawford), then money. */
        data class Score(val player: Int) : Change

        /** The cube: centred, or owned by either player at 2, 4 and 8 (where the decision exists). */
        data object Cube : Change
    }

    /**
     * One version: [position], its [label] for lists, its [short] label for the chart's axis, and for checker changes
     * where each slot's checker stands ([spots], bottom player's numbering, one per slot).
     */
    data class Version(val position: StudyPosition, val label: String, val short: String, val isBase: Boolean, val spots: List<Int> = emptyList())

    /** A point in the bottom player's numbering, 1..24, for [player]'s index [i]. */
    fun shown(player: Int, i: Int): Int = (if (player == Player.HUMAN) i else 23 - i) + 1

    /** Moves one of [player]'s checkers from [from] to [to] (their numbering), or null where it cannot stand there. */
    fun moveChecker(board: Board, player: Int, from: Int, to: Int): Board? {
        if (from == to) return board
        if (from !in 0..23 || to !in 0..23 || board.count(player, from) == 0) return null
        val opp = Player.other(player)
        if (board.count(opp, 23 - to) > 0) return null
        val human = IntArray(25) { board.count(Player.HUMAN, it) }
        val cpu = IntArray(25) { board.count(Player.CPU, it) }
        val side = if (player == Player.HUMAN) human else cpu
        side[from]--
        side[to]++
        return Board.fromSides(human, cpu)
    }

    /** The points [player]'s checker on [from] may be tried on: every point without the opponent's checkers. */
    fun openPoints(board: Board, player: Int): List<Int> = (23 downTo 0).filter { board.count(Player.other(player), 23 - it) == 0 }

    /** Whether [change] can be made in [base] at all, or why not. */
    fun problem(base: StudyPosition, change: Change): String? = when (change) {
        is Change.Checkers -> when {
            change.slots.isEmpty() -> "Tap a checker on the board."
            change.slots.any { it.targets.size < 2 } -> "Tap the points the checker may stand on."
            else -> null
        }
        is Change.Score -> if (base.setup.matchLength == 0) "A money game has no score. Set a match length in Analyze Position." else null
        Change.Cube -> if (base.setup.matchLength > 0 && base.setup.crawford) "No cube in the Crawford game." else null
    }

    /** The versions of [base] that [change] makes, in the chart's order (the base one included). */
    fun versions(base: StudyPosition, change: Change): List<Version> = when (change) {
        is Change.Checkers -> checkerVersions(base, change.slots)
        is Change.Score -> scoreVersions(base, change.player)
        Change.Cube -> cubeVersions(base)
    }.take(MAX_VERSIONS)

    private fun checkerVersions(base: StudyPosition, slots: List<Slot>): List<Version> {
        if (slots.isEmpty()) return emptyList()
        // One slot: back to front for its player; several: every combination.
        val ordered = slots.map { s -> s.copy(targets = s.targets.distinct().sortedDescending()) }
        var combos: List<List<Int>> = listOf(emptyList())
        for (s in ordered) combos = combos.flatMap { c -> s.targets.map { c + it } }
        val letters = "ABCDEF"
        return combos.mapNotNull { targets ->
            var b: Board? = base.board
            ordered.forEachIndexed { i, s -> b = b?.let { moveChecker(it, s.player, s.from, targets[i]) } }
            val board = b ?: return@mapNotNull null
            val names = ordered.mapIndexed { i, s -> shown(s.player, targets[i]) }
            val label = if (ordered.size == 1) "${if (ordered[0].player == Player.HUMAN) "Your" else "Opponent's"} checker on ${names[0]}"
            else names.mapIndexed { i, n -> "${letters[i]} $n" }.joinToString(" · ")
            Version(
                position = base.copy(setup = base.setup.copy(board = board)), label = label,
                short = if (ordered.size == 1) "${names[0]}" else label, isBase = targets == ordered.map { it.from },
                spots = names.map { it - 1 },
            )
        }
    }

    private fun scoreVersions(base: StudyPosition, player: Int): List<Version> {
        val s = base.setup
        val n = s.matchLength
        if (n == 0) return emptyList()
        val other = Player.other(player)
        val otherAway = n - s.score[other]
        val out = ArrayList<Version>()
        fun add(away: Int, crawford: Boolean, short: String) {
            val score = s.score.toMutableList().also { it[player] = n - away }
            val setup = s.copy(score = score, crawford = crawford, cube = if (crawford) CubeState() else s.cube)
            val aways = listOf(n - score[Player.HUMAN], n - score[Player.CPU])
            val tag = when {
                aways.min() != 1 || aways.max() == 1 -> ""
                crawford -> ", Crawford"
                else -> ", post-Crawford"
            }
            out += Version(
                base.copy(setup = setup), "You ${aways[0]}-away, opponent ${aways[1]}-away$tag", short,
                isBase = score == s.score && crawford == s.crawford,
            )
        }
        for (away in n downTo 1) {
            when {
                away > 1 -> add(away, crawford = s.crawford && otherAway == 1, "${away}a")
                otherAway == 1 -> add(1, crawford = false, "1a")
                else -> {
                    add(1, crawford = true, "1a C")
                    add(1, crawford = false, "1a PC")
                }
            }
        }
        out += Version(
            base.copy(setup = s.copy(matchLength = 0, score = listOf(0, 0), crawford = false)), "Money game", "$", isBase = false,
        )
        return out
    }

    private fun cubeVersions(base: StudyPosition): List<Version> {
        val s = base.setup
        if (s.matchLength > 0 && s.crawford) return emptyList()
        // Who may own the cube: for a take, centred or the doubler's; for a double, centred or the bottom player's.
        val owners = when {
            base.doubled -> listOf(Player.CPU)
            s.dice == null -> listOf(Player.HUMAN)
            else -> listOf(Player.HUMAN, Player.CPU)
        }
        val states = listOf(CubeState()) + owners.flatMap { o -> listOf(2, 4, 8).map { CubeState(it, o) } }
        return states.map { c ->
            val (label, short) = when (c.owner) {
                -1 -> "Cube centred" to "C"
                Player.HUMAN -> "You own the cube at ${c.value}" to "Y${c.value}"
                else -> "Opponent owns the cube at ${c.value}" to "O${c.value}"
            }
            Version(base.copy(setup = s.copy(cube = c)), label, short, isBase = c == s.cube || (c.value == 1 && s.cube.value <= 1))
        }
    }

    /** Whether [t] can be decided in [p] at all (a play that is legal there, a cube action that exists there). */
    fun isLegal(p: StudyPosition, t: Tracked): Boolean = when (t) {
        is Tracked.Play -> {
            val d = p.dice
            if (d == null) false else {
                val legal = MoveGenerator.legalMoves(p.board, Player.HUMAN, d.d1, d.d2)
                if (t.move.isEmpty) legal.all { it.isEmpty }
                else {
                    val target = runCatching { p.board.applyMove(Player.HUMAN, t.move) }.getOrNull()
                    target != null && legal.any { runCatching { p.board.applyMove(Player.HUMAN, it) }.getOrNull() == target }
                }
            }
        }
        is Tracked.Cube -> {
            val s = p.setup
            val crawford = s.matchLength > 0 && s.crawford
            when (t.kind) {
                DecisionKind.DOUBLE, DecisionKind.NO_DOUBLE -> !p.doubled && s.dice == null && !crawford && s.cube.canBeTurnedBy(Player.HUMAN)
                DecisionKind.TAKE, DecisionKind.PASS, DecisionKind.BEAVER -> p.doubled && !crawford
                else -> false
            }
        }
    }

    /** The loss of cube action [kind] in [eval] ([beavers] allowed), or null where the cube cannot be turned. */
    fun cubeLoss(eval: CubeEval, kind: DecisionKind, beavers: Boolean): Float? = when (kind) {
        DecisionKind.DOUBLE, DecisionKind.NO_DOUBLE ->
            if (!eval.cubeAvailable) null else DecisionMath.doubleError(eval, doubled = kind == DecisionKind.DOUBLE, beavers = beavers)
        else -> DecisionMath.takeError(eval, kind, beavers)
    }
}
