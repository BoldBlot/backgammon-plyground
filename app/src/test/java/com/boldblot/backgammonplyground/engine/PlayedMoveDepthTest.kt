package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.AnalysisSettings
import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.DiceSource
import com.boldblot.backgammonplyground.core.Dice
import com.boldblot.backgammonplyground.core.GameSearch
import com.boldblot.backgammonplyground.core.MatchController
import com.boldblot.backgammonplyground.core.MoveGenerator
import com.boldblot.backgammonplyground.core.PositionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Analyze Position with a play entered: the play is in the analysis, searched as deeply as the best plays, even one
 * the search would have dropped (as a game's analysis does with the play made).
 */
class PlayedMoveDepthTest {
    private val s = PositionIds.parseXgid("a--BBdB-B---AB-AcbAcBb----:0:0:1:64:2:2:0:11:10")
    private val ctx = CubeContext(s.matchLength, s.score, s.crawford, s.jacoby, s.cube, s.onRoll)

    @Test
    fun `a poor play entered is searched as deeply as the best`() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        e!!
        val dice = s.dice!!
        val moves = MoveGenerator.legalMoves(s.board, s.onRoll, dice.d1, dice.d2)
        // The 1-ply worst play: far outside any search's filter.
        val worst = e.rankMoves(s.board, ctx, dice.d1, dice.d2, Depth(1).code, null).candidates.last().move
        assertTrue(moves.size > 10)
        for (depth in listOf(Depth(3, com.boldblot.backgammonplyground.core.MoveFilter.NARROW).code, GameSearch.G36.code)) {
            val a = MatchController(e, DiceSource.secure(), AnalysisSettings(plies = depth, cubePlies = depth))
                .analyzeChecker(s.board, ctx, Dice(dice.d1, dice.d2), worst, depth)
            assertTrue("the play is in the analysis at $depth", a.playedIndex >= 0)
            val played = a.candidates[a.playedIndex]
            assertEquals(s.board.applyMove(s.onRoll, worst), s.board.applyMove(s.onRoll, played.move))
            assertEquals("searched as deeply as the best at $depth", a.candidates[0].plies, played.plies)
        }
    }

    @Test
    fun `a second play searched apart joins at the best's depth`() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        e!!
        val dice = s.dice!!
        val ranked = e.rankMoves(s.board, ctx, dice.d1, dice.d2, Depth(1).code, null).candidates
        val (first, second) = ranked.last().move to ranked[ranked.size - 2].move
        val depth = Depth(3, com.boldblot.backgammonplyground.core.MoveFilter.NARROW).code
        val c = MatchController(e, DiceSource.secure(), AnalysisSettings(plies = depth, cubePlies = depth))
        var a = c.analyzeChecker(s.board, ctx, Dice(dice.d1, dice.d2), first, depth)
        val other = c.analyzeChecker(s.board, ctx, Dice(dice.d1, dice.d2), second, depth)
        a = a.withCandidate(s.board, s.onRoll, other.candidates[other.playedIndex])
        for (m in listOf(first, second)) {
            val target = s.board.applyMove(s.onRoll, m)
            val cand = a.candidates.first { s.board.applyMove(s.onRoll, it.move) == target }
            assertEquals(a.candidates[0].plies, cand.plies)
        }
        assertEquals(s.board.applyMove(s.onRoll, first), s.board.applyMove(s.onRoll, a.candidates[a.playedIndex].move))
    }
}
