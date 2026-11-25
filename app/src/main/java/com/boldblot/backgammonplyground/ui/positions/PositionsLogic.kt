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
import com.boldblot.backgammonplyground.engine.SearchEngine
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
        QuizFilter.matches(p.entity.quizRight, p.entity.quizWrong, filter, p.entity.quizNeutral)
}

/** "3 right, 1 missed · balance +2". */
fun quizCountText(p: SavedPosition): String {
    val b = p.quizBalance
    val neutral = if (p.entity.quizNeutral > 0) ", ${p.entity.quizNeutral} neutral" else ""
    return "${p.entity.quizRight} right, ${p.entity.quizWrong} missed$neutral · balance ${if (b > 0) "+$b" else "$b"}"
}

/** Analyzes [p] for the quiz: at the quiz's depths (Settings → Quiz → Analysis in the quiz), with the analysis engine. */
suspend fun analyzeForQuiz(app: PlyGroundApp, p: StudyPosition, s: Settings, keep: List<Move> = emptyList()): PositionAnalysis {
    val match = p.ctx().matchLength > 0
    return analyzePosition(app, p, s.analysisEngine, s.quizCheckerDepth(match), s.quizCubeDepth(match), null, keep)
}

/** Analyzes [p] at the analysis depths in Settings, with the analysis engine; [keep]: see the other [analyzePosition]. */
suspend fun analyzePosition(app: PlyGroundApp, p: StudyPosition, s: Settings, waiting: Waiting? = null, keep: List<Move> = emptyList()): PositionAnalysis {
    val match = p.ctx().matchLength > 0
    return analyzePosition(app, p, s.analysisEngine, s.checkerDepth(match), s.cubeDepth(match), waiting, keep)
}

/**
 * Analyzes [p] with [engine]: a checker play at [checker], a cube decision at [cube]. The plays in [keep] (what was
 * played in the game, my answer) are searched as deeply as the best, as a game's analysis does with the play made:
 * the first as the search's played play, any other in a search of its own at the same depth, added to the list.
 */
suspend fun analyzePosition(
    app: PlyGroundApp, p: StudyPosition, engine: EngineKind, checker: Depth, cube: Depth, waiting: Waiting? = null,
    keep: List<Move> = emptyList(),
): PositionAnalysis = app.engine.run(engine, waiting, positionAnalysis(p, engine, checker, cube, keep))

/**
 * Analyzes [p] at the analysis depths in Settings in the background: in the background analysis slot when it has one
 * (PureTD), so a game never waits for it; otherwise on the analysis engine, in turn with the game.
 */
suspend fun analyzeInBackground(app: PlyGroundApp, p: StudyPosition, s: Settings, keep: List<Move> = emptyList()): PositionAnalysis {
    val match = p.ctx().matchLength > 0
    val block = positionAnalysis(p, s.analysisEngine, s.checkerDepth(match), s.cubeDepth(match), keep)
    return if (app.engine.twoSlots) app.engine.runBackground(block) else app.engine.run(s.analysisEngine, null, block)
}

/** [analyzeForQuiz] in the background analysis slot (the quiz's look-ahead): the next cards, while the player answers this one. */
suspend fun analyzeForQuizInBackground(app: PlyGroundApp, p: StudyPosition, s: Settings, keep: List<Move> = emptyList()): PositionAnalysis {
    val match = p.ctx().matchLength > 0
    val block = positionAnalysis(p, s.analysisEngine, s.quizCheckerDepth(match), s.quizCubeDepth(match), keep)
    return if (app.engine.twoSlots) app.engine.runBackground(block) else app.engine.run(s.analysisEngine, null, block)
}

/** The engine work of [analyzePosition]. */
fun positionAnalysis(p: StudyPosition, engine: EngineKind, checker: Depth, cube: Depth, keep: List<Move>): SearchEngine.() -> PositionAnalysis =
    {
        val dice = p.dice
        if (dice != null) {
            val c = MatchController(this, DiceSource.secure(), AnalysisSettings(plies = checker.code, cubePlies = cube.code))
            val moves = keep.filter { !it.isEmpty }.distinctBy { p.board.applyMove(Player.HUMAN, it) }
            // The label says what was searched: without the games where they cannot be played, or stopped early.
            val a = deepenIn(c, p, c.analyzeChecker(p.board, p.ctx(), dice, moves.firstOrNull(), checker.code), moves.drop(1), checker.code)
            PositionAnalysis(checker = a, label = "${engine.label} ${Depth.of(a.plies).label()}")
        } else {
            val e = cubeDecision(p.board, p.ctx(), cube.code)
            PositionAnalysis(cube = e, label = "${engine.label} ${Depth.of(e.plies ?: cube.code).label(cube = true)}")
        }
    }

/** [a] with each of [moves] not searched as deeply as its best play searched now at [plies] (as the played move is). */
private fun SearchEngine.deepenIn(c: MatchController, p: StudyPosition, start: CheckerAnalysis, moves: List<Move>, plies: Int): CheckerAnalysis {
    var a = start
    for (m in moves) {
        if (searchedDeeply(p, a, m)) continue
        val other = c.analyzeChecker(p.board, p.ctx(), p.dice!!, m, plies)
        other.candidates.getOrNull(other.playedIndex)?.let { a = a.withCandidate(p.board, Player.HUMAN, it) }
    }
    return a
}

/** Analysis [pa] of [p] with [moves] searched as deeply as its best play (only those that were not): the rest is kept. */
fun deepenPlays(p: StudyPosition, pa: PositionAnalysis, checker: Depth, cube: Depth, moves: List<Move>): SearchEngine.() -> PositionAnalysis =
    {
        val a = pa.checker
        if (a == null || p.dice == null) pa
        else {
            val c = MatchController(this, DiceSource.secure(), AnalysisSettings(plies = checker.code, cubePlies = cube.code))
            pa.copy(checker = deepenIn(c, p, a, moves.filter { !it.isEmpty }.distinctBy { p.board.applyMove(Player.HUMAN, it) }, a.plies))
        }
    }

/** Whether analysis [a] has [move] searched as deeply as its best play. */
fun searchedDeeply(p: StudyPosition, a: CheckerAnalysis, move: Move): Boolean {
    val target = p.board.applyMove(Player.HUMAN, move)
    val c = a.candidates.firstOrNull { p.board.applyMove(Player.HUMAN, it.move) == target } ?: return false
    return c.plies == a.candidates[0].plies
}

/** The plays of [sp] its analysis must search deeply: what was played in the game and my answer. */
fun keepMoves(sp: com.boldblot.backgammonplyground.data.SavedPosition): List<Move> = listOfNotNull(sp.played?.move, sp.answer?.move)

/** [ans] as a whole cube decision ("Double, take"), when it is one. */
fun MyAnswer.cubeAnswer(): CubeAnswer? = when {
    cube == DecisionKind.DOUBLE && expect == DecisionKind.TAKE -> CubeAnswer.DOUBLE_TAKE
    cube == DecisionKind.DOUBLE && expect == DecisionKind.PASS -> CubeAnswer.DOUBLE_PASS
    else -> null
}

/** The cube answers to choose from for [p]: No double, Double/take, Double/pass; or Take / Pass (and Beaver) when doubled. */
fun cubeAnswerChoices(p: StudyPosition): List<MyAnswer> =
    if (p.doubled) cubeActions(p).map { MyAnswer(cube = it) }
    else listOf(
        MyAnswer(cube = DecisionKind.NO_DOUBLE), MyAnswer(cube = DecisionKind.DOUBLE, expect = DecisionKind.TAKE),
        MyAnswer(cube = DecisionKind.DOUBLE, expect = DecisionKind.PASS),
    )

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
        ans.cube != null -> ans.cubeAnswer()?.let { cubeAnswerError(p, a, it) } ?: cubeError(p, a, ans.cube)
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
    a.cube != null -> a.cubeAnswer()?.label ?: cubeLabel(a.cube)
    else -> ""
}

/** The weighted error factor of [p] (×1.5 in a 1-point match, as for PR). */
fun factorOf(p: StudyPosition): Float = XgRules.errorFactor(p.setup.matchLength)

/** "Unlimited game", "5-point match, 2–3, Crawford" and the cube, for a saved position: the text before the score, the score, the rest. */
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
    return if (s.matchLength == 0) Triple("Unlimited game", null, cube + what)
    else Triple("${s.matchLength}-point match, ", s.score.toList(), (if (s.crawford) ", Crawford" else "") + cube + what)
}

/** What was played or answered, for lists: the move or the cube action. */
fun decisionText(p: StudyPosition, move: Move?, kind: DecisionKind?): String =
    if (move != null) describeAction(DecisionKind.CHECKER, p.board, Player.HUMAN, move) else kind?.let { cubeLabel(it) } ?: ""

/**
 * Whether [a] is below the standard of Settings → Analysis for [p] (the quiz then analyzes it again): searched by the
 * same engine at a shallower depth, or stopped early; an analysis of another engine of ours is left alone (it was
 * asked for), one read from another program's file counts as below unless [Settings.quizKeepForeign]. Played-out games
 * that were skipped because the decision was clear ([Settings.gamesOnErrorsOnly]) do not make an analysis shallow.
 */
fun belowStandard(p: StudyPosition, a: PositionAnalysis, s: Settings): Boolean {
    val match = p.ctx().matchLength > 0
    val checker = p.dice != null
    val engine: String?
    val code: Int?
    val imported: Boolean
    if (checker) {
        val c = a.checker ?: return false
        engine = c.engine; code = c.plies; imported = c.imported
    } else {
        val e = a.cube ?: return false
        engine = e.engine; code = e.plies; imported = e.imported
    }
    if (imported) return !s.quizKeepForeign
    if (engine != null && engine != s.analysisEngine.label) return false
    val standard = (if (checker) s.quizCheckerDepth(match) else s.quizCubeDepth(match)).let { if (!match || s.analysisEngine != EngineKind.PURETD) it.plain else it }
    val have = Depth.of(code ?: return true)
    if (have.atLeast(standard)) return false
    // Games skipped because the decision was clear: the plies before them were the standard's.
    if (standard.search != null && have.search == null && have.plies >= standard.plies && !have.incomplete) {
        val c = a.checker
        val e = a.cube
        if (checker && c != null && s.gamesOnErrorsOnly && c.candidates.size >= 2 && c.candidates[0].equity - c.candidates[1].equity > s.gamesGap) return false
        if (!checker && e != null && kotlin.math.abs(e.noDouble - e.doubleTake) > com.boldblot.backgammonplyground.core.GameSearch.CUBE_CLEAR) return false
    }
    return true
}

/**
 * My positions' average error (weighted), by the basis of Settings → My positions: the game play's error, the last
 * quiz answer's, or both averaged per position (the analysis each has now).
 */
suspend fun libraryAverageError(app: PlyGroundApp): Float? {
    val basis = app.settings.settings.first().averageBasis
    return com.boldblot.backgammonplyground.core.NewErrors.average(
        app.positions.positions(null).mapNotNull { sp ->
            basis.of(
                sp.played?.let { answerError(sp.position, sp.analysis, it) },
                sp.lastAnswer?.let { answerError(sp.position, sp.analysis, it) },
            )
        },
    )
}

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
                        app.positions.setAnalysis(p.id, analyzePosition(app, p.position, engine, checker, cube, keep = keepMoves(p)))
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

/**
 * A long press on a play in an analysis list: [move] searched to [a]'s full depth (as the played move is) and joined to [a] in
 * place of its shallower value; [a] as it was when it is already searched that deeply. [player] decides on [board].
 */
suspend fun deepenMove(
    app: com.boldblot.backgammonplyground.PlyGroundApp, engine: com.boldblot.backgammonplyground.core.EngineKind?,
    board: com.boldblot.backgammonplyground.core.Board, player: Int, ctx: com.boldblot.backgammonplyground.core.CubeContext,
    dice: com.boldblot.backgammonplyground.core.Dice, a: CheckerAnalysis, move: Move, cubeCode: Int,
): CheckerAnalysis {
    val target = board.applyMove(player, move)
    val c = a.candidates.firstOrNull { board.applyMove(player, it.move) == target }
    if (c != null && c.plies == a.candidates.firstOrNull()?.plies) return a
    val t0 = android.os.SystemClock.elapsedRealtime()
    val out = app.engine.run(engine, Waiting.ANALYSIS) {
        val r = MatchController(this, DiceSource.secure(), AnalysisSettings(plies = a.plies, cubePlies = cubeCode)).analyzeChecker(board, ctx, dice, move, a.plies)
        r.candidates.getOrNull(r.playedIndex)?.let { a.withCandidate(board, player, it) } ?: a
    }
    com.boldblot.backgammonplyground.AppLog.i(
        "Deepen play ${MoveNotation.format(board, player, move)} at ${Depth.of(a.plies).label()} in ${android.os.SystemClock.elapsedRealtime() - t0} ms " +
            "(${com.boldblot.backgammonplyground.core.PositionIds.toXgid(com.boldblot.backgammonplyground.core.PositionSetup(board, player, dice = dice, matchLength = ctx.matchLength, score = ctx.score, crawford = ctx.crawford, cube = ctx.cube, format = "XGID"))})",
    )
    return out
}
