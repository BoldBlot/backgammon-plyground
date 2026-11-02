package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The position shared from a game in progress: always for the player who decides. */
class CurrentPositionTest {
    private fun match(game: GameState) = MatchState(rules = MatchRules(length = 5), cpuLevel = CpuLevel.entries.first(), score = listOf(1, 2), game = game)

    @Test fun humanMovingIsACheckerPlayWithTheDice() {
        val p = StudyPosition.current(match(GameState(turn = Player.HUMAN, phase = Phase.MOVING, dice = Dice(3, 1))))!!
        assertEquals(Dice(3, 1), p.dice)
        assertFalse(p.doubled)
        assertEquals(listOf(1, 2), p.setup.score)
        val back = StudyPosition.parse(p.xgid)
        assertEquals(p.board, back.board)
        assertEquals(p.dice, back.dice)
        assertEquals(p.setup.score, back.setup.score)
        assertEquals(p.setup.matchLength, back.setup.matchLength)
    }

    @Test fun humanBeforeRollingIsACubeDecision() {
        val p = StudyPosition.current(match(GameState(turn = Player.HUMAN, phase = Phase.PRE_ROLL)))!!
        assertNull(p.dice)
        assertFalse(p.doubled)
    }

    /** The computer doubled: the human answers, so the position is turned to them (score too). */
    @Test fun computerDoubledIsTheHumansTakeDecision() {
        val p = StudyPosition.current(match(GameState(turn = Player.CPU, phase = Phase.DOUBLED)))!!
        assertTrue(p.doubled)
        assertEquals(listOf(1, 2), p.setup.score)
    }

    @Test fun noDecisionBetweenGames() {
        assertNull(StudyPosition.current(match(GameState(phase = Phase.OPENING))))
    }
}
