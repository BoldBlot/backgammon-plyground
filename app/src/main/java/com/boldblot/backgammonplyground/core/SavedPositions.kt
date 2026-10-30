package com.boldblot.backgammonplyground.core

import kotlinx.serialization.Serializable

/**
 * A position kept in My positions, as the player who decides faces it: they are [Player.HUMAN]
 * (XG's X, at the bottom). A checker play has dice; a cube decision has none, and with [doubled]
 * the opponent has doubled (take or pass). [setup] holds the rest (cube, score, rules).
 */
data class StudyPosition(val setup: PositionSetup, val doubled: Boolean, val beavers: Boolean) {
    val board: Board get() = setup.board
    val dice: Dice? get() = setup.dice
    val isChecker: Boolean get() = setup.dice != null

    /** The cube context of the decision: the doubler's for a take or pass, as the analysis sees it. */
    fun ctx(): CubeContext = CubeContext(
        matchLength = setup.matchLength, score = setup.score, crawford = setup.crawford, jacoby = setup.jacoby,
        cube = setup.cube, onRoll = setup.onRoll,
    )

    /** The XGID it is stored and exported as ([doubled]: the doubler's cube decision, without dice). */
    val xgid: String get() = PositionIds.toXgid(setup.copy(doubled = false))

    companion object {
        /** A position as stored ([xgid] with the player who decides as X; [doubled]: O has doubled). */
        fun stored(xgid: String, doubled: Boolean, beavers: Boolean): StudyPosition =
            StudyPosition(PositionIds.parse(xgid).copy(doubled = false), doubled, beavers)

        /** A position from an XGID or GNU ID, turned so that the player who decides is at the bottom. */
        fun parse(text: String, beavers: Boolean = false): StudyPosition {
            val s = PositionIds.parse(text)
            // With a double on offer the player to answer it decides; otherwise the player on roll.
            val decider = if (s.doubled) Player.other(s.onRoll) else s.onRoll
            return StudyPosition(turned(s, decider).copy(doubled = false), s.doubled, beavers)
        }

        /** The decision [d] of a game ([matchLength], [jacoby]), turned so that its player is at the bottom. */
        fun of(d: DecisionRecord, matchLength: Int, jacoby: Boolean): StudyPosition? {
            val doubled = when (d.kind) {
                DecisionKind.CHECKER -> false
                DecisionKind.DOUBLE, DecisionKind.NO_DOUBLE -> false
                DecisionKind.TAKE, DecisionKind.PASS, DecisionKind.BEAVER -> true
                else -> return null
            }
            if (d.kind == DecisionKind.CHECKER && d.dice == null) return null
            val onRoll = if (doubled) Player.other(d.player) else d.player
            val s = PositionSetup(
                board = d.board, onRoll = onRoll, dice = d.dice.takeIf { d.kind == DecisionKind.CHECKER },
                cube = d.cube, matchLength = matchLength, score = d.score, crawford = d.crawford, jacoby = jacoby,
                format = "XGID",
            )
            return StudyPosition(turned(s, d.player), doubled, d.beavers)
        }

        /** [s] with [decider]'s checkers at the bottom (sides, score and cube exchanged if needed). */
        fun turned(s: PositionSetup, decider: Int): PositionSetup {
            if (decider == Player.HUMAN) return s
            val b = s.board
            return s.copy(
                board = Board.fromSides(IntArray(25) { b.count(Player.CPU, it) }, IntArray(25) { b.count(Player.HUMAN, it) }),
                onRoll = Player.other(s.onRoll),
                score = s.score.reversed(),
                cube = if (s.cube.owner == -1) s.cube else s.cube.copy(owner = Player.other(s.cube.owner)),
            )
        }
    }
}

/** A saved position's analysis: a checker play's candidates, or the cube decision (the doubler's view). */
@Serializable
data class PositionAnalysis(
    val checker: CheckerAnalysis? = null,
    val cube: CubeEval? = null,
    /** "PureTD 3-ply" and the like. */
    val label: String = "",
)

/** The player's own right answer for a saved position: a play, or a cube action. */
@Serializable
data class MyAnswer(
    val move: Move? = null,
    /** DOUBLE or NO_DOUBLE (the player is on roll), TAKE, PASS or BEAVER (the player was doubled). */
    val cube: DecisionKind? = null,
) {
    companion object {
        /**
         * What was played in decision [d] (its player at the bottom): the first try when the decision
         * was tried again, as the blunder list counts it. Null for a raccoon decision.
         */
        fun played(d: DecisionRecord): MyAnswer? {
            val kind = d.firstTry?.kind ?: d.kind
            return when (kind) {
                DecisionKind.CHECKER -> (d.firstTry?.move ?: d.move)?.let { MyAnswer(move = it) }
                DecisionKind.DOUBLE, DecisionKind.NO_DOUBLE, DecisionKind.TAKE, DecisionKind.PASS, DecisionKind.BEAVER -> MyAnswer(cube = kind)
                else -> null
            }
        }
    }
}
