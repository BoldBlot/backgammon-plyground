package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.AnalysisSettings
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.CubeState
import com.boldblot.backgammonplyground.core.GameState
import com.boldblot.backgammonplyground.core.CpuLevel
import com.boldblot.backgammonplyground.core.DiceSource
import com.boldblot.backgammonplyground.core.MatExport
import com.boldblot.backgammonplyground.core.MatchController
import com.boldblot.backgammonplyground.core.MatchRules
import com.boldblot.backgammonplyground.core.MatchState
import com.boldblot.backgammonplyground.core.MoveGenerator
import com.boldblot.backgammonplyground.core.Phase
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.Resignation
import com.boldblot.backgammonplyground.core.WinType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Plays matches with resignations (both sides, accepted and rejected) against the real
 * engine and exports every game and every match as .mat files. scripts/check-mat.sh then
 * imports those files into GNU Backgammon and compares its re-export with ours.
 */
class ResignExportSimulationTest {

    companion object {
        private val engine: PureTdEngine? get() = TestEngine.engine

        private val names = listOf("Player", "gnubg Expert") // by player index
    }

    private class Counts {
        var humanOffers = 0
        var humanAccepted = 0
        var cpuOffers = 0
        var games = 0
    }

    private fun playMatch(tag: String, rules: MatchRules, seed: Int, counts: Counts, maxGames: Int = 8) {
        val e = engine!!
        val rnd = Random(seed)
        val c = MatchController(e, DiceSource { rnd.nextInt(1, 7) }, AnalysisSettings(plies = 0, cubePlies = 0, cpuPlies = null))
        var m = MatchState(rules = rules, cpuLevel = CpuLevel.EXPERT, startedAt = 1_760_000_000_000)
        val exported = mutableListOf<MatExport.Game>()
        while (!m.isOver && exported.size < maxGames) {
            m = c.openingRoll(m)
            var guard = 0
            while (m.game.phase != Phase.OVER) {
                check(guard++ < 3000) { "game did not finish" }
                val g = m.game
                when {
                    g.resignOffer != null -> {
                        counts.cpuOffers++
                        // The offer must follow the rules for the computer's own chances.
                        val probs = e.cubeDecision(g.board, m.ctx(Player.CPU), 0).probs
                        val highest = Resignation.highestCounted(m.rules, g.cube, m.score, Player.HUMAN)
                        assertEquals(Resignation.offer(probs, highest), g.resignOffer)
                        m = c.humanAnswerResign(m, accept = rnd.nextInt(4) > 0)
                    }
                    g.phase == Phase.DOUBLED && g.responder == Player.HUMAN ->
                        m = c.humanRespond(m, take = c.analyzeCube(m).shouldTake).first
                    g.turn == Player.CPU -> m = c.cpuStep(m).first
                    g.phase == Phase.PRE_ROLL -> {
                        val own = c.analyzeCube(m).probs
                        if (own[0] < 0.03f && rnd.nextInt(3) == 0) {
                            val type = WinType.entries.random(rnd)
                            val highest = Resignation.highestCounted(m.rules, g.cube, m.score, Player.CPU)
                            val expected = Resignation.accepts(type, own[3], own[4], highest)
                            val (next, accepted) = c.humanResign(m, type)
                            assertEquals("acceptance of $type with cpu gammon ${own[3]} bg ${own[4]}", expected, accepted)
                            counts.humanOffers++
                            if (accepted) {
                                counts.humanAccepted++
                                assertTrue(next.game.result!!.resigned)
                                assertEquals(Player.CPU, next.game.result!!.winner)
                            }
                            m = next
                            continue
                        }
                        val shouldDouble = g.mayDouble(m.rules, m.score) && c.analyzeCube(m).shouldDouble
                        m = if (shouldDouble) c.humanDouble(m).first else c.humanRoll(m).first
                    }
                    else -> {
                        val d = g.dice!!
                        // Mostly sensible play with some random moves, so that games get lopsided.
                        if (rnd.nextInt(40) == 0) {
                            val (next, accepted) = c.humanResign(m, WinType.entries.random(rnd))
                            counts.humanOffers++
                            if (accepted) counts.humanAccepted++
                            m = next
                            continue
                        }
                        val move = if (rnd.nextInt(3) == 0) {
                            MoveGenerator.legalMoves(g.board, Player.HUMAN, d.d1, d.d2).random(rnd)
                        } else {
                            e.chooseMove(g.board, m.ctx(), d.d1, d.d2, CpuLevel.EXPERT)
                        }
                        m = c.humanMove(m, move).first
                    }
                }
            }
            val r = m.game.result!!
            if (r.resigned) {
                val mult = r.points / m.game.cube.value
                assertTrue("points ${r.points} cube ${m.game.cube.value}", mult in 1..3 && r.points % m.game.cube.value == 0)
            }
            exported += MatExport.Game(m.gameNumber, m.score, m.game)
            counts.games++
            m = m.finishGame()
        }
        write(tag, rules, exported)
    }

    private fun write(tag: String, rules: MatchRules, games: List<MatExport.Game>) {
        val dir = File(System.getProperty("bg.exportdir") ?: return).also { it.mkdirs() }
        val info = MatExport.Info(rules, names, 1_760_000_000_000)
        File(dir, "match-$tag.mat").writeText(MatExport.write(info, games))
        for (g in games) File(dir, "game-$tag-${g.number}.mat").writeText(MatExport.write(info, listOf(g)))
    }

    /** [loser] has 15 checkers on its own point index [loserPoint]; the winner has one checker left on its 1-point. */
    private fun decided(loser: Int, loserPoint: Int): Board {
        val l = IntArray(25).also { it[loserPoint] = 15 }
        val w = IntArray(25).also { it[0] = 1 }
        return if (loser == Player.HUMAN) Board.fromSides(l, w) else Board.fromSides(w, l)
    }

    private fun state(board: Board, turn: Int, rules: MatchRules, score: List<Int> = listOf(0, 0), cube: CubeState = CubeState()) =
        MatchState(
            rules = rules, cpuLevel = CpuLevel.EXPERT, score = score,
            game = GameState(board = board, turn = turn, phase = Phase.PRE_ROLL, cube = cube),
        )

    @Test
    fun computerResignsExactlyWhatIsCertain() {
        assumeTrue("host engine library not provided", engine != null)
        val c = MatchController(engine!!, DiceSource { 3 }, AnalysisSettings(plies = 2))
        val money = MatchRules(length = 0, jacoby = false)
        // All home on the 1-point: bears off at least two checkers, so only a single game is lost.
        val (single, a1) = c.cpuStep(state(decided(Player.CPU, 0), Player.CPU, money))
        assertEquals(MatchController.CpuAction.OffersResign(WinType.SINGLE), a1)
        assertEquals(WinType.SINGLE, single.game.resignOffer)
        // On its 13-point: cannot bear off in time, cannot be backgammoned.
        assertEquals(MatchController.CpuAction.OffersResign(WinType.GAMMON), c.cpuStep(state(decided(Player.CPU, 12), Player.CPU, money)).second)
        // In the opponent's home board: backgammon.
        assertEquals(MatchController.CpuAction.OffersResign(WinType.BACKGAMMON), c.cpuStep(state(decided(Player.CPU, 20), Player.CPU, money)).second)
        // A gammon that does not count (money, Jacoby, cube centred) is resigned as a single game.
        assertEquals(
            MatchController.CpuAction.OffersResign(WinType.SINGLE),
            c.cpuStep(state(decided(Player.CPU, 12), Player.CPU, MatchRules(length = 0, jacoby = true))).second,
        )
        // Opening position: plays on.
        assertTrue(c.cpuStep(state(Board.INITIAL, Player.CPU, money)).second !is MatchController.CpuAction.OffersResign)

        // Rejected offers are not repeated at the same level; accepting ends the game with the right points.
        val rejected = c.humanAnswerResign(single, accept = false)
        assertEquals(null, rejected.game.resignOffer)
        assertTrue(c.cpuStep(rejected).second !is MatchController.CpuAction.OffersResign)
        val gammonOffer = c.cpuStep(state(decided(Player.CPU, 12), Player.CPU, money, cube = CubeState(2, Player.CPU))).first
        val over = c.humanAnswerResign(gammonOffer, accept = true).game
        assertEquals(Phase.OVER, over.phase)
        assertEquals(Player.HUMAN, over.result!!.winner)
        assertEquals(4, over.result!!.points)
        assertTrue(over.result!!.resigned)
    }

    @Test
    fun computerJudgesResignations() {
        assumeTrue("host engine library not provided", engine != null)
        val c = MatchController(engine!!, DiceSource { 3 }, AnalysisSettings(plies = 2))
        val money = MatchRules(length = 0, jacoby = false)
        // The human is gammoned for sure (but not backgammoned).
        val gammoned = state(decided(Player.HUMAN, 12), Player.HUMAN, money)
        assertEquals(false, c.humanResign(gammoned, WinType.SINGLE).second)
        assertEquals(true, c.humanResign(gammoned, WinType.GAMMON).second)
        assertEquals(true, c.humanResign(gammoned, WinType.BACKGAMMON).second)
        // Backgammoned for sure: only a backgammon is accepted.
        val backgammoned = state(decided(Player.HUMAN, 20), Player.HUMAN, money)
        assertEquals(false, c.humanResign(backgammoned, WinType.GAMMON).second)
        assertEquals(true, c.humanResign(backgammoned, WinType.BACKGAMMON).second)
        // Gammons do not count under the Jacoby rule with the cube centred...
        assertEquals(true, c.humanResign(state(decided(Player.HUMAN, 12), Player.HUMAN, MatchRules(length = 0, jacoby = true)), WinType.SINGLE).second)
        // ...nor when a single game already wins the match for the computer.
        assertEquals(true, c.humanResign(state(decided(Player.HUMAN, 12), Player.HUMAN, MatchRules(length = 5), score = listOf(2, 4)), WinType.SINGLE).second)
        assertEquals(false, c.humanResign(state(decided(Player.HUMAN, 12), Player.HUMAN, MatchRules(length = 5), score = listOf(2, 2)), WinType.SINGLE).second)
        // Rejected: the game goes on; accepted: the computer wins with the cube value x resigned level.
        val (rej, ok) = c.humanResign(gammoned, WinType.SINGLE)
        assertEquals(false, ok)
        assertEquals(Phase.PRE_ROLL, rej.game.phase)
        val acc = c.humanResign(state(decided(Player.HUMAN, 12), Player.HUMAN, money, cube = CubeState(2, Player.HUMAN)), WinType.GAMMON).first
        assertEquals(Player.CPU, acc.game.result!!.winner)
        assertEquals(4, acc.game.result!!.points)
    }

    @Test
    fun matchesWithResignationsExport() {
        assumeTrue("host engine library not provided", engine != null)
        val counts = Counts()
        playMatch("7pt", MatchRules(length = 7), seed = 11, counts)
        playMatch("5pt", MatchRules(length = 5), seed = 12, counts)
        playMatch("3pt", MatchRules(length = 3), seed = 13, counts)
        playMatch("1pt", MatchRules(length = 1), seed = 14, counts)
        playMatch("money", MatchRules(length = 0, jacoby = true), seed = 15, counts, maxGames = 6)
        playMatch("money-nojacoby", MatchRules(length = 0, jacoby = false), seed = 16, counts, maxGames = 6)
        playMatch("nocube", MatchRules(length = 5, useCube = false), seed = 17, counts)
        println("games ${counts.games}, human resignations ${counts.humanOffers} (${counts.humanAccepted} accepted), cpu offers ${counts.cpuOffers}")
        assertTrue("some resignations should happen", counts.humanOffers + counts.cpuOffers > 0)
    }
}
