package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The play screen's bar is the human's view of whoever is on roll, and it takes a roll into account. */
class WinningChancesTest {
    private fun sides(vararg points: Pair<Int, Int>) = IntArray(25).also { a -> points.forEach { (p, n) -> a[p - 1] = n } }

    /** 3-3 in a 5-point match: the computer on roll and doubling, you on the bar (feedback 2026-11-05). */
    private val doubled = Board.fromSides(
        sides(25 to 1, 23 to 1, 21 to 1, 13 to 3, 7 to 2, 6 to 3, 5 to 2, 3 to 2),
        sides(23 to 1, 21 to 3, 13 to 3, 9 to 1, 8 to 1, 6 to 4, 5 to 2),
    )

    /** A bearoff with the computer on roll at 12 pips against your 15: it doubled to 4. */
    private val bearoff = Board.fromSides(
        sides(1 to 4, 2 to 1, 3 to 3),
        sides(3 to 2, 2 to 1, 1 to 4),
    )

    private fun match(board: Board, turn: Int, phase: Phase, dice: Dice? = null, score: List<Int> = listOf(3, 3), cube: CubeState = CubeState()) = MatchState(
        rules = MatchRules(length = 5), cpuLevel = CpuLevel.EXPERT, score = score,
        game = GameState(board = board, turn = turn, phase = phase, dice = dice, cube = cube),
    )

    @Test fun theComputerOnRollIsEvaluatedForTheComputer() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        val m = match(doubled, Player.CPU, Phase.DOUBLED)
        val review = e!!.cubeDecision(doubled, m.ctx(Player.CPU), 0).probs
        val bar = WinningChances.forHuman(e, m)
        assertEquals(1 - review[0], bar[0], 0.0001f)
        assertEquals(review[3], bar[1], 0.0001f)
        assertEquals(review[4], bar[2], 0.0001f)
        assertEquals(review[1], bar[3], 0.0001f)
        assertEquals(review[2], bar[4], 0.0001f)
        // What the review showed for this position: You 47.7%, Computer 52.3%.
        assertEquals(0.477f, bar[0], 0.01f)
    }

    @Test fun aComputerAheadInTheBearoffIsTheFavourite() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        val bar = WinningChances.forHuman(e!!, match(bearoff, Player.CPU, Phase.DOUBLED, score = listOf(2, 0), cube = CubeState(2, Player.CPU)))
        assertTrue("you have ${bar[0]}", bar[0] < 0.5f)
    }

    @Test fun theHumanOnRollIsUnchanged() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        val m = match(doubled, Player.HUMAN, Phase.PRE_ROLL)
        assertEquals(e!!.cubeDecision(doubled, m.ctx(Player.HUMAN), 0).probs, WinningChances.forHuman(e, m))
    }

    @Test fun aRollCountsWithItsBestPlay() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        val dice = Dice(6, 5)
        val cpu = match(doubled, Player.CPU, Phase.MOVING, dice)
        val best = e!!.rankMoves(doubled, cpu.ctx(Player.CPU), 6, 5, 0, null).candidates.first().probs
        assertEquals(1 - best[0], WinningChances.forHuman(e, cpu)[0], 0.0001f)
        val race = Board.fromSides(sides(6 to 8, 5 to 7), sides(6 to 8, 5 to 7))
        val human = match(race, Player.HUMAN, Phase.MOVING, Dice(6, 6))
        val bestHuman = e.rankMoves(race, human.ctx(Player.HUMAN), 6, 6, 0, null).candidates.first().probs
        assertEquals(bestHuman[0], WinningChances.forHuman(e, human)[0], 0.0001f)
    }

    @Test fun noLegalMoveLeavesTheOtherPlayerOnRoll() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        // You are on the bar against a closed board.
        val closed = Board.fromSides(sides(25 to 1, 6 to 14), sides(6 to 2, 5 to 2, 4 to 2, 3 to 2, 2 to 2, 1 to 2, 13 to 3))
        val m = match(closed, Player.HUMAN, Phase.MOVING, Dice(6, 5))
        val next = e!!.cubeDecision(closed, m.ctx(Player.CPU), 0).probs
        assertEquals(1 - next[0], WinningChances.forHuman(e, m)[0], 0.0001f)
    }
}
