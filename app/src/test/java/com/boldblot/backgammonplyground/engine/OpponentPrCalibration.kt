package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.AnalysisSettings
import com.boldblot.backgammonplyground.core.CpuLevel
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.DiceSource
import com.boldblot.backgammonplyground.core.MatchController
import com.boldblot.backgammonplyground.core.MatchRules
import com.boldblot.backgammonplyground.core.MatchState
import com.boldblot.backgammonplyground.core.MistakeRate
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.PerformanceTotals
import com.boldblot.backgammonplyground.core.Phase
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.Summaries
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Measures the PR of each opponent on the new-match list (card #60) as the app would show it: money
 * games (Jacoby) against the engine's best 1-ply play, the computer's decisions analyzed as the app
 * does (checker 3-ply within 0.16; cube at 3-ply rather than the app's 4-ply, to keep the run short).
 * The engine's own error at 3-ply is not counted (it grades itself), so PR on XG's scale is higher by
 * about PureTD 3-ply's own PR. Off unless
 * -PbgCalibrateGames=N is given; prints one line per opponent.
 */
class OpponentPrCalibration {

    @Test
    fun measureOpponentPr() {
        val games = System.getProperty("bg.calibrate.games")?.toIntOrNull()
        assumeTrue("calibration not requested", games != null && TestEngine.engine != null)
        val e = TestEngine.engine!!
        // Maximum is left out: graded by the depth it plays at, its PR here is 0 by construction.
        // -PbgCalibrateSet=plain measures each checker depth on its own (no added mistakes).
        val opponents = when (System.getProperty("bg.calibrate.set")) {
            "plain" -> (1..2).map { "plain ${it}-ply checker, 3-ply cube" to MistakeRate(0f, 0f, checkerPlies = it, cubePlies = 3) }
            else -> MistakeRate.presets.map { "${it.name} ${it.description}" to it.rate }
        }
        for ((name, rate) in opponents) {
            val t0 = System.nanoTime()
            val perGame = ArrayList<Double>()
            var total = PerformanceTotals()
            for (i in 0 until games!!) {
                val rnd = Random(1000 + i)
                val c = MatchController(
                    e, DiceSource { rnd.nextInt(1, 7) },
                    AnalysisSettings(plies = 0, cubePlies = Depth(3).code, cpuPlies = Depth(3, MoveFilter.WIDE).code),
                )
                val level = CpuLevel.MISTAKES
                var m = MatchState(rules = MatchRules(length = 0, jacoby = true), cpuLevel = level, mistakes = rate)
                m = c.openingRoll(m)
                var guard = 0
                while (m.game.phase != Phase.OVER && guard++ < 3000) {
                    val g = m.game
                    m = when {
                        g.resignOffer != null -> c.humanAnswerResign(m, accept = true)
                        g.phase == Phase.DOUBLED && g.responder == Player.HUMAN -> c.humanRespond(m, take = c.analyzeCube(m).shouldTake).first
                        g.turn == Player.CPU -> c.cpuStep(m).first
                        g.phase == Phase.PRE_ROLL -> {
                            val dbl = g.mayDouble(m.rules, m.score) && c.analyzeCube(m).shouldDouble
                            if (dbl) c.humanDouble(m).first else c.humanRoll(m).first
                        }
                        else -> {
                            val d = g.dice!!
                            c.humanMove(m, e.chooseMove(g.board, m.ctx(), d.d1, d.d2, CpuLevel.EXPERT)).first
                        }
                    }
                }
                val p = Summaries.of(m.game, 0).performance[Player.CPU]
                total += p
                p.pr?.let { perGame += it }
            }
            val mean = perGame.average()
            val sd = sqrt(perGame.sumOf { (it - mean) * (it - mean) } / (perGame.size - 1).coerceAtLeast(1))
            println(
                "CALIBRATION %-28s PR %.2f (pooled) | per game %.2f ± %.2f | %d decisions, %d games, est %.1f, %.0f s".format(
                    name, total.pr ?: -1.0, mean, sd / sqrt(perGame.size.toDouble()), total.decisions, games,
                    rate.estimatedPr, (System.nanoTime() - t0) / 1e9,
                ),
            )
        }
    }
}
