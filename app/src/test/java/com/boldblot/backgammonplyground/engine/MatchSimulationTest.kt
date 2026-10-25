package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.AnalysisSettings
import com.boldblot.backgammonplyground.core.Blunders
import com.boldblot.backgammonplyground.core.GameState
import com.boldblot.backgammonplyground.core.CpuLevel
import com.boldblot.backgammonplyground.core.DecisionKind
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.Reanalysis
import com.boldblot.backgammonplyground.core.DiceSource
import com.boldblot.backgammonplyground.core.MatchController
import com.boldblot.backgammonplyground.core.MatchRules
import com.boldblot.backgammonplyground.core.MatchState
import com.boldblot.backgammonplyground.core.MistakeRate
import com.boldblot.backgammonplyground.core.MoveGenerator
import com.boldblot.backgammonplyground.core.Phase
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.PerformanceTotals
import com.boldblot.backgammonplyground.core.Summaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.random.Random

/** Plays complete matches through [MatchController] against the real engine. */
class MatchSimulationTest {

    companion object {
        private val engine: PureTdEngine? get() = TestEngine.engine
    }

    /**
     * Plays a match where the human side is driven by [humanPolicy]:
     * "best" = engine's best 0-ply move and correct cube actions; "random" = random legal moves.
     */
    private fun playMatch(
        rules: MatchRules, seed: Int, humanPolicy: String, cpuPlies: Int? = 0, maxGames: Int = 7,
        level: CpuLevel = CpuLevel.EXPERT, mistakes: MistakeRate? = null,
        onGame: (GameState) -> Unit = {},
    ): List<PerformanceTotals> {
        val e = engine!!
        val rnd = Random(seed)
        val c = MatchController(e, DiceSource { rnd.nextInt(1, 7) }, AnalysisSettings(plies = 0, cubePlies = 0, cpuPlies = cpuPlies))
        val auto = humanPolicy == "auto"
        var m = MatchState(rules = rules, cpuLevel = level, mistakes = mistakes)
        val totals = MutableList(2) { PerformanceTotals() }
        var games = 0
        while (!m.isOver && games < maxGames) {
            m = c.openingRoll(m)
            var guard = 0
            while (m.game.phase != Phase.OVER) {
                check(guard++ < 2000) { "game did not finish" }
                val g = m.game
                if (g.resignOffer != null) {
                    m = c.humanAnswerResign(m, accept = true)
                } else if (g.phase == Phase.DOUBLED && g.responder == Player.HUMAN) {
                    val eval = if (auto) c.autoCube(m) else c.analyzeCube(m)
                    val take = if (humanPolicy != "random") eval.shouldTake else rnd.nextBoolean()
                    // Only with beavers, so that the random sequences of the other tests stay as they were.
                    val beaver = m.rules.beaversAllowed && (if (humanPolicy != "random") eval.doubleTake < 0f else rnd.nextBoolean())
                    m = c.humanRespond(m, take = take, auto = auto, beaver = beaver).first
                } else if (g.phase == Phase.BEAVERED) {
                    m = c.humanAnswerBeaver(m, raccoon = if (humanPolicy == "random") rnd.nextBoolean() else c.analyzeCube(m).doubleTake > 0f).first
                } else if (g.turn == Player.CPU) {
                    m = c.cpuStep(m).first
                } else if (g.phase == Phase.PRE_ROLL) {
                    val shouldDouble = g.mayDouble(m.rules, m.score) && (if (auto) c.autoCube(m) else c.analyzeCube(m)).shouldDouble
                    // With beavers, the random player also doubles at random (so that the computer may beaver).
                    val randomDouble = humanPolicy == "random" && m.rules.beaversAllowed && g.mayDouble(m.rules, m.score) && rnd.nextInt(4) == 0
                    m = if ((humanPolicy != "random" && shouldDouble) || randomDouble) c.humanDouble(m, auto = auto).first else c.humanRoll(m, auto = auto).first
                } else if (auto) {
                    m = c.humanMove(m, c.autoMove(m), auto = true).first
                } else {
                    val d = g.dice!!
                    val move = if (humanPolicy == "best") {
                        e.chooseMove(g.board, m.ctx(), d.d1, d.d2, CpuLevel.EXPERT)
                    } else {
                        MoveGenerator.legalMoves(g.board, Player.HUMAN, d.d1, d.d2).random(rnd)
                    }
                    m = c.humanMove(m, move).first
                }
            }
            val s = Summaries.of(m.game, rules.length)
            totals[0] = totals[0] + s.performance[0]
            totals[1] = totals[1] + s.performance[1]
            // Every recorded decision carries analysis, except the computer's when only the human's are analyzed.
            for (d in m.game.decisions) {
                if ((d.player == Player.CPU && cpuPlies == null) || d.auto) {
                    assertNull(d.error)
                    continue
                }
                d.checker?.let { assertEquals(if (d.player == Player.CPU) cpuPlies else 0, it.plies) }
                if (d.kind == DecisionKind.CHECKER) assertNotNull(d.checker)
                if (d.kind != DecisionKind.CHECKER) assertNotNull(d.cubeEval)
            }
            onGame(m.game)
            m = m.finishGame()
            games++
        }
        return totals
    }

    @Test
    fun beaversAndRaccoonsPlayThroughAndAnalyzeAgain() {
        assumeTrue(engine != null)
        val games = mutableListOf<GameState>()
        playMatch(MatchRules(length = 0, beavers = true, raccoons = true), seed = 5, humanPolicy = "random", maxGames = 40) { games += it }
        val kinds = games.flatMap { g -> g.decisions.map { it.kind } }.toSet()
        println("decision kinds with beavers: $kinds")
        assertTrue("someone beavered", DecisionKind.BEAVER in kinds)
        assertTrue("a beaver was answered", DecisionKind.RACCOON in kinds || DecisionKind.NO_RACCOON in kinds)
        // A beaver is answered by the other player (the doubler).
        for (g in games) for ((i, d) in g.decisions.withIndex()) {
            if (d.kind != DecisionKind.BEAVER) continue
            assertNotNull(d.cubeEval)
            val next = g.decisions.getOrNull(i + 1)
            if (next != null && next.kind.isRaccoonDecision) assertEquals(Player.other(d.player), next.player)
        }
        // Analyzing again at another depth keeps every decision and analyzes each one.
        val re = Reanalysis(engine!!, Depth(1).code, Depth(2).code)
        val g = games.first { it.decisions.any { d -> d.kind == DecisionKind.BEAVER } }
        for (d in g.decisions) {
            if (!re.wanted(d, cpu = true)) continue
            val r = re.decision(d, matchLength = 0, jacoby = true)
            assertEquals(d.kind, r.kind)
            if (d.kind == DecisionKind.CHECKER && r.checker != null) assertEquals(Depth(1).code, r.checker!!.plies)
            if (d.kind != DecisionKind.CHECKER) assertNotNull(r.error)
        }
    }

    @Test
    fun bestPlayHasNearZeroPrAndRandomPlayIsTerrible() {
        assumeTrue("host engine library not provided", engine != null)
        val best = playMatch(MatchRules(length = 5), seed = 1, humanPolicy = "best")
        val random = playMatch(MatchRules(length = 5), seed = 2, humanPolicy = "random")
        val bestPr = best[0].pr!!
        val randomPr = random[0].pr!!
        println("best-play PR %.2f (%d decisions), random PR %.2f, cpu PR %.2f".format(
            bestPr, best[0].decisions, randomPr, best[1].pr ?: -1.0))
        assertTrue("engine-best play should be near perfect at 0-ply: $bestPr", bestPr < 1.0)
        assertTrue("random play should be awful: $randomPr", randomPr > 30.0)
        assertTrue(best[0].checkerDecisions > 0)
        assertTrue(best[0].rolls > 0)
    }

    @Test
    fun moneySessionWithJacobyRuns() {
        assumeTrue(engine != null)
        val t = playMatch(MatchRules(length = 0, jacoby = true), seed = 3, humanPolicy = "best")
        assertTrue(t[0].moves > 0)
        assertEquals(0, t[0].checkerBlunders)
    }

    @Test
    fun onePointMatchRuns() {
        assumeTrue(engine != null)
        val t = playMatch(MatchRules(length = 1), seed = 4, humanPolicy = "random")
        assertTrue(t[0].pr!! > 0)
    }

    @Test
    fun analyzingOnlyTheHumanLeavesTheComputerUnrated() {
        assumeTrue(engine != null)
        var blunders = 0
        val t = playMatch(MatchRules(length = 5), seed = 5, humanPolicy = "random", cpuPlies = null) { g ->
            blunders += Blunders.of(g, 5).size
        }
        assertTrue(t[0].pr!! > 0)
        assertTrue("random play blunders", blunders > 0)
        assertEquals(0, t[1].decisions)
        assertNull(t[1].pr)
        assertTrue("forced and unforced computer moves still count as moves", t[1].moves > 0)
    }

    @Test
    fun mistakeRateOpponentMakesErrorsAndBlunders() {
        assumeTrue(engine != null)
        val careless = MistakeRate(0.5f, 0.3f)
        val t = playMatch(MatchRules(length = 1), seed = 8, humanPolicy = "best", level = CpuLevel.MISTAKES, mistakes = careless, maxGames = 1)
        assertTrue("errors or blunders made", t[1].checkerErrors + t[1].checkerBlunders > 0)
        val clean = playMatch(MatchRules(length = 1), seed = 8, humanPolicy = "best", level = CpuLevel.MISTAKES, mistakes = MistakeRate(0f, 0f), maxGames = 1)
        assertTrue("no blunders at a zero rate", clean[1].checkerBlunders == 0)
        assertTrue(t[1].pr!! > clean[1].pr!!)
    }

    @Test
    fun computerAnalyzedAtItsOwnDepth() {
        assumeTrue(engine != null)
        // cpuPlies = 1, human 0: checked for every decision in playMatch.
        val t = playMatch(MatchRules(length = 3), seed = 6, humanPolicy = "random", cpuPlies = 1, maxGames = 1)
        assertTrue(t[1].checkerDecisions > 0)
    }

    @Test
    fun autofinishedDecisionsAreNotAnalyzedOrCounted() {
        assumeTrue(engine != null)
        var games = 0
        val t = playMatch(MatchRules(length = 3), seed = 7, humanPolicy = "auto", maxGames = 2) { g ->
            games++
            assertTrue(g.decisions.filter { it.player == Player.HUMAN }.all { it.auto && !it.counted })
            assertTrue(Blunders.of(g, 3).isEmpty())
        }
        assertTrue(games > 0)
        assertEquals(0, t[0].decisions)
        assertNull(t[0].pr)
        assertTrue("the computer is still analyzed", t[1].decisions > 0)
    }
}
