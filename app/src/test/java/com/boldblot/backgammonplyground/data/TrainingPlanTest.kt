package com.boldblot.backgammonplyground.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The training plan and the quiz's acceptable error (feedback 2026-11-05). */
class TrainingPlanTest {
    @Test fun aPlanSetsBothLevels() {
        for ((plan, level) in listOf(TrainingPlan.ERROR to 0.02f, TrainingPlan.BLUNDER to 0.08f, TrainingPlan.BIG_BLUNDER to 0.12f)) {
            val s = Settings().withPlan(plan)
            assertEquals(plan, s.trainingPlan)
            assertEquals(level, s.errorMin, 1e-6f)
            assertEquals(QuizCorrect.FIXED, s.quizCorrect)
            assertTrue(s.quizRight(level, null))
            assertFalse(s.quizRight(level + 0.001f, null))
        }
    }

    @Test fun customChangesNothingAndIsTheDefault() {
        val s = Settings(errorMin = 0.05f, quizCorrect = QuizCorrect.BEST_ONLY, errorsMode = ErrorsMode.TOP_X)
        assertEquals(TrainingPlan.CUSTOM, s.trainingPlan)
        assertEquals(s, s.withPlan(TrainingPlan.CUSTOM))
    }

    @Test fun aPlanKeepsManualAndEndsTheMethodsThatTreatTheLevelAsAFloor() {
        assertEquals(ErrorsMode.MANUAL, Settings(errorsMode = ErrorsMode.MANUAL).withPlan(TrainingPlan.BLUNDER).errorsMode)
        assertEquals(ErrorsMode.AUTOMATIC, Settings(errorsMode = ErrorsMode.AUTOMATIC).withPlan(TrainingPlan.BLUNDER).errorsMode)
        assertEquals(ErrorsMode.AUTOMATIC, Settings(errorsMode = ErrorsMode.ADAPTIVE).withPlan(TrainingPlan.BLUNDER).errorsMode)
        assertEquals(ErrorsMode.AUTOMATIC, Settings(errorsMode = ErrorsMode.TOP_X).withPlan(TrainingPlan.BLUNDER).errorsMode)
    }

    @Test fun adaptiveFollowsTheAverageButNeverGoesBelowTheErrorLine() {
        val s = Settings(quizCorrect = QuizCorrect.ADAPTIVE, quizAdaptiveRatio = 0.9f)
        assertTrue(s.quizRight(0.089f, 0.10f))
        assertFalse(s.quizRight(0.091f, 0.10f))
        assertTrue(s.quizRight(0.02f, 0.01f))
        assertFalse(s.quizRight(0.021f, 0.01f))
        assertTrue(s.quizRight(0.02f, null))
    }

    @Test fun byDefaultAnErrorIsNeutralAndOnlyABlunderIsMissed() {
        val s = Settings()
        assertEquals(QuizCorrect.NOT_ERROR, s.quizCorrect)
        assertEquals(QuizNeutral.NOT_BLUNDER, s.quizNeutral)
        assertEquals(QuizVerdict.RIGHT, s.quizVerdict(0f, null))
        assertEquals(QuizVerdict.RIGHT, s.quizVerdict(0.02f, null))
        assertEquals(QuizVerdict.NEUTRAL, s.quizVerdict(0.021f, null))
        assertEquals(QuizVerdict.NEUTRAL, s.quizVerdict(0.08f, null))
        assertEquals(QuizVerdict.WRONG, s.quizVerdict(0.081f, null))
    }

    @Test fun theNeutralLevels() {
        val best = Settings(quizCorrect = QuizCorrect.BEST_ONLY, quizNeutral = QuizNeutral.NOT_ERROR)
        assertEquals(QuizVerdict.RIGHT, best.quizVerdict(0f, null))
        assertEquals(QuizVerdict.NEUTRAL, best.quizVerdict(0.015f, null))
        assertEquals(QuizVerdict.WRONG, best.quizVerdict(0.03f, null))
        val fixed = Settings(quizCorrect = QuizCorrect.FIXED, quizFixedError = 0.04f, quizNeutral = QuizNeutral.FIXED, quizNeutralFixedError = 0.1f)
        assertEquals(QuizVerdict.RIGHT, fixed.quizVerdict(0.04f, null))
        assertEquals(QuizVerdict.NEUTRAL, fixed.quizVerdict(0.1f, null))
        assertEquals(QuizVerdict.WRONG, fixed.quizVerdict(0.101f, null))
    }

    @Test fun adaptiveIsRightToAFifthOfTheAverageAndNeutralToHalf() {
        val s = Settings(quizCorrect = QuizCorrect.ADAPTIVE, quizNeutral = QuizNeutral.ADAPTIVE)
        assertEquals(0.2f, s.quizAdaptiveRatio, 0f)
        assertEquals(0.5f, s.quizNeutralAdaptiveRatio, 0f)
        assertTrue(s.quizNeedsAverage)
        assertEquals(QuizVerdict.RIGHT, s.quizVerdict(0.039f, 0.2f))
        assertEquals(QuizVerdict.NEUTRAL, s.quizVerdict(0.041f, 0.2f))
        assertEquals(QuizVerdict.NEUTRAL, s.quizVerdict(0.099f, 0.2f))
        assertEquals(QuizVerdict.WRONG, s.quizVerdict(0.101f, 0.2f))
        // Neither level goes below the error line.
        assertEquals(QuizVerdict.RIGHT, s.quizVerdict(0.02f, 0.01f))
        assertEquals(QuizVerdict.WRONG, s.quizVerdict(0.021f, 0.01f))
    }

    @Test fun aNeutralLevelAtOrBelowTheRightLevelLeavesNoNeutralAnswers() {
        val s = Settings(quizCorrect = QuizCorrect.FIXED, quizFixedError = 0.1f, quizNeutral = QuizNeutral.NOT_BLUNDER)
        assertEquals(QuizVerdict.RIGHT, s.quizVerdict(0.1f, null))
        assertEquals(QuizVerdict.WRONG, s.quizVerdict(0.101f, null))
        val same = Settings(quizCorrect = QuizCorrect.NOT_ERROR, quizNeutral = QuizNeutral.NOT_ERROR)
        assertEquals(QuizVerdict.WRONG, same.quizVerdict(0.021f, null))
    }

    @Test fun aPlanSetsTheRightLevelAndLeavesTheNeutralOne() {
        val s = Settings(quizNeutral = QuizNeutral.FIXED, quizNeutralFixedError = 0.12f).withPlan(TrainingPlan.BLUNDER)
        assertEquals(QuizNeutral.FIXED, s.quizNeutral)
        assertEquals(0.12f, s.quizNeutralFixedError, 0f)
    }

    @Test fun theChoicesRunFromTheStrictestToTheMostLax() {
        assertEquals(listOf("Best play only", "Not an error", "Fixed level", "Adaptive"), QuizCorrect.entries.map { it.label })
        assertEquals(listOf("Not an error", "Not a blunder", "Fixed level", "Adaptive"), QuizNeutral.entries.map { it.label })
    }

    @Test fun aNeutralAnswerLeavesTheBalanceAlone() {
        val e = PositionEntity(xgid = "x", savedAt = 0, quizRight = 3, quizWrong = 1)
        val n = e.withQuizCount(QuizVerdict.NEUTRAL, 1)
        assertEquals(listOf(3, 1, 1), listOf(n.quizRight, n.quizWrong, n.quizNeutral))
        // Analyzed again, the neutral answer turns out missed: one count moves over.
        val fixed = n.withQuizCount(QuizVerdict.NEUTRAL, -1).withQuizCount(QuizVerdict.WRONG, 1)
        assertEquals(listOf(3, 2, 0), listOf(fixed.quizRight, fixed.quizWrong, fixed.quizNeutral))
    }

    @Test fun theOlderChoicesStillJudgeAsBefore() {
        assertTrue(Settings(quizCorrect = QuizCorrect.NOT_ERROR).quizRight(0.02f, null))
        assertFalse(Settings(quizCorrect = QuizCorrect.NOT_ERROR).quizRight(0.021f, null))
        assertTrue(Settings(quizCorrect = QuizCorrect.BEST_ONLY).quizRight(0.0004f, null))
        assertFalse(Settings(quizCorrect = QuizCorrect.BEST_ONLY).quizRight(0.001f, null))
    }
}
