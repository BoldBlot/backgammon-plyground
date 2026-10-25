package com.boldblot.backgammonplyground.core

import kotlinx.serialization.Serializable

/** Options chosen when starting a match (or a money session when [length] is 0). */
@Serializable
data class MatchRules(
    /** Match length in points; 0 = money game session (unlimited games). */
    val length: Int = 5,
    val useCube: Boolean = true,
    /** Money only: gammons count only after the cube has been turned. */
    val jacoby: Boolean = true,
    /** Match only: no doubling in the game after a player first reaches length - 1. */
    val crawford: Boolean = true,
    /** Money only: a player who is doubled may beaver (take and at once redouble, keeping the cube). */
    val beavers: Boolean = false,
    /** Money only, with [beavers]: the doubler may answer a beaver with a raccoon (redouble once more). */
    val raccoons: Boolean = false,
) {
    val isMoney: Boolean get() = length == 0
    val beaversAllowed: Boolean get() = isMoney && useCube && beavers
    val raccoonsAllowed: Boolean get() = beaversAllowed && raccoons
}

/** Doubling cube: value and owner (-1 = centred, otherwise a player index). */
@Serializable
data class CubeState(val value: Int = 1, val owner: Int = -1) {
    val centred: Boolean get() = owner == -1

    fun canBeTurnedBy(player: Int): Boolean = owner == -1 || owner == player
}

/** Everything the engine needs to know about the match situation. */
data class CubeContext(
    val matchLength: Int,
    val score: List<Int>,
    val crawford: Boolean,
    val jacoby: Boolean,
    val cube: CubeState,
    val onRoll: Int,
) {
    /** int[9] layout expected by the JNI bridge. */
    fun toEngine(): IntArray = intArrayOf(
        matchLength, score[0], score[1], if (crawford) 1 else 0,
        if (jacoby && matchLength == 0) 1 else 0, 0, cube.value, cube.owner, onRoll,
    )

    fun withOnRoll(player: Int) = copy(onRoll = player)
}

enum class WinType(val multiplier: Int) { SINGLE(1), GAMMON(2), BACKGAMMON(3) }

@Serializable
data class GameResult(
    val winner: Int,
    val type: WinType,
    /** Points won: cube value x multiplier (after the Jacoby rule). */
    val points: Int,
    /** True when the game ended because a double was passed. */
    val byPass: Boolean = false,
    /** True when the loser resigned (the winner accepted the resignation). */
    val resigned: Boolean = false,
)

object Scoring {

    /** Type of win once [winner] has borne off all checkers. */
    fun winType(board: Board, winner: Int): WinType {
        val loser = Player.other(winner)
        if (board.off(loser) > 0) return WinType.SINGLE
        // Backgammon: loser still has a checker on the bar or in the winner's home board
        // (the winner's points 1..6 are the loser's indices 18..23).
        if (board.bar(loser) > 0) return WinType.BACKGAMMON
        for (i in 18..23) if (board.count(loser, i) > 0) return WinType.BACKGAMMON
        return WinType.GAMMON
    }

    fun result(board: Board, winner: Int, cube: CubeState, rules: MatchRules): GameResult {
        var type = winType(board, winner)
        if (rules.isMoney && rules.jacoby && rules.useCube && cube.value == 1 && cube.centred) {
            type = WinType.SINGLE
        }
        return GameResult(winner, type, cube.value * type.multiplier)
    }

    /**
     * Whether [player] may offer a double now (before rolling).
     * Doubling is pointless and disallowed once the player already wins the
     * match by winning the game at the current cube value.
     */
    fun mayDouble(rules: MatchRules, cube: CubeState, player: Int, score: List<Int>, crawfordGame: Boolean): Boolean {
        if (!rules.useCube || !cube.canBeTurnedBy(player)) return false
        if (rules.isMoney) return cube.value < 64
        if (crawfordGame) return false
        return score[player] + cube.value < rules.length
    }
}
