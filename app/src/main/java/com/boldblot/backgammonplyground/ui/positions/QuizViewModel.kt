package com.boldblot.backgammonplyground.ui.positions

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
import com.boldblot.backgammonplyground.data.QuizCorrect
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
    /** The answer: a play, or a cube action. */
    val move: Move? = null,
    val cube: DecisionKind? = null,
    /** The engine's best play or cube action. */
    val bestMove: Move? = null,
    val bestCube: DecisionKind? = null,
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
)

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
    val result: QuizResult? = null,
    /** Answers given, right ones, and their total error (weighted). */
    val answered: Int = 0,
    val right: Int = 0,
    val totalError: Float = 0f,
    val finished: Boolean = false,
    val message: String? = null,
) {
    val current: SavedPosition? get() = queue.firstOrNull()
}

/** The My positions quiz: the positions [spec] picks (folders, labels, how many, order), answered on the board. */
class QuizViewModel(private val app: PlyGroundApp, private val spec: QuizSpec) : ViewModel() {

    private val _state = MutableStateFlow(QuizState())
    val state: StateFlow<QuizState> = _state.asStateFlow()
    private var options: MoveOptions? = null
    private var all: List<SavedPosition> = emptyList()

    init {
        viewModelScope.launch {
            val s = app.settings.settings.first()
            all = spec.pick(quizPositions(app, spec.folders), s)
            _state.update { it.copy(loading = false, settings = s) }
            start()
        }
    }

    /** A new round: the positions in random order (or newest first), at most [QuizSpec.limit] of them. */
    fun start() {
        val ordered = if (spec.newestFirst) all.sortedByDescending { it.entity.savedAt } else all.shuffled()
        val list = if (spec.limit > 0) ordered.take(spec.limit) else ordered
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

    /** Tap on the dice: answer when the move is complete, otherwise swap them. */
    fun tapDice() {
        val st = _state.value
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

    /** A cube action is the answer. */
    fun answerCube(k: DecisionKind) {
        val st = _state.value
        if (st.busy || st.result != null || st.current?.position?.dice != null) return
        answer(move = null, cube = k)
    }

    private fun answer(move: Move?, cube: DecisionKind?) {
        val sp = _state.value.current ?: return
        val p = sp.position
        val s = _state.value.settings
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                // A position saved without analysis (or imported) is analyzed now, and keeps it.
                val a = sp.analysis ?: analyzePosition(app, p, s).also { app.positions.setAnalysis(sp.id, it) }
                val factor = factorOf(p)
                val result = if (move != null) {
                    val c = a.checker!!
                    val best = c.candidates.firstOrNull()
                    // A play outside the stored candidates is analyzed with them.
                    val eq = equityOf(p, c, move) ?: if (c.candidates.isEmpty()) null else app.engine.run {
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
                        mine = sp.answer?.let { answerText(p, it) }, equity = eq, bestEquity = best?.equity, analysis = a,
                    )
                } else {
                    val err = (cubeError(p, a, cube!!) ?: 0f) * factor
                    val mine = sp.answer?.cube
                    val right = when {
                        mine != null -> mine == cube
                        s.quizCorrect == QuizCorrect.BEST_ONLY -> err < 0.0005f
                        else -> !XgRules.severity(err).atLeast(Severity.ERROR)
                    }
                    QuizResult(
                        right, err, XgRules.severity(err), cube = cube, bestCube = bestCube(p, a),
                        mine = sp.answer?.let { answerText(p, it) }, analysis = a,
                    )
                }
                val counted = app.positions.setResult(sp.id, result.right, MyAnswer(move = move, cube = cube))
                    ?.let { sp.copy(entity = it) }
                val deleted = counted != null && s.quizAutoDelete && counted.quizBalance >= s.quizDeleteAt
                if (deleted) app.positions.remove(sp.id)
                val played = sp.played?.let { answerWithError(p, a, it) }
                _state.update {
                    it.copy(
                        busy = false, result = result.copy(played = played, counts = counted?.let(::quizCountText), deleted = deleted), answered = it.answered + 1, right = it.right + if (result.right) 1 else 0,
                        totalError = it.totalError + result.error,
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

    /** The next position; a missed one comes back at the end with "Repeat until all are right". */
    fun next() {
        val st = _state.value
        val r = st.result ?: return
        val cur = st.current ?: return
        val rest = st.queue.drop(1) + if (!r.right && st.settings.quizRepeat) listOf(cur) else emptyList()
        _state.update { it.copy(queue = rest, result = null, finished = rest.isEmpty()) }
        if (rest.isNotEmpty()) show()
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }
}
