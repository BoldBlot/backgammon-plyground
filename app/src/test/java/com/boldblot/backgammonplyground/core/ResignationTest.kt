package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResignationTest {

    private val money = MatchRules(length = 0, jacoby = false)

    // probs: win, win gammon, win backgammon, lose gammon, lose backgammon
    private fun p(win: Float, wg: Float = 0f, wbg: Float = 0f, lg: Float = 0f, lbg: Float = 0f) = listOf(win, wg, wbg, lg, lbg)

    @Test
    fun acceptsUnlessOnePercentMore() {
        val bg = WinType.BACKGAMMON
        assertTrue(Resignation.accepts(WinType.SINGLE, 0.0099f, 0f, bg))
        assertFalse(Resignation.accepts(WinType.SINGLE, 0.01f, 0f, bg))
        assertTrue(Resignation.accepts(WinType.GAMMON, 1f, 0.0099f, bg))
        assertFalse(Resignation.accepts(WinType.GAMMON, 1f, 0.01f, bg))
        assertTrue(Resignation.accepts(WinType.BACKGAMMON, 1f, 1f, bg))
        // Bigger wins that do not count are no reason to reject.
        assertTrue(Resignation.accepts(WinType.SINGLE, 0.9f, 0.5f, WinType.SINGLE))
        assertTrue(Resignation.accepts(WinType.GAMMON, 1f, 0.5f, WinType.GAMMON))
    }

    @Test
    fun offersOnlyCertainOutcomes() {
        val bg = WinType.BACKGAMMON
        assertEquals(WinType.SINGLE, Resignation.offer(p(0f), bg))
        assertEquals(WinType.GAMMON, Resignation.offer(p(0f, lg = 1f), bg))
        assertEquals(WinType.BACKGAMMON, Resignation.offer(p(0f, lg = 1f, lbg = 1f), bg))
        assertEquals(WinType.SINGLE, Resignation.offer(p(0.0004f, lg = 0.0004f), bg)) // rounds to 0.0 %
        assertNull(Resignation.offer(p(0.001f), bg)) // 0.1 % to win: plays on
        assertNull(Resignation.offer(p(0f, lg = 0.5f), bg)) // gammon uncertain
        assertNull(Resignation.offer(p(0f, lg = 1f, lbg = 0.3f), bg)) // backgammon uncertain
        // When gammons do not count, an uncertain gammon is no reason to play on.
        assertEquals(WinType.SINGLE, Resignation.offer(p(0f, lg = 0.5f), WinType.SINGLE))
        assertEquals(WinType.GAMMON, Resignation.offer(p(0f, lg = 1f, lbg = 0.3f), WinType.GAMMON))
    }

    @Test
    fun countedWinsDependOnJacobyAndScore() {
        assertEquals(WinType.BACKGAMMON, Resignation.highestCounted(money, CubeState(), null, Player.CPU))
        val jacoby = MatchRules(length = 0, jacoby = true)
        assertEquals(WinType.SINGLE, Resignation.highestCounted(jacoby, CubeState(), null, Player.CPU))
        assertEquals(WinType.BACKGAMMON, Resignation.highestCounted(jacoby, CubeState(2, Player.HUMAN), null, Player.CPU))
        val five = MatchRules(length = 5)
        assertEquals(WinType.BACKGAMMON, Resignation.highestCounted(five, CubeState(), listOf(0, 0), Player.CPU))
        assertEquals(WinType.GAMMON, Resignation.highestCounted(five, CubeState(), listOf(0, 3), Player.CPU))
        assertEquals(WinType.SINGLE, Resignation.highestCounted(five, CubeState(), listOf(0, 4), Player.CPU))
        assertEquals(WinType.SINGLE, Resignation.highestCounted(five, CubeState(2, Player.HUMAN), listOf(0, 3), Player.CPU))
    }

    @Test
    fun resultUsesCubeAndJacoby() {
        assertEquals(6, Resignation.result(Player.CPU, WinType.BACKGAMMON, CubeState(2, Player.HUMAN), money).points)
        val j = Resignation.result(Player.CPU, WinType.GAMMON, CubeState(), MatchRules(length = 0, jacoby = true))
        assertEquals(WinType.SINGLE, j.type)
        assertEquals(1, j.points)
        assertTrue(j.resigned)
        // Match scores are not capped: a gammon in a 1-point match is worth 2.
        assertEquals(2, Resignation.result(Player.HUMAN, WinType.GAMMON, CubeState(), MatchRules(length = 1)).points)
    }

    @Test
    fun gameStateResignFlow() {
        val g = GameState(turn = Player.CPU, phase = Phase.PRE_ROLL).offerResign(WinType.GAMMON)
        val rejected = g.rejectResign()
        assertNull(rejected.resignOffer)
        assertEquals(WinType.GAMMON, rejected.resignRejected[Player.CPU])
        val over = g.acceptResign(money)
        assertEquals(Phase.OVER, over.phase)
        assertEquals(GameResult(Player.HUMAN, WinType.GAMMON, 2, resigned = true), over.result)
    }

    @Test
    fun diceArePresentedHigherFirst() {
        assertEquals("52", Dice(2, 5).toString())
        assertEquals(listOf(5, 2), Dice(2, 5).highFirst)
        assertEquals(listOf(4, 4), Dice(4, 4).highFirst)
    }
}
