package com.boldblot.backgammonplyground.core

/**
 * Deep dive: one position analyzed in versions that differ in one or two things (where some checkers stand, the
 * score, the cube), following one or more decisions (tracked plays or cube actions) through them. The player who
 * decides is at the bottom ([Player.HUMAN]), as in every [StudyPosition].
 */
object DeepDive {
    /** The most versions one deep dive analyzes. */
    const val MAX_VERSIONS = 100

    /** The most decisions followed at once. */
    const val MAX_TRACKED = 4

    /** Below this a loss counts as none: the tracked decision is (equal) best. */
    const val TIE = 0.0005f

    /** A decision followed through the versions. */
    sealed interface Tracked {
        /** A play of the bottom player (their numbering). */
        data class Play(val move: Move) : Tracked

        /** A cube action: [DecisionKind.DOUBLE], [DecisionKind.NO_DOUBLE], [DecisionKind.TAKE] or [DecisionKind.PASS]. */
        data class Cube(val kind: DecisionKind) : Tracked
    }

    /**
     * Checkers that move: [count] of [player]'s checkers on [from] (that player's numbering, 0..23), tried together on
     * each of [targets].
     */
    data class Slot(val player: Int, val from: Int, val targets: List<Int>, val count: Int = 1)

    /** What changes between the versions. */
    sealed interface Change {
        data class Checkers(val slots: List<Slot>) : Change

        /**
         * [player]'s score, from [from]-away (0: the match length) down to [to]-away, 1-away as Crawford and post-Crawford,
         * then [money] (a money game; not with two score changes).
         */
        data class Score(val player: Int, val from: Int = 0, val to: Int = 1, val money: Boolean = true) : Change

        /** The cube: centred, or owned by either player at 2, 4 and 8 (where the decision exists). */
        data object Cube : Change
    }

    /** One value of one change: its labels, where its checkers stand ([spots], bottom numbering) and how it is made. */
    class Step(
        val label: String,
        val short: String,
        val isBase: Boolean,
        val spots: List<Int>,
        /** What the version must be once every change is made (a Crawford step: still the Crawford game). */
        val check: (StudyPosition) -> Boolean = { true },
        val apply: (StudyPosition) -> StudyPosition?,
    )

    /**
     * One version: [position], its [label] for lists, its [short] label for the chart's axis, where each slot's
     * checkers stand ([spots], bottom numbering, the slots of every checker change in order), and its step index
     * along each change ([coords]).
     */
    data class Version(
        val position: StudyPosition,
        val label: String,
        val short: String,
        val isBase: Boolean,
        val spots: List<Int> = emptyList(),
        val coords: List<Int> = emptyList(),
    )

    /** A point in the bottom player's numbering, 1..24, for [player]'s index [i]. */
    fun shown(player: Int, i: Int): Int = (if (player == Player.HUMAN) i else 23 - i) + 1

    /** Moves [n] of [player]'s checkers from [from] to [to] (their numbering), or null where they cannot stand there. */
    fun moveChecker(board: Board, player: Int, from: Int, to: Int, n: Int = 1): Board? {
        if (from !in 0..23 || to !in 0..23 || board.count(player, from) < n) return null
        if (from == to) return board
        val opp = Player.other(player)
        if (board.count(opp, 23 - to) > 0) return null
        val human = IntArray(25) { board.count(Player.HUMAN, it) }
        val cpu = IntArray(25) { board.count(Player.CPU, it) }
        val side = if (player == Player.HUMAN) human else cpu
        side[from] -= n
        side[to] += n
        return Board.fromSides(human, cpu)
    }

    /** The points [player]'s checkers may be tried on: every point without the opponent's checkers. */
    fun openPoints(board: Board, player: Int): List<Int> = (23 downTo 0).filter { board.count(Player.other(player), 23 - it) == 0 }

    /** Whether [change] can be made in [base] at all, or why not. */
    fun problem(base: StudyPosition, change: Change): String? = when (change) {
        is Change.Checkers -> when {
            change.slots.isEmpty() -> "Tap a checker on the board."
            change.slots.any { it.targets.size < 2 } -> "Tap the points the checkers may stand on."
            else -> null
        }
        is Change.Score -> if (base.setup.matchLength == 0) "A money game has no score. Set a match length in Analyze Position." else null
        Change.Cube -> if (base.setup.matchLength > 0 && base.setup.crawford) "No cube in the Crawford game." else null
    }

    /** The steps of [change] in [base], in the chart's order (the base one included). */
    fun steps(base: StudyPosition, change: Change): List<Step> = when (change) {
        is Change.Checkers -> checkerSteps(base, change.slots)
        is Change.Score -> scoreSteps(base, change)
        Change.Cube -> cubeSteps(base)
    }

    /** The versions of [base] that [change] makes, in the chart's order (the base one included). */
    fun versions(base: StudyPosition, change: Change): List<Version> = versions(base, listOf(change))

    /**
     * The versions of [base] that [changes] (one or two) make: every combination of their steps that exists, the first
     * change's steps innermost. Applied checkers first, then the score, then the cube (the Crawford game takes a
     * centred cube).
     */
    fun versions(base: StudyPosition, changes: List<Change>): List<Version> = plan(base, changes).first

    /**
     * The versions of [changes] and each change's axis labels, keeping only the steps some version has ([Version.coords]
     * index these axes).
     */
    fun plan(base: StudyPosition, changes: List<Change>): Pair<List<Version>, List<List<String>>> {
        val (all, axes) = raw(base, changes)
        val used = axes.indices.map { d -> all.map { it.coords[d] }.toSortedSet().toList() }
        val versions = all.map { v -> v.copy(coords = v.coords.mapIndexed { d, i -> used[d].indexOf(i) }) }
        return versions to axes.indices.map { d -> used[d].map { axes[d][it].short } }
    }

    private fun raw(base: StudyPosition, changes: List<Change>): Pair<List<Version>, List<List<Step>>> {
        // Two scores: no money game (it has no score to cross with the other).
        val two = changes.count { it is Change.Score } == 2
        val made = changes.map { if (two && it is Change.Score) it.copy(money = false) else it }
        val axes = made.map { steps(base, it) }
        if (axes.isEmpty() || axes.any { it.isEmpty() }) return emptyList<Version>() to axes
        fun rank(c: Change) = when (c) { is Change.Checkers -> 0; is Change.Score -> 1; Change.Cube -> 2 }
        val order = made.indices.sortedBy { rank(made[it]) }
        var combos: List<List<Int>> = listOf(emptyList())
        for (a in axes.indices.reversed()) combos = combos.flatMap { c -> axes[a].indices.map { listOf(it) + c } }
        // combos: the last change outermost, the first innermost.
        val out = ArrayList<Version>()
        for (c in combos) {
            var p: StudyPosition? = base
            for (d in order) p = p?.let { axes[d][c[d]].apply(it) }?.let(::settled)
            val steps = c.mapIndexed { d, i -> axes[d][i] }
            val pos = p?.takeIf { v -> steps.all { it.check(v) } } ?: continue
            val checkerSteps = changes.indices.filter { changes[it] is Change.Checkers }.map { steps[it] }
            out += Version(
                position = pos,
                // The score as it ends up (two score changes make one score), the other changes' own labels.
                label = (made.indices.filter { made[it] !is Change.Score }.map { steps[it].label } +
                    listOfNotNull(scoreLabel(pos).takeIf { made.any { it is Change.Score } })).joinToString(" · "),
                short = steps.joinToString(" · ") { it.short },
                isBase = steps.all { it.isBase },
                spots = checkerSteps.flatMap { it.spots },
                coords = c,
            )
            if (out.size >= MAX_VERSIONS) break
        }
        return out to axes
    }

    /** How many versions [changes] would make before the cap and before dropping combinations that cannot be. */
    fun planned(base: StudyPosition, changes: List<Change>): Int {
        val two = changes.count { it is Change.Score } == 2
        return changes.fold(1) { n, c -> n * steps(base, if (two && c is Change.Score) c.copy(money = false) else c).size }
    }

    /** "You 1-away, opponent 2-away, Crawford", or "Money game". */
    fun scoreLabel(p: StudyPosition): String {
        val s = p.setup
        if (s.matchLength == 0) return "Money game"
        val a = s.matchLength - s.score[Player.HUMAN]
        val b = s.matchLength - s.score[Player.CPU]
        val tag = when {
            minOf(a, b) != 1 || maxOf(a, b) == 1 -> ""
            s.crawford -> ", Crawford"
            else -> ", post-Crawford"
        }
        return "You $a-away, opponent $b-away$tag"
    }

    /** The Crawford game only while exactly one player is 1-away; its cube centred. */
    private fun settled(p: StudyPosition): StudyPosition {
        val s = p.setup
        if (s.matchLength == 0 || !s.crawford) return p
        val ones = s.score.count { s.matchLength - it == 1 }
        return if (ones != 1) p.copy(setup = s.copy(crawford = false)) else if (!s.cube.centred) p.copy(setup = s.copy(cube = CubeState())) else p
    }

    private fun checkerSteps(base: StudyPosition, slots: List<Slot>): List<Step> {
        if (slots.isEmpty()) return emptyList()
        // One slot: back to front for its player; several: every combination.
        val ordered = slots.map { s -> s.copy(targets = s.targets.distinct().sortedDescending()) }
        var combos: List<List<Int>> = listOf(emptyList())
        for (s in ordered) combos = combos.flatMap { c -> s.targets.map { c + it } }
        val letters = "ABCDEF"
        return combos.map { targets ->
            val names = ordered.mapIndexed { i, s -> shown(s.player, targets[i]) }
            val one = ordered.singleOrNull()
            val label = if (one != null) {
                val who = if (one.player == Player.HUMAN) "Your" else "Opponent's"
                if (one.count == 1) "$who checker on ${names[0]}" else "$who ${one.count} checkers on ${names[0]}"
            } else names.mapIndexed { i, n -> "${letters[i]} $n" }.joinToString(" · ")
            Step(
                label = label, short = if (one != null) "${names[0]}" else label, isBase = targets == ordered.map { it.from },
                spots = names.map { it - 1 },
            ) { p ->
                var b: Board? = p.board
                ordered.forEachIndexed { i, s -> b = b?.let { moveChecker(it, s.player, s.from, targets[i], s.count) } }
                b?.let { p.copy(setup = p.setup.copy(board = it)) }
            }
        }.filter { it.apply(base) != null } // combinations that cannot be (a point the other side holds) are left out
    }

    private fun scoreSteps(base: StudyPosition, change: Change.Score): List<Step> {
        val s = base.setup
        val n = s.matchLength
        if (n == 0) return emptyList()
        val player = change.player
        val other = Player.other(player)
        fun away(p: StudyPosition, who: Int) = p.setup.matchLength - p.setup.score[who]
        val out = ArrayList<Step>()
        fun add(away: Int, crawford: Boolean?, short: String) {
            val score = s.score.toMutableList().also { it[player] = n - away }
            val aways = listOf(n - score[Player.HUMAN], n - score[Player.CPU])
            val tag = when {
                aways.min() != 1 || aways.max() == 1 -> ""
                crawford == true || (crawford == null && s.crawford) -> ", Crawford"
                else -> ", post-Crawford"
            }
            val isBase = score == s.score && (crawford == null || crawford == s.crawford)
            val check: (StudyPosition) -> Boolean = when (crawford) {
                // 1-away in the Crawford game: the other player must not be 1-away too.
                true -> { v -> v.setup.crawford && away(v, other) > 1 }
                false -> { v -> !v.setup.crawford }
                null -> { _ -> true }
            }
            out += Step("You ${aways[0]}-away, opponent ${aways[1]}-away$tag", short, isBase, emptyList(), check) { p ->
                if (p.setup.matchLength == 0) return@Step null
                val sc = p.setup.score.toMutableList().also { it[player] = n - away }
                p.copy(setup = p.setup.copy(score = sc, crawford = crawford ?: p.setup.crawford))
            }
        }
        val from = (if (change.from <= 0) n else change.from).coerceIn(1, n)
        val to = change.to.coerceIn(1, from)
        for (away in from downTo to) {
            if (away > 1) add(away, null, "${away}a")
            else {
                add(1, true, "1a C")
                add(1, false, "1a PC")
            }
        }
        if (change.money) out += Step("Money game", "$", false, emptyList()) { p -> p.copy(setup = p.setup.copy(matchLength = 0, score = listOf(0, 0), crawford = false)) }
        return out
    }

    private fun cubeSteps(base: StudyPosition): List<Step> {
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
            Step(label, short, c == s.cube || (c.centred && s.cube.centred), emptyList()) { p ->
                // The Crawford game (a score change made it) has no cube to own.
                if (p.setup.matchLength > 0 && p.setup.crawford && !c.centred) null else p.copy(setup = p.setup.copy(cube = c))
            }
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

    private val met by lazy { MatchEquityTable() }

    /**
     * The match winning chance of the bottom player for [equity] (normalized, at the cube of [p]); null for money.
     * For a take the equity is the taker's (lose the cube's value: pass).
     */
    fun mwc(p: StudyPosition, equity: Float): Double? {
        val s = p.setup
        if (s.matchLength == 0) return null
        val a = s.matchLength - s.score[Player.HUMAN]
        val b = s.matchLength - s.score[Player.CPU]
        val v = maxOf(1, s.cube.value)
        val win = met.after(a, b, s.crawford, v, 0)
        val lose = met.after(a, b, s.crawford, 0, v)
        return ((equity * (win - lose) + win + lose) / 2).coerceIn(0.0, 1.0)
    }

    /**
     * The loss of play [move] in [a] (best equity minus its equity), or null when [a] does not have it.
     * [board] is the position it was played from (plays are matched by the position they leave).
     */
    fun playLoss(a: CheckerAnalysis, board: Board, move: Move): Float? {
        val target = runCatching { board.applyMove(Player.HUMAN, move) }.getOrNull() ?: return null
        val c = a.candidates.firstOrNull { runCatching { board.applyMove(Player.HUMAN, it.move) }.getOrNull() == target } ?: return null
        return (a.bestEquity - c.equity).coerceAtLeast(0f)
    }
}
