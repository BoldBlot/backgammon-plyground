package com.boldblot.backgammonplyground.core

/** The groups of [AutoLabel]s, in the order they are listed and named. */
/** [allOf]: the group's labels can all be true at once, so an autofilter needs each one it names ([Labels.matchesAll]). */
enum class LabelGroup(val title: String, val allOf: Boolean = false) {
    /** A saved position's ID tag (#AB012), derived from the position, never stored as a label. */
    ID("ID#"),
    DECISION("Decision"), CUBE_SIDE("Cube side"), POSITION("Position"), GAME("Game"), SIZE("Size"),
    MISSED("Missed", true),
    STAGE("Stage", true), STAKES("Stakes", true), WHOSE("Whose"), MARKED("Marked"),
    THEMES("Themes (rule of thumb)", true),
    /** How the last quiz answer compares with the one before; worked out at each answer, never stored as a label. */
    TREND("Quiz trend"),
    /** The player's own labels. */
    MINE("My labels", true),
}

/**
 * The labels PlyGround works out for a blunder or a saved position. Stored by [name] (never
 * rename one: saved positions and linked folders keep them).
 */
enum class AutoLabel(val title: String, val group: LabelGroup, val description: String) {
    CHECKER("Checker", LabelGroup.DECISION, "A checker play."),
    CUBE("Cube", LabelGroup.DECISION, "A cube decision."),
    DOUBLE_SIDE("Double/no double", LabelGroup.CUBE_SIDE, "The cube decision of the player on roll."),
    TAKE_SIDE("Take/pass", LabelGroup.CUBE_SIDE, "The answer to a double."),
    CONTACT("Contact", LabelGroup.POSITION, "The two sides can still hit each other."),
    RACE("Race", LabelGroup.POSITION, "The two sides can no longer hit each other."),
    BEAR_OFF("Bear-off", LabelGroup.POSITION, "All the deciding player's checkers are in their home board."),
    MONEY("Money", LabelGroup.GAME, "A money game."),
    MATCH("Match", LabelGroup.GAME, "A match game."),
    CRAWFORD("Crawford", LabelGroup.GAME, "The Crawford game."),
    POST_CRAWFORD("Post-Crawford", LabelGroup.GAME, "After the Crawford game: a player needs one point."),
    DMP("DMP", LabelGroup.GAME, "Double match point: both players need one point (a 1-point match too)."),
    ERROR("Error", LabelGroup.SIZE, "A mistake of XG's error size."),
    BLUNDER("Blunder", LabelGroup.SIZE, "A mistake of XG's blunder size."),
    OPENING("Opening", LabelGroup.STAGE, "The first few moves: both pip counts are still 145 or more."),
    GAMMONISH("Gammonish", LabelGroup.STAKES, "Either side wins a gammon at least 20% of the time."),
    MISSED_HIT("Missed hit", LabelGroup.MISSED, "A blunder that did not hit when the best play hit, and no play without a hit was acceptable."),
    MISSED_POINT("Missed point", LabelGroup.MISSED, "A blunder that did not make a point when the best play made one, and no play without a point was acceptable."),
    MISSED_RUNNING("Missed running", LabelGroup.MISSED, "A blunder that left the checkers in the opponent's home board when the best play moved one out, and no play that stays was acceptable."),
    MINE("Mine", LabelGroup.WHOSE, "Your own mistake."),
    OPPONENTS("Opponent's", LabelGroup.WHOSE, "The opponent's mistake (imported matches)."),
    MARKED("Marked", LabelGroup.MARKED, "You marked it while playing."),
    ON_THE_BAR("On the bar", LabelGroup.THEMES, "The deciding player has a checker on the bar."),
    HIT("Hit", LabelGroup.THEMES, "The best play or the play made hits."),
    PRIME_VS_PRIME("Prime vs prime", LabelGroup.THEMES, "Both sides have a prime of four points or more."),
    HOLDING("Holding game", LabelGroup.THEMES, "A side behind in the race holds one anchor high in the opponent's board."),
    BACKGAME("Backgame", LabelGroup.THEMES, "A side far behind in the race holds two or more points in the opponent's board."),
    IMPROVED("Improved", LabelGroup.TREND, "The last quiz answer was better than the one before."),
    SAME("Same", LabelGroup.TREND, "The last quiz answer was as good as the one before."),
    WORSE("Worse", LabelGroup.TREND, "The last quiz answer was worse than the one before."),
}

/**
 * Labels by key: an [AutoLabel]'s name, or "my:" and the name of one of the player's own labels.
 * A [LabelFilter] is a set of keys: within a group any of them, across groups all of them.
 */
object Labels {
    private const val MY = "my:"
    private const val ID = "id:"

    /** The key of ID tag [tag] ("AB012"), for the filter and the tags shown on a saved position. */
    fun idKey(tag: String) = ID + tag.trim().uppercase()
    fun isId(key: String) = key.startsWith(ID)

    fun myKey(name: String) = MY + name.trim()
    fun isMine(key: String) = key.startsWith(MY)
    /** A quiz trend label ([LabelGroup.TREND]): it comes from the quiz, so it is neither ticked by hand nor stored with the others. */
    fun isTrend(key: String) = auto(key)?.group == LabelGroup.TREND

    /**
     * The trend of a quiz answer costing [error] after one costing [before] (both after the 1-point factor):
     * null without an earlier answer.
     */
    fun trend(before: Float?, error: Float?): AutoLabel? = when {
        before == null || error == null -> null
        error < before - 0.0005f -> AutoLabel.IMPROVED
        error > before + 0.0005f -> AutoLabel.WORSE
        else -> AutoLabel.SAME
    }

    fun auto(key: String): AutoLabel? = AutoLabel.entries.firstOrNull { it.name == key }

    fun title(key: String): String = if (isId(key)) "#" + key.removePrefix(ID) else auto(key)?.title ?: key.removePrefix(MY)
    fun group(key: String): LabelGroup = if (isId(key)) LabelGroup.ID else auto(key)?.group ?: LabelGroup.MINE

    /** [keys] in the order they are listed: by group, then as declared (own labels by name). */
    fun sorted(keys: Collection<String>): List<String> = keys.sortedWith(
        compareBy<String>({ group(it).ordinal }, { auto(it)?.ordinal ?: Int.MAX_VALUE }, { title(it).lowercase() }),
    )

    /**
     * True if [labels] pass autofilter [filter]: all its labels, except that in a group whose labels
     * exclude each other (Checker or Cube, Contact or Race, Money or Match…) one of them is enough.
     */
    fun matchesAll(labels: Set<String>, filter: Set<String>): Boolean =
        filter.groupBy { group(it) }.all { (g, keys) -> if (g.allOf) keys.all { it in labels } else keys.any { it in labels } }

    /** True if [labels] pass [filter]: in each group the filter uses, at least one of its labels. */
    fun matches(labels: Set<String>, filter: Set<String>): Boolean =
        filter.groupBy { group(it) }.values.all { keys -> keys.any { it in labels } }

    /** A folder name for [filter]: "Contact or Race · Cube"; "All blunders" without a filter. */
    fun folderName(filter: Set<String>): String {
        if (filter.isEmpty()) return "All blunders"
        return sorted(filter).groupBy { group(it) }.values.joinToString(" · ") { keys -> keys.joinToString(" or ") { title(it) } }
    }

    /** How a filter is stored with a folder (keys in a fixed order, one per line). */
    fun encode(filter: Set<String>): String = sorted(filter).joinToString("\n")
    fun decode(text: String): Set<String> = text.split('\n').filter { it.isNotBlank() }.toSet()

    /**
     * The labels of decision [d] of a game ([matchLength]); [opponent]: the opponent's decision
     * (imported matches), null when not known.
     */
    fun of(d: DecisionRecord, matchLength: Int, opponent: Boolean?): Set<AutoLabel> {
        val probs = d.checker?.candidates?.firstOrNull()?.probs ?: d.cubeEval?.probs
        val error = (d.scoredError ?: d.error)?.times(XgRules.errorFactor(matchLength))
        val best = d.checker?.candidates?.firstOrNull()?.move
        return compute(
            board = d.board, player = d.player, kind = d.kind, matchLength = matchLength, score = d.score,
            crawford = d.crawford, moves = listOfNotNull(d.move, best), probs = probs, error = error,
            played = d.reviewMove, analysis = d.checker, factor = XgRules.errorFactor(matchLength),
        ).apply {
            when (opponent) {
                true -> add(AutoLabel.OPPONENTS)
                false -> add(AutoLabel.MINE)
                null -> Unit
            }
        }
    }

    /** The labels of a saved position: [board] with the deciding player as [Player.HUMAN]. */
    fun of(
        board: Board, cubeDecision: Boolean, doubled: Boolean, matchLength: Int, score: List<Int>, crawford: Boolean,
        moves: List<Move>, probs: List<Float>?,
    ): Set<AutoLabel> = compute(
        board, Player.HUMAN,
        when {
            !cubeDecision -> DecisionKind.CHECKER
            doubled -> DecisionKind.TAKE
            else -> DecisionKind.DOUBLE
        },
        matchLength, score, crawford, moves, probs, error = null,
    )

    private fun compute(
        board: Board, player: Int, kind: DecisionKind, matchLength: Int, score: List<Int>, crawford: Boolean,
        moves: List<Move>, probs: List<Float>?, error: Float?,
        /** The play made and the analysis behind [error] (after [factor]), for the "missed" labels. */
        played: Move? = null, analysis: CheckerAnalysis? = null, factor: Float = 1f,
    ): MutableSet<AutoLabel> {
        val out = mutableSetOf<AutoLabel>()
        val opp = Player.other(player)
        if (kind == DecisionKind.CHECKER) out += AutoLabel.CHECKER else {
            out += AutoLabel.CUBE
            out += if (kind.isResponse) AutoLabel.TAKE_SIDE else AutoLabel.DOUBLE_SIDE
        }
        val race = board.isRace()
        out += if (race) AutoLabel.RACE else AutoLabel.CONTACT
        if (board.allHome(player)) out += AutoLabel.BEAR_OFF
        if (matchLength == 0) out += AutoLabel.MONEY else {
            out += AutoLabel.MATCH
            matchGameLabel(matchLength, score, crawford)?.let { out += it }
        }
        if (error != null) when {
            XgRules.severity(error).atLeast(Severity.BLUNDER) -> out += AutoLabel.BLUNDER
            XgRules.severity(error).atLeast(Severity.ERROR) -> out += AutoLabel.ERROR
        }
        if (board.pipCount(player) >= 145 && board.pipCount(opp) >= 145) out += AutoLabel.OPENING
        if (probs != null && probs.size >= 5 && (probs[1] >= 0.2f || probs[3] >= 0.2f)) out += AutoLabel.GAMMONISH
        if (board.bar(player) > 0) out += AutoLabel.ON_THE_BAR
        if (kind == DecisionKind.CHECKER && moves.any { hits(board, player, it) }) out += AutoLabel.HIT
        if (kind == DecisionKind.CHECKER && played != null && analysis != null && error != null && XgRules.severity(error).atLeast(Severity.BLUNDER)) {
            if (missed(analysis, played, factor) { hits(board, player, it) }) out += AutoLabel.MISSED_HIT
            if (missed(analysis, played, factor) { makesPoint(board, player, it) }) out += AutoLabel.MISSED_POINT
            if (missed(analysis, played, factor) { runs(it) }) out += AutoLabel.MISSED_RUNNING
        }
        if (!race) {
            if (prime(board, player) >= 4 && prime(board, opp) >= 4) out += AutoLabel.PRIME_VS_PRIME
            for (side in listOf(player, opp)) {
                val behind = board.pipCount(side) - board.pipCount(Player.other(side))
                val anchors = (18..23).count { board.count(side, it) >= 2 }
                if (anchors >= 2 && behind >= 50) out += AutoLabel.BACKGAME
                // The opponent's 3, 4 and 5 points and bar point (indices 21, 20, 19, 17 from this side).
                else if (anchors <= 1 && behind >= 10 && listOf(17, 19, 20, 21).any { board.count(side, it) >= 2 }) out += AutoLabel.HOLDING
            }
        }
        return out
    }

    /** Crawford, DMP (both need one point) or Post-Crawford (one of them does); null otherwise. */
    fun matchGameLabel(matchLength: Int, score: List<Int>, crawford: Boolean): AutoLabel? = when {
        matchLength <= 0 -> null
        crawford -> AutoLabel.CRAWFORD
        score.all { it == matchLength - 1 } -> AutoLabel.DMP
        score.any { it == matchLength - 1 } -> AutoLabel.POST_CRAWFORD
        else -> null
    }

    /**
     * Labels stored before DMP existed called double match point Post-Crawford: read them as DMP
     * (the player's own changes to the labels stay).
     */
    fun withDmp(labels: Set<String>, matchLength: Int, score: List<Int>, crawford: Boolean): Set<String> =
        if (AutoLabel.POST_CRAWFORD.name in labels && matchGameLabel(matchLength, score, crawford) == AutoLabel.DMP) {
            labels - AutoLabel.POST_CRAWFORD.name + AutoLabel.DMP.name
        } else labels

    /**
     * True if the best play does what [does] asks (hits, makes a point, runs), the play made [played] does not,
     * and neither does any other play in [analysis] that is acceptable (not itself an error).
     */
    private fun missed(analysis: CheckerAnalysis, played: Move, factor: Float, does: (Move) -> Boolean): Boolean {
        val best = analysis.candidates.firstOrNull() ?: return false
        if (!does(best.move) || does(played)) return false
        return analysis.candidates.none { c -> !does(c.move) && !XgRules.severity((best.equity - c.equity) * factor).atLeast(Severity.ERROR) }
    }

    /** True if [move] makes a point: one that had fewer than two of [player]'s checkers has two or more after it. */
    private fun makesPoint(board: Board, player: Int, move: Move): Boolean {
        val after = board.applyMove(player, move)
        return (0..23).any { board.count(player, it) < 2 && after.count(player, it) >= 2 }
    }

    /** True if [move] takes a checker out of the opponent's home board (indices 18..23) to the outside. */
    private fun runs(move: Move): Boolean = move.steps.any { it.from in 18..23 && it.to < 18 }

    private fun hits(board: Board, player: Int, move: Move): Boolean {
        var b = board
        for (s in move.steps) {
            if (b.isHit(player, s)) return true
            b = b.applyStep(player, s)
        }
        return false
    }

    /** [player]'s longest run of made points (two checkers or more). */
    private fun prime(board: Board, player: Int): Int {
        var best = 0
        var run = 0
        for (i in 0..23) {
            run = if (board.count(player, i) >= 2) run + 1 else 0
            best = maxOf(best, run)
        }
        return best
    }
}

/** [this] decision turned so that its player is [Player.HUMAN] (sides, score and cube exchanged). */
fun DecisionRecord.turned(): DecisionRecord {
    if (player == Player.HUMAN) return this
    val b = board
    return copy(
        player = Player.HUMAN,
        board = Board.fromSides(IntArray(25) { b.count(Player.CPU, it) }, IntArray(25) { b.count(Player.HUMAN, it) }),
        score = score.reversed(),
        cube = if (cube.owner == -1) cube else cube.copy(owner = Player.other(cube.owner)),
    )
}
