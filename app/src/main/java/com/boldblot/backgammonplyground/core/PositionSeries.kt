package com.boldblot.backgammonplyground.core

/**
 * A series of positions for a set (Match Library → Sets): text with one XGID or GNU ID per line, kept in the
 * order given. Each position is stored as My positions stores one, with the player who decides at the bottom;
 * [Item.bottomDecides] remembers whether that was the bottom player of the imported ID, so that a set from one
 * match can name who decides.
 */
object PositionSeries {

    /** One position; [played]: the move played there, when the sender knew it (a legal play of the decider). */
    class Item(val position: StudyPosition, val bottomDecides: Boolean, val played: Move? = null)

    class Preview(
        val items: List<Item>,
        /** Lines with text that is not a position ID. */
        val skipped: Int,
    ) {
        /** The match length all the positions share (0 = money), or null when they differ or there are none. */
        val matchLength: Int? get() = items.map { it.position.setup.matchLength }.distinct().singleOrNull()

        /** How many come with the move played. */
        val withPlayed: Int get() = items.count { it.played != null }
    }

    /**
     * The positions of [text], one ID per non-blank line; [played], when it has one entry per such line, gives the move
     * played in each ("13/7 8/7", "" when not known). An entry that is not a legal play there is left out; another
     * count of entries is ignored altogether.
     */
    fun read(text: String, beavers: Boolean = false, played: List<String>? = null): Preview {
        var skipped = 0
        val lines = text.lines().filter { it.isNotBlank() }
        val moves = played?.takeIf { it.size == lines.size }
        val items = lines.mapIndexedNotNull { i, line ->
            val it = runCatching { item(line, beavers) }.getOrNull() ?: run { skipped++; return@mapIndexedNotNull null }
            val m = moves?.getOrNull(i)?.let { text -> playedMove(text, it.position) }
            if (m == null) it else Item(it.position, it.bottomDecides, m)
        }
        return Preview(items, skipped)
    }

    /**
     * [text] ("13/7 8/7", "8/5(2) 6/5(2)", "bar/22*", "6/off", "24/18/13") as the legal play of [p]'s decider it is (plays
     * are matched by the position they leave), or null.
     */
    fun playedMove(text: String, p: StudyPosition): Move? {
        if (text.isBlank()) return null
        val d = p.dice ?: return null
        val steps = runCatching { MatImport.steps(text).map { (a, b) -> Step(a - 1, b - 1) } }.getOrNull() ?: return null
        val target = runCatching { p.board.applyMove(Player.HUMAN, Move(steps)) }.getOrNull() ?: return null
        return MoveGenerator.legalMoves(p.board, Player.HUMAN, d.d1, d.d2).firstOrNull {
            !it.isEmpty && runCatching { p.board.applyMove(Player.HUMAN, it) }.getOrNull() == target
        }
    }

    /** One line's position, turned so that its decider is at the bottom. */
    fun item(line: String, beavers: Boolean = false): Item {
        val s = PositionIds.parse(line)
        // As StudyPosition.parse: with a double on offer the player to answer it decides, otherwise the player on roll.
        val decider = if (s.doubled) Player.other(s.onRoll) else s.onRoll
        return Item(StudyPosition.parse(line, beavers), bottomDecides = decider == Player.HUMAN)
    }

    /** [p] as an XGID for Analyze Position: as stored, with the double on offer for a take or pass. */
    fun analyzeId(p: StudyPosition): String = PositionIds.toXgid(p.setup.copy(doubled = p.doubled))

    /** The score of [p] as the imported ID had it: (bottom player, top player). */
    fun score(p: StudyPosition, bottomDecides: Boolean): Pair<Int, Int> {
        val s = p.setup.score
        return if (bottomDecides) s[0] to s[1] else s[1] to s[0]
    }

    /**
     * A set position's line in lists: its number, and in a set from one match who decides and the score by name
     * ("3 · Anna to play · Anna 2, Bob 4 (7 pt)").
     */
    fun line(number: Int, p: StudyPosition, bottomDecides: Boolean, fromMatch: Boolean, bottomName: String, topName: String): String {
        if (!fromMatch) return "$number"
        val bottom = bottomName.ifBlank { "Bottom" }
        val top = topName.ifBlank { "Top" }
        val decider = if (bottomDecides) bottom else top
        val what = when {
            p.dice != null -> "$decider to play"
            p.doubled -> "$decider to take or pass"
            else -> "$decider's cube decision"
        }
        val length = p.setup.matchLength
        val score = if (length > 0) score(p, bottomDecides).let { (b, t) -> " · $bottom $b, $top $t ($length pt)" } else " · unlimited"
        return "$number · $what$score"
    }
}
