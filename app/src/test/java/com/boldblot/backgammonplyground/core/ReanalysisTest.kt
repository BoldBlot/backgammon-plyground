package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which games need the computer's decisions analyzed again (feedback 2026-11-02 #12). */
class ReanalysisTest {
    private fun rec(player: Int, analyzed: Boolean, auto: Boolean = false) = DecisionRecord(
        player = player, kind = DecisionKind.CHECKER, board = Board.INITIAL, cube = CubeState(), score = listOf(0, 0), crawford = false,
        dice = Dice(3, 1), move = Move.NONE, auto = auto,
        checker = if (analyzed) CheckerAnalysis(emptyList(), -1, 0f, 0f, 0f, legalMoves = 0, plies = 1) else null,
    )

    @Test
    fun computerMovesPlayedWithoutAnalysisAreFound() {
        // Computer analysis off: its plays have no analysis, so Analyze again includes them by default.
        assertTrue(Reanalysis.unanalyzedCpu(GameState(decisions = listOf(rec(Player.HUMAN, true), rec(Player.CPU, false)))))
        assertFalse(Reanalysis.unanalyzedCpu(GameState(decisions = listOf(rec(Player.HUMAN, false), rec(Player.CPU, true)))))
        assertFalse(Reanalysis.unanalyzedCpu(GameState(decisions = listOf(rec(Player.CPU, false, auto = true)))))
    }
}
