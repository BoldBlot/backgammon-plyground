package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The game quiz: which decisions of a saved match it stops at, which stay, and in what order. */
class GameQuizTest {
    private val move = Move(listOf(Step(5, 3)))
    private val other = Move(listOf(Step(7, 5)))

    /** A counted checker decision by [player]: the best play, the next one [gap] behind, and the game's play costing [error]. */
    private fun checker(player: Int, error: Float, gap: Float, counted: Boolean = true) = DecisionRecord(
        player = player, kind = DecisionKind.CHECKER, board = Board.INITIAL, cube = CubeState(), score = listOf(0, 0), crawford = false,
        dice = Dice(2, 1), move = move,
        checker = CheckerAnalysis(
            listOf(Candidate(move, 0.5f, emptyList(), 3), Candidate(other, 0.5f - gap, emptyList(), 3)),
            playedIndex = 0, playedEquity = 0.5f - error, bestEquity = 0.5f, worstEquity = 0f, legalMoves = 2, plies = 3,
        ),
        error = error, counted = counted,
    )

    /** A counted doubling decision: No double is [gap] better than doubling, and the game's action cost [error]. */
    private fun cube(player: Int, error: Float, gap: Float) = DecisionRecord(
        player = player, kind = DecisionKind.NO_DOUBLE, board = Board.INITIAL, cube = CubeState(), score = listOf(0, 0), crawford = false,
        cubeEval = CubeEval(0.4f, 0.4f - gap, 1f, 0.4f, shouldDouble = false, shouldTake = true, optional = false, cubeAvailable = true, probs = emptyList()),
        error = error, counted = true,
    )

    private val games = listOf(
        listOf(checker(Player.HUMAN, 0.10f, 0.20f), checker(Player.CPU, 0f, 0.01f), checker(Player.HUMAN, 0f, 0.30f, counted = false)),
        listOf(cube(Player.HUMAN, 0.03f, 0.03f), checker(Player.CPU, 0.05f, 0.015f), checker(Player.HUMAN, 0f, 0.50f)),
    )
    private val all = GameQuiz.decisions(games, matchLength = 7)

    private fun ids(ps: List<GameQuizPick>) = ps.map { it.game to it.index }

    @Test
    fun onlyCountedAnalyzedDecisionsAreOffered() {
        assertEquals(listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1, 1 to 2), ids(all))
        assertEquals(0.03f, all[2].gap!!, 1e-6f)
    }

    @Test
    fun errorsInTheGameGoByTheLevel() {
        val errors = GameQuizRules(stops = setOf(GameQuizStop.WRONG), wrongLevel = GameQuizLevel.ERROR, limit = 0)
        assertEquals(listOf(0 to 0, 1 to 0, 1 to 1), ids(GameQuiz.pick(all, errors)))
        assertEquals(listOf(0 to 0), ids(GameQuiz.pick(all, errors.copy(wrongLevel = GameQuizLevel.BLUNDER))))
        assertEquals(listOf(0 to 0, 1 to 1), ids(GameQuiz.pick(all, errors.copy(wrongLevel = GameQuizLevel.CUSTOM, wrongCustom = 0.04f))))
    }

    @Test
    fun closeDecisionsGoByTheGapAndStopsAddUp() {
        val close = GameQuizRules(stops = setOf(GameQuizStop.CLOSE), closeLevel = GameQuizLevel.ERROR, limit = 0)
        assertEquals(listOf(0 to 1, 1 to 1), ids(GameQuiz.pick(all, close)))
        assertEquals(listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1), ids(GameQuiz.pick(all, close.copy(stops = setOf(GameQuizStop.CLOSE, GameQuizStop.WRONG)))))
        assertEquals(5, GameQuiz.pick(all, close.copy(stops = setOf(GameQuizStop.ANY))).size)
        assertTrue(GameQuiz.pick(all, close.copy(stops = emptySet())).isEmpty())
    }

    @Test
    fun sideAndCubeFilter() {
        val any = GameQuizRules(stops = setOf(GameQuizStop.ANY), limit = 0)
        assertEquals(listOf(0 to 0, 1 to 0, 1 to 2), ids(GameQuiz.pick(all, any.copy(side = GameQuizSide.MINE))))
        assertEquals(listOf(0 to 1, 1 to 1), ids(GameQuiz.pick(all, any.copy(side = GameQuizSide.THEIRS))))
        assertEquals(listOf(0 to 0, 0 to 1, 1 to 1, 1 to 2), ids(GameQuiz.pick(all, any.copy(cube = false))))
    }

    @Test
    fun thePriorityKeepsAndTheOrderArranges() {
        val any = GameQuizRules(stops = setOf(GameQuizStop.ANY), limit = 2)
        // The two biggest errors, asked as in the game.
        assertEquals(listOf(0 to 0, 1 to 1), ids(GameQuiz.pick(all, any.copy(priority = GameQuizPriority.BIGGEST))))
        assertEquals(listOf(0 to 0, 0 to 1), ids(GameQuiz.pick(all, any.copy(priority = GameQuizPriority.FIRST))))
        assertEquals(listOf(1 to 1, 1 to 2), ids(GameQuiz.pick(all, any.copy(priority = GameQuizPriority.LAST))))
        val random = GameQuiz.pick(all, any.copy(priority = GameQuizPriority.BIGGEST, order = GameQuizOrder.RANDOM), kotlin.random.Random(1))
        assertEquals(setOf(0 to 0, 1 to 1), ids(random).toSet())
        assertEquals(2, GameQuiz.pick(all, any.copy(priority = GameQuizPriority.RANDOM), kotlin.random.Random(1)).size)
    }
}
