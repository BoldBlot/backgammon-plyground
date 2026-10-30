package com.boldblot.backgammonplyground.ui.positions

import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.AnalysisSettings
import com.boldblot.backgammonplyground.core.CheckerAnalysis
import com.boldblot.backgammonplyground.core.DecisionKind
import com.boldblot.backgammonplyground.core.DecisionMath
import com.boldblot.backgammonplyground.core.DiceSource
import com.boldblot.backgammonplyground.core.MatchController
import com.boldblot.backgammonplyground.core.Move
import com.boldblot.backgammonplyground.core.MoveNotation
import com.boldblot.backgammonplyground.core.MyAnswer
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.PositionAnalysis
import com.boldblot.backgammonplyground.core.StudyPosition
import com.boldblot.backgammonplyground.core.XgRules
import com.boldblot.backgammonplyground.data.Settings
import com.boldblot.backgammonplyground.ui.components.describeAction
import com.boldblot.backgammonplyground.ui.components.eq

/** Analyzes [p] at the analysis depths in Settings, with the analysis engine. */
suspend fun analyzePosition(app: PlyGroundApp, p: StudyPosition, s: Settings): PositionAnalysis {
    val plies = s.checkerDepth.code
    val cube = s.cubeDepth.code
    return app.engine.run {
        val dice = p.dice
        if (dice != null) {
            val c = MatchController(this, DiceSource.secure(), AnalysisSettings(plies = plies, cubePlies = cube))
            PositionAnalysis(checker = c.analyzeChecker(p.board, p.ctx(), dice, null, plies), label = "${s.analysisEngine.label} ${s.checkerDepth.label()}")
        } else {
            PositionAnalysis(cube = cubeDecision(p.board, p.ctx(), cube), label = "${s.analysisEngine.label} ${s.cubeDepth.label(cube = true)}")
        }
    }
}

/** [move]'s equity in [a] (null if it is not among the stored candidates). */
fun equityOf(p: StudyPosition, a: CheckerAnalysis, move: Move): Float? {
    val target = p.board.applyMove(Player.HUMAN, move)
    return a.candidates.firstOrNull { p.board.applyMove(Player.HUMAN, it.move) == target }?.equity
}

/** What answer [ans] costs in [p] by analysis [a], after the 1-point factor; null if the analysis cannot say. */
fun answerError(p: StudyPosition, a: PositionAnalysis?, ans: MyAnswer): Float? {
    a ?: return null
    val raw = when {
        ans.move != null -> a.checker?.let { c -> equityOf(p, c, ans.move)?.let { (c.bestEquity - it).coerceAtLeast(0f) } }
        ans.cube != null -> cubeError(p, a, ans.cube)
        else -> null
    } ?: return null
    return raw * factorOf(p)
}

/** "24/20 8/5 (−0.323)": an answer with its cost when the analysis knows it. */
fun answerWithError(p: StudyPosition, a: PositionAnalysis?, ans: MyAnswer): String =
    answerText(p, ans) + (answerError(p, a, ans)?.takeIf { it >= 0.0005f }?.let { " (${eq(-it)})" } ?: "")

/** The cube actions a position offers: Double / No double, or Take / Pass (and Beaver). */
fun cubeActions(p: StudyPosition): List<DecisionKind> = when {
    !p.doubled -> listOf(DecisionKind.NO_DOUBLE, DecisionKind.DOUBLE)
    p.beavers -> listOf(DecisionKind.TAKE, DecisionKind.PASS, DecisionKind.BEAVER)
    else -> listOf(DecisionKind.TAKE, DecisionKind.PASS)
}

fun cubeLabel(k: DecisionKind): String = when (k) {
    DecisionKind.NO_DOUBLE -> "No double"
    DecisionKind.DOUBLE -> "Double"
    DecisionKind.TAKE -> "Take"
    DecisionKind.PASS -> "Pass"
    DecisionKind.BEAVER -> "Beaver"
    else -> k.name.lowercase()
}

/** The cost of cube action [k] in [p] by analysis [a] (raw equity, before the 1-point factor). */
fun cubeError(p: StudyPosition, a: PositionAnalysis, k: DecisionKind): Float? {
    val e = a.cube ?: return null
    return if (p.doubled) DecisionMath.takeError(e, k, p.beavers) else DecisionMath.doubleError(e, k == DecisionKind.DOUBLE, p.beavers)
}

/** The engine's right cube action in [p]. */
fun bestCube(p: StudyPosition, a: PositionAnalysis): DecisionKind? =
    cubeActions(p).minByOrNull { cubeError(p, a, it) ?: Float.MAX_VALUE }

/** The player's own answer as text ("13/7 8/7", "Double", "Take"). */
fun answerText(p: StudyPosition, a: MyAnswer): String = when {
    a.move != null -> MoveNotation.format(p.board, Player.HUMAN, a.move)
    a.cube != null -> cubeLabel(a.cube)
    else -> ""
}

/** The weighted error factor of [p] (×1.5 in a 1-point match, as for PR). */
fun factorOf(p: StudyPosition): Float = XgRules.errorFactor(p.setup.matchLength)

/** "Money game", "5-point match, 2–3, Crawford" and the cube, for a saved position. */
fun contextText(p: StudyPosition): String {
    val s = p.setup
    val game = if (s.matchLength == 0) "Money game" else
        "${s.matchLength}-point match, ${s.score[0]}–${s.score[1]}" + if (s.crawford) ", Crawford" else ""
    val cube = when {
        s.cube.value == 1 -> ""
        s.cube.owner == Player.HUMAN -> " · you own the cube at ${s.cube.value}"
        else -> " · the opponent owns the cube at ${s.cube.value}"
    }
    val what = when {
        p.dice != null -> " · to play ${p.dice}"
        p.doubled -> " · you are doubled"
        else -> " · cube decision"
    }
    return game + cube + what
}

/** What was played or answered, for lists: the move or the cube action. */
fun decisionText(p: StudyPosition, move: Move?, kind: DecisionKind?): String =
    if (move != null) describeAction(DecisionKind.CHECKER, p.board, Player.HUMAN, move) else kind?.let { cubeLabel(it) } ?: ""
