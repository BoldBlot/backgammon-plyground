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
    fun doubleMatchPointIsDmpNotPostCrawford() {
        val race = Board.fromSides(IntArray(25).also { it[2] = 10; it[4] = 5 }, IntArray(25).also { it[3] = 15 })
        val onepoint = Labels.of(rec(race), matchLength = 1, opponent = null)
        assertTrue(AutoLabel.DMP in onepoint)
        assertFalse(AutoLabel.POST_CRAWFORD in onepoint)
        val dmp = Labels.of(rec(race, score = listOf(4, 4)), matchLength = 5, opponent = null)
        assertTrue(AutoLabel.DMP in dmp)
        assertFalse(AutoLabel.POST_CRAWFORD in dmp)
        assertFalse(AutoLabel.DMP in Labels.of(rec(race, score = listOf(4, 2)), matchLength = 5, opponent = null))
        // Labels stored before DMP existed read as DMP; others stay as they were.
        assertEquals(setOf("MATCH", "DMP"), Labels.withDmp(setOf("MATCH", "POST_CRAWFORD"), 1, listOf(0, 0), false))
        assertEquals(setOf("MATCH", "POST_CRAWFORD"), Labels.withDmp(setOf("MATCH", "POST_CRAWFORD"), 5, listOf(4, 2), false))
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

    // ---- missed hit / point / running ------------------------------------------------------------------

    /** HUMAN: 5 on its 11-point (index 10), 5 on index 8, 5 on index 5; the computer: a blot where index 4 hits, 14 on its ace point. */
    private val hitBoard = Board.fromSides(
        IntArray(25).also { it[10] = 5; it[8] = 5; it[5] = 5 },
        IntArray(25).also { it[19] = 1; it[23] = 14 },
    )
    private val hit = Move(listOf(Step(8, 4)))
    private val quiet = Move(listOf(Step(10, 6)))
    private val quiet2 = Move(listOf(Step(10, 7)))

    private fun checkerRec(board: Board, played: Move, vararg cands: Pair<Move, Float>, error: Float) =
        DecisionRecord(
            Player.HUMAN, DecisionKind.CHECKER, board, CubeState(), listOf(0, 0), false, dice = Dice(4, 2), move = played, error = error,
            checker = CheckerAnalysis(
                cands.map { Candidate(it.first, it.second, listOf(0.5f, 0.1f, 0f, 0.1f, 0f), 1) },
                playedIndex = cands.indexOfFirst { it.first == played }, playedEquity = 0f, bestEquity = cands.first().second,
                worstEquity = cands.last().second, legalMoves = cands.size, plies = 1,
            ),
        )

    @Test
    fun missedHitWhenNoQuietPlayIsAcceptable() {
        val l = Labels.of(checkerRec(hitBoard, quiet, hit to 0.30f, quiet to 0.10f, quiet2 to 0.12f, error = 0.20f), 0, opponent = false)
        assertTrue(AutoLabel.MISSED_HIT in l)
        assertFalse(AutoLabel.MISSED_POINT in l)
        assertFalse(AutoLabel.MISSED_RUNNING in l)
    }

    @Test
    fun noMissedHitWhenAQuietPlayWasAcceptable() {
        val l = Labels.of(checkerRec(hitBoard, quiet, hit to 0.30f, quiet2 to 0.29f, quiet to 0.10f, error = 0.20f), 0, opponent = false)
        assertFalse(AutoLabel.MISSED_HIT in l)
    }

    @Test
    fun noMissedHitWhenTheHitWasPlayedOrTheMistakeSmall() {
        assertFalse(AutoLabel.MISSED_HIT in Labels.of(checkerRec(hitBoard, hit, hit to 0.30f, quiet to 0.10f, error = 0.0f), 0, opponent = false))
        assertFalse(AutoLabel.MISSED_HIT in Labels.of(checkerRec(hitBoard, quiet, hit to 0.30f, quiet to 0.25f, error = 0.05f), 0, opponent = false))
    }

    @Test
    fun missedPointAndMissedRunning() {
        // The best play moves both back checkers (21 and 18 → 22 point...) is not needed: a point is made with 10/7 8/7.
        val point = Move(listOf(Step(10, 7), Step(8, 7)))
        val pointBoard = Board.fromSides(
            IntArray(25).also { it[10] = 5; it[8] = 5; it[5] = 5 }, IntArray(25).also { it[23] = 15 },
        )
        val l = Labels.of(checkerRec(pointBoard, quiet, point to 0.30f, quiet to 0.10f, error = 0.20f), 0, opponent = false)
        assertTrue(AutoLabel.MISSED_POINT in l)
        assertFalse(AutoLabel.MISSED_HIT in l)
        val backBoard = Board.fromSides(
            IntArray(25).also { it[23] = 2; it[10] = 13 }, IntArray(25).also { it[23] = 15 },
        )
        val run = Move(listOf(Step(23, 17)))
        val stay = Move(listOf(Step(10, 4)))
        val r = Labels.of(checkerRec(backBoard, stay, run to 0.20f, stay to 0.00f, error = 0.20f), 0, opponent = false)
        assertTrue(AutoLabel.MISSED_RUNNING in r)
    }

    @Test
    fun quizTrend() {
        // The last quiz answer against the one before: a lower cost is an improvement.
        assertEquals(null, Labels.trend(null, 0.05f))
        assertEquals(null, Labels.trend(0.05f, null))
        assertEquals(AutoLabel.IMPROVED, Labels.trend(0.08f, 0.02f))
        assertEquals(AutoLabel.WORSE, Labels.trend(0f, 0.03f))
        assertEquals(AutoLabel.SAME, Labels.trend(0.05f, 0.05f))
        assertEquals(AutoLabel.SAME, Labels.trend(0f, 0.0004f))
        // As good as the last answer and right this time: "Well done"; a better answer stays "Improved", a worse one "Worse".
        assertEquals(AutoLabel.WELL_DONE, Labels.trend(0f, 0f, right = true))
        assertEquals(AutoLabel.WELL_DONE, Labels.trend(0.01f, 0.01f, right = true))
        assertEquals(AutoLabel.IMPROVED, Labels.trend(0.08f, 0f, right = true))
        assertEquals(AutoLabel.WORSE, Labels.trend(0f, 0.01f, right = true))
        assertEquals(AutoLabel.SAME, Labels.trend(0.05f, 0.05f, right = false))
        // The trend labels come from the quiz: they are a group of their own and are never stored with the others.
        assertTrue(listOf(AutoLabel.IMPROVED, AutoLabel.SAME, AutoLabel.WORSE, AutoLabel.WELL_DONE).all { Labels.isTrend(it.name) && it.group == LabelGroup.TREND })
        assertFalse(Labels.isTrend(AutoLabel.BLUNDER.name))
        assertFalse(Labels.isTrend(Labels.myKey("Improved")))
        assertEquals(setOf(AutoLabel.RACE.name), com.boldblot.backgammonplyground.data.PositionsRepository.storedLabels(
            com.boldblot.backgammonplyground.data.PositionsRepository.encodeLabels(setOf(AutoLabel.RACE.name, AutoLabel.IMPROVED.name)),
        ))
    }
}
