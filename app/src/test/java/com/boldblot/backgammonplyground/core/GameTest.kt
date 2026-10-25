package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTest {

    private fun side(vararg pairs: Pair<Int, Int>): IntArray {
        val s = IntArray(25)
        for ((point, n) in pairs) s[if (point == 25) BAR else point - 1] = n
        return s
    }

    @Test
    fun winTypes() {
        // Human has borne off everything; cpu has none off.
        val gammon = Board.fromSides(IntArray(25), side(1 to 15))  // cpu on its own 1-point
        assertEquals(WinType.GAMMON, Scoring.winType(gammon, Player.HUMAN))
        val bg = Board.fromSides(IntArray(25), side(1 to 14, 20 to 1)) // cpu checker in human's home
        assertEquals(WinType.BACKGAMMON, Scoring.winType(bg, Player.HUMAN))
        val barBg = Board.fromSides(IntArray(25), side(1 to 14, 25 to 1))
        assertEquals(WinType.BACKGAMMON, Scoring.winType(barBg, Player.HUMAN))
        val single = Board.fromSides(IntArray(25), side(1 to 14))
        assertEquals(WinType.SINGLE, Scoring.winType(single, Player.HUMAN))
    }

    @Test
    fun jacobyRuleCancelsGammonsWithCentredCube() {
        val gammon = Board.fromSides(IntArray(25), side(1 to 15))
        val money = MatchRules(length = 0, jacoby = true)
        assertEquals(1, Scoring.result(gammon, Player.HUMAN, CubeState(), money).points)
        assertEquals(4, Scoring.result(gammon, Player.HUMAN, CubeState(2, Player.CPU), money).points)
        val noJacoby = money.copy(jacoby = false)
        assertEquals(2, Scoring.result(gammon, Player.HUMAN, CubeState(), noJacoby).points)
        val match = MatchRules(length = 5)
        assertEquals(2, Scoring.result(gammon, Player.HUMAN, CubeState(), match).points)
    }

    @Test
    fun openingRollDecidesStarter() {
        val g = GameState().openingRoll(humanDie = 2, cpuDie = 5)
        assertEquals(Player.CPU, g.turn)
        assertEquals(Phase.MOVING, g.phase)
        assertEquals(Dice(2, 5), g.dice)
        assertTrue(g.openingMove)
    }

    @Test
    fun opponentLastRollSurvivesResume() {
        val humanStarts = GameState().openingRoll(humanDie = 5, cpuDie = 2)
        assertNull(humanStarts.opponentLastRoll(Player.HUMAN))
        // Computer has moved; the human is about to roll, then to move.
        val preRoll = GameState(phase = Phase.PRE_ROLL, turn = Player.HUMAN,
            rolls = listOf(RollRecord(Player.HUMAN, Dice(5, 2), opening = true), RollRecord(Player.CPU, Dice(6, 4))))
        assertEquals(Dice(6, 4), preRoll.opponentLastRoll(Player.HUMAN))
        assertEquals(Dice(6, 4), preRoll.roll(Dice(3, 3)).opponentLastRoll(Player.HUMAN))
        assertNull(preRoll.opponentLastRoll(Player.CPU))
    }

    @Test
    fun doubleTakeAndPass() {
        var g = GameState(phase = Phase.PRE_ROLL, turn = Player.HUMAN)
        g = g.double()
        assertEquals(Phase.DOUBLED, g.phase)
        assertEquals(Player.CPU, g.responder)
        val taken = g.take()
        assertEquals(CubeState(2, Player.CPU), taken.cube)
        assertEquals(Phase.PRE_ROLL, taken.phase)
        assertEquals(Player.HUMAN, taken.turn)
        val passed = g.pass()
        assertEquals(Phase.OVER, passed.phase)
        assertEquals(GameResult(Player.HUMAN, WinType.SINGLE, 1, byPass = true), passed.result)
    }

    @Test
    fun mayDoubleRules() {
        val rules = MatchRules(length = 5)
        assertTrue(Scoring.mayDouble(rules, CubeState(), Player.HUMAN, listOf(0, 0), crawfordGame = false))
        assertFalse(Scoring.mayDouble(rules, CubeState(), Player.HUMAN, listOf(4, 0), crawfordGame = true))
        assertFalse(Scoring.mayDouble(rules, CubeState(2, Player.CPU), Player.HUMAN, listOf(0, 0), false))
        // Human at 3-away... score 3 in 5-pt match with cube 2: 3 + 2 >= 5 -> no point doubling
        assertFalse(Scoring.mayDouble(rules, CubeState(2, Player.HUMAN), Player.HUMAN, listOf(3, 0), false))
        assertFalse(Scoring.mayDouble(rules.copy(useCube = false), CubeState(), Player.HUMAN, listOf(0, 0), false))
    }

    @Test
    fun crawfordGameAfterReachingMatchPointMinusOne() {
        val rules = MatchRules(length = 3)
        var m = MatchState(rules = rules, cpuLevel = CpuLevel.EXPERT)
        // Human wins a gammon: 2 points -> 2-0 in a 3-point match -> next is Crawford.
        m = m.copy(game = m.game.copy(phase = Phase.OVER, result = GameResult(Player.HUMAN, WinType.GAMMON, 2)))
        m = m.finishGame()
        assertEquals(listOf(2, 0), m.score)
        assertTrue(m.game.crawford)
        assertNull(m.winner)
        // CPU wins the Crawford game: 2-1, post-Crawford -> no Crawford flag any more.
        m = m.copy(game = m.game.copy(phase = Phase.OVER, result = GameResult(Player.CPU, WinType.SINGLE, 1)))
        m = m.finishGame()
        assertEquals(listOf(2, 1), m.score)
        assertFalse(m.game.crawford)
        // Human wins: match over.
        m = m.copy(game = m.game.copy(phase = Phase.OVER, result = GameResult(Player.HUMAN, WinType.SINGLE, 1)))
        m = m.finishGame()
        assertEquals(Player.HUMAN, m.winner)
    }

    @Test
    fun movingLastCheckerEndsGame() {
        val board = Board.fromSides(side(1 to 1), side(1 to 15))
        var g = GameState(board = board, phase = Phase.MOVING, dice = Dice(1, 2), turn = Player.HUMAN)
        val move = MoveGenerator.legalMoves(board, Player.HUMAN, 1, 2).single()
        g = g.move(move, MatchRules(length = 5))
        assertEquals(Phase.OVER, g.phase)
        assertEquals(WinType.GAMMON, g.result!!.type)
    }

    @Test
    fun notation() {
        val b = Board.INITIAL
        fun fmt(vararg steps: Pair<Int, Int>) = MoveNotation.format(b, Player.HUMAN, Move(steps.map { Step(it.first, it.second) }))
        assertEquals("13/7 8/7", fmt(12 to 6, 7 to 6))
        assertEquals("24/13", fmt(23 to 17, 17 to 12))
        assertEquals("8/5(2) 6/5(2)", fmt(7 to 4, 7 to 4, 5 to 4, 5 to 4))
        // A hit on the way: cpu blot on our 18-point
        val cpu = IntArray(25).also { it[6] = 1; it[2] = 14 } // cpu index 6 = our point 18
        val human = IntArray(25).also { it[23] = 2; it[12] = 5; it[7] = 3; it[5] = 5 }
        val hb = Board.fromSides(human, cpu)
        assertEquals("24/18*/13", MoveNotation.format(hb, Player.HUMAN, Move(listOf(Step(23, 17), Step(17, 12)))))
    }
}

class ClosedOutTest {
    @org.junit.Test
    fun closedOutNeedsBarCheckerAndSixMadePoints() {
        val me = IntArray(25).also { it[BAR] = 1; it[12] = 14 }
        val opp = IntArray(25).also { for (i in 0..5) it[i] = 2; it[20] = 3 }
        val b = Board.fromSides(me, opp)
        org.junit.Assert.assertTrue(b.closedOut(Player.HUMAN))
        org.junit.Assert.assertFalse(b.closedOut(Player.CPU))
        // one point open (a blot) is not closed
        val opp2 = opp.copyOf().also { it[3] = 1; it[20] = 4 }
        org.junit.Assert.assertFalse(Board.fromSides(me, opp2).closedOut(Player.HUMAN))
        // no checker on the bar: not closed out
        val me2 = me.copyOf().also { it[BAR] = 0; it[12] = 15 }
        org.junit.Assert.assertFalse(Board.fromSides(me2, opp).closedOut(Player.HUMAN))
    }
}
