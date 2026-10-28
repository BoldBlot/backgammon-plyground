package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.PositionIds
import com.boldblot.backgammonplyground.core.Step
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The pruned checker search keeps the best few moves however far behind the window they are. */
class PruningTest {
    @Test
    fun keepsAMoveJustOutsideTheWindow() {
        assumeTrue(TestEngine.engine != null)
        // FlashBack Boot Camp, 6-2: PureTD rates 16/10 5/3 0.164 behind 16/10 16/14 at 1-ply, so a
        // 0.16 window alone dropped it; searched, it is the best play by 0.29 at 3-ply.
        val s = PositionIds.parseXgid("XGID=-BBBBCBb-----b--B--cbbbb--:0:0:1:62:0:0:3:0:10")
        val ctx = CubeContext(s.matchLength, s.score, s.crawford, s.jacoby, s.cube, s.onRoll)
        val want = s.board.applyStep(s.onRoll, Step(15, 9)).applyStep(s.onRoll, Step(4, 2))
        val r = TestEngine.engine!!.rankMoves(s.board, ctx, 6, 2, Depth(3, MoveFilter.WIDE).code, null)
        assertEquals(want, s.board.applyMove(s.onRoll, r.candidates[0].move))
    }
}
