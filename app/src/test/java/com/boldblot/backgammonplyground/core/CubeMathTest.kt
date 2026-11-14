package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The match equity table is Kazaross XG2, and the match cube formulas count a free redouble. */
class CubeMathTest {
    private val met = MatchEquityTable()

    @Test
    fun `the table is Kazaross XG2`() {
        assertEquals(0.5, met.met(1, 1, false), 0.0)
        assertEquals(0.67736, met.met(1, 2, false), 0.0) // the Crawford game
        assertEquals(0.32264, met.met(2, 1, false), 0.0)
        assertEquals(0.59947, met.met(2, 3, false), 0.0)
        assertEquals(0.48803, met.met(2, 1, true), 0.0) // after it: the free drop
        assertEquals(1 - 0.48803, met.met(1, 2, true), 1e-12)
        for (a in 1..25) for (b in 1..25) assertEquals(1.0, met.met(a, b, false) + met.met(b, a, false), 1e-9)
    }

    @Test
    fun `a game's result moves on in the table`() {
        // 2-away each, a point won: the Crawford game, the winner 1-away.
        assertEquals(met.met(1, 2, false), met.after(2, 2, false, 1, 0), 0.0)
        // The Crawford game lost by the leader: post-Crawford.
        assertEquals(met.met(1, 2, true), met.after(1, 3, true, 0, 1), 0.0)
        assertEquals(1.0, met.after(2, 5, false, 2, 0), 0.0)
        assertEquals(0.0, met.after(2, 5, false, 0, 8), 0.0)
    }

    @Test
    fun `past 25 away the table carries on from Kazaross`() {
        // A point more to go costs about as much just past the table as just inside it: 25 against 24 is 0.46914,
        // 25 against 23 is 0.43779.
        assertEquals(met.met(25, 23, false), met.met(26, 24, false), 0.005)
        assertEquals(met.met(24, 25, false), met.met(25, 26, false), 0.005)
        assertEquals(0.5, met.met(30, 30, false), 1e-9)
        assertEquals(1.0, met.met(40, 33, false) + met.met(33, 40, false), 1e-9)
    }

    @Test
    fun `a take that leaves a free redouble is worth the redoubled cube`() {
        // The leader, 2-away against 6-away, doubles to 2 with 75% and no gammons. Any loss now costs the taker
        // the match, so the taker redoubles to 4 at no risk: the leader wins the match with 75% and is otherwise
        // 2-away each. Valued as if the taker sat on the 2-cube it was 0.888: a pass, and the cause of every play
        // at -1.000 in the played-out games.
        val mc = MatchCube(met, listOf(0.75f, 0f, 0f, 0f, 0f), 2, 6, false, 0.68)
        val (_, dt, dp) = mc.decision(CubeView.CENTRED, 1)
        assertEquals(0.75 + 0.25 * 0.5, dt, 1e-6)
        assertTrue(dt < dp)
    }

    @Test
    fun `the holder of a free redouble is not valued as if the cube were dead`() {
        // The same game from the taker's side once the cube is theirs at 2: on roll they redouble at once.
        val taker = MatchCube(met, listOf(0.25f, 0f, 0f, 0f, 0f), 6, 2, false, 0.68)
        val (nd, redoubled, _) = taker.decision(CubeView.MINE, 2)
        assertEquals(0.25 * 0.5, redoubled, 1e-6)
        assertTrue(redoubled > nd)
        // And from the leader's side, the taker holding it: the same value.
        val leader = MatchCube(met, listOf(0.75f, 0f, 0f, 0f, 0f), 2, 6, false, 0.68)
        assertEquals(1 - 0.25 * 0.5, leader.cubeful(CubeView.THEIRS, 2), 1e-6)
    }
}
