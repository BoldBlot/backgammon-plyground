package com.boldblot.backgammonplyground.ui.positions

import com.boldblot.backgammonplyground.core.Candidate
import com.boldblot.backgammonplyground.core.CheckerAnalysis
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.EngineKind
import com.boldblot.backgammonplyground.core.Move
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.PositionAnalysis
import com.boldblot.backgammonplyground.core.StudyPosition
import com.boldblot.backgammonplyground.data.PositionsRepository
import com.boldblot.backgammonplyground.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BelowStandardTest {
    // A money position with a roll: the standard is the plies of Settings → Analysis (games are for matches).
    private val p = StudyPosition.parse("XGID=-b----E-C---eE---c-e----B-:0:0:1:21:0:0:0:0:10")

    private fun analysis(plies: Int, engine: String? = "PureTD", level: String? = null) = PositionAnalysis(
        checker = CheckerAnalysis(
            candidates = listOf(Candidate(Move(emptyList()), 0.1f, emptyList(), plies), Candidate(Move(emptyList()), 0.0f, emptyList(), plies)),
            playedIndex = 0, playedEquity = 0.1f, bestEquity = 0.1f, worstEquity = 0f, legalMoves = 2, plies = plies, engine = engine, level = level,
        ),
        label = "x",
    )

    private val settings = Settings(analysisEngine = EngineKind.PURETD, checkerPlies = 3, moveFilter = MoveFilter.WIDE)

    @Test
    fun shallowerSearchByTheSameEngineIsBelow() {
        assertTrue(belowStandard(p, analysis(Depth(1).code), settings))
        assertEquals(false, belowStandard(p, analysis(Depth(3, MoveFilter.WIDE).code), settings))
        assertEquals(false, belowStandard(p, analysis(Depth(4, MoveFilter.WIDE).code), settings))
    }

    @Test
    fun anotherEnginesAnalysisIsLeftAlone() {
        assertEquals(false, belowStandard(p, analysis(Depth(1).code, engine = "GNU BG"), settings))
    }

    @Test
    fun anAnalysisFromAnotherProgramIsKeptUnlessTheSettingSaysNot() {
        val xg = analysis(Depth(1).code, engine = "eXtreme Gammon", level = "XG Roller++")
        assertEquals(false, belowStandard(p, xg, settings))
        assertTrue(belowStandard(p, xg, settings.copy(quizKeepForeign = false)))
    }

    @Test
    fun anAnalysisStoppedEarlyIsBelow() {
        assertTrue(belowStandard(p, analysis(Depth(3, MoveFilter.WIDE, incomplete = true).code), settings))
    }

    @Test
    fun theQuizCanHaveItsOwnStandard() {
        // An 11-point match position analyzed at 3-ply (games analyzed at 3-ply), every decision played out in the rule.
        val match = StudyPosition.parse("XGID=-b----E-C---eE---c-e----B-:0:0:1:21:0:0:0:11:10")
        val plain = analysis(Depth(3, MoveFilter.WIDE).code)
        val s = settings.copy(gamesOnErrorsOnly = false)
        assertEquals(false, belowStandard(match, plain, s))
        // The quiz's own match depth asks for the played-out games: 3-ply is below it.
        val own = s.copy(quizOwnDepths = true, quizMatchChecker = com.boldblot.backgammonplyground.core.GameSearch.G36.code)
        assertTrue(belowStandard(match, plain, own))
        assertEquals(com.boldblot.backgammonplyground.core.GameSearch.G36.code, own.quizCheckerDepth(true).code)
        // Money games keep the analysis depths when the quiz sets none for them; the analysis settings are untouched.
        assertEquals(false, belowStandard(p, plain, own))
        assertEquals(s.checkerDepth(true), own.checkerDepth(true))
        // Switched off, the quiz follows Settings → Analysis again.
        assertEquals(false, belowStandard(match, plain, own.copy(quizOwnDepths = false)))
    }

    @Test
    fun exportedAnalysisNamesTheLevelOfAGameAnalysis() {
        val game = analysis(Depth(3, MoveFilter.WIDE).code).copy(label = PositionsRepository.GAME_ANALYSIS)
        assertEquals("PureTD 3-ply, pruned", PositionsRepository.exportedAnalysis(game).label)
        // Any other label is kept as it is.
        assertEquals("x", PositionsRepository.exportedAnalysis(analysis(3)).label)
    }
}
