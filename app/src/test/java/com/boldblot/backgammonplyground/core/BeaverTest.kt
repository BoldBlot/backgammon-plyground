package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BeaverTest {

    private val money = MatchRules(length = 0, beavers = true, raccoons = true)

    /** The computer on roll, having just doubled. */
    private fun doubled() = GameState(phase = Phase.PRE_ROLL, turn = Player.CPU).double()

    @Test
    fun beaversAndRaccoonsOnlyInMoneyGamesWithTheCube() {
        assertTrue(money.beaversAllowed)
        assertTrue(money.raccoonsAllowed)
        assertFalse(money.copy(length = 5).beaversAllowed)
        assertFalse(money.copy(useCube = false).beaversAllowed)
        assertFalse(money.copy(beavers = false).raccoonsAllowed)
    }

    @Test
    fun beaverKeepsTheCubeWithTheBeaverer() {
        val g = doubled().beaver(raccoons = false)
        assertEquals(Phase.PRE_ROLL, g.phase)
        assertEquals(Player.CPU, g.turn) // the doubler still rolls
        assertEquals(CubeState(4, Player.HUMAN), g.cube)
        assertFalse(g.mayDouble(money, listOf(0, 0)))
    }

    @Test
    fun withRaccoonsTheDoublerAnswersTheBeaver() {
        val b = doubled().beaver(raccoons = true)
        assertEquals(Phase.BEAVERED, b.phase)
        assertEquals(4, b.offeredCube)
        assertEquals(CubeState(4, Player.HUMAN), b.acceptBeaver().cube)
        val r = b.raccoon()
        assertEquals(Phase.PRE_ROLL, r.phase)
        assertEquals(CubeState(8, Player.HUMAN), r.cube)
        assertEquals(Player.CPU, r.turn)
    }

    private fun eval(doubleTake: Float) = CubeEval(
        noDouble = 0.1f, doubleTake = doubleTake, doublePass = 1f, optimal = 0.1f,
        shouldDouble = false, shouldTake = true, optional = false, cubeAvailable = true, probs = listOf(0.5f, 0.1f, 0f, 0.1f, 0f),
    )

    @Test
    fun beaverAndRaccoonErrors() {
        // Double/take worth -0.2 to the doubler: the taker should beaver (+0.4 instead of +0.2).
        val e = eval(-0.2f)
        assertEquals(0f, DecisionMath.takeError(e, DecisionKind.BEAVER, beavers = true), 1e-6f)
        assertEquals(0.2f, DecisionMath.takeError(e, DecisionKind.TAKE, beavers = true), 1e-6f)
        assertEquals(0f, DecisionMath.takeError(e, DecisionKind.TAKE, beavers = false), 1e-6f)
        // The doubler then plays on (-0.4) rather than raccooning (-0.8).
        assertEquals(0f, DecisionMath.raccoonError(e, raccooned = false), 1e-6f)
        assertEquals(0.4f, DecisionMath.raccoonError(e, raccooned = true), 1e-6f)
        // A good double (+0.3) beavered: a beaver error of 0.3; the raccoon is right.
        val good = eval(0.3f)
        assertEquals(0.3f, DecisionMath.takeError(good, DecisionKind.BEAVER, beavers = true), 1e-6f)
        assertEquals(0f, DecisionMath.raccoonError(good, raccooned = true), 1e-6f)
        // Doubling into a beaver costs the doubled loss: no double 0.1, double -0.4 with beavers.
        assertEquals(0.5f, DecisionMath.doubleError(e, doubled = true, beavers = true), 1e-6f)
        assertEquals(0.3f, DecisionMath.doubleError(e, doubled = true, beavers = false), 1e-6f)
    }

    @Test
    fun matExportWritesBeaversAsGnubg() {
        var g = GameState(phase = Phase.PRE_ROLL, turn = Player.CPU)
        fun rec(p: Int, k: DecisionKind) = DecisionRecord(p, k, g.board, g.cube, listOf(0, 0), false)
        g = g.record(rec(Player.CPU, DecisionKind.DOUBLE)).double()
        g = g.record(rec(Player.HUMAN, DecisionKind.BEAVER)).beaver(raccoons = true)
        g = g.record(rec(Player.CPU, DecisionKind.RACCOON)).raccoon()
        assertEquals(
            listOf(Player.CPU to " Doubles => 2", Player.HUMAN to " Beavers => 4", Player.CPU to " Beavers => 8", Player.HUMAN to ""),
            MatExport.entries(g),
        )
        var h = GameState(phase = Phase.PRE_ROLL, turn = Player.CPU)
        h = h.record(rec(Player.CPU, DecisionKind.DOUBLE)).double()
        h = h.record(rec(Player.HUMAN, DecisionKind.BEAVER)).beaver(raccoons = true)
        h = h.record(rec(Player.CPU, DecisionKind.NO_RACCOON)).acceptBeaver()
        assertEquals(listOf(Player.CPU to " Doubles => 2", Player.HUMAN to " Beavers => 4"), MatExport.entries(h))
    }
}
