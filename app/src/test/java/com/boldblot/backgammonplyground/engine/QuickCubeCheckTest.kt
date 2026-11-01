package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.*
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The quick cube check ([AnalysisSettings.quickCubePlies]) stands in for the deep analysis only when doubling is a blunder by it. */
class QuickCubeCheckTest {
    private val quick = Depth(2).code
    private val deep = Depth(4).code

    private fun state(board: Board, cube: CubeState) = MatchState(
        rules = MatchRules(length = 5), cpuLevel = CpuLevel.EXPERT,
        game = GameState(board = board, turn = Player.HUMAN, phase = Phase.PRE_ROLL, cube = cube),
    )

    @Test fun aHopelessPositionKeepsTheQuickCheck() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        // The human far behind (all 15 on their 24-point), the computer about to bear off its last two checkers.
        val human = IntArray(25).also { it[23] = 15 }
        val cpu = IntArray(25).also { it[0] = 2 }
        val m = state(Board.fromSides(human, cpu), CubeState(2, Player.HUMAN))
        val c = MatchController(e!!, DiceSource.secure(), AnalysisSettings(plies = Depth(3).code, cubePlies = deep, quickCubePlies = quick))
        assertEquals(e.cubeDecision(m.game.board, m.ctx(), quick), c.rollCubeEval(m))
    }

    @Test fun theQuickCheckDecidesExactlyByTheBlunderLine() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        val c = MatchController(e!!, DiceSource.secure(), AnalysisSettings(plies = Depth(3).code, cubePlies = Depth(3).code, quickCubePlies = quick))
        // Race positions from even to lopsided (pips 60-90 against 70), and the opening position.
        val boards = listOf(Board.INITIAL) + listOf(4, 5, 6, 7).map { k ->
            val human = IntArray(25).also { it[k] = 10; it[1] = 5 }
            val cpu = IntArray(25).also { it[5] = 10; it[2] = 5 }
            Board.fromSides(human, cpu)
        }
        for (b in boards) {
            val m = state(b, CubeState())
            val q = e.cubeDecision(b, m.ctx(), quick)
            val after = DecisionMath.afterDouble(q, false)
            val hopeless = q.noDouble <= XgRules.VERY_NEGATIVE && after <= XgRules.VERY_NEGATIVE && after <= q.noDouble + XgRules.OPTIONAL_EPSILON
            val blunder = q.cubeAvailable && (DecisionMath.doubleError(q, doubled = true) * XgRules.errorFactor(5) > XgRules.BLUNDER_THRESHOLD || hopeless)
            val expected = if (blunder) q else e.cubeDecision(b, m.ctx(), Depth(3).code)
            assertEquals(expected, c.rollCubeEval(m))
        }
    }

    @Test fun noQuickCheckWhenItIsNotShallower() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        val human = IntArray(25).also { it[23] = 15 }
        val cpu = IntArray(25).also { it[0] = 2 }
        val m = state(Board.fromSides(human, cpu), CubeState(2, Player.HUMAN))
        val c = MatchController(e!!, DiceSource.secure(), AnalysisSettings(plies = Depth(2).code, cubePlies = Depth(2).code, quickCubePlies = quick))
        assertEquals(e.cubeDecision(m.game.board, m.ctx(), Depth(2).code), c.rollCubeEval(m))
    }
}
