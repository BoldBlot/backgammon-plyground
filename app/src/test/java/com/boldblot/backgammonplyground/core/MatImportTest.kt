package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MatImportTest {

    private val rules = MatchRules(length = 5)

    /** A random game played to the end, with an early double taken now and then. */
    private fun randomGame(seed: Int): GameState {
        val r = Random(seed)
        var h: Int
        var c: Int
        do { h = r.nextInt(1, 7); c = r.nextInt(1, 7) } while (h == c)
        var g = GameState().openingRoll(h, c)
        var turns = 0
        while (g.phase != Phase.OVER && turns++ < 2000) {
            when (g.phase) {
                Phase.MOVING -> {
                    val d = g.dice!!
                    val moves = MoveGenerator.legalMoves(g.board, g.turn, d.d1, d.d2).filter { !it.isEmpty }
                    val m = if (moves.isEmpty()) Move.NONE else moves[r.nextInt(moves.size)]
                    g = g.record(DecisionRecord(g.turn, DecisionKind.CHECKER, g.board, g.cube, listOf(0, 0), false, dice = d, move = m)).move(m, rules)
                }
                Phase.PRE_ROLL -> {
                    if (g.mayDouble(rules, listOf(0, 0)) && r.nextInt(25) == 0) {
                        g = g.record(DecisionRecord(g.turn, DecisionKind.DOUBLE, g.board, g.cube, listOf(0, 0), false)).double()
                        g = g.record(DecisionRecord(g.responder, DecisionKind.TAKE, g.board, g.cube, listOf(0, 0), false)).take()
                    } else g = g.roll(Dice(r.nextInt(1, 7), r.nextInt(1, 7)))
                }
                else -> error("unexpected ${g.phase}")
            }
        }
        return g
    }

    private fun actions(g: GameState) = g.decisions.filter { it.kind != DecisionKind.NO_DOUBLE }.map {
        Triple(it.player, it.kind, if (it.move != null) it.board.applyMove(it.player, it.move!!) else it.board)
    }

    @Test
    fun exportedGamesReadBackTheSame() {
        for (seed in 1..40) {
            val g = randomGame(seed)
            val text = MatExport.write(
                MatExport.Info(rules, listOf("Me", "PureTD PR 4"), 0),
                listOf(MatExport.Game(1, listOf(0, 0), g)),
            )
            val parsed = MatImport.parse(text)
            assertEquals(listOf("PureTD PR 4", "Me"), parsed.names)
            // The human is the right column of PlyGround's files.
            val m = MatImport.replay(parsed, you = 1)
            val back = m.games.single().state
            assertEquals("seed $seed", actions(g), actions(back))
            assertEquals("seed $seed", g.result, back.result)
            assertTrue(back.decisions.all { it.pending })
        }
    }

    @Test
    fun readsXgStyleNotation() {
        val text = """
            ; [Player 1 "Alice"]
            ; [Player 2 "Bob"]
            ; [EventDate "2026.10.30"]

             3 point match

             Game 1
             Alice : 0                      Bob : 0
              1) 31: 8/5 6/5                  66: 24/18(2) 13/7(2)
              2) 52: 13/8 13/11                63: 18/9
              3)  Doubles => 2                 Takes
              4) 64: 24/14
        """.trimIndent() + "\n" + " ".repeat(34) + "Wins 2 points\n"
        val p = MatImport.parse(text)
        assertEquals(3, p.rules.length)
        assertEquals(listOf("Alice", "Bob"), p.names)
        val m = MatImport.replay(p, you = 0)
        val g = m.games.single().state
        assertEquals(GameResult(Player.CPU, WinType.SINGLE, 2, resigned = true), g.result)
        // Bob's 24/18(2) 13/7(2) and the run 18/9 (one checker, 3 and 6).
        val bob = g.decisions.filter { it.player == Player.CPU && it.kind == DecisionKind.CHECKER }
        assertEquals(2, bob.size)
        assertEquals(4, bob[0].move!!.steps.size)
        assertEquals(listOf(0, 2), m.score)
        assertTrue(g.decisions.any { it.kind == DecisionKind.TAKE && it.player == Player.CPU })
    }

    @Test
    fun crawfordGameIsFound() {
        val game = { n: Int, a: Int, b: Int, winner: Int ->
            " Game $n\n Alice : $a                      Bob : $b\n  1) 31: 8/5 6/5\n" +
                (if (winner == 0) "      Wins 1 point\n" else " ".repeat(34) + "Wins 1 point\n") + "\n"
        }
        val text = " 3 point match\n\n" + game(1, 0, 0, 0) + game(2, 1, 0, 0) + game(3, 2, 0, 1) + game(4, 2, 1, 1)
        val m = MatImport.replay(MatImport.parse(text), you = 0)
        assertEquals(listOf(false, false, true, false), m.games.map { it.state.crawford })
    }
}

class MatImportFilesTest {
    /** Every game in a file that ends with "Wins" is read and replayed. */
    private fun check(name: String, text: String) {
        val p = MatImport.parse(text)
        val finished = Regex("""Wins\s+\d+\s+point""").findAll(text).count()
        for (you in 0..1) {
            val m = try { MatImport.replay(p, you) } catch (e: MatImport.FormatException) { throw AssertionError("$name: ${e.message}") }
            assertEquals(name, finished, m.games.size)
        }
    }

    @Test
    fun readsGnubgAndPlyGroundFiles() {
        val dir = java.io.File(javaClass.classLoader!!.getResource("mat")!!.toURI())
        val files = dir.listFiles { f -> f.name.endsWith(".mat") }!!
        assertTrue(files.size >= 5)
        files.forEach { check(it.name, it.readText()) }
        // More files, when present (not in the repository).
        java.io.File("build/mat-check").listFiles { f -> f.name.endsWith(".mat") }?.forEach { check(it.name, it.readText()) }
    }
}
