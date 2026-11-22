package com.boldblot.backgammonplyground.core

import com.boldblot.backgammonplyground.core.DeepDive.Change
import com.boldblot.backgammonplyground.core.DeepDive.Slot
import com.boldblot.backgammonplyground.core.DeepDive.Tracked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepDiveTest {

    /** You (bottom): 24, 16, 13×3, 8×3, 7×2, 6×5; opponent (your numbering): 17×4, 19×5, 12×4, 3×2; 64 to play. */
    private fun base(matchLength: Int = 7, score: List<Int> = listOf(2, 0), dice: Dice? = Dice(6, 4), doubled: Boolean = false, crawford: Boolean = false): StudyPosition {
        val me = IntArray(25).also { it[23] = 1; it[15] = 1; it[12] = 3; it[7] = 3; it[6] = 2; it[5] = 5 }
        val opp = IntArray(25).also { it[24 - 17] = 4; it[24 - 19] = 5; it[24 - 12] = 4; it[24 - 3] = 2 }
        val setup = PositionSetup(
            board = Board.fromSides(me, opp), onRoll = if (doubled) Player.CPU else Player.HUMAN, dice = dice,
            matchLength = matchLength, score = score, crawford = crawford, format = "XGID",
        )
        return StudyPosition(setup, doubled = doubled, beavers = false)
    }

    private val move2414 = Move(listOf(Step(23, 17), Step(17, 13)))

    @Test
    fun oneCheckerVersionsGoBackToFrontAndSkipHeldPoints() {
        val b = base()
        // The blot on 16 tried on 18, 16, 15, 12 (the opponent's point: skipped) and 9.
        val v = DeepDive.versions(b, Change.Checkers(listOf(Slot(Player.HUMAN, 15, listOf(15, 17, 14, 11, 8)))))
        assertEquals(listOf("18", "16", "15", "9"), v.map { it.short })
        assertEquals(listOf(false, true, false, false), v.map { it.isBase })
        assertEquals(listOf(17, 15, 14, 8), v.map { it.spots.single() })
        val moved = v[3].position.board
        assertEquals(0, moved.count(Player.HUMAN, 15))
        assertEquals(1, moved.count(Player.HUMAN, 8))
        assertEquals(15, moved.onBoard(Player.HUMAN))
    }

    @Test
    fun opponentsCheckerIsShownInYourNumbering() {
        val b = base()
        // One of the opponent's checkers on your 3 point (their 22) tried on your 3 and 5.
        val v = DeepDive.versions(b, Change.Checkers(listOf(Slot(Player.CPU, 21, listOf(21, 19)))))
        assertEquals(listOf("3", "5"), v.map { it.short })
        assertEquals("Opponent's checker on 5", v[1].label)
        assertEquals(1, v[1].position.board.count(Player.CPU, 19))
    }

    @Test
    fun severalCheckersMakeEveryCombinationThatFits() {
        val b = base()
        val slots = listOf(Slot(Player.HUMAN, 12, listOf(12, 10, 9)), Slot(Player.CPU, 21, listOf(21, 19)))
        val v = DeepDive.versions(b, Change.Checkers(slots))
        assertEquals(6, v.size)
        assertEquals("A 13 · B 3", v.first { it.isBase }.label)
        // Your checker on 5 and the opponent's on your 5 cannot both be: a slot target taken by the other side is skipped.
        val clash = DeepDive.versions(b, Change.Checkers(listOf(Slot(Player.HUMAN, 12, listOf(12, 4)), Slot(Player.CPU, 21, listOf(21, 19)))))
        assertEquals(3, clash.size)
    }

    @Test
    fun scoreVersionsRunDownToCrawfordAndPostCrawfordThenMoney() {
        val v = DeepDive.versions(base(), Change.Score(Player.CPU))
        assertEquals(listOf("7a", "6a", "5a", "4a", "3a", "2a", "1a Crawford", "1a post", "$"), v.map { it.short })
        assertTrue(v[0].isBase)
        assertTrue(v[6].position.setup.crawford)
        assertFalse(v[7].position.setup.crawford)
        assertEquals(listOf(2, 6), v[6].position.setup.score)
        assertEquals(0, v[8].position.setup.matchLength)
        assertEquals("You 5-away, opponent 1-away, Crawford", v[6].label)
    }

    @Test
    fun cubeVersionsOnlyWhereTheDecisionExists() {
        assertEquals(7, DeepDive.versions(base(), Change.Cube).size)
        val take = DeepDive.versions(base(dice = null, doubled = true), Change.Cube)
        assertEquals(listOf("C", "O2", "O4", "O8"), take.map { it.short })
        assertEquals("No cube in the Crawford game.", DeepDive.problem(base(score = listOf(6, 0), crawford = true), Change.Cube))
        assertTrue(DeepDive.problem(base(matchLength = 0, score = listOf(0, 0)), Change.Score(Player.CPU)) != null)
    }

    @Test
    fun trackedPlayIsIllegalWhereItsPathIsBlocked() {
        val b = base()
        assertTrue(DeepDive.isLegal(b, Tracked.Play(move2414)))
        // The opponent makes your 18 and 20 points: 24/14 cannot be played with 64.
        val me = IntArray(25) { b.board.count(Player.HUMAN, it) }
        val opp = IntArray(25) { b.board.count(Player.CPU, it) }
        opp[24 - 17] = 2; opp[24 - 18] = 2; opp[24 - 20] = 2; opp[24 - 19] = 3
        val blocked = b.copy(setup = b.setup.copy(board = Board.fromSides(me, opp)))
        assertFalse(DeepDive.isLegal(blocked, Tracked.Play(move2414)))
        // Without dice there is no play to follow.
        assertFalse(DeepDive.isLegal(base(dice = null), Tracked.Play(move2414)))
    }

    @Test
    fun cubeActionsAreIllegalInTheCrawfordGame() {
        val take = base(dice = null, doubled = true)
        assertTrue(DeepDive.isLegal(take, Tracked.Cube(DecisionKind.TAKE)))
        assertFalse(DeepDive.isLegal(take, Tracked.Cube(DecisionKind.DOUBLE)))
        val crawford = DeepDive.versions(take, Change.Score(Player.CPU)).first { it.short == "1a Crawford" }
        assertFalse(DeepDive.isLegal(crawford.position, Tracked.Cube(DecisionKind.TAKE)))
        val double = base(dice = null)
        assertTrue(DeepDive.isLegal(double, Tracked.Cube(DecisionKind.DOUBLE)))
        val theirs = double.copy(setup = double.setup.copy(cube = CubeState(2, Player.CPU)))
        assertFalse(DeepDive.isLegal(theirs, Tracked.Cube(DecisionKind.NO_DOUBLE)))
    }

    @Test
    fun cubeLossNeedsALiveCube() {
        val dead = CubeEval(0.5f, 0.6f, 1f, 0.5f, false, true, false, cubeAvailable = false, probs = emptyList())
        assertNull(DeepDive.cubeLoss(dead, DecisionKind.DOUBLE, false))
        val live = dead.copy(cubeAvailable = true)
        // Double, take (0.6) beats no double (0.5).
        assertEquals(0.1f, DeepDive.cubeLoss(live, DecisionKind.NO_DOUBLE, false)!!, 1e-6f)
        assertEquals(0f, DeepDive.cubeLoss(live, DecisionKind.DOUBLE, false)!!, 1e-6f)
    }

    @Test
    fun aStackMovesTogether() {
        val b = base()
        // Two of the three checkers on 13 tried on 13, 11 and 10.
        val v = DeepDive.versions(b, Change.Checkers(listOf(Slot(Player.HUMAN, 12, listOf(12, 10, 9), count = 2))))
        assertEquals(listOf("13", "11", "10"), v.map { it.short })
        assertEquals("Your 2 checkers on 11", v[1].label)
        assertEquals(1, v[1].position.board.count(Player.HUMAN, 12))
        assertEquals(2, v[1].position.board.count(Player.HUMAN, 10))
        // More than stand there cannot move.
        assertNull(DeepDive.moveChecker(b.board, Player.HUMAN, 15, 14, 2))
    }

    @Test
    fun twoChangesMakeEveryCombinationFirstChangeInnermost() {
        val b = base()
        val changes = listOf(Change.Checkers(listOf(Slot(Player.HUMAN, 15, listOf(15, 14)))), Change.Cube)
        val v = DeepDive.versions(b, changes)
        assertEquals(2 * 7, v.size)
        assertEquals(listOf(0, 0), v[0].coords)
        assertEquals(listOf(1, 0), v[1].coords)
        assertEquals(listOf(0, 1), v[2].coords)
        assertEquals("Your checker on 16 · Cube centred", v[0].label)
        assertTrue(v[0].isBase)
        assertEquals(CubeState(2, Player.HUMAN), v[2].position.setup.cube)
        assertEquals(listOf(15), v[2].spots)
    }

    @Test
    fun theCrawfordGameHasOnlyTheCentredCube() {
        val v = DeepDive.versions(base(), listOf(Change.Cube, Change.Score(Player.CPU)))
        val crawfordRow = DeepDive.steps(base(), Change.Score(Player.CPU)).indexOfFirst { it.short == "1a Crawford" }
        val inCrawford = v.filter { it.coords[1] == crawfordRow }
        assertEquals(listOf("C"), inCrawford.map { DeepDive.steps(base(), Change.Cube)[it.coords[0]].short })
        assertEquals(9 * 7 - 6, v.size)
    }

    @Test
    fun playLossIsMatchedByThePositionLeft() {
        val b = base()
        val other = Move(listOf(Step(15, 9), Step(15 - 6, 5)))
        val cands = listOf(
            Candidate(move = move2414, equity = 0.30f, probs = emptyList(), plies = 1),
            Candidate(move = Move(listOf(Step(15, 11), Step(11, 5))), equity = 0.25f, probs = emptyList(), plies = 1),
        )
        val a = CheckerAnalysis(cands, 0, 0.30f, 0.30f, 0.25f, 2, 1)
        // 16/10/6 written step by step another way is the same play.
        assertEquals(0.05f, DeepDive.playLoss(a, b.board, other)!!, 1e-6f)
        assertEquals(0f, DeepDive.playLoss(a, b.board, move2414)!!, 1e-6f)
        assertNull(DeepDive.playLoss(a, b.board, Move(listOf(Step(12, 6), Step(12, 8)))))
    }

    @Test
    fun aScoreRangeKeepsOnlyItsSteps() {
        val v = DeepDive.versions(base(), Change.Score(Player.CPU, from = 4, to = 2, money = false))
        assertEquals(listOf("4a", "3a", "2a"), v.map { it.short })
        val withOne = DeepDive.versions(base(), Change.Score(Player.CPU, from = 2, to = 1, money = true))
        assertEquals(listOf("2a", "1a Crawford", "1a post", "$"), withOne.map { it.short })
    }

    @Test
    fun bothScoresCrossWithCrawfordWorkedOutFromTheCombinedScore() {
        val changes = listOf(Change.Score(Player.HUMAN, from = 2, to = 1), Change.Score(Player.CPU, from = 2, to = 1))
        val (v, axes) = DeepDive.plan(base(), changes)
        // No money game with two scores.
        assertEquals(listOf(listOf("2a", "1a Crawford", "1a post"), listOf("2a", "1a Crawford", "1a post")), axes)
        // 2a×2a, you 1a C × opp 2a, you 1a PC × opp 2a, the same the other way, and 1a × 1a only as post-Crawford.
        assertEquals(6, v.size)
        val dmp = v.single { it.position.setup.score == listOf(6, 6) }
        assertFalse(dmp.position.setup.crawford)
        assertEquals(listOf(2, 2), dmp.coords)
        val crawford = v.single { it.position.setup.score == listOf(6, 5) && it.position.setup.crawford }
        assertEquals("You 1-away, opponent 2-away, Crawford", crawford.label)
        assertNull(DeepDive.problem(base(), changes[0]))
    }

    @Test
    fun matchWinningChancesSpanALossAndAWinOfTheCube() {
        val b = base()
        val win = DeepDive.mwc(b, 1f)!!
        val lose = DeepDive.mwc(b, -1f)!!
        assertTrue(win > lose)
        assertEquals((win + lose) / 2, DeepDive.mwc(b, 0f)!!, 1e-9)
        assertNull(DeepDive.mwc(base(matchLength = 0, score = listOf(0, 0)), 0f))
    }

    @Test
    fun preCrawfordCrawfordAndPostCrawfordCanBeChosen() {
        fun shorts(c: Change.Score) = DeepDive.versions(base(), c).map { it.short }
        assertEquals(listOf("1a post", "$"), shorts(Change.Score(Player.CPU, pre = false, crawford = false)))
        assertEquals(listOf("3a", "2a", "1a Crawford"), shorts(Change.Score(Player.CPU, from = 3, money = false, post = false)))
        val none = Change.Score(Player.CPU, money = false, pre = false, crawford = false, post = false)
        assertTrue(DeepDive.problem(base(), none) != null)
        // Both post-Crawford only: the 1-away of each, and double match point.
        val both = listOf(Change.Score(Player.HUMAN, pre = false, crawford = false), Change.Score(Player.CPU, pre = false, crawford = false))
        val v = DeepDive.versions(base(), both)
        assertEquals(1, v.size)
        assertEquals(listOf(6, 6), v[0].position.setup.score)
    }
}
