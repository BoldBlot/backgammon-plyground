package com.boldblot.backgammonplyground.ui.positions

import com.boldblot.backgammonplyground.core.StudyPosition
import com.boldblot.backgammonplyground.data.QuizTheirCube
import com.boldblot.backgammonplyground.data.QuizMyCube
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boldblot.backgammonplyground.AppLog
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.AnalysisSettings
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.DecisionKind
import com.boldblot.backgammonplyground.core.DiceSource
import com.boldblot.backgammonplyground.core.MatchController
import com.boldblot.backgammonplyground.core.Move
import com.boldblot.backgammonplyground.core.MyAnswer
import com.boldblot.backgammonplyground.core.MoveOptions
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.PositionAnalysis
import com.boldblot.backgammonplyground.core.Severity
import com.boldblot.backgammonplyground.core.Step
import com.boldblot.backgammonplyground.core.XgRules
import com.boldblot.backgammonplyground.data.DeletedPosition
import com.boldblot.backgammonplyground.data.QuizCorrect
import com.boldblot.backgammonplyground.ui.review.ReviewView
import com.boldblot.backgammonplyground.ui.review.ReanalyzeRequest
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.EngineKind
import com.boldblot.backgammonplyground.data.SavedPosition
import com.boldblot.backgammonplyground.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The answer to one quiz position. */
data class QuizResult(
    val right: Boolean,
    /** The engine's verdict on the answer: its cost after the 1-point factor, and its severity. */
    val error: Float,
    val severity: Severity,
    /** The answer: a play, or a cube action ([cubeAnswer] for a doubling decision, with the expected answer). */
    val move: Move? = null,
    val cube: DecisionKind? = null,
    val cubeAnswer: CubeAnswer? = null,
    /** The engine's best play or cube action. */
    val bestMove: Move? = null,
    val bestCube: DecisionKind? = null,
    val bestCubeAnswer: CubeAnswer? = null,
    /** The position's 1-point-match factor: [error] / [factor] is the plain equity lost. */
    val factor: Float = 1f,
    /** The player's own saved answer, when the position has one: it decides [right]. */
    val mine: String? = null,
    /** What was played in the game the position comes from, with its cost. */
    val played: String? = null,
    /** Equities of the answer and the best play (checker play). */
    val equity: Float? = null,
    val bestEquity: Float? = null,
    val analysis: PositionAnalysis,
    /** The position's quiz answers with this one: "3 right, 1 missed · balance +2". */
    val counts: String? = null,
    /** Its balance reached the threshold in Settings and it was deleted from My positions. */
    val deleted: Boolean = false,
    /** Deleted with the Delete button (Undo puts it back). */
    val removed: DeletedPosition? = null,
    /** The answer and the engine's as the quiz asked them ("Double" when the opponent's answer wasn't asked). */
    val youText: String? = null,
    val bestText: String? = null,
    /** Doubled with the follow-up question: the player's "the double was right", and the engine's. */
    val doubleClaim: Boolean? = null,
    val doubleRight: Boolean? = null,
)

/** A card answered this round, for the arrows back through the quiz and the list at its end. */
data class QuizCard(val position: SavedPosition, val result: QuizResult)

data class QuizState(
    val loading: Boolean = true,
    val settings: Settings = Settings(),
    /** The positions still to answer this round, the current one first. */
    val queue: List<SavedPosition> = emptyList(),
    val total: Int = 0,
    /** Board as shown (with the move being entered). */
    val board: Board = Board.INITIAL,
    val steps: List<Step> = emptyList(),
    val movable: Set<Int> = emptySet(),
    val diceOrder: List<Int> = emptyList(),
    val diceDim: List<Float> = emptyList(),
    val diceError: Boolean = false,
    val moveComplete: Boolean = false,
    /** Analyzing the position or the answer. */
    val busy: Boolean = false,
    /** "Analyze again" on the result is running. */
    val reanalyzing: Boolean = false,
    val result: QuizResult? = null,
    /** After an answer: the board with arrows, bare, or after a move; [candidate] is the play picked in the list. */
    val view: ReviewView = ReviewView.ARROWS,
    val candidate: Int? = null,
    /** Answers given, right ones, and their total error (plain equity, without the 1-point factor). */
    val answered: Int = 0,
    val right: Int = 0,
    val totalError: Float = 0f,
    /** Positions deleted during this round: with Delete or as learned ([learned] of them). */
    val deleted: Int = 0,
    val learned: Int = 0,
    /** The position deleted last with Delete, for the Undo snackbar. */
    val lastRemoved: DeletedPosition? = null,
    val finished: Boolean = false,
    val message: String? = null,
    /** Cards answered this round, oldest first. */
    val history: List<QuizCard> = emptyList(),
    /** The answered card shown with the arrows (index into [history]); null = the current card. */
    val viewing: Int? = null,
    /** Doubled, with the follow-up question: the take or pass given, waiting for "was the double right?". */
    val firstCube: DecisionKind? = null,
) {
    val current: SavedPosition? get() = queue.firstOrNull()
}

/** The My positions quiz: the positions [spec] picks (folders, labels, how many, order), answered on the board. */
class QuizViewModel(val app: PlyGroundApp, private val spec: QuizSpec) : ViewModel() {

    private val _state = MutableStateFlow(QuizState())
    val state: StateFlow<QuizState> = _state.asStateFlow()
    private var options: MoveOptions? = null
    private var all: List<SavedPosition> = emptyList()
    /** Positions deleted during the quiz: "Again" leaves them out. */
    private val gone = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            val s = app.settings.settings.first()
            all = spec.pick(quizPositions(app, spec.folders), s)
            _state.update { it.copy(loading = false, settings = s) }
            start()
            // Settings opened from the quiz apply when it comes back (the round's cards stay as they are).
            app.settings.settings.collect { n -> _state.update { it.copy(settings = n) } }
        }
    }

    /** A new round: the positions in random order (or newest first), at most [QuizSpec.limit] of them. */
    fun start() {
        val left = all.filter { it.id !in gone }
        val ordered = if (spec.newestFirst) left.sortedByDescending { it.entity.savedAt } else left.shuffled()
        val picked = if (spec.limit > 0) ordered.take(spec.limit) else ordered
        // Turned over, a double becomes the doubler's full decision (the player's own answer is about the take).
        val turn = _state.value.settings.quizTheirCube == QuizTheirCube.FLIP
        val list = picked.map { sp ->
            if (turn && sp.position.doubled) sp.copy(position = turnedToDoubler(sp.position), answer = null, played = null) else sp
        }
        _state.update { QuizState(loading = false, settings = it.settings, queue = list, total = list.size, finished = list.isEmpty()) }
        show()
    }

    /** Sets up the board for the current position. */
    private fun show() {
        val p = _state.value.current?.position ?: return
        val d = p.dice
        options = d?.let { MoveOptions(p.board, Player.HUMAN, it.d1, it.d2) }
        val opts = options
        _state.update {
            it.copy(
                board = p.board, steps = emptyList(), movable = opts?.movableFrom(emptyList()) ?: emptySet(),
                diceOrder = d?.highFirst ?: emptyList(), diceDim = d?.highFirst?.map { 0f } ?: emptyList(),
                moveComplete = opts != null && !opts.canMove, result = null, message = null,
                view = ReviewView.ARROWS, candidate = null, firstCube = null,
            )
        }
    }

    // ---- move input (as in a game) ---------------------------------------------------------

    fun tap(target: Int) {
        val st = _state.value
        val opts = options ?: return
        if (st.busy || st.result != null || st.moveComplete) return
        val step = opts.tapStep(st.steps, target, st.diceOrder)
        if (step == null) flash() else apply(st.steps + step)
    }

    fun drag(from: Int, to: Int) {
        val st = _state.value
        val opts = options ?: return
        if (st.busy || st.result != null || st.moveComplete || from !in st.movable) return
        val chain = opts.destinations(st.steps, from)[to] ?: return
        apply(st.steps + chain)
    }

    /** Tap on the dice: answer when the move is complete, otherwise swap them; once answered, the next card. */
    fun tapDice() {
        val st = _state.value
        if (st.viewing != null) { forward(); return }
        if (st.result != null && !st.busy) { next(); return }
        val opts = options ?: return
        if (st.busy || st.result != null) return
        if (st.moveComplete) { done(); return }
        val order = st.diceOrder.reversed()
        _state.update { it.copy(diceOrder = order, diceDim = dim(opts, it.steps, order, false)) }
    }

    fun undo() {
        val st = _state.value
        if (st.steps.isEmpty() || st.busy || st.result != null) return
        apply(st.steps.dropLast(1))
    }

    private fun apply(steps: List<Step>) {
        val opts = options ?: return
        val p = _state.value.current?.position ?: return
        var b = p.board
        for (s in steps) b = b.applyStep(Player.HUMAN, s)
        val complete = opts.isComplete(steps)
        _state.update {
            it.copy(
                board = b, steps = steps, movable = if (complete) emptySet() else opts.movableFrom(steps),
                diceDim = dim(opts, steps, it.diceOrder, complete), moveComplete = complete,
            )
        }
    }

    private fun dim(opts: MoveOptions, steps: List<Step>, order: List<Int>, complete: Boolean): List<Float> {
        if (order.size < 2) return emptyList()
        if (!opts.canMove) return listOf(0f, 0f)
        if (complete) return listOf(1f, 1f)
        val used = opts.usedDice(steps)
        if (order[0] == order[1]) return listOf(minOf(used.size, 2) / 2f, maxOf(0, used.size - 2) / 2f)
        return order.map { if (it in used) 1f else 0f }
    }

    private fun flash() {
        _state.update { it.copy(diceError = true) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(450)
            _state.update { it.copy(diceError = false) }
        }
    }

    // ---- answers ---------------------------------------------------------------------------

    /** The checker play entered on the board is the answer. */
    fun done() {
        val opts = options ?: return
        val st = _state.value
        if (!st.moveComplete || st.busy || st.result != null) return
        answer(move = if (opts.canMove) Move(st.steps) else Move.NONE, cube = null)
    }

    /**
     * A response to a double (take, pass, beaver) is the answer; or a cube action alone ("No double / Double",
     * Settings → My positions → Quiz). With the follow-up question it waits for [answerDoubleClaim].
     */
    fun answerCube(k: DecisionKind) {
        val st = _state.value
        if (st.busy || st.result != null || st.current?.position?.dice != null) return
        if (k.isResponse && st.settings.quizTheirCube == QuizTheirCube.THEN_DOUBLE) {
            _state.update { it.copy(firstCube = k) }
            return
        }
        answer(move = null, cube = k)
    }

    /** The follow-up to take or pass: whether the opponent's double was right. */
    fun answerDoubleClaim(right: Boolean) {
        val st = _state.value
        val k = st.firstCube ?: return
        if (st.busy || st.result != null) return
        answer(move = null, cube = k, claim = right)
    }

    /**
     * A doubling decision's answer, with the opponent's expected answer ("Double, pass"). [partial]: only the
     * cube action was asked, so the opponent's answer is taken as the right one.
     */
    fun answerCube(c: CubeAnswer, partial: Boolean = false) {
        val st = _state.value
        if (st.busy || st.result != null || st.current?.position?.dice != null) return
        answer(move = null, cube = c.kind, cubeAnswer = c, partial = partial)
    }

    private fun answer(move: Move?, cube: DecisionKind?, cubeAnswer: CubeAnswer? = null, partial: Boolean = false, claim: Boolean? = null) {
        val sp = _state.value.current ?: return
        val p = sp.position
        val s = _state.value.settings
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                // A position saved without analysis (or imported) is analyzed now, and keeps it.
                val a = sp.analysis ?: analyzePosition(app, p, s).also { app.positions.setAnalysis(sp.id, it) }
                val factor = factorOf(p)
                val asked = cubeAnswer?.let { if (partial) withBestResponse(p, a, it.double) else it }
                val judged = judge(sp, a, move, cube, asked, s, null)
                val result = judged.withAsked(p, a, s, cubeAnswer.takeIf { partial }, claim)
                val counted = app.positions.setResult(sp.id, result.right, MyAnswer(move = move, cube = cube))
                    ?.let { sp.copy(entity = it) }
                val deleted = counted != null && s.quizAutoDelete && counted.quizBalance >= s.quizDeleteAt
                if (deleted) {
                    app.positions.remove(sp.id)
                    gone += sp.id
                }
                val played = sp.played?.let { answerWithError(p, a, it, s.errorUnit) }
                _state.update {
                    it.copy(
                        busy = false, result = result.copy(played = played, counts = counted?.let(::quizCountText), deleted = deleted), answered = it.answered + 1, right = it.right + if (result.right) 1 else 0,
                        totalError = it.totalError + result.error / factor,
                        deleted = it.deleted + if (deleted) 1 else 0, learned = it.learned + if (deleted) 1 else 0,
                        board = p.board,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLog.error("Quiz analysis failed", e)
                _state.update { it.copy(busy = false, message = "Analysis failed: ${e.message}") }
            }
        }
    }

    /**
     * The verdict on the answer ([move], or [cube] / [cubeAnswer]) to [sp] by analysis [a]; a play outside its
     * candidates is analyzed with [engine] (the analysis engine when null).
     */
    private suspend fun judge(
        sp: SavedPosition, a: PositionAnalysis, move: Move?, cube: DecisionKind?, cubeAnswer: CubeAnswer?, s: Settings, engine: EngineKind?,
    ): QuizResult {
        val p = sp.position
        val factor = factorOf(p)
        return if (move != null) {
            val c = a.checker!!
            val best = c.candidates.firstOrNull()
            val eq = equityOf(p, c, move) ?: if (c.candidates.isEmpty()) null else app.engine.run(engine) {
                MatchController(this, DiceSource.secure(), AnalysisSettings(plies = c.plies, cubePlies = s.cubeDepth.code))
                    .analyzeChecker(p.board, p.ctx(), p.dice!!, move, c.plies).playedEquity
            }
            val err = if (eq == null) 0f else ((c.bestEquity - eq) * factor).coerceAtLeast(0f)
            val mine = sp.answer?.move
            val right = when {
                mine != null -> p.board.applyMove(Player.HUMAN, mine) == p.board.applyMove(Player.HUMAN, move)
                s.quizCorrect == QuizCorrect.BEST_ONLY -> err < 0.0005f
                else -> !XgRules.severity(err).atLeast(Severity.ERROR)
            }
            QuizResult(
                right, err, XgRules.severity(err), move = move, bestMove = best?.move,
                mine = sp.answer?.let { answerText(p, it) }, equity = eq, bestEquity = best?.equity, analysis = a, factor = factor,
            )
        } else {
            val raw = if (cubeAnswer != null) cubeAnswerError(p, a, cubeAnswer) else cubeError(p, a, cube!!)
            val err = (raw ?: 0f) * factor
            val mine = sp.answer?.cube
            val right = when {
                mine != null -> mine == cube
                s.quizCorrect == QuizCorrect.BEST_ONLY -> err < 0.0005f
                else -> !XgRules.severity(err).atLeast(Severity.ERROR)
            }
            val bestAnswer = if (cubeAnswer != null) bestCubeAnswer(p, a) else null
            QuizResult(
                right, err, XgRules.severity(err), cube = cube, cubeAnswer = cubeAnswer,
                bestCube = bestAnswer?.kind ?: bestCube(p, a), bestCubeAnswer = bestAnswer,
                mine = sp.answer?.let { answerText(p, it) }, analysis = a, factor = factor,
            )
        }
    }

    /**
     * [this] verdict as the quiz asked: [partialOf] (the cube action alone) names the answers without the
     * opponent's, and [claim] (the follow-up "was the double right?") must be right too.
     */
    private fun QuizResult.withAsked(p: StudyPosition, a: PositionAnalysis, s: Settings, partialOf: CubeAnswer?, claim: Boolean?): QuizResult {
        var r = this
        if (partialOf != null) {
            val best = bestCubeAnswer(p, a)
            val three = s.quizMyCube == QuizMyCube.THREE
            r = r.copy(
                youText = if (partialOf.double) "Double" else if (three) partialOf.label else "No double",
                bestText = best?.let { b -> if (b.double) "Double" else if (three) b.label else "No double" },
            )
        }
        if (claim != null) {
            val engine = doubleWasRight(p, a)
            r = r.copy(doubleClaim = claim, doubleRight = engine, right = r.right && (engine == null || engine == claim))
        }
        return r
    }

    /**
     * "Analyze again" on the result: analyzes the position with [r]'s engine and depths, keeps the new analysis
     * on the position and judges the answer again by it. If right / missed changes, the position's quiz counts
     * and this round's score change with it.
     */
    fun reanalyze(r: ReanalyzeRequest) {
        val st = _state.value
        val old = st.result ?: return
        val sp = st.current ?: return
        if (st.reanalyzing) return
        val s = st.settings
        _state.update { it.copy(reanalyzing = true, message = null) }
        viewModelScope.launch {
            try {
                val p = sp.position
                val a = analyzePosition(app, p, r.engine, r.checker, Depth(r.cubePlies))
                val kept = !old.deleted && old.removed == null
                if (kept) app.positions.setAnalysis(sp.id, a)
                // An answer of the cube action alone is judged again with the opponent's best answer by the new analysis.
                val partialOf = old.cubeAnswer.takeIf { old.youText != null }
                val asked = partialOf?.let { withBestResponse(p, a, it.double) } ?: old.cubeAnswer
                val fresh = judge(sp.copy(analysis = a), a, old.move, old.cube, asked, s, r.engine).withAsked(p, a, s, partialOf, old.doubleClaim)
                val flipped = fresh.right != old.right
                val counted = if (kept && flipped) app.positions.fixResult(sp.id, fresh.right)?.let { sp.copy(entity = it) } else null
                val result = fresh.copy(
                    played = sp.played?.let { answerWithError(p, a, it, s.errorUnit) },
                    counts = counted?.let(::quizCountText) ?: old.counts, deleted = old.deleted, removed = old.removed,
                )
                _state.update {
                    it.copy(
                        reanalyzing = false, result = result, candidate = null,
                        queue = it.queue.mapIndexed { i, q -> if (i == 0) q.copy(analysis = a, entity = counted?.entity ?: q.entity) else q },
                        right = it.right + if (!flipped) 0 else if (fresh.right) 1 else -1,
                        totalError = it.totalError - old.error / old.factor + fresh.error / fresh.factor,
                        message = "Analyzed again: ${a.label}." + if (flipped) " The verdict changed." else "",
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLog.error("Quiz analysis failed", e)
                _state.update { it.copy(reanalyzing = false, message = "Analysis failed: ${e.message}") }
            }
        }
    }

    /** The next position; a missed one comes back at the end with "Repeat until all are right". */
    fun next() {
        val st = _state.value
        val r = st.result ?: return
        val cur = st.current ?: return
        val again = !r.right && st.settings.quizRepeat && !r.deleted && r.removed == null
        val rest = st.queue.drop(1) + if (again) listOf(cur) else emptyList()
        _state.update { it.copy(queue = rest, result = null, finished = rest.isEmpty(), history = it.history + QuizCard(cur, r), viewing = null) }
        if (rest.isNotEmpty()) show()
    }

    /** The arrow back: the card answered before the one shown (view only). */
    fun back() = _state.update {
        val at = it.viewing ?: it.history.size
        if (at <= 0) it else it.copy(viewing = at - 1, candidate = null, view = ReviewView.ARROWS)
    }

    /** The arrow forward: the next answered card, then the current one again. */
    fun forward() = _state.update {
        val at = it.viewing ?: return@update it
        it.copy(viewing = if (at + 1 < it.history.size) at + 1 else null, candidate = null, view = ReviewView.ARROWS)
    }

    /** Files the card shown (the current one, or an answered one) in [folders] instead of its folders now. */
    fun moveToFolders(id: Long, folders: Set<Long>) {
        viewModelScope.launch {
            app.positions.setCategories(id, folders)
            _state.update { it.copy(message = if (folders.isEmpty()) "Out of all folders." else "Moved.") }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun setView(v: ReviewView) = _state.update { it.copy(view = v) }

    /** Shows play [i] of the analysis on the board (null: the best play and the answer). */
    fun selectCandidate(i: Int?) = _state.update { it.copy(candidate = i) }

    /** Deletes the answered position from My positions and all its folders (Undo: [undoDelete]). */
    fun deleteCurrent() {
        val st = _state.value
        val r = st.result ?: return
        val sp = st.current ?: return
        if (r.deleted || r.removed != null) return
        viewModelScope.launch {
            val d = app.positions.removeForUndo(sp.id) ?: return@launch
            gone += sp.id
            _state.update {
                it.copy(
                    result = if (it.current?.id == sp.id) it.result?.copy(removed = d) else it.result,
                    deleted = it.deleted + 1, lastRemoved = d,
                )
            }
        }
    }

    /** Puts back the position deleted last with Delete. */
    fun undoDelete(d: DeletedPosition) {
        viewModelScope.launch {
            app.positions.restore(d)
            gone -= d.entity.id
            _state.update {
                it.copy(
                    result = if (it.result?.removed == d) it.result.copy(removed = null) else it.result,
                    deleted = (it.deleted - 1).coerceAtLeast(0), lastRemoved = null,
                )
            }
        }
    }
}
