package com.boldblot.backgammonplyground.ui.positions

import com.boldblot.backgammonplyground.engine.Waiting
import com.boldblot.backgammonplyground.PlyGroundApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.boldblot.backgammonplyground.core.AnalysisSettings
import com.boldblot.backgammonplyground.core.CheckerAnalysis
import com.boldblot.backgammonplyground.core.DecisionKind
import com.boldblot.backgammonplyground.core.DecisionMath
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.EngineKind
import com.boldblot.backgammonplyground.core.DiceSource
import com.boldblot.backgammonplyground.core.MatchController
import com.boldblot.backgammonplyground.core.Move
import com.boldblot.backgammonplyground.core.MoveNotation
import com.boldblot.backgammonplyground.core.MyAnswer
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.Labels
import com.boldblot.backgammonplyground.core.PositionAnalysis
import com.boldblot.backgammonplyground.core.QuizFilter
import com.boldblot.backgammonplyground.core.StudyPosition
import com.boldblot.backgammonplyground.core.XgRules
import com.boldblot.backgammonplyground.data.SavedPosition
import com.boldblot.backgammonplyground.data.Settings
import com.boldblot.backgammonplyground.ui.components.describeAction
import com.boldblot.backgammonplyground.ui.components.err
import com.boldblot.backgammonplyground.data.ErrorUnit

/** Whether [p] passes [filter]: its labels (those in use, see [Settings.visible]) and its quiz answers ([QuizFilter]). */
fun passesFilter(p: SavedPosition, filter: Set<String>, s: Settings): Boolean {
    val labels = s.visible(filter)
    return (labels.isEmpty() || Labels.matches(s.visible(p.labels), labels)) &&
        QuizFilter.matches(p.entity.quizRight, p.entity.quizWrong, filter)
}

/** "3 right, 1 missed · balance +2". */
fun quizCountText(p: SavedPosition): String {
    val b = p.quizBalance
    return "${p.entity.quizRight} right, ${p.entity.quizWrong} missed · balance ${if (b > 0) "+$b" else "$b"}"
}

/** Analyzes [p] at the analysis depths in Settings, with the analysis engine. */
suspend fun analyzePosition(app: PlyGroundApp, p: StudyPosition, s: Settings, waiting: Waiting? = null): PositionAnalysis {
    val match = p.ctx().matchLength > 0
    return analyzePosition(app, p, s.analysisEngine, s.checkerDepth(match), s.cubeDepth(match), waiting)
}

/** Analyzes [p] with [engine]: a checker play at [checker], a cube decision at [cube]. */
suspend fun analyzePosition(app: PlyGroundApp, p: StudyPosition, engine: EngineKind, checker: Depth, cube: Depth, waiting: Waiting? = null): PositionAnalysis =
    app.engine.run(engine, waiting) {
        val dice = p.dice
        if (dice != null) {
            val c = MatchController(this, DiceSource.secure(), AnalysisSettings(plies = checker.code, cubePlies = cube.code))
            // The label says what was searched: without the games where they cannot be played, or stopped early.
            val a = c.analyzeChecker(p.board, p.ctx(), dice, null, checker.code)
            PositionAnalysis(checker = a, label = "${engine.label} ${Depth.of(a.plies).label()}")
        } else {
            val e = cubeDecision(p.board, p.ctx(), cube.code)
            PositionAnalysis(cube = e, label = "${engine.label} ${Depth.of(e.plies ?: cube.code).label(cube = true)}")
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

/** "24/20 8/5 (−0.323)": an answer with the equity it loses (plain, without the 1-point factor) in [unit], when the analysis knows it. */
fun answerWithError(p: StudyPosition, a: PositionAnalysis?, ans: MyAnswer, unit: ErrorUnit): String =
    answerText(p, ans) + (answerError(p, a, ans)?.div(factorOf(p))?.takeIf { it >= 0.0005f }?.let { " (${err(-it, unit)})" } ?: "")

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

/**
 * The quiz's answers to a doubling decision, as on FlashBack cards: the cube action with the answer it
 * expects from the opponent. [double]: double; [take]: the opponent should take (or beaver).
 */
enum class CubeAnswer(val label: String, val double: Boolean, val take: Boolean) {
    NO_DOUBLE("No double", false, true),
    TOO_GOOD("Too good to double", false, false),
    DOUBLE_TAKE("Double, take", true, true),
    DOUBLE_PASS("Double, pass", true, false);

    /** The cube action alone. */
    val kind: DecisionKind get() = if (double) DecisionKind.DOUBLE else DecisionKind.NO_DOUBLE
}

/**
 * What [c] costs in [p] by analysis [a] (raw equity, before the 1-point factor): the cube action's error
 * plus the error of the opponent's answer it expects (take or pass, taker's view). Null without a cube analysis.
 */
fun cubeAnswerError(p: StudyPosition, a: PositionAnalysis, c: CubeAnswer): Float? {
    val e = a.cube ?: return null
    val (take, pass, beaver) = DecisionMath.responseEquities(e, p.beavers)
    val takeBest = maxOf(take, beaver ?: take)
    val response = (maxOf(takeBest, pass) - if (c.take) takeBest else pass).coerceAtLeast(0f)
    return DecisionMath.doubleError(e, c.double, p.beavers) + response
}

/** The best [CubeAnswer] with the cube action [double]: the opponent's answer, which wasn't asked, made right. */
fun withBestResponse(p: StudyPosition, a: PositionAnalysis, double: Boolean): CubeAnswer =
    CubeAnswer.entries.filter { it.double == double }.minBy { cubeAnswerError(p, a, it) ?: Float.MAX_VALUE }

/** Whether the opponent's double was right in doubled position [p]: doubling costs nothing more than not doubling. */
fun doubleWasRight(p: StudyPosition, a: PositionAnalysis): Boolean? {
    val e = a.cube ?: return null
    return DecisionMath.doubleError(e, true, p.beavers) <= DecisionMath.doubleError(e, false, p.beavers)
}

/** Doubled position [p] turned over: the doubler at the bottom, before the double (their full cube decision). */
fun turnedToDoubler(p: StudyPosition): StudyPosition =
    StudyPosition(StudyPosition.turned(p.setup, p.setup.onRoll), doubled = false, beavers = p.beavers)

/** The engine's answer among [CubeAnswer] in doubling decision [p]. */
fun bestCubeAnswer(p: StudyPosition, a: PositionAnalysis): CubeAnswer? =
    if (a.cube == null) null else CubeAnswer.entries.minByOrNull { cubeAnswerError(p, a, it) ?: Float.MAX_VALUE }

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

/** "Money game", "5-point match, 2–3, Crawford" and the cube, for a saved position: the text before the score, the score, the rest. */
fun contextParts(p: StudyPosition): Triple<String, List<Int>?, String> {
    val s = p.setup
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
    return if (s.matchLength == 0) Triple("Money game", null, cube + what)
    else Triple("${s.matchLength}-point match, ", s.score.toList(), (if (s.crawford) ", Crawford" else "") + cube + what)
}

/** What was played or answered, for lists: the move or the cube action. */
fun decisionText(p: StudyPosition, move: Move?, kind: DecisionKind?): String =
    if (move != null) describeAction(DecisionKind.CHECKER, p.board, Player.HUMAN, move) else kind?.let { cubeLabel(it) } ?: ""

/** My positions' average error (weighted): of the positions whose game play is known and was an error. */
suspend fun libraryAverageError(app: PlyGroundApp): Float? =
    com.boldblot.backgammonplyground.core.NewErrors.average(
        app.positions.positions(null).mapNotNull { sp -> sp.played?.let { answerError(sp.position, sp.analysis, it) } },
    )

/** The keys (XGID, doubled) of everything in My positions, to tell which game decisions are in it. */
suspend fun libraryKeys(app: PlyGroundApp): Set<Pair<String, Boolean>> =
    app.positions.positions(null).map { it.position.xgid to it.position.doubled }.toSet()

/**
 * The automatic methods' line on a match's summary: how many of match [matchId]'s errors are in My positions,
 * and with Top X before its end how many will go. Null with Manual.
 */
suspend fun filedText(app: PlyGroundApp, matchId: Long, matchOver: Boolean): String? {
    val s = app.settings.settings.first()
    if (s.errorsMode == com.boldblot.backgammonplyground.data.ErrorsMode.MANUAL) return null
    val keys = libraryKeys(app)
    val filed = app.repository.errorPositions(matchId, com.boldblot.backgammonplyground.core.Severity.ERROR).count { (it.xgid to it.doubled) in keys }
    val what = if (filed == 1) "1 error" else "$filed errors"
    return when {
        s.errorsMode == com.boldblot.backgammonplyground.data.ErrorsMode.TOP_X && !matchOver ->
            "My positions: $what so far; at the end of the match its ${s.topX} biggest errors go in (Top X per match)."
        else -> "My positions: $what of this match went in (${s.errorsMode.label})."
    }
}

/**
 * "Analyze again" on a folder: every position in it, one after another, with the chosen engine and depths
 * (handy when a new engine comes). It runs in the app's scope, so it goes on when the screen is left;
 * Stop keeps the positions already done.
 */
object FolderAnalysis {
    data class Progress(val folder: String, val done: Int, val total: Int)

    private val _progress = kotlinx.coroutines.flow.MutableStateFlow<Progress?>(null)
    val progress: kotlinx.coroutines.flow.StateFlow<Progress?> = _progress
    private var job: kotlinx.coroutines.Job? = null

    fun start(app: PlyGroundApp, folderId: Long, folder: String, engine: EngineKind, checker: Depth, cube: Depth) {
        if (job?.isActive == true) return
        job = app.appScope.launch {
            var failed = 0
            var done = 0
            try {
                val list = app.positions.positions(folderId)
                _progress.value = Progress(folder, 0, list.size)
                for (p in list) {
                    try {
                        app.positions.setAnalysis(p.id, analyzePosition(app, p.position, engine, checker, cube))
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        failed++
                        com.boldblot.backgammonplyground.AppLog.error("Folder analysis failed for position ${p.id}", e)
                    }
                    done++
                    _progress.value = Progress(folder, done, list.size)
                }
                val text = ("Analyzed “$folder” again: $done position${if (done == 1) "" else "s"}" + if (failed > 0) ", $failed failed." else ".")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(app, text, android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                _progress.value = null
            }
        }
    }

    fun stop() { job?.cancel() }
}
