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

    @Test fun theOlderChoicesStillJudgeAsBefore() {
        assertTrue(Settings(quizCorrect = QuizCorrect.NOT_ERROR).quizRight(0.02f, null))
        assertFalse(Settings(quizCorrect = QuizCorrect.NOT_ERROR).quizRight(0.021f, null))
        assertTrue(Settings(quizCorrect = QuizCorrect.BEST_ONLY).quizRight(0.0004f, null))
        assertFalse(Settings(quizCorrect = QuizCorrect.BEST_ONLY).quizRight(0.001f, null))
    }
}
