package com.boldblot.backgammonplyground.ui.positions

import com.boldblot.backgammonplyground.engine.Waiting
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
import com.boldblot.backgammonplyground.core.AutoLabel
import com.boldblot.backgammonplyground.core.Labels
import com.boldblot.backgammonplyground.data.QuizVerdict
import com.boldblot.backgammonplyground.ui.review.ReviewView
import com.boldblot.backgammonplyground.ui.review.ReanalyzeRequest
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.EngineKind
import com.boldblot.backgammonplyground.data.SavedPosition
import com.boldblot.backgammonplyground.data.Settings
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The answer to one quiz position. */
data class QuizResult(
    val right: Boolean,
    /** Not right, but within the neutral level (Settings → Quiz): it counts neither as right nor as missed. */
    val neutral: Boolean = false,
    /** How the answer compares with the last one to the same position; null = the first answer. */
    val trend: AutoLabel? = null,
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
    /** Its balance reached the threshold in Settings and it left My positions (into Learned, or deleted when Learned is off). */
    val deleted: Boolean = false,
    /** Moved to Learned with the Learned chip after the answer ([deleted] is on too). */
    val learnedByHand: Boolean = false,
    /** In Favourites: it can't move to Learned. */
    val favourite: Boolean = false,
    /** Deleted with the Delete button (Undo puts it back). */
    val removed: DeletedPosition? = null,
    /** The answer and the engine's as the quiz asked them ("Double" when the opponent's answer wasn't asked). */
    val youText: String? = null,
    val bestText: String? = null,
    /** Doubled with the follow-up question: the player's "the double was right", and the engine's. */
    val doubleClaim: Boolean? = null,
    val doubleRight: Boolean? = null,
    /** After "Analyze again": the saved answer is an error by the new analysis, and the card asks what to do with it. */
    val savedNote: SavedAnswerNote? = null,
) {
    val verdict: QuizVerdict get() = if (right) QuizVerdict.RIGHT else if (neutral) QuizVerdict.NEUTRAL else QuizVerdict.WRONG
}

/** The player's saved answer ([text]) that the analysis now calls an error of [error] (plain equity). */
data class SavedAnswerNote(val text: String, val error: Float)

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
    val neutral: Int = 0,
    val totalError: Float = 0f,
    /** Positions deleted during this round: with Delete or as learned ([learned] of them). */
    val deleted: Int = 0,
    val learned: Int = 0,
    /** The position deleted last with Delete, for the Undo snackbar. */
    val lastRemoved: DeletedPosition? = null,
    val finished: Boolean = false,
    /** Ended with Finish before the last card. */
    val stoppedEarly: Boolean = false,
    val message: String? = null,
    /** Cards answered this round, oldest first. */
    val history: List<QuizCard> = emptyList(),
    /** The answered card shown with the arrows (index into [history]); null = the current card. */
    val viewing: Int? = null,
    /** Doubled, with the follow-up question: the take or pass given, waiting for "was the double right?". */
    val firstCube: DecisionKind? = null,
    /** Quiz a friend: nothing is written to the positions, and no answer is kept for the statistics. */
    val friend: Boolean = false,
    /** A game quiz ([gameTitle]: its match): the cards are a match's decisions, not in My positions; nothing is saved. */
    val detached: Boolean = false,
    val gameTitle: String? = null,
    /** Game quiz: the cards that are the opponent's decisions, and how they are shown. */
    val theirs: Set<Long> = emptySet(),
    val theirView: TheirView = TheirView.COLOUR,
) {
    /** Whether [sp] is shown as the opponent's decision, and how. */
    fun viewOf(sp: SavedPosition): TheirView? = theirView.takeIf { sp.id in theirs }

    val current: SavedPosition? get() = queue.firstOrNull()
}

/**
 * The My positions quiz: the positions [spec] picks (folders, labels, how many, order), answered on the board.
 * With [game] it is a game quiz instead: the decisions of a saved match, which are not in My positions.
 */
class QuizViewModel(val app: PlyGroundApp, private val spec: QuizSpec, private val game: GameQuizSpec? = null) : ViewModel() {

    /** The cards are not in My positions (game quiz): nothing about them is stored. */
    private val detached = game != null
    /** The answers go to the positions' quiz counts, last answers and trends (not with Quiz a friend or a game quiz). */
    private val keep = !spec.friend && !detached
    private var theirs: Set<Long> = emptySet()
    private var gameTitle: String? = null

    private val _state = MutableStateFlow(QuizState())
    val state: StateFlow<QuizState> = _state.asStateFlow()
    private var options: MoveOptions? = null
    private var all: List<SavedPosition> = emptyList()
    /** Positions deleted during the quiz: "Again" leaves them out. */
    private val gone = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            val s = app.settings.settings.first()
            if (game != null) {
                // The priority and the limit pick the decisions once; "Repeat" asks the same ones.
                gameQuizDecisions(app, game.matchId)?.let { (m, ds) ->
                    val (cards, their) = gameQuizCards(m, com.boldblot.backgammonplyground.core.GameQuiz.pick(ds, game.rules))
                    all = cards; theirs = their; gameTitle = matchTitle(m)
                }
            } else all = spec.pick(quizPositions(app, spec.folders), s)
            _state.update { it.copy(loading = false, settings = s) }
            start()
            // Settings opened from the quiz apply when it comes back (the round's cards stay as they are).
            app.settings.settings.collect { n -> _state.update { it.copy(settings = n) } }
        }
    }

    /** The cards of the round asked last ([repeat] asks them again), and every card asked since the quiz opened ([another] leaves them out). */
    private var round: List<SavedPosition> = emptyList()
    private val seen = mutableSetOf<Long>()
    /** Cards answered since the quiz opened, as they are now: asked again, they compare with the latest answer. */
    private val latest = mutableMapOf<Long, SavedPosition>()

    /** Counts the cards moved on from: a slow analysis of an earlier card must not touch the card shown now. */
    private var cardToken = 0

    /**
     * Positions whose analysis is below the standard (Settings → Quiz): analyzed again while the card is shown, so the
     * answer rarely waits. Each position is tried once a round, so a search that falls short cannot repeat.
     */
    private val refreshed = mutableMapOf<Long, kotlinx.coroutines.Deferred<PositionAnalysis?>>()

    private fun staleAnalysis(sp: SavedPosition, s: Settings): Boolean =
        !detached && s.quizReanalyze && sp.id !in refreshed && sp.analysis?.let { belowStandard(sp.position, it, s) } == true

    private fun refreshAnalysis(sp: SavedPosition, s: Settings) {
        if (!staleAnalysis(sp, s)) return
        AppLog.i("Quiz: position ${sp.id} is below the standard (${sp.analysis?.label}), analyzing it again")
        refreshed[sp.id] = viewModelScope.async {
            runCatching { analyzePosition(app, sp.position, s, keep = keepMoves(sp)) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; AppLog.error("Quiz: analyzing again failed", it) }
                .getOrNull()
        }
    }

    /** A new round: the positions in [QuizSpec.order], at most [QuizSpec.limit] of them, none asked before when [fresh]. */
    fun start() = begin(fresh = false)

    /** "Another 10": a round with the same settings, of cards not asked yet since the quiz opened. */
    fun another() = begin(fresh = true)

    /** "Repeat": the same cards again (those deleted or learned left out). */
    fun repeat() = ask(round.filter { it.id !in gone })

    /** How many cards "Another" would ask (0 = none left, or the quiz asks them all). */
    fun anotherCount(): Int = if (spec.limit <= 0 || detached) 0 else minOf(spec.limit, all.count { it.id !in gone && it.id !in seen })

    private fun begin(fresh: Boolean) {
        val left = all.filter { it.id !in gone && !(fresh && it.id in seen) }
        // A game quiz's cards come picked and in order.
        val ordered = if (detached) left else spec.order.sort(left)
        round = if (spec.limit > 0 && !detached) ordered.take(spec.limit) else ordered
        ask(round)
    }

    private fun ask(picked: List<SavedPosition>) {
        seen += picked.map { it.id }
        // Turned over, a double becomes the doubler's full decision (the player's own answer is about the take).
        val turn = _state.value.settings.quizTheirCube == QuizTheirCube.FLIP && !detached
        val list = picked.map { old ->
            val sp = latest[old.id]?.let { l -> old.copy(entity = l.entity, lastAnswer = l.lastAnswer, labels = l.labels) } ?: old
            if (turn && sp.position.doubled) sp.copy(position = turnedToDoubler(sp.position), answer = null, played = null) else sp
        }
        _state.update {
            QuizState(
                loading = false, settings = it.settings, queue = list, total = list.size, finished = list.isEmpty(),
                friend = if (detached) game?.friend == true else spec.friend, detached = detached, gameTitle = gameTitle, theirs = theirs, theirView = game?.theirView ?: TheirView.COLOUR,
            )
        }
        show()
    }

    /** "Finish": ends the round now (an answered card counts) and shows the summary. */
    fun finish() {
        val st = _state.value
        val cur = st.current
        val r = st.result
        _state.update {
            it.copy(
                finished = true, stoppedEarly = true, queue = emptyList(), result = null, viewing = null,
                history = if (cur != null && r != null) it.history + QuizCard(cur, r) else it.history,
            )
        }
    }

    /** Sets up the board for the current position. */
    private fun show() {
        val p = _state.value.current?.position ?: return
        val d = p.dice
        options = d?.let { MoveOptions(p.board, Player.HUMAN, it.d1, it.d2) }
        val opts = options
        _state.value.current?.let { refreshAnalysis(it, _state.value.settings) }
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
     * Settings → Quiz). With the follow-up question it waits for [answerDoubleClaim].
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
            val t0 = android.os.SystemClock.elapsedRealtime()
            AppLog.i("Quiz: answer to position ${sp.id}" + if (sp.analysis == null) ", analyzing it first" else ", saved analysis ${sp.analysis?.label}")
            try {
                // A position saved without analysis (or imported) is analyzed now, and keeps it.
                refreshAnalysis(sp, s)
                val fresh = refreshed[sp.id]?.await()?.also { if (!detached) app.positions.setAnalysis(sp.id, it) }
                val a = fresh ?: sp.analysis ?: analyzePosition(app, p, s, keep = keepMoves(sp)).also { if (!detached) app.positions.setAnalysis(sp.id, it) }
                AppLog.i("Quiz: position ${sp.id} analyzed in ${android.os.SystemClock.elapsedRealtime() - t0} ms")
                val factor = factorOf(p)
                val asked = cubeAnswer?.let { if (partial) withBestResponse(p, a, it.double) else it }
                val judged = judge(sp, a, move, cube, asked, s, null)
                val given = MyAnswer(move = move, cube = cube)
                val asJudged = judged.withAsked(p, a, s, cubeAnswer.takeIf { partial }, claim)
                // The trend: this answer's cost against the last answer's (the first time, the original play's), both by
                // the analysis the position has now (a play outside the candidates is valued by the engine, as a new one is).
                // Not kept (Quiz a friend, game quiz): no trend, and nothing written.
                val trend = if (!keep) null else Labels.trend(
                    (sp.lastAnswer ?: sp.played)?.let { answerCost(p, a, it, s) },
                    if (move != null) judged.error else answerError(p, a, given),
                    right = asJudged.right,
                )
                val result = asJudged.copy(trend = trend)
                val counted = (if (keep) app.positions.setResult(sp.id, result.verdict, given, trend) else null)?.let {
                    sp.copy(
                        entity = it, analysis = a, lastAnswer = given,
                        labels = sp.labels.filterNotTo(HashSet()) { k -> Labels.isTrend(k) } + listOfNotNull(trend?.name),
                    )
                }
                counted?.let { latest[sp.id] = it }
                if (counted != null) app.positions.recordAnswer(sp.id, result.verdict, result.error / factor, cube = p.dice == null, match = p.setup.matchLength > 0)
                // A game quiz's answers count in the statistics like any quiz's (its positions are not in My positions, so
                // they carry no counts, folders or labels), unless it is a quiz for a friend.
                else if (detached && game?.friend != true) app.positions.recordAnswer(sp.id, result.verdict, result.error / factor, cube = p.dice == null, match = p.setup.matchLength > 0)
                val favourite = !detached && app.positions.isFavourite(sp.id)
                // A neutral answer leaves the balance alone, so it never moves a position to Learned.
                val deleted = counted != null && !result.neutral && s.quizAutoDelete && counted.quizBalance >= s.quizDeleteAt && !favourite
                if (deleted) {
                    if (s.quizLearned) app.positions.learn(sp.id, s.learnedKeep) else app.positions.remove(sp.id)
                    gone += sp.id
                }
                val played = sp.played?.let { answerWithError(p, a, it, s.errorUnit) }
                _state.update {
                    it.copy(
                        busy = false, result = result.copy(played = played, counts = counted?.let(::quizCountText), deleted = deleted, favourite = favourite), answered = it.answered + 1, right = it.right + if (result.right) 1 else 0, neutral = it.neutral + if (result.neutral) 1 else 0,
                        totalError = it.totalError + result.error / factor,
                        deleted = it.deleted + if (deleted) 1 else 0, learned = it.learned + if (deleted) 1 else 0,
                        board = p.board,
                        // The card as it is now: a repeat compares with this answer, and the list after the quiz shows its trend.
                        queue = if (counted != null && it.queue.firstOrNull()?.id == sp.id) listOf(counted) + it.queue.drop(1) else it.queue,
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
        val average = if (s.quizNeedsAverage) libraryAverageError(app) else null
        return if (move != null) {
            val c = a.checker!!
            val best = c.candidates.firstOrNull()
            val eq = playedEquity(p, c, move, s, engine)
            val err = if (eq == null) 0f else ((c.bestEquity - eq) * factor).coerceAtLeast(0f)
            val mine = sp.answer?.move
            val verdict = when {
                mine != null -> if (p.board.applyMove(Player.HUMAN, mine) == p.board.applyMove(Player.HUMAN, move)) QuizVerdict.RIGHT else QuizVerdict.WRONG
                else -> s.quizVerdict(err, average)
            }
            QuizResult(
                verdict == QuizVerdict.RIGHT, verdict == QuizVerdict.NEUTRAL, error = err, severity = XgRules.severity(err), move = move, bestMove = best?.move,
                mine = sp.answer?.let { answerText(p, it) }, equity = eq, bestEquity = best?.equity, analysis = a, factor = factor,
            )
        } else {
            val raw = if (cubeAnswer != null) cubeAnswerError(p, a, cubeAnswer) else cubeError(p, a, cube!!)
            val err = (raw ?: 0f) * factor
            val mine = sp.answer?.cube
            // My answer as a whole decision ("Double, take") is matched whole when the quiz asked for one.
            val mineWhole = sp.answer?.cubeAnswer()
            val verdict = when {
                mine != null && mineWhole != null && cubeAnswer != null -> if (mineWhole == cubeAnswer) QuizVerdict.RIGHT else QuizVerdict.WRONG
                mine != null -> if (mine == cube) QuizVerdict.RIGHT else QuizVerdict.WRONG
                else -> s.quizVerdict(err, average)
            }
            val bestAnswer = if (cubeAnswer != null) bestCubeAnswer(p, a) else null
            QuizResult(
                verdict == QuizVerdict.RIGHT, verdict == QuizVerdict.NEUTRAL, error = err, severity = XgRules.severity(err), cube = cube, cubeAnswer = cubeAnswer,
                bestCube = bestAnswer?.kind ?: bestCube(p, a), bestCubeAnswer = bestAnswer,
                mine = sp.answer?.let { answerText(p, it) }, analysis = a, factor = factor,
            )
        }
    }

    /** [move]'s equity by [c]: from its candidates, else analyzed now with [engine] at [c]'s depth (null without candidates). */
    private suspend fun playedEquity(p: StudyPosition, c: com.boldblot.backgammonplyground.core.CheckerAnalysis, move: Move, s: Settings, engine: EngineKind?): Float? =
        equityOf(p, c, move) ?: if (c.candidates.isEmpty()) null else app.engine.run(engine) {
            MatchController(this, DiceSource.secure(), AnalysisSettings(plies = c.plies, cubePlies = s.cubeDepth.code))
                .analyzeChecker(p.board, p.ctx(), p.dice!!, move, c.plies).playedEquity
        }

    /** What [ans] costs by [a] after the 1-point factor, like [answerError], but a play outside the candidates is analyzed now. */
    private suspend fun answerCost(p: StudyPosition, a: PositionAnalysis, ans: MyAnswer, s: Settings): Float? {
        answerError(p, a, ans)?.let { return it }
        val move = ans.move ?: return null
        val c = a.checker ?: return null
        if (p.dice == null) return null
        val eq = runCatching { playedEquity(p, c, move, s, null) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; AppLog.error("Quiz: the last answer could not be analyzed", it) }
            .getOrNull() ?: return null
        return ((c.bestEquity - eq) * factorOf(p)).coerceAtLeast(0f)
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
            val claimRight = engine == null || engine == claim
            r = r.copy(doubleClaim = claim, doubleRight = engine, right = r.right && claimRight, neutral = r.neutral && claimRight)
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
        val token = cardToken
        _state.update { it.copy(reanalyzing = true, message = null) }
        viewModelScope.launch {
            val t0 = android.os.SystemClock.elapsedRealtime()
            AppLog.i("Quiz: analyze again, position ${sp.id}, ${r.engine.label} ${r.checker.label()} / ${r.cube.label(cube = true)}")
            try {
                val p = sp.position
                val a = analyzePosition(app, p, r.engine, r.checker, r.cube, Waiting.ANALYSIS, keep = keepMoves(sp))
                AppLog.i("Quiz: analyze again of position ${sp.id} done in ${android.os.SystemClock.elapsedRealtime() - t0} ms" + if (token != cardToken) " (the quiz had moved on)" else "")
                val kept = !old.deleted && old.removed == null && !detached
                if (kept) app.positions.setAnalysis(sp.id, a)
                // A saved answer that the new analysis calls an error: the card asks what to do with it.
                val note = sp.answer?.takeIf { kept }?.let { mine ->
                    val cost = answerCost(p, a, mine, s) ?: return@let null
                    if (s.quizVerdict(cost, if (s.quizNeedsAverage) libraryAverageError(app) else null) == QuizVerdict.RIGHT) null
                    else SavedAnswerNote(answerText(p, mine), cost / factorOf(p))
                }
                rejudge(sp, a, old, r.engine, "Analyzed again: ${a.label}.", note, token)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLog.error("Quiz analysis failed", e)
                if (token == cardToken) _state.update { it.copy(reanalyzing = false, message = "Analysis failed: ${e.message}") }
            }
        }
    }

    /**
     * The answer [old] to [sp] judged again by analysis [a] (after "Analyze again", or a change of the saved
     * answer). If right / missed changes, the position's quiz counts and this round's score change with it.
     */
    private suspend fun rejudge(sp: SavedPosition, a: PositionAnalysis, old: QuizResult, engine: EngineKind?, message: String, note: SavedAnswerNote?, token: Int) {
        val s = _state.value.settings
        val p = sp.position
        val kept = !old.deleted && old.removed == null && !detached
        // An answer of the cube action alone is judged again with the opponent's best answer by the new analysis.
        val partialOf = old.cubeAnswer.takeIf { old.youText != null }
        val asked = partialOf?.let { withBestResponse(p, a, it.double) } ?: old.cubeAnswer
        val fresh = judge(sp.copy(analysis = a), a, old.move, old.cube, asked, s, engine).withAsked(p, a, s, partialOf, old.doubleClaim)
        val flipped = fresh.verdict != old.verdict
        val counted = if (kept && keep && flipped) app.positions.fixResult(sp.id, old.verdict, fresh.verdict)?.let { sp.copy(entity = it) } else null
        val result = fresh.copy(
            trend = old.trend,
            played = sp.played?.let { answerWithError(p, a, it, s.errorUnit) },
            counts = counted?.let(::quizCountText) ?: old.counts, deleted = old.deleted, removed = old.removed,
            favourite = old.favourite, learnedByHand = old.learnedByHand, savedNote = note,
        )
        if (token != cardToken) {
            // The quiz moved on while this ran: the position keeps its new analysis and the round its new score, and the
            // card in the list of answered ones (and a repeat of it) is updated, but what is on the screen stays.
            _state.update {
                it.copy(
                    history = it.history.map { c -> if (c.position.id == sp.id) c.copy(position = c.position.copy(analysis = a), result = result) else c },
                    queue = it.queue.map { q -> if (q.id == sp.id) q.copy(analysis = a, entity = counted?.entity ?: q.entity) else q },
                    right = it.right + (if (fresh.right) 1 else 0) - (if (old.right) 1 else 0),
                    neutral = it.neutral + (if (fresh.neutral) 1 else 0) - (if (old.neutral) 1 else 0),
                    totalError = it.totalError - old.error / old.factor + fresh.error / fresh.factor,
                )
            }
            return
        }
        _state.update {
            it.copy(
                reanalyzing = false, result = result, candidate = null,
                queue = it.queue.mapIndexed { i, q -> if (i == 0) sp.copy(analysis = a, entity = counted?.entity ?: q.entity) else q },
                right = it.right + (if (fresh.right) 1 else 0) - (if (old.right) 1 else 0),
                neutral = it.neutral + (if (fresh.neutral) 1 else 0) - (if (old.neutral) 1 else 0),
                totalError = it.totalError - old.error / old.factor + fresh.error / fresh.factor,
                message = message + if (flipped) " The verdict changed." else "",
            )
        }
    }

    /** The note after "Analyze again": the saved answer stays as it is. */
    fun keepSavedAnswer() = _state.update { it.copy(result = it.result?.copy(savedNote = null)) }

    /**
     * The note after "Analyze again": the saved answer becomes the analysis's best ([toBest]) or is cleared, and
     * the answer is judged again.
     */
    fun changeSavedAnswer(toBest: Boolean) {
        val st = _state.value
        val old = st.result ?: return
        val sp = st.current ?: return
        if (old.savedNote == null || st.reanalyzing || detached) return
        val a = old.analysis
        val p = sp.position
        val best = if (!toBest) null else a.checker?.candidates?.firstOrNull()?.let { MyAnswer(move = it.move) }
            ?: (if (p.doubled) bestCube(p, a)?.let { MyAnswer(cube = it) } else cubeAnswerChoices(p).minByOrNull { answerError(p, a, it) ?: Float.MAX_VALUE })
        if (toBest && best == null) return
        val token = cardToken
        _state.update { it.copy(reanalyzing = true, message = null) }
        viewModelScope.launch {
            try {
                app.positions.setAnswer(sp.id, best)
                rejudge(sp.copy(answer = best), a, old, null, if (best == null) "Saved answer cleared." else "Saved answer: ${answerText(p, best)}.", null, token)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLog.error("Quiz: the saved answer could not be changed", e)
                if (token == cardToken) _state.update { it.copy(reanalyzing = false, message = "Analysis failed: ${e.message}") }
            }
        }
    }

    /** The next position; a missed one comes back at the end with "Repeat until all are right". */
    fun next() {
        val st = _state.value
        val r = st.result ?: return
        val cur = st.current ?: return
        val again = r.verdict == QuizVerdict.WRONG && st.settings.quizRepeat && !r.deleted && r.removed == null
        val rest = st.queue.drop(1) + if (again) listOf(cur) else emptyList()
        cardToken++
        _state.update { it.copy(queue = rest, result = null, finished = rest.isEmpty(), history = it.history + QuizCard(cur, r), viewing = null, reanalyzing = false) }
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
        if (detached) return
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
        if (detached) return
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

    /** The Learned chip on the result: into Learned, or (tapped again) back. A favourite stays out of Learned. */
    fun toggleLearned(resetCounters: Boolean = false) {
        if (detached) return
        val st = _state.value
        val r = st.result ?: return
        val sp = st.current ?: return
        if (r.removed != null || r.favourite || (r.deleted && !r.learnedByHand)) return
        viewModelScope.launch {
            val back = r.learnedByHand
            if (back) {
                app.positions.unlearn(listOf(sp.id), resetCounters)
                gone -= sp.id
            } else {
                app.positions.learn(sp.id, st.settings.learnedKeep)
                gone += sp.id
            }
            val by = if (back) -1 else 1
            _state.update {
                it.copy(
                    result = it.result?.copy(deleted = !back, learnedByHand = !back),
                    deleted = it.deleted + by, learned = it.learned + by,
                )
            }
        }
    }

    /** A position that went to Learned in this round, put back from the summary. */
    fun putBack(id: Long, resetCounters: Boolean) {
        if (detached) return
        viewModelScope.launch {
            app.positions.unlearn(listOf(id), resetCounters)
            gone -= id
            _state.update { st ->
                st.copy(
                    history = st.history.map { c ->
                        if (c.position.id == id && c.result.deleted) c.copy(result = c.result.copy(deleted = false, learnedByHand = false)) else c
                    },
                    deleted = (st.deleted - 1).coerceAtLeast(0), learned = (st.learned - 1).coerceAtLeast(0),
                )
            }
        }
    }

    /** The star on the result: into Favourites (out of Learned, if the Learned chip just sent it there), or out. */
    fun toggleFavourite() {
        if (detached) return
        val st = _state.value
        val r = st.result ?: return
        val sp = st.current ?: return
        if (r.removed != null || (r.deleted && !r.learnedByHand)) return
        viewModelScope.launch {
            app.positions.setFavourite(sp.id, !r.favourite)
            val backFromLearned = r.learnedByHand
            if (backFromLearned) gone -= sp.id
            val by = if (backFromLearned) 1 else 0
            _state.update {
                it.copy(
                    result = it.result?.copy(favourite = !r.favourite, deleted = false, learnedByHand = false),
                    deleted = it.deleted - by, learned = it.learned - by,
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
