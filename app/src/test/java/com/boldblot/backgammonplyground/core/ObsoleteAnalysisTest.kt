package com.boldblot.backgammonplyground.core

import com.boldblot.backgammonplyground.data.AppJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObsoleteAnalysisTest {
    private val checker = CheckerAnalysis(emptyList(), 0, 0f, 0f, 0f, 1, plies = Depth(3).code, engine = "PureTD")
    private val cube = CubeEval(0f, 0f, 1f, 0f, false, true, false, true, listOf(0.5f, 0f, 0f, 0f, 0f), engine = "PureTD", plies = Depth(4).code)

    @Test fun markingAddsTheTagAndSurvivesSaving() {
        val a = PositionAnalysis(checker = checker, label = "PureTD 3-ply")
        assertFalse(a.obsolete)
        val m = a.markedObsolete()!!
        assertTrue(m.obsolete)
        assertEquals("PureTD 3-ply (obsolete)", m.shownLabel)
        assertTrue(m.checker!!.label.endsWith(OBSOLETE))
        val back = AppJson.decodeFromString<PositionAnalysis>(AppJson.encodeToString(m))
        assertTrue(back.obsolete)
        // Marking again changes nothing.
        assertNull(m.markedObsolete())
    }

    @Test fun cubeEvaluationsAreMarkedToo() {
        val m = PositionAnalysis(cube = cube).markedObsolete()!!
        assertEquals("PureTD 4-ply (obsolete)", m.shownLabel)
    }

    @Test fun analysesFromOtherProgramsStay() {
        val xg = checker.copy(engine = "eXtreme Gammon", level = "XG Roller++")
        assertNull(PositionAnalysis(checker = xg).markedObsolete())
    }

    @Test fun oldAnalysesReadAsCurrentAndFreshOnesDoNotStoreTheFlag() {
        val json = AppJson.encodeToString(checker)
        assertFalse("obsolete" in json)
        assertFalse(AppJson.decodeFromString<CheckerAnalysis>(json).obsolete)
    }
}
