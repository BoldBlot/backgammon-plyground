package com.boldblot.backgammonplyground.ui.game

import com.boldblot.backgammonplyground.AppLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boldblot.backgammonplyground.BuildConfig
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.AnalysisSettings
import com.boldblot.backgammonplyground.core.BAR
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.CheckerAnalysis
import com.boldblot.backgammonplyground.core.CpuLevel
import com.boldblot.backgammonplyground.core.CubeEval
import com.boldblot.backgammonplyground.core.CubeState
import com.boldblot.backgammonplyground.core.DecisionRecord
import com.boldblot.backgammonplyground.core.DecisionKind
import com.boldblot.backgammonplyground.core.Dice
import com.boldblot.backgammonplyground.core.DiceSource
import com.boldblot.backgammonplyground.core.EngineKind
import com.boldblot.backgammonplyground.core.Feedback
import com.boldblot.backgammonplyground.core.FirstTry
import com.boldblot.backgammonplyground.core.XgRules
import com.boldblot.backgammonplyground.core.GameResult
import com.boldblot.backgammonplyground.core.MatchController
import com.boldblot.backgammonplyground.core.MatchRules
import com.boldblot.backgammonplyground.core.MatchState
import com.boldblot.backgammonplyground.core.MistakeRate
import com.boldblot.backgammonplyground.core.Move
import com.boldblot.backgammonplyground.core.MoveGenerator
import com.boldblot.backgammonplyground.core.MoveNotation
import com.boldblot.backgammonplyground.core.MoveOptions
import com.boldblot.backgammonplyground.core.OFF
import com.boldblot.backgammonplyground.core.PerformanceTotals
import com.boldblot.backgammonplyground.core.Phase
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.Reanalysis
import com.boldblot.backgammonplyground.core.Severity
import com.boldblot.backgammonplyground.core.Step
import com.boldblot.backgammonplyground.core.Summaries
import com.boldblot.backgammonplyground.core.WinType
import com.boldblot.backgammonplyground.data.AnalysisTiming
import com.boldblot.backgammonplyground.data.CpuArrows
import com.boldblot.backgammonplyground.data.GameKey
import com.boldblot.backgammonplyground.data.MatchReport
import com.boldblot.backgammonplyground.data.MeterMode
import com.boldblot.backgammonplyground.data.Settings
import com.boldblot.backgammonplyground.data.TrainingMode
import com.boldblot.backgammonplyground.ui.board.ArrowColors
import com.boldblot.backgammonplyground.ui.board.MoveArrow
import com.boldblot.backgammonplyground.ui.board.arrowsFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the player sees at the end of a game. */
data class GameOverInfo(
    val result: GameResult,
    val gamePerformance: PerformanceTotals,
    val cpuPerformance: PerformanceTotals,
    val score: List<Int>,
    val matchOver: Boolean,
    val matchWinner: Int?,
    val isMoney: Boolean,
    val matchId: Long,
    val gameNumber: Int,
    /** Match length (0 = money session). */
    val matchLength: Int,
    /** All games of the match or session so far, this one included. */
    val match: MatchReport?,
    /** The human's errors and blunders: of the whole match with [afterMatch], otherwise of this game. */
    val mistakes: Int = 0,
    /** The match is over and its mistakes were held back for now ([TrainingMode.AFTER_GAME]). */
    val afterMatch: Boolean = false,
    /** The game the mistake review opens on (the first with a mistake). */
    val reviewGame: Int = gameNumber,
    /** Played without analysis ([AnalysisTiming.NONE]): the summary offers to analyze the match now. */
    val offerAnalysis: Boolean = false,
    /** Decisions left unanalyzed by Stop: PR and mistakes count only the analyzed ones. */
    val notAnalyzed: Int = 0,
)

/** "Try again" training: the prompt names the mistake; the answer shows only when asked ([revealed]). */
data class RetryUi(val revealed: Boolean = false)

/** A short notice drawn in the middle of the board for a turn without a choice. */
data class BoardNotice(val kind: Kind, val title: String, val detail: String? = null) {
    enum class Kind { NO_MOVE, FORCED }
}

data class GameUiState(
    val loading: Boolean = true,
    val match: MatchState? = null,
    /** False if the player left this match out of the statistics. */
    val inStats: Boolean = true,
    /** Board as displayed (includes the human's partial move and CPU animation). */
    val board: Board = Board.INITIAL,
    val busy: Boolean = false,
    val busyText: String? = null,
    /** Human move input. */
    val steps: List<Step> = emptyList(),
    val movable: Set<Int> = emptySet(),
    /** The human's dice in display order; a tap on a checker uses the left die first. */
    val diceOrder: List<Int> = emptyList(),
    /** Per displayed die: 0 = available, 0.5 = half used (doubles), 1 = used. */
    val diceDim: List<Float> = emptyList(),
    /** Briefly true after a tap that cannot be played (red outline on the dice). */
    val diceError: Boolean = false,
    /** The computer's last roll, shown greyed until the human rolls or doubles. */
    val cpuLastDice: Dice? = null,
    val moveComplete: Boolean = false,
    val noLegalMove: Boolean = false,
    /** Practice mode prompt. */
    val feedback: Feedback? = null,
    /** Set while the prompt is a "Try again" one. */
    val retry: RetryUi? = null,
    /** Board shown instead of the live one while studying a move (with arrows). */
    val preview: Board? = null,
    val previewArrows: List<MoveArrow> = emptyList(),
    /** Candidate whose arrow is shown (index into the analysis list), -1 = played vs best. */
    val previewCandidate: Int? = null,
    val hint: CheckerAnalysis? = null,
    val cubeHint: CubeEval? = null,
    val hinted: Boolean = false,
    val message: String? = null,
    /** CPU's last move, drawn as markers (human orientation indices). */
    val lastCpuSteps: List<Step> = emptyList(),
    val gameOver: GameOverInfo? = null,
    val settings: Settings = Settings(),
    val meter: List<Float>? = null,
    val error: String? = null,
    /** Autofinish is playing the human's side (a tap anywhere stops it). */
    val autoFinishing: Boolean = false,
    /** "No move" / "Forced move" sign in the middle of the board, shown for a moment. */
    val notice: BoardNotice? = null,
    /** Resignation chosen while it was not the human's turn; offered at their next turn. */
    val queuedResign: WinType? = null,
    /** Decisions of this match still waiting for the background analysis. */
    val analysisLeft: Int = 0,
    /** Roughly how long those will take (ms; null until a decision has been timed). */
    val analysisEta: Long? = null,
    /** "Keep rolling": roll for the human even when they could double (until the cube changes or the game ends). */
    val keepRolling: Boolean = false,
    /** The meter in [MeterMode.BAR] shows its numbers after a tap, until the human's next decision. */
    val meterNumbers: Boolean = false,
)

/** Autofinish can be started while a game is in progress and nothing else is pending. */
val GameUiState.canAutoFinish: Boolean
    get() {
        val g = match?.game ?: return false
        return !busy && !autoFinishing && gameOver == null && g.phase != Phase.OVER && g.phase != Phase.OPENING
    }

/** The human may resign on their own turn, before or after rolling (not in the middle of a prompt). */
val GameUiState.canResign: Boolean
    get() {
        val g = match?.game ?: return false
        return !busy && feedback == null && g.turn == Player.HUMAN && (g.phase == Phase.PRE_ROLL || g.phase == Phase.MOVING)
    }

/**
 * The human may choose a resignation now that is offered at their next turn: while the computer
 * plays, or while they are closed out and the turns cycle by themselves.
 */
val GameUiState.canQueueResign: Boolean
    get() {
        val g = match?.game ?: return false
        return !canResign && !autoFinishing && gameOver == null && queuedResign == null &&
            g.resignOffer == null && g.phase != Phase.OVER && g.phase != Phase.OPENING
    }

private enum class AutoMode { FINISH, DEBUG }

class GameViewModel(val app: PlyGroundApp) : ViewModel() {

    private val repo = app.repository
    private val engineService = app.engine
    private val secureDice = DiceSource.secure()
    /** Debug builds only: dice values queued for the next rolls. */
    private val forcedDice = ArrayDeque<Int>()
    private val controller = MatchController(
        app.engine.analysis, DiceSource { forcedDice.removeFirstOrNull() ?: secureDice.die() }, opponents = { app.engine.of(it) },
    )

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private var options: MoveOptions? = null
    /**
     * Who plays the human's side: [AutoMode.FINISH] = autofinish (the engine at World Class, to the end of the
     * game, not analyzed or counted); [AutoMode.DEBUG] = debug builds' auto-play (with random
     * mistakes, analyzed as usual, across games); null = the human.
     */
    private var autoMode: AutoMode? = null
    private var autoJob: Job? = null
    /** State the running [autoJob] acts on (a step starts the next one before it ends). */
    private var autoFor: MatchState? = null
    private var cpuJob: Job? = null
    /** Plays the human's turn when there is no choice (no legal move, or a forced move). */
    private var humanAutoJob: Job? = null
    /** State the running [humanAutoJob] acts on. */
    private var humanAutoFor: MatchState? = null
    /** Match to continue with after the game-over panel is dismissed. */
    private var pendingNext: MatchState? = null
    private var noticeJob: Job? = null

    init {
        viewModelScope.launch {
            app.settings.settings.collect { s ->
                val checker = s.checkerDepth.code
                controller.settings = AnalysisSettings(
                    plies = checker, cubePlies = s.cubeDepth.code, cpuPlies = if (s.cpuAnalysis) checker else null,
                    deferred = s.quietPlay && s.analysisTiming == AnalysisTiming.BACKGROUND,
                    none = s.quietPlay && s.analysisTiming == AnalysisTiming.NONE,
                )
                val meterOn = s.meter != MeterMode.OFF && _state.value.settings.meter == MeterMode.OFF
                _state.update { it.copy(settings = s) }
                // The meter switched on (in Settings during a match): show it now, not after the next move.
                if (meterOn) _state.value.match?.let { updateMeter(it) }
            }
        }
        viewModelScope.launch {
            val current = repo.loadCurrent()
            val inStats = current?.let { repo.match(it.id)?.inStats } ?: true
            _state.update { it.copy(loading = false, inStats = inStats) }
            if (current != null) resume(current)
            // Decisions left for the background analysis when the app was closed.
            pendingGames += repo.gamesWithPending()
            startBackgroundAnalysis()
        }
    }

    private val settings: Settings get() = _state.value.settings

    // ---- lifecycle of a match ----------------------------------------------------------

    fun newMatch(rules: MatchRules, level: CpuLevel, mistakes: MistakeRate?, engine: EngineKind) {
        cpuJob?.cancel()
        stopAutoFinish()
        viewModelScope.launch {
            _state.value.match?.let { old -> if (!old.isOver) repo.finishMatch(old, abandoned = true) }
            val rate = mistakes?.takeIf { level == CpuLevel.MISTAKES }
            val id = repo.createMatch(rules, level, rate, engine)
            val m = MatchState(id = id, rules = rules, cpuLevel = level, mistakes = rate, engine = engine)
            pendingNext = null
            analyzedNow.clear()
            _state.update { GameUiState(loading = false, settings = it.settings) }
            startGame(m)
        }
    }

    /** Leaves the current match out of the statistics and rating, or counts it again. */
    fun toggleInStats() {
        val m = _state.value.match ?: return
        val on = !_state.value.inStats
        _state.update { it.copy(inStats = on) }
        viewModelScope.launch { repo.setInStats(m.id, on) }
    }

    /** Abandons the current match (statistics of finished games are kept). */
    fun abandonMatch() {
        cpuJob?.cancel()
        stopAutoFinish()
        val m = _state.value.match ?: return
        viewModelScope.launch {
            repo.finishMatch(m, abandoned = !m.rules.isMoney)
            repo.clearCurrent()
            pendingNext = null
            _state.update { GameUiState(loading = false, settings = it.settings) }
        }
    }

    private suspend fun startGame(m: MatchState) {
        val opened = engine { controller.openingRoll(m) }
        val g = opened.game
        val opening = g.rolls.last().dice
        val starter = if (g.turn == Player.HUMAN) "You start" else "Computer starts"
        _state.update { it.copy(keepRolling = false) }
        setMatch(opened, message = "Opening roll: you ${opening.d1}, computer ${opening.d2}. $starter.")
        continueFlow()
    }

    private fun resume(m: MatchState) {
        setMatch(m, message = null)
        if (m.game.phase == Phase.PRE_ROLL) _state.update { it.copy(cpuLastDice = m.game.opponentLastRoll(Player.HUMAN)) }
        if (m.game.phase == Phase.OVER) {
            viewModelScope.launch { handleGameOver(m) }
        } else if (m.game.phase == Phase.OPENING) {
            viewModelScope.launch { startGame(m) }
        } else {
            continueFlow()
        }
    }

    private suspend fun <T> engine(text: String? = null, block: () -> T): T {
        _state.update { it.copy(busy = true, busyText = text) }
        try {
            return engineService.run { block() }
        } catch (e: Throwable) {
            if (e !is kotlinx.coroutines.CancellationException) AppLog.error("Engine call failed (${text ?: "no text"})", e)
            throw e
        } finally {
            _state.update { it.copy(busy = false, busyText = null) }
        }
    }

    /** Installs a new match state and recomputes the move input helpers. */
    private fun setMatch(m: MatchState, message: String? = _state.value.message, keepCpuMarkers: Boolean = true) {
        val g = m.game
        if (message != null && message != _state.value.message) {
            AppLog.i("score ${m.score[Player.HUMAN]}-${m.score[Player.CPU]} cube ${g.cube.value} ${g.phase}: $message")
        }
        options = if (g.phase == Phase.MOVING && g.turn == Player.HUMAN && g.dice != null) {
            MoveOptions(g.board, Player.HUMAN, g.dice.d1, g.dice.d2)
        } else null
        val opts = options
        _state.update {
            it.copy(
                match = m, board = g.board, steps = emptyList(),
                movable = opts?.movableFrom(emptyList()) ?: emptySet(),
                diceOrder = g.dice?.highFirst ?: emptyList(),
                diceDim = if (opts != null && g.dice != null) diceDim(opts, emptyList(), g.dice.highFirst, false) else emptyList(),
                diceError = false,
                // The computer's last roll is shown until the human rolls or doubles.
                cpuLastDice = if (g.turn == Player.HUMAN && g.phase == Phase.PRE_ROLL) it.cpuLastDice else null,
                moveComplete = false, noLegalMove = opts != null && !opts.canMove,
                hint = null, cubeHint = null, preview = null, previewArrows = emptyList(), previewCandidate = null,
                hinted = if (g.phase == Phase.MOVING || g.phase == Phase.PRE_ROLL) it.hinted else false,
                message = if (opts != null && !opts.canMove) "You rolled ${g.dice}: no legal move." else message,
                lastCpuSteps = if (keepCpuMarkers) it.lastCpuSteps else emptyList(),
                // "Keep rolling" ends when the cube changes hands.
                keepRolling = it.keepRolling && keepRollingCube == g.cube,
            )
        }
        viewModelScope.launch { repo.saveCurrent(withAnalyzed(m)) }
        if (settings.meter != MeterMode.OFF) updateMeter(m)
    }

    private fun updateMeter(m: MatchState) {
        if (m.game.phase == Phase.OVER) return
        viewModelScope.launch {
            val eval = engineService.run { cubeDecision(m.game.board, m.ctx(Player.HUMAN), 0) }
            _state.update { it.copy(meter = eval.probs) }
        }
    }

    // ---- flow control ---------------------------------------------------------------

    /** Decides what happens next after any state change. */
    private fun continueFlow() {
        val st = _state.value
        val m = st.match ?: return
        val g = m.game
        // A "Try again" prompt always waits for the player.
        if (st.feedback != null && (settings.pauseOnMistake || st.retry != null) && autoMode == null) return
        if (g.phase == Phase.OVER && autoMode == AutoMode.FINISH) stopAutoFinish()
        if (g.phase == Phase.OVER && st.queuedResign != null) _state.update { it.copy(queuedResign = null) }
        val queued = st.queuedResign
        if (queued != null && autoMode == null && st.canResign) {
            _state.update { it.copy(queuedResign = null) }
            resign(queued)
            return
        }
        val mode = autoMode
        if (mode != null && g.phase != Phase.OVER && (g.turn == Player.HUMAN || g.phase == Phase.DOUBLED || g.resignOffer != null)) {
            autoStep(m, mode)
            return
        }
        when {
            g.phase == Phase.OVER -> viewModelScope.launch { handleGameOver(m) }
            g.turn == Player.CPU && g.resignOffer != null -> Unit // waiting for the human's answer
            g.turn == Player.CPU && g.phase != Phase.DOUBLED -> runCpu()
            // Rolls by themselves when there is no cube decision to make, or with "Keep rolling" on.
            g.turn == Player.HUMAN && g.phase == Phase.PRE_ROLL &&
                ((settings.autoRoll && !g.mayDouble(m.rules, m.score)) || st.keepRolling) -> roll()
            g.turn == Player.HUMAN && g.phase == Phase.MOVING -> playIfNoChoice(m)
            else -> Unit // waiting for the human
        }
    }

    private fun runCpu() {
        if (cpuJob?.isActive == true) return
        cpuJob = viewModelScope.launch {
            var m = _state.value.match ?: return@launch
            delay(250)
            while (m.game.turn == Player.CPU && m.game.phase in setOf(Phase.PRE_ROLL, Phase.MOVING)) {
                val before = m
                val (next, action) = engine("Computer is thinking…") { controller.cpuStep(m) }
                when (action) {
                    is MatchController.CpuAction.Doubled -> {
                        setMatch(next, message = "Computer doubles to ${before.game.cube.value * 2}.")
                        // Autofinish (and debug auto-play) answer the double themselves.
                        if (autoMode != null) continueFlow()
                        return@launch
                    }
                    is MatchController.CpuAction.OffersResign -> {
                        setMatch(next, message = "Computer offers to resign ${resignText(action.type, next)}.")
                        if (autoMode != null) continueFlow()
                        return@launch
                    }
                    is MatchController.CpuAction.Rolled -> {
                        setMatch(next, message = "Computer rolls ${next.game.dice}.", keepCpuMarkers = false)
                        delay(settings.animation.stepMillis.coerceAtLeast(150) + 250)
                    }
                    is MatchController.CpuAction.Moved -> {
                        animateCpuMove(before.game.board, action.move)
                        val text = if (action.move.isEmpty) "Computer cannot move." else
                            "Computer plays ${MoveNotation.format(before.game.board, Player.CPU, action.move)}."
                        if (action.move.isEmpty && autoMode == null) {
                            showNotice(BoardNotice(BoardNotice.Kind.NO_MOVE, "Computer can't move", "Rolled ${before.game.dice}"))
                        }
                        setMatch(next, message = text)
                        _state.update { it.copy(cpuLastDice = before.game.dice) }
                    }
                }
                m = next
            }
            continueFlow()
        }
    }

    private var arrowsJob: Job? = null

    /** The computer's move checker by checker, leaving its arrows on the board as [Settings.cpuArrows] says. */
    private suspend fun animateCpuMove(start: Board, move: Move) {
        val stepDelay = settings.animation.stepMillis
        val arrows = settings.cpuArrows
        arrowsJob?.cancel()
        var b = start
        val shown = mutableListOf<Step>()
        for (s in move.steps) {
            b = b.applyStep(Player.CPU, s)
            shown += s
            if (stepDelay > 0) {
                _state.update { it.copy(board = b, lastCpuSteps = if (arrows == CpuArrows.OFF) emptyList() else shown.toList()) }
                delay(stepDelay)
            }
        }
        _state.update { it.copy(lastCpuSteps = if (arrows == CpuArrows.OFF) emptyList() else move.steps) }
        if (arrows == CpuArrows.ONE_SECOND && move.steps.isNotEmpty()) {
            arrowsJob = viewModelScope.launch {
                delay(1000)
                _state.update { if (it.lastCpuSteps == move.steps) it.copy(lastCpuSteps = emptyList()) else it }
            }
        }
    }

    /**
     * The human's turn with no choice to make: with no legal move the turn passes after 0.5 s
     * (the roll stays visible); a forced move (only one resulting position) is played for them
     * when [Settings.autoForced] is on, and with [Settings.autoBearOff] a bear-off once contact is
     * broken (the analysis engine's best play; not analyzed or counted). Not while a practice prompt
     * is open or a move is being entered.
     */
    private fun playIfNoChoice(m: MatchState) {
        val opts = options ?: return
        val forced = opts.canMove && opts.isForced && settings.autoForced
        val bearOff = opts.canMove && !forced && settings.autoBearOff && m.game.board.isRace() && m.game.board.allHome(Player.HUMAN)
        if (opts.canMove && !forced && !bearOff) {
            return
        }
        if (bearOff) {
            bearOff(m)
            return
        }
        if (humanAutoJob?.isActive == true && humanAutoFor === m) return
        humanAutoFor = m
        humanAutoJob = viewModelScope.launch {
            fun blocked(st: GameUiState) =
                st.match !== m || st.busy || st.feedback != null || st.steps.isNotEmpty() || autoMode != null
            val move = opts.distinctMoves.firstOrNull()
            val notation = move?.let { MoveNotation.format(m.game.board, Player.HUMAN, it) }
            if (!blocked(_state.value)) {
                showNotice(
                    if (forced) BoardNotice(BoardNotice.Kind.FORCED, "Forced move", notation)
                    else BoardNotice(BoardNotice.Kind.NO_MOVE, "You can't move", "Rolled ${m.game.dice}"),
                )
            }
            delay(if (forced) 300 else 500)
            val st = _state.value
            if (blocked(st)) return@launch
            if (!forced || move == null) {
                confirmMove()
                return@launch
            }
            _state.update {
                it.copy(busy = true, hint = null, message = "Forced move: $notation.")
            }
            try {
                animateHumanMove(m.game.board, move)
            } finally {
                _state.update { it.copy(busy = false) }
            }
            applyHuman(engine { controller.humanMove(m, move, st.hinted) }, before = m)
        }
    }

    /** Auto-bear-off: the analysis engine's best play for the human, played after a moment. */
    private fun bearOff(m: MatchState) {
        if (humanAutoJob?.isActive == true && humanAutoFor === m) return
        humanAutoFor = m
        humanAutoJob = viewModelScope.launch {
            fun blocked(st: GameUiState) =
                st.match !== m || st.busy || st.feedback != null || st.steps.isNotEmpty() || autoMode != null
            delay(300)
            if (blocked(_state.value)) return@launch
            val move = engine("Bearing off…") { controller.hintMoves(m) }.candidates.firstOrNull()?.move ?: return@launch
            if (blocked(_state.value)) return@launch
            val notation = MoveNotation.format(m.game.board, Player.HUMAN, move)
            showNotice(BoardNotice(BoardNotice.Kind.FORCED, "Bear-off", notation))
            _state.update { it.copy(busy = true, message = "Bear-off: $notation.") }
            try {
                animateHumanMove(m.game.board, move)
            } finally {
                _state.update { it.copy(busy = false) }
            }
            applyHuman(engine { controller.humanMove(m, move, hinted = false, auto = true) }, before = m)
        }
    }

    /** Shows [n] in the middle of the board for [millis] (or until another notice replaces it). */
    private fun showNotice(n: BoardNotice, millis: Long = NOTICE_MILLIS) {
        noticeJob?.cancel()
        _state.update { it.copy(notice = n) }
        noticeJob = viewModelScope.launch {
            delay(millis)
            _state.update { if (it.notice === n) it.copy(notice = null) else it }
        }
    }

    private suspend fun handleGameOver(played: MatchState) {
        retry = null
        // Decisions the background analysis finished during the game go into its record.
        val m = withAnalyzed(played)
        analyzedNow.keys.removeAll { it.first == m.gameNumber }
        val summary = Summaries.of(m.game, m.rules.length)
        val factor = XgRules.errorFactor(m.rules.length)
        val mistakes = m.game.decisions.count { d ->
            d.player == Player.HUMAN && !d.auto && XgRules.severity((d.scoredError ?: 0f) * factor).atLeast(Severity.ERROR)
        }
        repo.saveFinishedGame(m, summary)
        if (m.game.decisions.any { it.pending }) {
            pendingGames += GameKey(m.id, m.gameNumber)
            startBackgroundAnalysis()
        }
        val next = m.finishGame()
        if (next.isOver) {
            repo.finishMatch(next)
            repo.clearCurrent()
        } else {
            repo.saveCurrent(next)
        }
        pendingNext = next
        if (!next.isOver && !settings.gameSummary) {
            _state.update { it.copy(notice = null) }
            nextGame()
            return
        }
        val afterMatch = settings.trainingMode == TrainingMode.AFTER_GAME && next.isOver
        val (matchMistakes, firstMistake) = if (afterMatch) repo.humanMistakes(m.id) else 0 to null
        val report = runCatching { repo.matchReport(m.id) }.getOrNull()
        _state.update {
            it.copy(
                notice = null,
                gameOver = GameOverInfo(
                    result = m.game.result!!, gamePerformance = summary.performance[0],
                    cpuPerformance = summary.performance[1], score = next.score,
                    matchOver = next.isOver, matchWinner = next.winner, isMoney = m.rules.isMoney,
                    matchId = m.id, gameNumber = m.gameNumber, matchLength = m.rules.length, match = report,
                    mistakes = if (afterMatch) matchMistakes else mistakes, afterMatch = afterMatch,
                    reviewGame = firstMistake ?: m.gameNumber,
                    offerAnalysis = settings.quietPlay && settings.analysisTiming == AnalysisTiming.NONE,
                ),
            )
        }
        updateAnalysisLeft()
    }

    fun nextGame() {
        val next = pendingNext ?: return
        pendingNext = null
        _state.update { it.copy(gameOver = null, lastCpuSteps = emptyList(), hinted = false) }
        if (next.isOver) {
            _state.update { GameUiState(loading = false, settings = it.settings) }
            return
        }
        viewModelScope.launch { startGame(next) }
    }

    /** Shows or hides the summary after each game (Settings, or the box on the summary). */
    fun setGameSummary(on: Boolean) {
        viewModelScope.launch { app.settings.update { it.copy(gameSummary = on) } }
    }

    /** Ends a money session after a game (or closes a finished match). */
    fun endSession() {
        val next = pendingNext ?: _state.value.match ?: return
        pendingNext = null
        viewModelScope.launch {
            if (!next.isOver) repo.finishMatch(next)
            repo.clearCurrent()
            _state.update { GameUiState(loading = false, settings = it.settings) }
        }
    }

    // ---- human actions -------------------------------------------------------------

    /** Adds the human's decision behind [fb] to the blunder list if it is a big enough mistake ([Settings.blunderList]). */
    private fun saveBlunder(next: MatchState, fb: Feedback?) {
        val min = settings.blunderList.minSeverity
        if (fb == null || !fb.severity.atLeast(min)) return
        val index = next.game.decisions.indexOfLast { it == fb.decision }
        if (index >= 0) viewModelScope.launch { repo.saveIfBlunder(next, index, min) }
    }

    /** Whether the practice prompt opens for [fb] now (not in [TrainingMode.AFTER_GAME]). */
    private fun shouldShow(fb: Feedback?): Boolean {
        if (settings.trainingMode == TrainingMode.AFTER_GAME) return false
        val threshold = settings.feedback.minSeverity
        return fb != null && threshold != null && fb.severity.atLeast(threshold)
    }

    /** A decision being tried again: the position before it, its first try and the tries since. */
    private data class Retry(val base: MatchState, val first: FirstTry, val retries: Int = 0)

    private var retry: Retry? = null

    /**
     * Applies the human's decision, made in [before]. In "Try again" training a mistake opens a
     * prompt that can return to [before]; a decision made again from there keeps its first try,
     * which is what PR counts and the blunder list keeps.
     */
    private fun applyHuman(result: Pair<MatchState, Feedback?>, message: String? = null, before: MatchState) {
        var (next, fb) = result
        val r = retry?.takeIf { it.base === before }
        if (r != null) {
            // The record of this decision follows the decisions made before it.
            val i = before.game.decisions.size
            val d = next.game.decisions.getOrNull(i)
            if (d != null && d.player == Player.HUMAN) {
                next = next.copy(game = next.game.copy(decisions = next.game.decisions.toMutableList().also {
                    it[i] = d.copy(firstTry = r.first.copy(retries = r.retries))
                }))
            }
        } else {
            saveBlunder(next, fb)
        }
        val show = shouldShow(fb)
        val tryAgain = show && settings.trainingMode == TrainingMode.TRY_AGAIN && autoMode == null
        retry = when {
            !tryAgain -> null
            r != null -> r
            else -> fb!!.decision.let { d -> Retry(before, FirstTry(d.kind, d.move, d.error, d.counted)) }
        }
        val text = when {
            r == null || show -> message
            (fb?.weightedError ?: 0f) < 0.0005f -> listOfNotNull("Right this time.", message).joinToString(" ")
            else -> listOfNotNull("Better this time.", message).joinToString(" ")
        }
        setMatch(next, message = text, keepCpuMarkers = false)
        _state.update { it.copy(feedback = if (show) fb else null, retry = if (tryAgain) RetryUi() else null, hinted = false, meterNumbers = false) }
        if (next.game.decisions.any { it.pending }) startBackgroundAnalysis()
        if (show && !tryAgain && settings.pauseOnMistake) showPlayedVsBest(fb!!)
        continueFlow()
    }

    /** "Try again": back to the position before the mistake. */
    fun tryAgain() {
        val r = retry ?: return
        if (_state.value.busy) return
        retry = r.copy(retries = r.retries + 1)
        _state.update { it.copy(feedback = null, retry = null, preview = null, previewArrows = emptyList(), previewCandidate = null) }
        setMatch(r.base, message = "Try again.", keepCpuMarkers = false)
        continueFlow()
    }

    /** "Show answer" on a "Try again" prompt: the full analysis, as in immediate training. */
    fun showAnswer() {
        val fb = _state.value.feedback ?: return
        retry = null
        _state.update { it.copy(retry = RetryUi(revealed = true)) }
        showPlayedVsBest(fb)
    }

    /**
     * Shows the position before the decision with the played move's arrows in its severity's colour,
     * and the best move's too with [Settings.bestMoveArrows] (otherwise it is picked from the list).
     */
    private fun showPlayedVsBest(fb: Feedback) {
        val d = fb.decision
        val a = d.checker ?: return
        val played = a.candidates.getOrNull(a.playedIndex)?.move ?: d.move ?: return
        val best = a.candidates.firstOrNull()?.move ?: return
        val mine = arrowsFor(Player.HUMAN, played, ArrowColors.forSeverity(fb.severity))
        if (settings.bestMoveArrows) preview(d.board, arrowsFor(Player.HUMAN, best, ArrowColors.best) + mine, -1)
        else preview(d.board, mine, a.playedIndex)
    }

    fun roll() {
        val m = _state.value.match ?: return
        if (m.game.phase != Phase.PRE_ROLL || m.game.turn != Player.HUMAN || _state.value.busy) return
        val hinted = _state.value.hinted
        viewModelScope.launch { applyHuman(engine { controller.humanRoll(m, hinted) }, before = m) }
    }

    fun double() {
        val m = _state.value.match ?: return
        if (!m.game.mayDouble(m.rules, m.score) || m.game.turn != Player.HUMAN || _state.value.busy) return
        val hinted = _state.value.hinted
        viewModelScope.launch { doDouble(m, hinted, auto = false) }
    }

    private suspend fun doDouble(m: MatchState, hinted: Boolean, auto: Boolean) {
        val result = engine("Computer is considering your double…") { controller.humanDouble(m, hinted, auto) }
        applyHuman(result, message = cpuCubeMessage(result.first), before = m)
    }

    /** The computer's answer to the human's double or beaver (its last decision in [next]), or null. */
    private fun cpuCubeMessage(next: MatchState): String? {
        val g = next.game
        val last = g.decisions.lastOrNull()?.takeIf { it.player == Player.CPU } ?: return null
        return when (last.kind) {
            DecisionKind.TAKE -> "Computer takes."
            DecisionKind.PASS -> "Computer passes."
            DecisionKind.BEAVER ->
                if (g.phase == Phase.BEAVERED) "Computer beavers to ${g.offeredCube}. Play on, or raccoon?"
                else "Computer beavers: the cube is at ${g.cube.value}."
            DecisionKind.RACCOON -> "Computer raccoons: the cube is at ${g.cube.value}."
            DecisionKind.NO_RACCOON -> "Computer plays on at ${g.cube.value}."
            else -> null
        }
    }

    /** The human answers the computer's double: take, pass, or beaver ([beaver] with [take]). */
    fun respond(take: Boolean, beaver: Boolean = false) {
        val m = _state.value.match ?: return
        if (m.game.phase != Phase.DOUBLED || m.game.responder != Player.HUMAN || _state.value.busy) return
        val hinted = _state.value.hinted
        viewModelScope.launch {
            val result = engine { controller.humanRespond(m, take, hinted, beaver = beaver) }
            applyHuman(result, message = cpuCubeMessage(result.first), before = m)
        }
    }

    /** The human answers the computer's beaver: play on at 4×, or raccoon to 8×. */
    fun answerBeaver(raccoon: Boolean) {
        val m = _state.value.match ?: return
        if (m.game.phase != Phase.BEAVERED || m.game.turn != Player.HUMAN || _state.value.busy) return
        val hinted = _state.value.hinted
        viewModelScope.launch { applyHuman(engine { controller.humanAnswerBeaver(m, raccoon, hinted) }, before = m) }
    }

    /** The human offers to resign [type]; the computer accepts or rejects. */
    fun resign(type: WinType) {
        val st = _state.value
        val m = st.match ?: return
        if (!st.canResign) {
            if (st.canQueueResign) {
                _state.update {
                    it.copy(queuedResign = type, message = "You will offer to resign ${resignText(type, m)} at your next turn.")
                }
            }
            return
        }
        viewModelScope.launch {
            val (next, accepted) = engine("Computer is considering your resignation…") { controller.humanResign(m, type) }
            if (accepted) {
                setMatch(next, message = "Computer accepts your resignation.", keepCpuMarkers = false)
                continueFlow()
            } else {
                // Keep a partly entered move on the board.
                _state.update { it.copy(match = next, message = "Computer rejects your resignation of ${resignText(type, next)}.") }
                repo.saveCurrent(next)
                continueFlow()
            }
        }
    }

    /** Withdraws a resignation queued for the human's next turn. */
    fun cancelQueuedResign() {
        _state.update { it.copy(queuedResign = null, message = "Resignation withdrawn.") }
    }

    /** The human accepts or rejects the computer's resignation. */
    fun answerResign(accept: Boolean) {
        val m = _state.value.match ?: return
        if (m.game.resignOffer == null || m.game.turn != Player.CPU || _state.value.busy) return
        val next = controller.humanAnswerResign(m, accept)
        setMatch(next, message = if (accept) "You accept the computer's resignation." else "You reject the computer's resignation.")
        continueFlow()
    }

    /**
     * Tap on a point (human orientation index 0..23) or the bar ([BAR]): the checker there moves
     * by the left die, or by the other die if the left one is used or cannot be played from there.
     * If neither works the dice flash red.
     */
    fun tap(target: Int) {
        val st = _state.value
        val opts = options ?: return
        if (st.busy || st.preview != null || target == OFF) return
        val ownChecker = if (target == BAR) st.board.bar(Player.HUMAN) > 0 else st.board.count(Player.HUMAN, target) > 0
        if (!ownChecker) return
        val step = if (st.moveComplete) null else opts.tapStep(st.steps, target, st.diceOrder)
        if (step == null) flashDice() else applySteps(st.steps + step)
    }

    /** Tap on the dice: finish the move when it is complete, otherwise swap the dice. */
    fun tapDice() {
        val st = _state.value
        val m = st.match ?: return
        val opts = options ?: return
        if (st.busy || st.preview != null || m.game.phase != Phase.MOVING || m.game.turn != Player.HUMAN) return
        if (st.moveComplete || st.noLegalMove) {
            confirmMove()
            return
        }
        val order = st.diceOrder.reversed()
        _state.update { it.copy(diceOrder = order, diceDim = diceDim(opts, it.steps, order, false)) }
    }

    private var flashJob: Job? = null

    private fun flashDice() {
        flashJob?.cancel()
        _state.update { it.copy(diceError = true) }
        flashJob = viewModelScope.launch {
            delay(450)
            _state.update { it.copy(diceError = false) }
        }
    }

    /** Drag-and-drop: move the checker on [from] to [to] if that is legal. */
    fun drag(from: Int, to: Int) {
        val st = _state.value
        val opts = options ?: return
        if (st.busy || st.moveComplete || from !in st.movable) return
        val chain = opts.destinations(st.steps, from)[to] ?: return
        applySteps(st.steps + chain)
    }

    private fun applySteps(steps: List<Step>) {
        val opts = options ?: return
        val m = _state.value.match ?: return
        var b = m.game.board
        for (s in steps) b = b.applyStep(Player.HUMAN, s)
        val complete = opts.isComplete(steps)
        _state.update {
            it.copy(
                board = b, steps = steps,
                movable = if (complete) emptySet() else opts.movableFrom(steps),
                diceDim = diceDim(opts, steps, it.diceOrder, complete), moveComplete = complete,
            )
        }
        if (complete && settings.autoConfirm) confirmMove()
    }

    fun undo() {
        val st = _state.value
        if (st.steps.isEmpty() || st.busy) return
        val m = st.match ?: return
        val opts = options ?: return
        val steps = st.steps.dropLast(1)
        var b = m.game.board
        for (s in steps) b = b.applyStep(Player.HUMAN, s)
        _state.update {
            it.copy(
                board = b, steps = steps,
                movable = opts.movableFrom(steps), diceDim = diceDim(opts, steps, it.diceOrder, false), moveComplete = false,
            )
        }
    }

    /**
     * How dimmed each displayed die is. For doubles each die stands for two moves, so every
     * move dims half a die (left die first). A finished move dims both; no legal move dims neither.
     */
    private fun diceDim(opts: MoveOptions, steps: List<Step>, order: List<Int>, complete: Boolean): List<Float> {
        if (order.size < 2) return emptyList()
        // With no legal move the roll stays bright, so it can be read.
        if (!opts.canMove) return listOf(0f, 0f)
        if (complete) return listOf(1f, 1f)
        val used = opts.usedDice(steps)
        if (order[0] == order[1]) {
            val u = used.size
            return listOf(minOf(u, 2) / 2f, maxOf(0, u - 2) / 2f)
        }
        return order.map { if (it in used) 1f else 0f }
    }

    fun confirmMove() {
        val st = _state.value
        val m = st.match ?: return
        val opts = options ?: return
        if (st.busy || m.game.phase != Phase.MOVING || m.game.turn != Player.HUMAN) return
        val move = when {
            !opts.canMove -> Move.NONE
            opts.isComplete(st.steps) -> Move(st.steps)
            else -> return
        }
        val hinted = st.hinted
        viewModelScope.launch { applyHuman(engine("Analyzing your move…") { controller.humanMove(m, move, hinted) }, before = m) }
    }

    fun hint() {
        val st = _state.value
        val m = st.match ?: return
        if (st.busy) return
        viewModelScope.launch {
            when {
                m.game.phase == Phase.MOVING && m.game.turn == Player.HUMAN -> {
                    val h = engine("Thinking…") { controller.hintMoves(m) }
                    _state.update { it.copy(hint = h, hinted = true) }
                }
                m.game.phase == Phase.PRE_ROLL && m.game.turn == Player.HUMAN -> {
                    val c = engine("Thinking…") { controller.analyzeCube(m) }
                    _state.update { it.copy(cubeHint = c, hinted = true) }
                }
                m.game.phase == Phase.DOUBLED && m.game.responder == Player.HUMAN ||
                    m.game.phase == Phase.BEAVERED && m.game.turn == Player.HUMAN -> {
                    val c = engine("Thinking…") { controller.analyzeCube(m) }
                    _state.update { it.copy(cubeHint = c, hinted = true) }
                }
            }
        }
    }

    /** The cube when "Keep rolling" was switched on: it switches off when the cube changes hands. */
    private var keepRollingCube: CubeState? = null

    /** "Keep rolling" on or off: rolls for the human even when they could double. */
    fun toggleKeepRolling() {
        val m = _state.value.match ?: return
        val on = !_state.value.keepRolling
        keepRollingCube = m.game.cube
        _state.update { it.copy(keepRolling = on) }
        if (on) continueFlow()
    }

    /** The meter's numbers in [MeterMode.BAR], until the human's next decision. */
    fun toggleMeterNumbers() = _state.update { it.copy(meterNumbers = !it.meterNumbers) }

    /** "Don't ask again" on the Double or Pass confirmation. */
    fun stopConfirming(double: Boolean) {
        viewModelScope.launch { app.settings.update { if (double) it.copy(confirmDouble = false) else it.copy(confirmPass = false) } }
    }

    // ---- background analysis ----------------------------------------------------------

    /** Finished games with decisions waiting for the background analysis. */
    private val pendingGames = LinkedHashSet<GameKey>()
    /** The current match's decisions analyzed in the background during their game: (game, index) → record. */
    private val analyzedNow = HashMap<Pair<Int, Int>, DecisionRecord>()
    private var analysisJob: Job? = null

    /** A decision waiting for the background analysis, with what it needs from its match. */
    private class PendingDecision(
        val matchId: Long, val gameNumber: Int, val index: Int, val decision: DecisionRecord,
        val matchLength: Int, val jacoby: Boolean, val opponentKey: String, val saved: Boolean,
    )

    /** [m] with the decisions of its game that the background analysis has finished. */
    private fun withAnalyzed(m: MatchState): MatchState {
        if (analyzedNow.keys.none { it.first == m.gameNumber }) return m
        val decisions = m.game.decisions.mapIndexed { i, d -> if (d.pending) analyzedNow[m.gameNumber to i] ?: d else d }
        return m.copy(game = m.game.copy(decisions = decisions))
    }

    /**
     * When the background analysis may take its next decision. In its own slot (PureTD analyzes,
     * Analyze during play on) at any time: the game never waits for it. Otherwise it shares the
     * game's engine, so only while no game is being played (a summary is open, or the match is over).
     */
    private fun idleForAnalysis(st: GameUiState): Boolean {
        if (engineService.twoSlots && st.settings.analyzeDuringPlay) return true
        if (st.gameOver != null) return true
        val g = st.match?.game ?: return true
        return g.phase == Phase.OVER
    }

    /** Average time per decision of the background analysis (ms; 0 = none timed yet). */
    private var analysisMillis = 0.0

    private fun startBackgroundAnalysis() {
        if (analysisJob?.isActive == true) return
        analysisJob = viewModelScope.launch {
            while (true) {
                updateAnalysisLeft()
                state.first { idleForAnalysis(it) }
                val next = nextPending() ?: break
                analyze(next)
            }
            updateAnalysisLeft()
        }
    }

    /** The oldest decision waiting: in finished games first, then in the game being played. */
    private suspend fun nextPending(): PendingDecision? {
        while (pendingGames.isNotEmpty()) {
            val key = pendingGames.first()
            val saved = repo.savedGame(key.matchId, key.number)
            val i = saved?.second?.decisions?.indexOfFirst { it.pending } ?: -1
            val match = if (i >= 0) repo.match(key.matchId) else null
            if (saved == null || match == null || i < 0) {
                pendingGames.remove(key)
                continue
            }
            return PendingDecision(key.matchId, key.number, i, saved.second.decisions[i], match.length, match.jacoby, match.cpuLevel, saved = true)
        }
        val m = _state.value.match ?: return null
        // A finished game's decisions are taken from its saved record (handleGameOver).
        if (m.game.phase == Phase.OVER) return null
        val i = m.game.decisions.indices.firstOrNull { m.game.decisions[it].pending && (m.gameNumber to it) !in analyzedNow } ?: return null
        return PendingDecision(m.id, m.gameNumber, i, m.game.decisions[i], m.rules.length, m.rules.jacoby, m.opponentKey, saved = false)
    }

    private suspend fun analyze(p: PendingDecision) {
        val s = settings
        val started = android.os.SystemClock.elapsedRealtime()
        val done = try {
            val work: com.boldblot.backgammonplyground.engine.SearchEngine.() -> DecisionRecord = {
                Reanalysis(this, s.checkerDepth.code, s.cubeDepth.code).decision(p.decision, p.matchLength, p.jacoby)
            }
            (if (engineService.twoSlots) engineService.runBackground(work) else engineService.run(block = work)).also {
                val ms = (android.os.SystemClock.elapsedRealtime() - started).toDouble()
                analysisMillis = if (analysisMillis == 0.0) ms else analysisMillis * 0.8 + ms * 0.2
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLog.error("Background analysis failed", e)
            p.decision
        }.copy(pending = false)
        if (p.saved) {
            val (e, g) = repo.savedGame(p.matchId, p.gameNumber) ?: return
            if (g.decisions.getOrNull(p.index)?.pending != true) return
            val game = g.copy(decisions = g.decisions.toMutableList().also { it[p.index] = done })
            repo.saveReanalyzedGame(e, game, p.matchLength)
            refreshGameOver(p.matchId, p.gameNumber, game, p.matchLength)
        } else {
            // Kept aside while the game goes on (the game's state is not touched mid-play) and saved with it.
            analyzedNow[p.gameNumber to p.index] = done
            _state.value.match?.takeIf { it.id == p.matchId }?.let { repo.saveCurrent(withAnalyzed(it)) }
        }
        if (done.player == Player.HUMAN) {
            repo.saveIfBlunder(done, p.matchId, p.gameNumber, p.index, p.matchLength, p.opponentKey, s.blunderList.minSeverity)
        }
    }

    /** The end-of-game summary of [matchId] with game [number] analyzed further ([game]). */
    private suspend fun refreshGameOver(matchId: Long, number: Int, game: com.boldblot.backgammonplyground.core.GameState, matchLength: Int) {
        val info = _state.value.gameOver?.takeIf { it.matchId == matchId } ?: return
        val summary = if (info.gameNumber == number) Summaries.of(game, matchLength) else null
        val factor = XgRules.errorFactor(matchLength)
        val gameMistakes = if (summary != null) game.decisions.count { d ->
            d.player == Player.HUMAN && !d.auto && XgRules.severity((d.scoredError ?: 0f) * factor).atLeast(Severity.ERROR)
        } else info.mistakes
        val (matchMistakes, firstMistake) = if (info.afterMatch) repo.humanMistakes(matchId) else 0 to null
        val report = runCatching { repo.matchReport(matchId) }.getOrNull()
        _state.update { st ->
            val cur = st.gameOver?.takeIf { it.matchId == matchId } ?: return@update st
            st.copy(
                gameOver = cur.copy(
                    gamePerformance = summary?.performance?.get(0) ?: cur.gamePerformance,
                    cpuPerformance = summary?.performance?.get(1) ?: cur.cpuPerformance,
                    match = report ?: cur.match,
                    mistakes = if (cur.afterMatch) matchMistakes else gameMistakes,
                    reviewGame = if (cur.afterMatch) firstMistake ?: cur.reviewGame else cur.reviewGame,
                ),
            )
        }
    }

    /** Counts the decisions of the current match (or the one whose summary is open) still to analyze. */
    private suspend fun updateAnalysisLeft() {
        val st = _state.value
        val id = st.gameOver?.matchId ?: st.match?.id
        var n = 0
        if (id != null) {
            for (key in pendingGames.filter { it.matchId == id }) {
                n += repo.savedGame(key.matchId, key.number)?.second?.decisions?.count { it.pending } ?: 0
            }
            st.match?.takeIf { it.id == id && it.game.phase != Phase.OVER }?.let { m ->
                n += m.game.decisions.indices.count { m.game.decisions[it].pending && (m.gameNumber to it) !in analyzedNow }
            }
        }
        _state.update { it.copy(analysisLeft = n, analysisEta = if (n > 0 && analysisMillis > 0) (analysisMillis * n).toLong() else null) }
    }

    /**
     * Stop on the summary: match [matchId]'s decisions still waiting stay unanalyzed (not counted
     * in PR or statistics; Analyze again in the review finishes them).
     */
    fun stopAnalysis(matchId: Long) {
        viewModelScope.launch {
            pendingGames.removeAll { it.matchId == matchId }
            val dropped = repo.dropPending(matchId)
            _state.update { st -> st.copy(gameOver = st.gameOver?.takeIf { it.matchId == matchId }?.let { it.copy(notAnalyzed = it.notAnalyzed + dropped) } ?: st.gameOver) }
            updateAnalysisLeft()
        }
    }

    /** "Analyze now" after a match played without analysis: its decisions go to the background analysis. */
    fun analyzeMatchNow(matchId: Long) {
        viewModelScope.launch {
            pendingGames += repo.markForAnalysis(matchId, cpu = settings.cpuAnalysis)
            _state.update { st -> st.copy(gameOver = st.gameOver?.copy(offerAnalysis = false)) }
            startBackgroundAnalysis()
        }
    }

    /** Debug builds only: the next roll will be a double of [value]. */
    fun forceDouble(value: Int) {
        if (!BuildConfig.DEBUG) return
        forcedDice.addLast(value)
        forcedDice.addLast(value)
    }

    fun setAutoPlay(on: Boolean) {
        if (!BuildConfig.DEBUG) return
        autoMode = if (on) AutoMode.DEBUG else null
        if (on) continueFlow()
    }

    /** Autofinish: the engine plays the human's side to the end of the game. */
    fun startAutoFinish() {
        val st = _state.value
        val m = st.match ?: return
        if (!st.canAutoFinish) return
        autoMode = AutoMode.FINISH
        // Drop a partly entered move, hints and an open practice prompt.
        setMatch(m)
        retry = null
        _state.update { it.copy(autoFinishing = true, feedback = null, retry = null, hinted = false) }
        continueFlow()
    }

    /** The player taps to stop autofinish and plays on (a forced move or no move is still played for them). */
    fun stopAutoFinishAndPlay() {
        if (autoMode != AutoMode.FINISH) return
        stopAutoFinish()
        continueFlow()
    }

    /** Stops autofinish; the human plays on from the current position. */
    fun stopAutoFinish() {
        if (autoMode != AutoMode.FINISH) return
        autoMode = null
        autoJob?.cancel()
        _state.update { it.copy(autoFinishing = false, board = it.match?.game?.board ?: it.board) }
    }

    private fun autoStep(m: MatchState, mode: AutoMode) {
        if (autoJob?.isActive == true && autoFor === m) return
        autoFor = m
        val g = m.game
        val auto = mode == AutoMode.FINISH
        autoJob = viewModelScope.launch {
            delay(if (auto) settings.autoFinishSpeed.pauseMillis else 300)
            if (autoMode != mode || _state.value.match !== m) return@launch
            when {
                g.resignOffer != null && g.turn == Player.CPU -> answerResign(accept = true)
                g.phase == Phase.DOUBLED && g.responder == Player.HUMAN -> {
                    val view = engine { if (auto) controller.autoCube(m) else controller.analyzeCube(m) }
                    val result = engine { controller.humanRespond(m, view.shouldTake, hinted = false, auto = auto, beaver = view.doubleTake < 0f) }
                    applyHuman(result, message = cpuCubeMessage(result.first), before = m)
                }
                g.phase == Phase.BEAVERED && g.turn == Player.HUMAN -> {
                    val view = engine { if (auto) controller.autoCube(m) else controller.analyzeCube(m) }
                    applyHuman(engine { controller.humanAnswerBeaver(m, raccoon = view.doubleTake > 0f, hinted = false, auto = auto) }, before = m)
                }
                g.phase == Phase.PRE_ROLL && g.turn == Player.HUMAN -> {
                    val wantDouble = g.mayDouble(m.rules, m.score) &&
                        engine { if (auto) controller.autoCube(m) else controller.analyzeCube(m) }.let { it.cubeAvailable && it.shouldDouble }
                    if (wantDouble) doDouble(m, hinted = false, auto = auto)
                    else applyHuman(engine { controller.humanRoll(m, hinted = false, auto = auto) }, before = m)
                }
                g.phase == Phase.MOVING && g.turn == Player.HUMAN -> {
                    val d = g.dice!!
                    val rnd = kotlin.random.Random
                    val move = when {
                        auto -> engine { controller.autoMove(m) }
                        rnd.nextFloat() < 0.25f -> MoveGenerator.legalMoves(g.board, Player.HUMAN, d.d1, d.d2).random(rnd)
                        else -> engine { app.engine.analysis.chooseMove(g.board, m.ctx(), d.d1, d.d2, CpuLevel.EXPERT) }
                    }
                    if (auto) animateHumanMove(g.board, move)
                    applyHuman(engine { controller.humanMove(m, move, hinted = false, auto = auto) }, before = m)
                }
            }
        }
    }

    /** Shows an automatic move of the human checker by checker (at the animation speed). */
    private suspend fun animateHumanMove(start: Board, move: Move) {
        val stepDelay = settings.animation.stepMillis
        if (stepDelay == 0L) return
        var b = start
        for (s in move.steps) {
            b = b.applyStep(Player.HUMAN, s)
            _state.update { it.copy(board = b) }
            delay(stepDelay)
        }
    }

    fun closeHint() = _state.update { it.copy(hint = null, cubeHint = null, preview = null, previewArrows = emptyList(), previewCandidate = null) }

    fun dismissFeedback() {
        retry = null
        _state.update { it.copy(feedback = null, retry = null, preview = null, previewArrows = emptyList(), previewCandidate = null) }
        continueFlow()
    }

    /** Shows [board] instead of the live position (null = back to live). */
    fun preview(board: Board?, arrows: List<MoveArrow> = emptyList(), candidate: Int? = null) =
        _state.update { it.copy(preview = board, previewArrows = arrows, previewCandidate = candidate) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    companion object {
        /** "a gammon (4 points)" for a resignation of [type] at the current cube. */
        fun resignText(type: WinType, m: MatchState): String {
            val points = com.boldblot.backgammonplyground.core.Resignation.result(Player.HUMAN, type, m.game.cube, m.rules).points
            val what = when (type) {
                WinType.SINGLE -> "a single game"
                WinType.GAMMON -> "a gammon"
                WinType.BACKGAMMON -> "a backgammon"
            }
            return "$what ($points point${if (points > 1) "s" else ""})"
        }

        /** How long a "No move" / "Forced move" notice stays on the board. */
        const val NOTICE_MILLIS = 1800L

        const val OFF_TARGET = -1
        const val BAR_TARGET = BAR
    }
}
