package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** An 11-point match analyzed by eXtreme Gammon (match-11pt.xg), and the same match as XG exported it to .mat. */
class XgImportTest {

    private fun bytes(name: String) = requireNotNull(javaClass.getResourceAsStream("/xg/$name")) { name }.readBytes()
    private val xg by lazy { XgImport.parse(bytes("match-11pt.xg")) }
    private val mat by lazy { MatImport.parse(String(bytes("match-11pt.mat"))) }

    private fun actions(m: MatImport.ImportedMatch) = m.games.map { g ->
        g.state.decisions.map { d -> listOf(d.player, d.kind, d.dice, d.move?.let { d.board.applyMove(d.player, it) }) }
    }

    @Test
    fun readsTheMatchHeader() {
        assertTrue(XgImport.isXg(bytes("match-11pt.xg")))
        assertFalse(XgImport.isXg(bytes("match-11pt.mat")))
        assertEquals(11, xg.match.rules.length)
        assertTrue(xg.match.rules.crawford)
        assertEquals(mat.names, xg.match.names)
        assertEquals(mat.games.size, xg.match.games.size)
        assertNotNull(xg.match.startedAt)
    }

    @Test
    fun replaysTheSameGamesAsTheMatFile() {
        for (you in 0..1) {
            val a = MatImport.replay(xg.match, you)
            val b = MatImport.replay(mat, you)
            assertEquals(b.score, a.score)
            assertEquals(b.winner, a.winner)
            assertEquals(b.games.map { it.score }, a.games.map { it.score })
            assertEquals(b.games.map { it.state.result }, a.games.map { it.state.result })
            assertEquals(actions(b), actions(a))
        }
    }

    @Test
    fun keepsXgsAnalysis() {
        val plain = MatImport.replay(xg.match, 0)
        val m = XgImport.withAnalysis(plain, xg, 0)
        assertEquals(actions(plain), actions(m))
        val decisions = m.games.flatMap { it.state.decisions }
        val checkers = decisions.filter { it.kind == DecisionKind.CHECKER }
        // XG analyzed every checker play of this file.
        assertTrue(checkers.all { !it.pending && it.checker != null && it.checker!!.engine == XgImport.ENGINE && it.checker!!.imported })
        for (d in checkers) {
            val a = d.checker!!
            if (a.legalMoves == 0) continue
            assertEquals(d.board.applyMove(d.player, d.move!!), d.board.applyMove(d.player, a.candidates[a.playedIndex].move))
            assertEquals(a.candidates[0].equity, a.bestEquity, 0f)
            assertEquals(a.candidates[a.playedIndex].equity, a.playedEquity, 0f)
            assertEquals(a.error, d.error!!, 0f)
            assertTrue(a.candidates.all { c -> c.probs.size == 5 && c.probs.all { it in 0f..1f } })
        }
        // Doubles and the answers to them carry XG's cube evaluation, from the doubler's side.
        val doubles = decisions.filter { it.kind == DecisionKind.DOUBLE }
        assertEquals(7, doubles.size)
        assertTrue(doubles.all { it.cubeEval?.engine == XgImport.ENGINE && !it.pending })
        assertTrue(decisions.filter { it.kind.isResponse }.all { it.cubeEval?.engine == XgImport.ENGINE && !it.pending })
        // What XG did not analyze waits for PlyGround's own analysis.
        assertTrue(decisions.all { it.pending == (it.checker == null && it.cubeEval == null) })
    }

    @Test
    fun valuesAreXgs() {
        val m = XgImport.withAnalysis(MatImport.replay(xg.match, 0), xg, 0)
        val decisions = m.games.flatMap { it.state.decisions }
        // The match's doubles in order, as XG shows them: no double, double/take, and the level.
        val doubles = decisions.filter { it.kind == DecisionKind.DOUBLE }.map { it.cubeEval!! }
        assertEquals(0.5227f, doubles[0].noDouble, 0.0001f)
        assertEquals(0.5432f, doubles[0].doubleTake, 0.0001f)
        assertEquals(1f, doubles[0].doublePass, 0.0001f)
        assertEquals("eXtreme Gammon 3-ply", doubles[0].label)
        assertEquals("eXtreme Gammon rollout", doubles[2].label)
        // The third double was a mistake of 0.020 by XG's rollout; the second was passed correctly.
        assertEquals(0.0201f, decisions.filter { it.kind == DecisionKind.DOUBLE }[2].error!!, 0.0001f)
        assertEquals(0f, decisions.first { it.kind == DecisionKind.PASS }.error!!, 0f)
        assertTrue(decisions.first { it.kind == DecisionKind.CHECKER }.checker!!.label.startsWith("eXtreme Gammon "))
    }

    @Test
    fun survivesJson() {
        val m = XgImport.withAnalysis(MatImport.replay(xg.match, 1), xg, 1)
        val g = m.games.first().state
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        assertEquals(g.decisions, json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), g)).decisions)
    }

    @Test
    fun theOriginalAnalysisComesBackAfterAnalyzingAgain() {
        val full = XgImport.withAnalysis(MatImport.replay(xg.match, 0), xg, 0).games.first().state
        // As if XG had left one decision out: it waits for PlyGround's analysis.
        val left = full.decisions.indexOfFirst { it.kind == DecisionKind.NO_DOUBLE }
        val original = full.copy(decisions = full.decisions.mapIndexed { i, d ->
            if (i == left) d.copy(pending = true, cubeEval = null, error = null, counted = false) else d
        })
        assertFalse(XgImport.reanalyzed(original, original))
        // The background analysis fills in what XG left out: that is not a new analysis of the file's.
        val filled = original.copy(decisions = original.decisions.map { d ->
            if (d.pending) d.copy(pending = false, cubeEval = CubeEval(0.1f, 0.2f, 1f, 0.2f, true, true, false, true, listOf(0.5f, 0.1f, 0f, 0.1f, 0f), engine = "PureTD", plies = 3), error = 0.1f) else d
        })
        assertTrue(original.decisions.any { it.pending })
        assertFalse(XgImport.reanalyzed(original, filled))
        // Analyze again: every decision gets PureTD's analysis.
        val again = filled.copy(decisions = filled.decisions.map { d ->
            d.copy(checker = d.checker?.copy(engine = "PureTD", level = null, plies = 3), cubeEval = d.cubeEval?.copy(engine = "PureTD", level = null, plies = 3))
        })
        assertTrue(XgImport.reanalyzed(original, again))
        val back = XgImport.restored(original, again)
        assertFalse(XgImport.reanalyzed(original, back))
        for ((i, d) in original.decisions.withIndex()) {
            // XG's own analysis is back exactly; what XG had not analyzed keeps PlyGround's.
            if (!d.pending) assertEquals(d, back.decisions[i]) else assertEquals(again.decisions[i], back.decisions[i])
        }
        assertTrue(back.decisions.none { it.pending })
    }

    @Test
    fun bearingOffWithALargerDie() {
        // XG stores a checker borne off as its point less the die: 6 from the 5-point is -2, not -1. Such a play
        // was read as no play at all ("a roll of 63 without a move").
        assertEquals(listOf(4 to -1, 2 to -1), XgImport.playedSteps(intArrayOf(4, -2, 2, -1, -1, -1, -1, -1)))
        assertEquals(listOf(3 to -1, 3 to 2), XgImport.playedSteps(intArrayOf(3, -2, 3, 2, -1, -1, -1, -1)))
        assertEquals(listOf(4 to 2, 0 to -1), XgImport.playedSteps(intArrayOf(4, 2, 0, -1, -1, -1, -1, -1)))
        assertEquals(listOf(24 to 18, 2 to 0), XgImport.playedSteps(intArrayOf(24, 18, 2, 0, -1, -1, -1, -1)))
        assertEquals(listOf(5 to -1, 5 to -1, 5 to -1, 4 to -1), XgImport.playedSteps(intArrayOf(5, -1, 5, -1, 5, -1, 4, -6)))
        assertEquals(emptyList<Pair<Int, Int>>(), XgImport.playedSteps(IntArray(8) { -1 }))
        // A roll the player resigned after: XG leaves the play as zeros (was read as "1/1 1/1 1/1 1/1").
        assertEquals(emptyList<Pair<Int, Int>>(), XgImport.playedSteps(IntArray(8)))
    }

    @Test
    fun aGameThatCannotBeReplayedIsLeftOutOfAnXgImport() {
        val parsed = XgImport.parse(bytes("match-11pt.xg")).match
        val second = parsed.games[1]
        val edited = MatImport.ParsedMatch(
            parsed.rules, parsed.names, parsed.startedAt,
            parsed.games.map { if (it === second) MatImport.ParsedGame(it.number, it.score, it.entries, unreadable = "the position was edited.") else it },
        )
        // A .mat-style import fails on it; an .xg import leaves the game out and keeps the rest.
        assertThrows(MatImport.FormatException::class.java) { MatImport.replay(edited, 0) }
        val im = MatImport.replay(edited, 0, skipBad = true)
        assertEquals(listOf(second.number to "the position was edited."), im.skipped)
        assertEquals(parsed.games.size - 1, im.games.size)
        assertFalse(im.games.any { it.number == second.number })
        assertEquals(im.skipped, XgImport.withAnalysis(im, XgImport.parse(bytes("match-11pt.xg")), 0).skipped)
        // A broken game is left out the same way.
        val broken = MatImport.ParsedMatch(
            parsed.rules, parsed.names, parsed.startedAt,
            parsed.games.map { if (it === second) MatImport.ParsedGame(it.number, it.score, it.entries.take(3) + it.entries.drop(5)) else it },
        )
        assertEquals(listOf(second.number), MatImport.replay(broken, 0, skipBad = true).skipped.map { it.first })
    }

    @Test
    fun refusesOtherFiles() {
        assertThrows(MatImport.FormatException::class.java) { XgImport.parse(bytes("match-11pt.mat")) }
        val cut = bytes("match-11pt.xg").copyOf(20000)
        assertThrows(MatImport.FormatException::class.java) { XgImport.parse(cut) }
        // Damage inside the compressed match itself.
        val broken = bytes("match-11pt.xg").also { for (i in it.size - 30000 until it.size - 29000) it[i] = 0x55 }
        assertThrows(MatImport.FormatException::class.java) { XgImport.parse(broken) }
    }
}
