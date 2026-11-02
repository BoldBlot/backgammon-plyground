package com.boldblot.backgammonplyground.core

/**
 * The quiz-answer part of the My positions and quiz filters. Its keys ("quiz:…") sit in the same set as
 * the label keys; as with labels, any of a group and all groups must match.
 */
enum class QuizFilter(val title: String, val group: Group, val test: (right: Int, wrong: Int) -> Boolean) {
    NOT_TRIED("Not tried yet", Group.ANSWERS, { r, w -> r + w == 0 }),
    SOME_RIGHT("Answered right", Group.ANSWERS, { r, _ -> r > 0 }),
    SOME_WRONG("Missed", Group.ANSWERS, { _, w -> w > 0 }),
    // Released keys keep their names (BALANCE_BELOW, BALANCE_ZERO, BALANCE_ABOVE); the others came in 0.7.11.
    BALANCE_BELOW_3("Below −3", Group.BALANCE, { r, w -> r - w < -3 }),
    BALANCE_BELOW_2("Below −2", Group.BALANCE, { r, w -> r - w < -2 }),
    BALANCE_BELOW_1("Below −1", Group.BALANCE, { r, w -> r - w < -1 }),
    BALANCE_BELOW("Below 0", Group.BALANCE, { r, w -> r - w < 0 }),
    BALANCE_ZERO("0", Group.BALANCE, { r, w -> r == w }),
    BALANCE_ABOVE("Above 0", Group.BALANCE, { r, w -> r - w > 0 }),
    BALANCE_ABOVE_1("Above +1", Group.BALANCE, { r, w -> r - w > 1 }),
    BALANCE_ABOVE_2("Above +2", Group.BALANCE, { r, w -> r - w > 2 }),
    BALANCE_ABOVE_3("Above +3", Group.BALANCE, { r, w -> r - w > 3 });

    enum class Group(val title: String) { ANSWERS("Quiz answers"), BALANCE("Quiz balance (right − missed)") }

    val key: String get() = PREFIX + name

    companion object {
        private const val PREFIX = "quiz:"

        fun of(key: String): QuizFilter? = if (key.startsWith(PREFIX)) entries.firstOrNull { it.name == key.removePrefix(PREFIX) } else null

        /** Whether a position answered [right] and [wrong] times passes the quiz keys of [filter]. */
        fun matches(right: Int, wrong: Int, filter: Set<String>): Boolean =
            filter.mapNotNull(::of).groupBy { it.group }.all { (_, any) -> any.any { it.test(right, wrong) } }

        /** "Quiz answers: Missed · Quiz balance: Below 0", or "" without quiz keys. */
        fun text(filter: Set<String>): String =
            filter.mapNotNull(::of).sortedBy { it.ordinal }.groupBy { it.group }
                .entries.joinToString(" · ") { (g, l) -> "${g.title}: ${l.joinToString(" or ") { it.title }}" }
    }
}
