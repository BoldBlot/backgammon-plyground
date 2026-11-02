package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Analysis labels name the engine, and say so when an older analysis did not record it. */
class AnalysisLabelTest {
    private val probs = listOf(0.6f, 0.2f, 0.01f, 0.1f, 0.005f)
    private val pruned = Depth(3, MoveFilter.entries.first { it != MoveFilter.ALL && it != MoveFilter.WIDE }).code

    private fun checker(engine: String?) = CheckerAnalysis(emptyList(), -1, 0f, 0f, 0f, legalMoves = 0, plies = pruned, engine = engine)
    private fun cube(engine: String?, plies: Int?) =
        CubeEval(0.5f, 0.6f, 1f, 0.6f, shouldDouble = true, shouldTake = true, optional = false, cubeAvailable = true, probs = probs, engine = engine, plies = plies)

    @Test fun recordedEngineIsNamed() {
        assertEquals("PureTD ${plyLabel(pruned)}", checker("PureTD").label)
        assertEquals("PureTD 4-ply", cube("PureTD", Depth(4).code).label)
    }

    @Test fun missingEngineSaysUnknown() {
        assertEquals("Engine unknown, ${plyLabel(pruned)}", checker(null).label)
        assertEquals("Engine unknown", cube(null, null).label)
        assertEquals("Engine unknown, 4-ply", cube(null, Depth(4).code).label)
    }

    /** A position saved before 0.7.11: the engine is only in the position's label. */
    @Test fun oldSavedCheckerPlayTakesTheEngineFromItsLabel() {
        val a = PositionAnalysis(checker = checker(null), label = "PureTD 3-ply").withRecordedEngine()
        assertEquals("PureTD", a.checker!!.engine)
        assertEquals("PureTD ${plyLabel(pruned)}", a.checker?.label)
    }

    @Test fun oldSavedCubeDecisionTakesEngineAndDepthFromItsLabel() {
        val a = PositionAnalysis(cube = cube(null, null), label = "GNU BG 4-ply").withRecordedEngine()
        assertEquals("GNU BG 4-ply", a.cube!!.label)
    }

    @Test fun positionSavedWithoutAnyLabelSaysUnknown() {
        val a = PositionAnalysis(cube = cube(null, null)).withRecordedEngine()
        assertEquals("Engine unknown", a.cube!!.label)
        assertEquals("Engine unknown", a.label)
    }
}
