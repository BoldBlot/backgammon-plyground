package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class MistakesTest {

    private val moves = List(6) { i -> Move(listOf(Step(23 - i, 20 - i))) }

    /** Best 0.30, then losses 0.01 (inaccuracy), 0.03 and 0.05 (errors), 0.10 and 0.40 (blunders). */
    private val ranked = listOf(0.30f, 0.29f, 0.27f, 0.25f, 0.20f, -0.10f).mapIndexed { i, eq ->
        Candidate(moves[i], eq, listOf(0.5f, 0f, 0f, 0f, 0f), 3)
    }

    @Test
    fun picksTheDrawnMistakeSize() {
        val blunderer = MistakeMaker(MistakeRate(0f, 1f), Random(1))
        repeat(50) { assertTrue(blunderer.checker(ranked, 1f) in setOf(moves[4], moves[5])) }
        val errs = MistakeMaker(MistakeRate(1f, 0f), Random(2))
        repeat(50) { assertTrue(errs.checker(ranked, 1f) in setOf(moves[2], moves[3])) }
        val perfect = MistakeMaker(MistakeRate(0f, 0f), Random(3))
        repeat(50) { assertEquals(moves[0], perfect.checker(ranked, 1f)) }
    }

    @Test
    fun playsBestWhenNoMistakeOfThatSizeExists() {
        val close = ranked.take(2)
        val m = MistakeMaker(MistakeRate(0f, 1f), Random(4))
        repeat(20) { assertEquals(moves[0], m.checker(close, 1f)) }
    }

    @Test
    fun ratesAreRoughlyKept() {
        val m = MistakeMaker(MistakeRate(0.2f, 0.1f), Random(5))
        var errors = 0
        var blunders = 0
        val n = 20000
        repeat(n) {
            when (m.checker(ranked, 1f)) {
                moves[2], moves[3] -> errors++
                moves[4], moves[5] -> blunders++
            }
        }
        assertEquals(0.2, errors.toDouble() / n, 0.015)
        assertEquals(0.1, blunders.toDouble() / n, 0.01)
    }

    @Test
    fun cubeMistakesFlipOnlyWhenTheWrongActionHasTheDrawnSize() {
        // Double/take: no double costs 0.05 (an error), passing costs 0.35 (a blunder).
        val eval = CubeEval(
            noDouble = 0.60f, doubleTake = 0.65f, doublePass = 1.0f, optimal = 0.65f, shouldDouble = true,
            shouldTake = true, optional = false, cubeAvailable = true, probs = listOf(0.7f, 0.1f, 0f, 0f, 0f),
        )
        val errs = MistakeMaker(MistakeRate(1f, 0f), Random(6))
        repeat(20) { assertEquals(false, errs.double(eval, 1f)) }
        repeat(20) { assertEquals(true, errs.take(eval, 1f)) }
        val blunderer = MistakeMaker(MistakeRate(0f, 1f), Random(7))
        repeat(20) { assertEquals(true, blunderer.double(eval, 1f)) }
        // Passing costs 0.35: more than the largest cube mistake, so it still takes.
        repeat(20) { assertEquals(true, blunderer.take(eval, 1f)) }
        // Passing costs 0.15: a blunder within the limit.
        val closer = eval.copy(doublePass = 0.80f)
        repeat(20) { assertEquals(false, blunderer.take(closer, 1f)) }
    }

    @Test
    fun noAbsurdDoubles() {
        // Far from the window: doubling costs 0.60 (no double -0.50, double/take -1.10).
        val hopeless = CubeEval(
            noDouble = -0.50f, doubleTake = -1.10f, doublePass = 1.0f, optimal = -0.50f, shouldDouble = false,
            shouldTake = true, optional = false, cubeAvailable = true, probs = listOf(0.2f, 0f, 0f, 0.1f, 0f),
        )
        val blunderer = MistakeMaker(MistakeRate(0f, 1f), Random(8))
        repeat(50) { assertEquals(false, blunderer.double(hopeless, 1f)) }
    }

    @Test
    fun opponentKeyRoundTrip() {
        val r = MistakeRate(0.15f, 0.04f)
        val key = Opponent.key(CpuLevel.MISTAKES, r)
        assertEquals(CpuLevel.MISTAKES to r, Opponent.parse(key))
        assertEquals("15% errors, 4% blunders", Opponent.label(key))
        assertEquals(CpuLevel.SUPREMO to null, Opponent.parse("SUPREMO"))
        assertNull(Opponent.parse(Opponent.key(CpuLevel.GRANDMASTER, r)).second)
        assertEquals("Grandmaster", Opponent.label("GRANDMASTER"))
        assertTrue(Opponent.rating(Opponent.key(CpuLevel.MISTAKES, MistakeRate.PR15)) < Opponent.rating(Opponent.key(CpuLevel.MISTAKES, MistakeRate.PR4)))
        val deep = MistakeRate(0.1f, 0.02f, checkerPlies = 2, cubePlies = 4, maxMistake = 0.4f)
        assertEquals(CpuLevel.MISTAKES to deep, Opponent.parse(Opponent.key(CpuLevel.MISTAKES, deep)))
        assertEquals("10% errors, 2% blunders, 2-ply / 4-ply cube, max 0.40", deep.label)
    }

    @Test
    fun everyPresetDescribesItsSettings() {
        // Percent signs in the text must not reach a format string.
        for (p in MistakeRate.presets) assertTrue(p.rate.settingsText, p.rate.settingsText.endsWith("largest 0.25"))
        assertEquals("Checker 1-ply, cube 3-ply · 45% errors, 16% blunders, largest 0.25", MistakeRate.PR15.settingsText)
    }

    @Test
    fun presetsAreSortedByEstimatedPr() {
        val prs = MistakeRate.presets.map { it.rate.estimatedPr }
        assertEquals(prs.sorted(), prs)
        for ((p, target) in MistakeRate.presets.zip(listOf(4.0, 6.0, 8.0, 10.0, 15.0))) assertEquals(p.name, target, p.rate.estimatedPr, 0.6)
    }

    @Test
    fun checkerBlundersStayWithinTheLimit() {
        // Blunders available: 0.10 and 0.40; with a 0.25 limit only the 0.10 one is played.
        val m = MistakeMaker(MistakeRate(0f, 1f, maxMistake = 0.25f), Random(9))
        repeat(50) { assertEquals(moves[4], m.checker(ranked, 1f)) }
        val wide = MistakeMaker(MistakeRate(0f, 1f, maxMistake = 0.5f), Random(10))
        assertTrue((1..50).map { wide.checker(ranked, 1f) }.toSet() == setOf(moves[4], moves[5]))
    }

    @Test
    fun namesAreEngineAndPr() {
        assertEquals("PureTD PR 4", Opponent.name(Opponent.key(CpuLevel.MISTAKES, MistakeRate.PR4)))
        assertEquals("PureTD PR <2", Opponent.name(Opponent.key(CpuLevel.MAXIMUM, null)))
        val custom = MistakeRate(0.1f, 0.02f, checkerPlies = 2, cubePlies = 4, maxMistake = 0.4f)
        assertTrue(Opponent.name(Opponent.key(CpuLevel.MISTAKES, custom)).matches(Regex("PureTD PR \\d+\\.\\d")))
        assertEquals("Ann Smith", com.boldblot.backgammonplyground.ui.review.MatFiles.fileName("  Ann: \"Smith\" "))
        assertEquals("Player", com.boldblot.backgammonplyground.ui.review.MatFiles.fileName("  "))
    }
}
