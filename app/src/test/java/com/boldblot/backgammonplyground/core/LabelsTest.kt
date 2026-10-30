package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelsTest {

    private fun rec(board: Board, kind: DecisionKind = DecisionKind.CHECKER, score: List<Int> = listOf(0, 0), crawford: Boolean = false, error: Float? = null) =
        DecisionRecord(Player.HUMAN, kind, board, CubeState(), score, crawford, dice = Dice(3, 1), move = Move.NONE, error = error)

    @Test
    fun openingContactMatchError() {
        val l = Labels.of(rec(Board.INITIAL, error = 0.05f), matchLength = 5, opponent = false)
        assertEquals(
            setOf(AutoLabel.CHECKER, AutoLabel.CONTACT, AutoLabel.MATCH, AutoLabel.ERROR, AutoLabel.OPENING, AutoLabel.MINE),
            l,
        )
    }

    @Test
    fun raceBearOffMoneyCubeAndPostCrawford() {
        val race = Board.fromSides(IntArray(25).also { it[2] = 10; it[4] = 5 }, IntArray(25).also { it[3] = 15 })
        val money = Labels.of(rec(race, DecisionKind.TAKE, error = 0.2f), matchLength = 0, opponent = true)
        assertTrue(money.containsAll(listOf(AutoLabel.CUBE, AutoLabel.TAKE_SIDE, AutoLabel.RACE, AutoLabel.BEAR_OFF, AutoLabel.MONEY, AutoLabel.BLUNDER, AutoLabel.OPPONENTS)))
        assertFalse(AutoLabel.CONTACT in money)
        val post = Labels.of(rec(race, score = listOf(4, 2)), matchLength = 5, opponent = null)
        assertTrue(AutoLabel.POST_CRAWFORD in post)
        assertTrue(AutoLabel.CRAWFORD in Labels.of(rec(race, score = listOf(4, 2), crawford = true), 5, null))
    }

    @Test
    fun filterIsAnyWithinAGroupAndAllAcross() {
        val labels = setOf("CUBE", "RACE", "MATCH")
        assertTrue(Labels.matches(labels, setOf("CONTACT", "RACE")))
        assertTrue(Labels.matches(labels, setOf("CUBE", "RACE")))
        assertFalse(Labels.matches(labels, setOf("CHECKER", "RACE")))
        assertTrue(Labels.matches(labels, emptySet()))
        assertEquals("Cube · Contact or Race", Labels.folderName(setOf("RACE", "CUBE", "CONTACT")))
        assertEquals(setOf("RACE", "my:Primes"), Labels.decode(Labels.encode(setOf("my:Primes", "RACE"))))
    }

    @Test
    fun opponentDecisionTurnsToTheBottom() {
        val d = DecisionRecord(Player.CPU, DecisionKind.DOUBLE, Board.INITIAL, CubeState(2, Player.CPU), listOf(1, 3), false)
        val t = d.turned()
        assertEquals(Player.HUMAN, t.player)
        assertEquals(listOf(3, 1), t.score)
        assertEquals(Player.HUMAN, t.cube.owner)
    }
}
