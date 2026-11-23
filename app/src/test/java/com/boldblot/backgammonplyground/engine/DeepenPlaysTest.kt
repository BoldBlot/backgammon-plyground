package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.Dice
import com.boldblot.backgammonplyground.core.EngineKind
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.PositionSetup
import com.boldblot.backgammonplyground.core.StudyPosition
import com.boldblot.backgammonplyground.ui.positions.deepenPlays
import com.boldblot.backgammonplyground.ui.positions.positionAnalysis
import com.boldblot.backgammonplyground.ui.positions.searchedDeeply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Deep dive: a play followed after the run (in the analysis from the first pass only, or not in it) is searched as
 * deeply as the best, and the rest of the analysis is kept.
 */
class DeepenPlaysTest {
    private val p = run {
        val me = IntArray(25).also { it[23] = 1; it[15] = 1; it[12] = 3; it[7] = 3; it[6] = 2; it[5] = 5 }
        val opp = IntArray(25).also { it[24 - 17] = 4; it[24 - 19] = 5; it[24 - 12] = 4; it[24 - 3] = 2 }
        StudyPosition(
            PositionSetup(Board.fromSides(me, opp), Player.HUMAN, dice = Dice(6, 4), matchLength = 7, score = listOf(2, 0), format = "XGID"),
            doubled = false, beavers = false,
        )
    }

    @Test
    fun `plays followed later are deepened, the rest kept`() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        e!!
        val depth = Depth(3, MoveFilter.NARROW)
        val first = positionAnalysis(p, EngineKind.PURETD, depth, depth, emptyList())(e)
        val c = first.checker!!
        // A play of the first pass only, and the 1-ply worst play (not in the analysis at all).
        val shallow = c.candidates.firstOrNull { it.plies != c.candidates[0].plies }?.move
        val worst = e.rankMoves(p.board, p.ctx(), 6, 4, Depth(1).code, null).candidates.last().move
        val moves = listOfNotNull(shallow, worst)
        assertTrue(moves.any { !searchedDeeply(p, c, it) })
        val deeper = deepenPlays(p, first, depth, depth, moves)(e)
        for (m in moves) assertTrue("$m searched deeply", searchedDeeply(p, deeper.checker!!, m))
        assertEquals(c.candidates[0].move, deeper.checker!!.candidates[0].move)
        assertEquals(first.label, deeper.label)
    }
}
