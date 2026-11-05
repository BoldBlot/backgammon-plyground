package com.boldblot.backgammonplyground.ui.review

import com.boldblot.backgammonplyground.core.*
import com.boldblot.backgammonplyground.data.GameEntity
import com.boldblot.backgammonplyground.data.MatchEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Marked chip in review (feedback 2026-11-05): what shows, and where "Next mistake" stops. */
class ReviewMarkedTest {
    private fun decision(error: Float = 0f) = DecisionRecord(
        player = Player.HUMAN, kind = DecisionKind.CHECKER, board = Board.INITIAL, cube = CubeState(), score = listOf(0, 0),
        crawford = false, error = error, counted = true,
    )

    private fun entity(n: Int) = GameEntity(
        matchId = 1, number = n, finishedAt = 0, winner = 0, winType = "", points = 1, summary = "", record = "", scoreBeforeHuman = 0, scoreBeforeCpu = 0,
    )

    // Game 1: 0 fine, 1 a mistake, 2 marked, 3 a mistake and marked, 4 fine. Game 2: 0 fine, 1 marked.
    private val games = listOf(
        entity(1) to GameState(decisions = listOf(decision(), decision(0.05f), decision(), decision(0.1f), decision()), marked = setOf(2, 3)),
        entity(2) to GameState(decisions = listOf(decision(), decision()), marked = setOf(1)),
    )

    private fun state(filter: ReviewFilter?, marked: Boolean, position: Int = 0, hasMarks: Boolean = true) =
        ReviewState(games = games, filter = filter, marked = marked, hasMarks = hasMarks, position = position)

    private fun ReviewState.listed() = decisions.map { it.index }

    @Test fun reviewOpensOnAllWithMarkedStoppingAtMistakesAndMarks() {
        val s = state(ReviewFilter.ALL, marked = true)
        assertEquals(listOf(0, 1, 2, 3, 4), s.listed())
        assertEquals(0 to 1, s.nextMistake)
        assertEquals(0 to 2, s.copy(position = 1).nextMistake)
        assertEquals(1 to 1, s.copy(position = 3).nextMistake)
    }

    @Test fun markedAloneListsAndStopsOnlyAtMarks() {
        val s = state(null, marked = true)
        assertEquals(listOf(2, 3), s.listed())
        assertEquals(0 to 1, s.nextMistake)
        assertEquals(1 to 0, s.copy(position = 1).nextMistake)
    }

    @Test fun allWithoutMarkedStopsOnlyAtMistakes() {
        val s = state(ReviewFilter.ALL, marked = false)
        assertEquals(listOf(0, 1, 2, 3, 4), s.listed())
        assertEquals(0 to 1, s.nextMistake)
        assertEquals(0 to 3, s.copy(position = 1).nextMistake)
        assertNull(s.copy(position = 3).nextMistake)
    }

    @Test fun myMistakesAsBeforeUntilMarkedIsAdded() {
        assertEquals(listOf(1, 3), state(ReviewFilter.MISTAKES, marked = false).listed())
        assertEquals(listOf(2, 3), state(ReviewFilter.MISTAKES, marked = true).listed())
        assertEquals(listOf(2, 3), state(ReviewFilter.MINE, marked = true).listed())
    }

    @Test fun fromHistoryTheMarksNoLongerCount() {
        val s = state(ReviewFilter.ALL, marked = false, hasMarks = false)
        assertEquals(listOf(0, 1, 2, 3, 4), s.listed())
        assertEquals(0 to 3, s.copy(position = 1).nextMistake)
        assertEquals(false, s.copy(position = 2).currentMarked)
    }

    @Test fun anObviousNoDoubleStaysListedWhenMarked() {
        val obvious = decision().copy(kind = DecisionKind.NO_DOUBLE, counted = false)
        val g = listOf(entity(1) to GameState(decisions = listOf(obvious, obvious), marked = setOf(1)))
        assertEquals(listOf(1), ReviewState(games = g, hasMarks = true, marked = true).listed())
    }

    @Test fun marksSurviveSavingTheGame() {
        val g = GameState(marked = setOf(2, 5))
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        assertEquals(setOf(2, 5), json.decodeFromString<GameState>(json.encodeToString(g)).marked)
    }
}
