package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MatExportTest {

    private val rules = MatchRules(length = 5)
    private fun step(from: Int, to: Int) = Step(from - 1, to - 1) // 1-based points, 25 = bar, 0 = off

    @Test
    fun layoutMatchesGnubg() {
        // Computer opens with 3-1 (8/5 6/5), human 6-4 (24/18 13/9), computer doubles, human passes.
        var g = GameState().openingRoll(humanDie = 1, cpuDie = 3)
        val cpuMove = Move(listOf(step(8, 5), step(6, 5)))
        g = g.record(DecisionRecord(Player.CPU, DecisionKind.CHECKER, g.board, g.cube, listOf(0, 0), false, dice = g.dice, move = cpuMove))
            .move(cpuMove, rules)
        g = g.roll(Dice(4, 6))
        val humanMove = Move(listOf(step(13, 9), step(24, 18)))
        g = g.record(DecisionRecord(Player.HUMAN, DecisionKind.CHECKER, g.board, g.cube, listOf(0, 0), false, dice = g.dice, move = humanMove))
            .move(humanMove, rules)
        g = g.record(DecisionRecord(Player.CPU, DecisionKind.DOUBLE, g.board, g.cube, listOf(0, 0), false)).double()
        g = g.record(DecisionRecord(Player.HUMAN, DecisionKind.PASS, g.board, g.cube, listOf(0, 0), false)).pass()

        val text = MatExport.write(
            MatExport.Info(rules, listOf("Player", "gnubg World Class"), 0),
            listOf(MatExport.Game(2, listOf(1, 0), g)),
        ).replace("\r\n", "\n")
        val body = text.substringAfter(" 5 point match\n\n")
        val expected = listOf(
            " Game 2",
            " gnubg World Class : 0          Player : 1",
            "  1) 31: 8/5 6/5                 64: 24/18 13/9",
            "  2)  Doubles => 2                Drops",
            "      Wins 1 point",
            "",
            "",
        ).joinToString("\n")
        assertEquals(expected, body)
    }

    @Test
    fun movesUseBarOffHitsAndGnubgOrder() {
        // Human (bottom) checker on the bar enters on the opponent's 5-point with a hit, then moves on.
        val human = IntArray(25).also { it[24] = 1; it[5] = 14 }
        val cpu = IntArray(25).also { it[23 - 19] = 1; it[0] = 14 } // a blot on the human's 20-point
        val board = Board.fromSides(human, cpu)
        val move = Move(listOf(Step(24, 19), Step(19, 17)))
        assertEquals("25/20* 20/18", MatExport.formatMove(board, Player.HUMAN, move))
        val bearOff = Board.fromSides(IntArray(25).also { it[2] = 2 }, IntArray(25).also { it[10] = 15 })
        assertEquals("3/1 3/0", MatExport.formatMove(bearOff, Player.HUMAN, Move(listOf(Step(2, 0), Step(2, OFF)))))
    }
}
