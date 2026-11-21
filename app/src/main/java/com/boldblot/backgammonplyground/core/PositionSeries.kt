package com.boldblot.backgammonplyground.core

/**
 * A series of positions for a set (Match Library → Sets): text with one XGID or GNU ID per line, kept in the
 * order given. Each position is stored as My positions stores one, with the player who decides at the bottom;
 * [Item.bottomDecides] remembers whether that was the bottom player of the imported ID, so that a set from one
 * match can name who decides.
 */
object PositionSeries {

    class Item(val position: StudyPosition, val bottomDecides: Boolean)

    class Preview(
        val items: List<Item>,
        /** Lines with text that is not a position ID. */
        val skipped: Int,
    ) {
        /** The match length all the positions share (0 = money), or null when they differ or there are none. */
        val matchLength: Int? get() = items.map { it.position.setup.matchLength }.distinct().singleOrNull()
    }

    fun read(text: String, beavers: Boolean = false): Preview {
        var skipped = 0
        val items = text.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            runCatching { item(line, beavers) }.getOrNull() ?: run { skipped++; null }
        }
        return Preview(items, skipped)
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
        val score = if (length > 0) score(p, bottomDecides).let { (b, t) -> " · $bottom $b, $top $t ($length pt)" } else " · money"
        return "$number · $what$score"
    }
}
