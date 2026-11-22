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
        assertEquals(listOf("7a", "6a", "5a", "4a", "3a", "2a", "1a C", "1a PC", "$"), v.map { it.short })
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
        val crawford = DeepDive.versions(take, Change.Score(Player.CPU)).first { it.short == "1a C" }
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
}
