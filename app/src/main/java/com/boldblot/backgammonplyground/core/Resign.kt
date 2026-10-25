package com.boldblot.backgammonplyground.core

/**
 * Resignations ("surrender a single game, a gammon or a backgammon").
 *
 * Probabilities are the engine's cubeless outcome chances: win (including gammons),
 * win gammon (including backgammons), win backgammon, lose gammon, lose backgammon.
 *
 *  - The computer accepts a resignation unless it has at least [REJECT_CHANCE] (1 %)
 *    of winning more than offered: a single game is rejected with >= 1 % gammon
 *    chances, a gammon with >= 1 % backgammon chances; a backgammon is always accepted.
 *  - The computer offers to resign when it has no chance of winning and the size of
 *    the loss is certain: a backgammon when it loses a backgammon for sure, a gammon
 *    when it loses a gammon for sure and cannot be backgammoned, a single game when it
 *    cannot be gammoned. "No chance" / "for sure" means the chance rounds to 0.0 % /
 *    100.0 % ([CERTAIN]).
 *  - A bigger win that would not change anything does not count: under the Jacoby rule
 *    with the cube in the middle, or in a match when a smaller win already takes the
 *    match. (A gammon chance cannot be a reason to reject a single game that already
 *    wins the match, for example.)
 */
object Resignation {
    const val REJECT_CHANCE = 0.01f
    const val CERTAIN = 0.0005f

    /** Points a resignation of [type] is worth at the current cube (after the Jacoby rule). */
    fun result(winner: Int, type: WinType, cube: CubeState, rules: MatchRules): GameResult {
        val counted = minOf(type, highestCounted(rules, cube, null, winner))
        return GameResult(winner, counted, cube.value * counted.multiplier, resigned = true)
    }

    /**
     * Largest kind of win that is worth more to [winner] than the one below it.
     * [score] is null when the match score is irrelevant (money).
     */
    fun highestCounted(rules: MatchRules, cube: CubeState, score: List<Int>?, winner: Int): WinType {
        if (rules.isMoney) {
            val jacoby = rules.jacoby && rules.useCube && cube.value == 1 && cube.centred
            return if (jacoby) WinType.SINGLE else WinType.BACKGAMMON
        }
        if (score == null) return WinType.BACKGAMMON
        val need = rules.length - score[winner]
        return when {
            cube.value >= need -> WinType.SINGLE
            2 * cube.value >= need -> WinType.GAMMON
            else -> WinType.BACKGAMMON
        }
    }

    /**
     * Whether the player offered a resignation of [offered] accepts it.
     * [winGammon] and [winBackgammon] are that player's own chances.
     */
    fun accepts(offered: WinType, winGammon: Float, winBackgammon: Float, highestCounted: WinType): Boolean = when {
        offered >= highestCounted -> true
        offered == WinType.SINGLE -> winGammon < REJECT_CHANCE
        offered == WinType.GAMMON -> winBackgammon < REJECT_CHANCE
        else -> true
    }

    /**
     * The resignation a player with outcome chances [probs] (own point of view) offers,
     * or null if the game is not yet decided. [opponentHighest] is the largest win that
     * counts for the opponent.
     */
    fun offer(probs: List<Float>, opponentHighest: WinType): WinType? {
        val win = probs[0]
        val loseGammon = if (opponentHighest >= WinType.GAMMON) probs[3] else 0f
        val loseBackgammon = if (opponentHighest >= WinType.BACKGAMMON) probs[4] else 0f
        return when {
            win > CERTAIN -> null
            loseBackgammon >= 1f - CERTAIN -> WinType.BACKGAMMON
            loseGammon >= 1f - CERTAIN && loseBackgammon <= CERTAIN -> WinType.GAMMON
            loseGammon <= CERTAIN -> WinType.SINGLE
            else -> null
        }
    }
}
