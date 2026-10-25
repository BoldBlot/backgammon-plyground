package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XgRulesTest {

    private fun cube(nd: Float, dt: Float, dp: Float = 1f, available: Boolean = true, optional: Boolean = false) =
        CubeEval(nd, dt, dp, maxOf(nd, minOf(dt, dp)), shouldDouble = minOf(dt, dp) > nd, shouldTake = dt <= dp,
            optional = optional, cubeAvailable = available, probs = List(5) { 0f })

    @Test
    fun manualExamplePerformance() {
        // XG2 manual: a blunder of -0.105 and an error of -0.035 over 13 moves gives
        // performance 0.140 / 13 = 0.0108, i.e. PR 5.38.
        var t = PerformanceTotals()
        t = t.add(DecisionKind.CHECKER, counted = true, weightedError = 0.105f)
        t = t.add(DecisionKind.CHECKER, counted = true, weightedError = 0.035f)
        repeat(11) { t = t.add(DecisionKind.CHECKER, counted = true, weightedError = 0f) }
        assertEquals(13, t.decisions)
        assertEquals(0.140 / 13 * 500, t.pr!!, 1e-4)
        assertEquals(5.385, t.pr!!, 1e-3)
        assertEquals(1, t.checkerBlunders)
        assertEquals(1, t.checkerErrors)
        assertEquals("Expert", XgRules.ratingName(t.pr!!))
    }

    @Test
    fun eloEquivalentMatchesManualFormula() {
        // Manual: performance -0.0108 -> roughly 2062 with the linear formula; the exact
        // logistic formula gives a nearby value.
        val elo = XgRules.eloEquivalent(5.4)
        assertTrue(elo in 2000.0..2120.0)
        // Perfect play maps to 2000 * 1/(1+e^-1.12) + 732
        assertEquals(1.0 / (1.0 + Math.exp(-1.12)) * 2000 + 732, XgRules.eloEquivalent(0.0), 1e-6)
    }

    @Test
    fun severityThresholdsAreStrict() {
        assertEquals(Severity.NONE, XgRules.severity(0f))
        assertEquals(Severity.INACCURACY, XgRules.severity(0.020f))
        assertEquals(Severity.ERROR, XgRules.severity(0.021f))
        assertEquals(Severity.ERROR, XgRules.severity(0.052f)) // manual example
        assertEquals(Severity.ERROR, XgRules.severity(0.080f))
        assertEquals(Severity.BLUNDER, XgRules.severity(0.081f))
    }

    @Test
    fun onePointMatchFactor() {
        assertEquals(1.5f, XgRules.errorFactor(1))
        assertEquals(1f, XgRules.errorFactor(0))
        assertEquals(1f, XgRules.errorFactor(7))
        // manual: a move is a blunder in a 1-point match if it is 0.080/1.5 = 0.053 worse
        assertEquals(Severity.BLUNDER, XgRules.severity(0.054f * XgRules.errorFactor(1)))
    }

    @Test
    fun obviousCheckerPlaysAreNotDecisions() {
        val c = Candidate(Move.NONE, 0f, List(5) { 0f }, 2)
        val forced = CheckerAnalysis(listOf(c), 0, 0.1f, 0.1f, 0.1f, legalMoves = 1, plies = 2)
        assertFalse(forced.isDecision)
        val flat = CheckerAnalysis(listOf(c, c), 0, 0.1f, 0.1f, 0.0995f, legalMoves = 2, plies = 2)
        assertFalse(flat.isDecision)
        val real = CheckerAnalysis(listOf(c, c), 1, 0.05f, 0.1f, 0.05f, legalMoves = 2, plies = 2)
        assertTrue(real.isDecision)
        assertEquals(0.05f, real.error, 1e-6f)
    }

    @Test
    fun obviousCubeDecisions() {
        // A real double: always a decision when doubled.
        assertTrue(DecisionMath.isDoubleDecision(cube(0.6f, 0.7f), doubled = true))
        // Close no-double: counted.
        assertTrue(DecisionMath.isDoubleDecision(cube(0.30f, 0.25f), doubled = false))
        // Obvious no double: before - after >= 0.2.
        assertFalse(DecisionMath.isDoubleDecision(cube(0.10f, -0.20f), doubled = false))
        // Obvious too good: before - drop (1.0) >= 0.2.
        assertFalse(DecisionMath.isDoubleDecision(cube(1.25f, 1.6f), doubled = false))
        // Optional double: same equity either way.
        assertFalse(DecisionMath.isDoubleDecision(cube(0.5f, 0.5f), doubled = false))
        // Cube unavailable: never a decision.
        assertFalse(DecisionMath.isDoubleDecision(cube(0.6f, 0.7f, available = false), doubled = true))
        // Very negative before and after.
        assertFalse(DecisionMath.isDoubleDecision(cube(-0.95f, -0.97f, dp = 1f), doubled = false))
    }

    @Test
    fun cubeErrors() {
        // Correct double/take: nd 0.5, dt 0.6, dp 1.0
        val e = cube(0.5f, 0.6f)
        assertEquals(0f, DecisionMath.doubleError(e, doubled = true), 1e-6f)
        assertEquals(0.1f, DecisionMath.doubleError(e, doubled = false), 1e-6f)
        // Taker: take = -0.6, pass = -1.0 -> passing costs 0.4
        assertEquals(0f, DecisionMath.takeError(e, took = true), 1e-6f)
        assertEquals(0.4f, DecisionMath.takeError(e, took = false), 1e-6f)
        // Double/pass: dt 1.3 > dp 1.0; after double = 1.0; taking costs 0.3
        val dp = cube(0.8f, 1.3f)
        assertEquals(0f, DecisionMath.doubleError(dp, doubled = true), 1e-6f)
        assertEquals(0.2f, DecisionMath.doubleError(dp, doubled = false), 1e-6f)
        assertEquals(0.3f, DecisionMath.takeError(dp, took = true), 1e-6f)
    }

    @Test
    fun cubeDecisionsCountTowardsPr() {
        var t = PerformanceTotals()
        t = t.add(DecisionKind.NO_DOUBLE, counted = false, weightedError = 0f) // obvious: ignored
        assertNull(t.pr)
        t = t.add(DecisionKind.TAKE, counted = true, weightedError = 0.1f)
        t = t.add(DecisionKind.DOUBLE, counted = true, weightedError = 0f)
        assertEquals(2, t.cubeDecisions)
        assertEquals(500 * 0.1 / 2, t.cubePr!!, 1e-5)
        assertEquals(1, t.takeBlunders)
    }

    @Test
    fun luckRatingUsesGnubgLevels() {
        assertNull(XgRules.luckRating(0.0, 0))
        // Average luck per roll x 10 against +-0.3 and +-0.6 (20 rolls).
        assertEquals("None", XgRules.luckRating(0.5, 20))
        assertEquals("Good dice, man!", XgRules.luckRating(0.7, 20))
        assertEquals("Go to Las Vegas", XgRules.luckRating(1.3, 20))
        assertEquals("Bad dice, man!", XgRules.luckRating(-0.7, 20))
        assertEquals("Go to bed", XgRules.luckRating(-1.3, 20))
    }

    @Test
    fun uncountedTakeDecisionsAreLeftOut() {
        val t = PerformanceTotals()
            .add(DecisionKind.TAKE, counted = false, weightedError = 0.3f)
            .add(DecisionKind.PASS, counted = true, weightedError = 0.1f)
        assertEquals(1, t.takeDecisions)
        assertEquals(0.1, t.takeError, 1e-6)
        assertEquals(1, t.takeBlunders)
    }
}
