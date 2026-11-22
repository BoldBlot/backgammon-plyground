package com.boldblot.backgammonplyground.ui.deepdive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boldblot.backgammonplyground.AppLog
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.DecisionKind
import com.boldblot.backgammonplyground.core.DeepDive
import com.boldblot.backgammonplyground.core.DeepDive.Change
import com.boldblot.backgammonplyground.core.DeepDive.Slot
import com.boldblot.backgammonplyground.core.DeepDive.Tracked
import com.boldblot.backgammonplyground.core.DeepDive.Version
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.EngineKind
import com.boldblot.backgammonplyground.core.MoveNotation
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.PositionAnalysis
import com.boldblot.backgammonplyground.core.PositionIds
import com.boldblot.backgammonplyground.core.PositionSeries
import com.boldblot.backgammonplyground.core.StudyPosition
import com.boldblot.backgammonplyground.data.PositionSetsRepository
import com.boldblot.backgammonplyground.ui.components.bestCubeLabel
import com.boldblot.backgammonplyground.ui.components.describeAction
import com.boldblot.backgammonplyground.ui.positions.positionAnalysis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What a deep dive starts from: [base] (the player who decides at the bottom), the decision to follow, and the
 * engine and depths (null: those of Settings → Analysis).
 */
data class DeepDiveRequest(
    val base: StudyPosition,
    val tracked: Tracked,
    val engine: EngineKind? = null,
    val checker: Depth? = null,
    val cube: Depth? = null,
    val id: Long = System.nanoTime(),
)

/** What to change, as the chips name it. */
enum class DiveMode(val label: String) { CHECKER("One checker"), CHECKERS("Several checkers"), SCORE("Score"), CUBE("Cube") }

/** One version's analysis: [legal] false when the tracked decision cannot be made there (no analysis then). */
data class Outcome(val legal: Boolean, val loss: Float? = null, val best: String = "", val analysis: PositionAnalysis? = null) {
    val isBest: Boolean get() = legal && loss != null && loss <= DeepDive.TIE
}

data class DeepDiveState(
    val base: StudyPosition,
    val tracked: Tracked,
    val mode: DiveMode = DiveMode.CHECKER,
    val slots: List<Slot> = emptyList(),
    /** The slot the board's taps edit; slots.size: the next tap picks a new checker. */
    val editing: Int = 0,
    val scorePlayer: Int = Player.CPU,
    /** The versions analyzed (or being analyzed), and their outcomes so far (null: not yet). */
    val versions: List<Version> = emptyList(),
    val outcomes: List<Outcome?> = emptyList(),
    val running: Boolean = false,
    val selected: Int = 0,
    val saved: Boolean = false,
    val message: String? = null,
) {
    val board: Board get() = base.board
    val change: Change
        get() = when (mode) {
            DiveMode.CHECKER, DiveMode.CHECKERS -> Change.Checkers(slots)
            DiveMode.SCORE -> Change.Score(scorePlayer)
            DiveMode.CUBE -> Change.Cube
        }
    val problem: String? get() = DeepDive.problem(base, change)

    /** How many versions the set-up makes (before the cap). */
    val planned: Int
        get() = when (mode) {
            DiveMode.CHECKER, DiveMode.CHECKERS -> if (slots.isEmpty()) 0 else slots.fold(1) { n, s -> n * s.targets.size }
            else -> DeepDive.versions(base, change).size
        }
    val hasResults: Boolean get() = versions.isNotEmpty()

    /** The results are ranked as bars (several checkers: no natural order), not charted as a line. */
    val ranked: Boolean get() = mode == DiveMode.CHECKERS && slots.size > 1

    val trackedText: String
        get() = when (val t = tracked) {
            is Tracked.Play -> MoveNotation.format(base.board, Player.HUMAN, t.move)
            is Tracked.Cube -> describeAction(t.kind, base.board, Player.HUMAN, null)
        }
}

/** Deep dive: the set-up of the versions, their analysis one by one, the results (kept only when saved to a set). */
class DeepDiveViewModel(val app: PlyGroundApp, private val request: DeepDiveRequest) : ViewModel() {

    private val _state = MutableStateFlow(
        DeepDiveState(
            base = request.base, tracked = request.tracked,
            mode = if (request.base.isChecker) DiveMode.CHECKER else DiveMode.CUBE,
        ),
    )
    val state: StateFlow<DeepDiveState> = _state.asStateFlow()
    private var job: Job? = null

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun setMode(m: DiveMode) = _state.update {
        val keep = if (m == DiveMode.CHECKER) it.slots.take(1) else it.slots
        it.copy(mode = m, slots = if (m == DiveMode.CHECKER || m == DiveMode.CHECKERS) keep else it.slots, editing = if (keep.isEmpty()) 0 else keep.lastIndex)
    }

    fun setScorePlayer(p: Int) = _state.update { it.copy(scorePlayer = p) }

    /** For a cube decision: which action to follow. */
    fun setTracked(kind: DecisionKind) = _state.update { it.copy(tracked = Tracked.Cube(kind)) }

    /** A tap on the board (bottom player's numbering) while setting up checker changes. */
    fun tapPoint(t: Int) {
        if (t !in 0..23) return
        _state.update { st ->
            if (st.mode != DiveMode.CHECKER && st.mode != DiveMode.CHECKERS) return@update st
            val b = st.board
            val slot = st.slots.getOrNull(st.editing)
            if (slot == null) {
                val s = when {
                    b.count(Player.HUMAN, t) > 0 -> Slot(Player.HUMAN, t, listOf(t))
                    b.count(Player.CPU, 23 - t) > 0 -> Slot(Player.CPU, 23 - t, listOf(23 - t))
                    else -> return@update st.copy(message = "Tap a checker first: yours or your opponent's.")
                }
                val used = st.slots.count { it.player == s.player && it.from == s.from }
                if (used >= b.count(s.player, s.from)) return@update st.copy(message = "Every checker on that point is already moving.")
                return@update st.copy(slots = st.slots + s, editing = st.slots.size)
            }
            val i = if (slot.player == Player.HUMAN) t else 23 - t
            if (i == slot.from) return@update st
            if (b.count(Player.other(slot.player), 23 - i) > 0) {
                return@update st.copy(message = if (slot.player == Player.HUMAN) "Your opponent has checkers there." else "You have checkers there.")
            }
            val targets = if (i in slot.targets) slot.targets - i else slot.targets + i
            st.copy(slots = st.slots.toMutableList().also { it[st.editing] = slot.copy(targets = targets) })
        }
    }

    fun allOpenPoints() = editSlot { s -> s.copy(targets = DeepDive.openPoints(_state.value.board, s.player)) }
    fun clearPoints() = editSlot { s -> s.copy(targets = listOf(s.from)) }

    private fun editSlot(f: (Slot) -> Slot) = _state.update { st ->
        val s = st.slots.getOrNull(st.editing) ?: return@update st
        st.copy(slots = st.slots.toMutableList().also { it[st.editing] = f(s) })
    }

    /** Picks the slot the board edits; [i] = the number of slots: the next tap picks another checker. */
    fun editSlotAt(i: Int) = _state.update { it.copy(editing = i.coerceIn(0, it.slots.size)) }

    fun removeSlot(i: Int) = _state.update { st ->
        val slots = st.slots.filterIndexed { k, _ -> k != i }
        st.copy(slots = slots, editing = if (slots.isEmpty()) 0 else slots.lastIndex)
    }

    /** Several checkers: the next tap on the board picks one more checker. */
    fun addSlot() = _state.update { it.copy(editing = it.slots.size) }

    /** Analyzes the versions the set-up makes, one by one. */
    fun start() {
        val st = _state.value
        if (st.running) return
        st.problem?.let { msg -> _state.update { it.copy(message = msg) }; return }
        val versions = DeepDive.versions(st.base, st.change)
        if (versions.size < 2) { _state.update { it.copy(message = "That makes fewer than two versions to compare.") }; return }
        val first = versions.indexOfFirst { it.isBase }.coerceAtLeast(0)
        _state.update { it.copy(versions = versions, outcomes = List(versions.size) { null }, running = true, selected = first, saved = false) }
        job = viewModelScope.launch {
            val s = app.settings.settings.first()
            val engine = request.engine ?: s.analysisEngine
            val tracked = st.tracked
            versions.forEachIndexed { i, v ->
                val o = try {
                    evaluate(v.position, tracked, engine, request.checker ?: s.checkerDepth(v.position.setup.matchLength > 0), request.cube ?: s.cubeDepth(v.position.setup.matchLength > 0))
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    AppLog.error("Deep dive: version ${v.label} failed", e)
                    Outcome(legal = true, best = "Analysis failed")
                }
                _state.update { it.copy(outcomes = it.outcomes.toMutableList().also { l -> l[i] = o }) }
            }
            AppLog.i("Deep dive: ${versions.size} versions of ${st.mode} done")
            _state.update { it.copy(running = false) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.update { it.copy(running = false) }
    }

    /** Back to the set-up: the results are dropped. */
    fun reset() {
        stop()
        _state.update { it.copy(versions = emptyList(), outcomes = emptyList(), selected = 0, saved = false) }
    }

    fun select(i: Int) = _state.update { it.copy(selected = i.coerceIn(0, maxOf(0, it.versions.lastIndex))) }

    private suspend fun evaluate(p: StudyPosition, t: Tracked, engine: EngineKind, checker: Depth, cube: Depth): Outcome {
        if (!DeepDive.isLegal(p, t)) return Outcome(legal = false)
        val keep = (t as? Tracked.Play)?.let { listOf(it.move) } ?: emptyList()
        val a = app.engine.run(engine, null, positionAnalysis(p, engine, checker, cube, keep))
        a.checker?.let { c ->
            val best = c.candidates.firstOrNull()?.let { MoveNotation.format(p.board, Player.HUMAN, it.move) } ?: ""
            return Outcome(legal = true, loss = c.error, best = best, analysis = a)
        }
        val e = a.cube ?: return Outcome(legal = true, best = "No analysis")
        val kind = (t as Tracked.Cube).kind
        val loss = DeepDive.cubeLoss(e, kind, p.beavers) ?: return Outcome(legal = false)
        return Outcome(legal = true, loss = loss, best = bestCubeLabel(e, if (p.doubled) DecisionKind.TAKE else null, p.beavers), analysis = a)
    }

    /** The version's position as an XGID for Analyze Position (a double on offer included). */
    fun openInAnalysis(i: Int) {
        val v = _state.value.versions.getOrNull(i) ?: return
        val p = v.position
        app.pendingAnalyze.value = PositionIds.toXgid(p.setup.copy(doubled = p.doubled))
    }

    /** Which versions Save keeps. */
    enum class Which { ALL, NOT_BEST, SHOWN }

    /** Saves the chosen versions with their analyses as a new set of the Match Library. */
    fun save(name: String, which: Which, done: () -> Unit) {
        val st = _state.value
        val picked = st.versions.indices.filter { i ->
            when (which) {
                Which.ALL -> true
                Which.NOT_BEST -> st.outcomes.getOrNull(i)?.isBest != true
                Which.SHOWN -> i == st.selected
            }
        }
        if (picked.isEmpty()) { _state.update { it.copy(message = "No versions to save.") }; return }
        viewModelScope.launch {
            val items = picked.map { PositionSeries.Item(st.versions[it].position, bottomDecides = true) }
            val setId = app.sets.create(name, items, fromMatch = false, bottomName = "", topName = "", matchLength = 0)
            val ids = app.sets.positionIds(setId)
            picked.forEachIndexed { k, i ->
                val a = st.outcomes.getOrNull(i)?.analysis ?: return@forEachIndexed
                ids.getOrNull(k)?.let { app.sets.setAnalysis(PositionSetsRepository.cardId(it), a) }
            }
            AppLog.i("Deep dive: saved ${picked.size} versions as set $setId")
            _state.update { it.copy(saved = true, message = "Saved ${picked.size} positions: Match Library → Sets.") }
            done()
        }
    }

    override fun onCleared() {
        job?.cancel()
    }
}
