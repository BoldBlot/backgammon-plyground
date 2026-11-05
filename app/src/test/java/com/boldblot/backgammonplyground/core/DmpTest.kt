package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The DMP sign replaces the cube only when both players need one point (feedback 2026-11-05). */
class DmpTest {
    @Test fun bothNeedOnePoint() {
        assertTrue(Scoring.isDmp(5, listOf(4, 4), crawford = false))
        assertTrue(Scoring.isDmp(1, listOf(0, 0), crawford = false))
    }

    @Test fun notAtOtherScores() {
        assertFalse(Scoring.isDmp(5, listOf(4, 3), crawford = false))
        assertFalse(Scoring.isDmp(5, listOf(3, 3), crawford = false))
        assertFalse(Scoring.isDmp(0, listOf(0, 0), crawford = false))
        assertFalse(Scoring.isDmp(5, listOf(4, 4), crawford = true))
    }

    @Test fun nobodyMayDoubleAtDmp() {
        val rules = MatchRules(length = 5)
        assertFalse(Scoring.mayDouble(rules, CubeState(), Player.HUMAN, listOf(4, 4), false))
        assertFalse(Scoring.mayDouble(rules, CubeState(), Player.CPU, listOf(4, 4), false))
    }
}
