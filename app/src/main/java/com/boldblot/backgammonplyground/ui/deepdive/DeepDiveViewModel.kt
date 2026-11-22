package com.boldblot.backgammonplyground.ui.deepdive

import androidx.compose.ui.graphics.Color
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
import com.boldblot.backgammonplyground.core.Move
import com.boldblot.backgammonplyground.core.MoveGenerator
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
 * What a deep dive starts from: [base] (the player who decides at the bottom), the decision to follow first, the
 * plays offered for the other colours ([candidates], best first; empty: every legal play), and the engine and depths
 * (null: those of Settings → Analysis).
 */
data class DeepDiveRequest(
    val base: StudyPosition,
    val tracked: Tracked,
    val candidates: List<Move> = emptyList(),
    val engine: EngineKind? = null,
    val checker: Depth? = null,
    val cube: Depth? = null,
    /** More decisions for the other colour slots, in order (from My positions and the quiz). */
    val preset: List<Tracked> = emptyList(),
    val id: Long = System.nanoTime(),
) {
    companion object {
        /** A cube answer as a followed decision (a beaver counts as the take; raccoons are not followed). */
        private fun tracked(a: com.boldblot.backgammonplyground.core.MyAnswer?): Tracked? = when {
            a == null -> null
            a.move != null -> a.move.takeIf { !it.isEmpty }?.let { Tracked.Play(it) }
            a.cube == DecisionKind.BEAVER -> Tracked.Cube(DecisionKind.TAKE)
            a.cube in setOf(DecisionKind.DOUBLE, DecisionKind.NO_DOUBLE, DecisionKind.TAKE, DecisionKind.PASS) -> Tracked.Cube(a.cube!!)
            else -> null
        }

        /**
         * From My positions and the quiz: the colour slots start with what was played in the game, the last quiz answer
         * and the best decision of [a] (the same decision once); null when there is none of them.
         */
        fun preset(p: StudyPosition, a: PositionAnalysis?, played: com.boldblot.backgammonplyground.core.MyAnswer?, last: com.boldblot.backgammonplyground.core.MyAnswer?): DeepDiveRequest? {
            val best = of(p, a)?.tracked
            val list = listOfNotNull(tracked(played), tracked(last), best)
            val first = list.firstOrNull() ?: return null
            return DeepDiveRequest(p, first, a?.checker?.candidates?.map { it.move }.orEmpty(), preset = list.drop(1))
        }

        /**
         * A deep dive of [p] following [move] or [cube] (an answer, a play made), else the best decision of [a];
         * null when there is nothing to follow (a checker play without a play or an analysis).
         */
        fun of(p: StudyPosition, a: PositionAnalysis?, move: Move? = null, cube: DecisionKind? = null): DeepDiveRequest? {
            val moves = a?.checker?.candidates?.map { it.move }.orEmpty()
            val e = a?.cube
            val tracked: Tracked = when {
                p.isChecker && move != null && !move.isEmpty -> Tracked.Play(move)
                p.isChecker -> moves.firstOrNull()?.takeIf { !it.isEmpty }?.let { Tracked.Play(it) } ?: return null
                cube != null -> Tracked.Cube(if (cube == DecisionKind.BEAVER) DecisionKind.TAKE else cube)
                e != null && p.doubled -> Tracked.Cube(if (e.doubleTake <= e.doublePass) DecisionKind.TAKE else DecisionKind.PASS)
                e != null -> Tracked.Cube(if (e.shouldDouble) DecisionKind.DOUBLE else DecisionKind.NO_DOUBLE)
                p.doubled -> Tracked.Cube(DecisionKind.TAKE)
                else -> Tracked.Cube(DecisionKind.NO_DOUBLE)
            }
            return DeepDiveRequest(p, tracked, moves)
        }
    }
}

/** What to change, as the chips name it. */
enum class DiveMode(val label: String) { CHECKER("One checker"), CHECKERS("Several checkers"), SCORE("Score"), CUBE("Cube") }

/** One change being set up: its kind, its checker slots, and for the score whose score moves. */
data class DimSetup(
    val mode: DiveMode,
    val slots: List<Slot> = emptyList(),
    val scorePlayer: Int = Player.CPU,
    /** The score's range: from [from]-away (0: the match length) down to [to]-away, and a money game. */
    val from: Int = 0,
    val to: Int = 1,
    val money: Boolean = true,
) {
    val change: Change
        get() = when (mode) {
            DiveMode.CHECKER, DiveMode.CHECKERS -> Change.Checkers(slots)
            DiveMode.SCORE -> Change.Score(scorePlayer, from, to, money)
            DiveMode.CUBE -> Change.Cube
        }
    val checkers: Boolean get() = mode == DiveMode.CHECKER || mode == DiveMode.CHECKERS
}

/** A version's analysis (null while not analyzed), the plays it was asked to keep, and whether it failed. */
data class VersionResult(val analysis: PositionAnalysis? = null, val kept: Set<Tracked> = emptySet(), val failed: Boolean = false)

/** The loss of one followed decision in one version. */
sealed interface Loss {
    /** It cannot be decided there (not legal, no cube to turn). */
    data object Illegal : Loss
    data object Pending : Loss
    data class Value(val v: Float) : Loss {
        val best: Boolean get() = v <= DeepDive.TIE
    }
}

/** The colours of the followed decisions: chart lines, arrows, highlights, legend slots. */
val TrackColors = listOf(Color(0xFF1E88E5), Color(0xFFF4511E), Color(0xFF8E24AA), Color(0xFF00897B))

data class DeepDiveState(
    val base: StudyPosition,
    /** What the colour slots offer, best first. */
    val options: List<Tracked>,
    /** The followed decisions by colour slot ([DeepDive.MAX_TRACKED]); the first is always set. */
    val tracked: List<Tracked?>,
    val dims: List<DimSetup>,
    /** The change and the slot the board's taps edit (slot = its slots' size: the next tap picks new checkers). */
    val editDim: Int = 0,
    val editing: Int = 0,
    val versions: List<Version> = emptyList(),
    /** Each change's axis labels, for the chart and the grid. */
    val axes: List<List<String>> = emptyList(),
    val results: List<VersionResult?> = emptyList(),
    /** [version][colour slot] */
    val losses: List<List<Loss>> = emptyList(),
    val running: Boolean = false,
    val selected: Int = 0,
    /** Which change runs along the chart (with two). */
    val primary: Int = 0,
    /** The analysis each version gets (null until Settings are read). */
    val engine: EngineKind? = null,
    val checkerDepth: Depth? = null,
    val cubeDepth: Depth? = null,
    val saved: Boolean = false,
    val message: String? = null,
) {
    val board: Board get() = base.board
    val changes: List<Change> get() = dims.map { it.change }
    val problem: String?
        get() = dims.firstNotNullOfOrNull { DeepDive.problem(base, it.change) }
            ?: if (dims.size == 2 && !dims[0].checkers && dims[0].mode == dims[1].mode &&
                !(dims[0].mode == DiveMode.SCORE && dims[0].scorePlayer != dims[1].scorePlayer)
            ) "Pick two different things to change (both scores: one each)." else null
    val planned: Int get() = if (problem != null) 0 else DeepDive.planned(base, changes)
    val hasResults: Boolean get() = versions.isNotEmpty()
    val secondary: Int? get() = if (dims.size == 2) 1 - primary else null

    /** The chart ranks bars (several checkers along it: no natural order) instead of drawing lines. */
    val ranked: Boolean get() = dims.getOrNull(primary)?.let { it.mode == DiveMode.CHECKERS && it.slots.size > 1 } == true

    /** The value of the second change the chart shows. */
    val slice: Int get() = secondary?.let { versions.getOrNull(selected)?.coords?.getOrNull(it) } ?: 0

    /** The versions along the chart: per step of the primary change, the version (at [slice]) or null. */
    val row: List<Int?>
        get() {
            val axis = axes.getOrNull(primary) ?: return emptyList()
            val sec = secondary
            return axis.indices.map { j ->
                versions.indices.firstOrNull { i -> versions[i].coords[primary] == j && (sec == null || versions[i].coords[sec] == slice) }
            }
        }

    fun text(t: Tracked): String = when (t) {
        is Tracked.Play -> MoveNotation.format(base.board, Player.HUMAN, t.move)
        is Tracked.Cube -> describeAction(t.kind, base.board, Player.HUMAN, null)
    }

    val firstText: String get() = text(tracked[0]!!)

    /** The first followed decision is (equal) best in version [i]. */
    fun firstBest(i: Int): Boolean = (losses.getOrNull(i)?.getOrNull(0) as? Loss.Value)?.best == true

    /**
     * The equity of followed decision [k] in version [i] (the bottom player's, normalized; for a take the taker's), or
     * null; [k] = -1: the best decision's.
     */
    fun equityOf(i: Int, k: Int): Float? {
        val v = versions.getOrNull(i) ?: return null
        val a = results.getOrNull(i)?.analysis ?: return null
        val t = if (k < 0) null else tracked.getOrNull(k) ?: return null
        a.checker?.let { c ->
            if (t == null) return c.candidates.firstOrNull()?.equity
            val m = (t as? Tracked.Play)?.move ?: return null
            val target = runCatching { v.position.board.applyMove(Player.HUMAN, m) }.getOrNull() ?: return null
            return c.candidates.firstOrNull { runCatching { v.position.board.applyMove(Player.HUMAN, it.move) }.getOrNull() == target }?.equity
        }
        val e = a.cube ?: return null
        val after = minOf(e.doubleTake, e.doublePass)
        val kind = (t as? Tracked.Cube)?.kind
        return when {
            v.position.doubled -> when (kind) {
                null -> maxOf(-e.doubleTake, -e.doublePass)
                DecisionKind.TAKE -> -e.doubleTake
                DecisionKind.PASS -> -e.doublePass
                else -> null
            }
            kind == null -> if (e.cubeAvailable) maxOf(e.noDouble, after) else e.noDouble
            kind == DecisionKind.NO_DOUBLE -> e.noDouble
            kind == DecisionKind.DOUBLE -> if (e.cubeAvailable) after else null
            else -> null
        }
    }

    /** The best play or cube action of version [i], or "". */
    fun bestText(i: Int): String {
        val v = versions.getOrNull(i) ?: return ""
        val a = results.getOrNull(i)?.analysis ?: return ""
        a.checker?.candidates?.firstOrNull()?.let { return MoveNotation.format(v.position.board, Player.HUMAN, it.move) }
        a.cube?.let { return bestCubeLabel(it, if (v.position.doubled) DecisionKind.TAKE else null, v.position.beavers) }
        return ""
    }
}

/** Deep dive: the set-up of the versions, their analysis one by one, the results (kept only when saved to a set). */
class DeepDiveViewModel(val app: PlyGroundApp, private val request: DeepDiveRequest) : ViewModel() {

    private val _state = MutableStateFlow(initial())
    val state: StateFlow<DeepDiveState> = _state.asStateFlow()
    private var job: Job? = null

    private fun initial(): DeepDiveState {
        val p = request.base
        val options: List<Tracked> = if (p.isChecker) {
            val d = p.dice!!
            val moves = request.candidates.ifEmpty { MoveGenerator.legalMoves(p.board, Player.HUMAN, d.d1, d.d2) }
            val all = (listOfNotNull((request.tracked as? Tracked.Play)?.move) + moves).filter { !it.isEmpty }
            all.distinctBy { runCatching { p.board.applyMove(Player.HUMAN, it) }.getOrNull() }.map { Tracked.Play(it) }
        } else if (p.doubled) listOf(Tracked.Cube(DecisionKind.TAKE), Tracked.Cube(DecisionKind.PASS))
        else listOf(Tracked.Cube(DecisionKind.NO_DOUBLE), Tracked.Cube(DecisionKind.DOUBLE))
        val wanted = (listOf(request.tracked) + request.preset).map { t -> options.firstOrNull { same(p, it, t) } ?: t }
        val slots = wanted.fold(emptyList<Tracked>()) { acc, t -> if (acc.any { same(p, it, t) }) acc else acc + t }.take(DeepDive.MAX_TRACKED)
        return DeepDiveState(
            base = p, options = (options + slots.filter { t -> options.none { same(p, it, t) } }), tracked = slots + List(DeepDive.MAX_TRACKED - slots.size) { null },
            engine = request.engine, checkerDepth = request.checker, cubeDepth = request.cube,
            dims = listOf(DimSetup(if (p.isChecker) DiveMode.CHECKER else DiveMode.CUBE)),
        )
    }

    /** Two plays are the same when they leave the same position. */
    private fun same(p: StudyPosition, a: Tracked, b: Tracked): Boolean = when {
        a is Tracked.Play && b is Tracked.Play ->
            runCatching { p.board.applyMove(Player.HUMAN, a.move) }.getOrNull() == runCatching { p.board.applyMove(Player.HUMAN, b.move) }.getOrNull()
        else -> a == b
    }

    init {
        // The analysis: the one the deep dive came from, else Settings → Analysis.
        viewModelScope.launch {
            val s = app.settings.settings.first()
            val match = request.base.setup.matchLength > 0
            _state.update {
                it.copy(
                    engine = it.engine ?: s.analysisEngine,
                    checkerDepth = it.checkerDepth ?: s.checkerDepth(match),
                    cubeDepth = it.cubeDepth ?: s.cubeDepth(match),
                )
            }
        }
    }

    /** Another engine or depths: after a run, every version is analyzed again. */
    fun setAnalysis(engine: EngineKind, checker: Depth, cube: Depth) {
        val st = _state.value
        if (st.engine == engine && st.checkerDepth == checker && st.cubeDepth == cube) return
        stop()
        _state.update { withLosses(it.copy(engine = engine, checkerDepth = checker, cubeDepth = cube, results = List(it.versions.size) { null })) }
        if (_state.value.hasResults) ensure()
    }

    /** The score change [d]'s range: from [from]-away down to [to]-away, and the money game. */
    fun setScoreRange(d: Int, from: Int, to: Int, money: Boolean) = updateDim(d) { it.copy(from = from, to = to, money = money) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    // ---- the followed decisions ----

    /** Sets colour slot [k] to [t] (null: empty; the first slot is always set). Missing analyses follow. */
    fun setTracked(k: Int, t: Tracked?) {
        if (k == 0 && t == null) return
        _state.update { st ->
            val list = st.tracked.toMutableList()
            // A decision already in another slot moves here.
            if (t != null) list.indices.forEach { j -> if (j != k && list[j]?.let { same(st.base, it, t) } == true) list[j] = null }
            list[k] = t
            if (list[0] == null) list[0] = t ?: st.tracked[0]
            withLosses(st.copy(tracked = list))
        }
        if (_state.value.hasResults) ensure()
    }

    // ---- the set-up ----

    fun setMode(d: Int, m: DiveMode) = _state.update { st ->
        val dim = st.dims.getOrNull(d) ?: return@update st
        val slots = if (m == DiveMode.CHECKER) dim.slots.take(1) else dim.slots
        // Two scores: one each.
        val other = st.dims.getOrNull(1 - d)
        val player = if (m == DiveMode.SCORE && other?.mode == DiveMode.SCORE) Player.other(other.scorePlayer) else dim.scorePlayer
        val dims = st.dims.toMutableList().also { it[d] = dim.copy(mode = m, scorePlayer = player, slots = if (m == DiveMode.CHECKER || m == DiveMode.CHECKERS) slots else dim.slots) }
        st.copy(dims = dims, editDim = d, editing = if (slots.isEmpty()) 0 else slots.lastIndex)
    }

    fun setScorePlayer(d: Int, p: Int) = updateDim(d) { it.copy(scorePlayer = p) }

    /** A second change: the first kind the first change is not. */
    fun addDim() = _state.update { st ->
        if (st.dims.size >= 2) return@update st
        val used = st.dims[0].mode
        val mode = when {
            used != DiveMode.SCORE && st.base.setup.matchLength > 0 -> DiveMode.SCORE
            used != DiveMode.CUBE -> DiveMode.CUBE
            else -> DiveMode.CHECKER
        }
        st.copy(dims = st.dims + DimSetup(mode), primary = 0)
    }

    fun removeDim() = _state.update { it.copy(dims = it.dims.take(1), editDim = 0, editing = it.dims[0].slots.size.coerceAtMost(maxOf(0, it.dims[0].slots.lastIndex)), primary = 0) }

    private fun updateDim(d: Int, f: (DimSetup) -> DimSetup) = _state.update { st ->
        val dim = st.dims.getOrNull(d) ?: return@update st
        st.copy(dims = st.dims.toMutableList().also { it[d] = f(dim) })
    }

    /** How many of [player]'s checkers on [from] the slots already move (all changes). */
    private fun used(st: DeepDiveState, player: Int, from: Int, except: Pair<Int, Int>? = null): Int =
        st.dims.withIndex().sumOf { (d, dim) -> dim.slots.withIndex().filter { (i, s) -> s.player == player && s.from == from && (d to i) != except }.sumOf { it.value.count } }

    /** A tap on the board (bottom player's numbering) while setting up checker changes. */
    fun tapPoint(t: Int) {
        if (t !in 0..23) return
        _state.update { st ->
            val d = st.editDim
            val dim = st.dims.getOrNull(d)?.takeIf { it.checkers } ?: return@update st
            val b = st.board
            val slot = dim.slots.getOrNull(st.editing)
            if (slot == null) {
                if (dim.mode == DiveMode.CHECKER && dim.slots.isNotEmpty()) return@update st
                val s = when {
                    b.count(Player.HUMAN, t) > 0 -> Slot(Player.HUMAN, t, listOf(t))
                    b.count(Player.CPU, 23 - t) > 0 -> Slot(Player.CPU, 23 - t, listOf(23 - t))
                    else -> return@update st.copy(message = "Tap a checker first: yours or your opponent's.")
                }
                if (used(st, s.player, s.from) >= b.count(s.player, s.from)) return@update st.copy(message = "Every checker on that point is already moving.")
                val dims = st.dims.toMutableList().also { it[d] = dim.copy(slots = dim.slots + s) }
                return@update st.copy(dims = dims, editing = dim.slots.size)
            }
            val i = if (slot.player == Player.HUMAN) t else 23 - t
            if (i == slot.from) return@update st
            if (b.count(Player.other(slot.player), 23 - i) > 0) {
                return@update st.copy(message = if (slot.player == Player.HUMAN) "Your opponent has checkers there." else "You have checkers there.")
            }
            val targets = if (i in slot.targets) slot.targets - i else slot.targets + i
            st.copy(dims = st.dims.toMutableList().also { it[d] = dim.copy(slots = dim.slots.toMutableList().also { l -> l[st.editing] = slot.copy(targets = targets) }) })
        }
    }

    private fun editSlot(f: (Slot) -> Slot) = _state.update { st ->
        val dim = st.dims.getOrNull(st.editDim) ?: return@update st
        val s = dim.slots.getOrNull(st.editing) ?: return@update st
        st.copy(dims = st.dims.toMutableList().also { it[st.editDim] = dim.copy(slots = dim.slots.toMutableList().also { l -> l[st.editing] = f(s) }) })
    }

    fun allOpenPoints() = editSlot { s -> s.copy(targets = DeepDive.openPoints(_state.value.board, s.player)) }
    fun clearPoints() = editSlot { s -> s.copy(targets = listOf(s.from)) }

    /** How many checkers slot [i] of change [d] moves together: 1 up to the checkers on its point not otherwise moving. */
    fun setCount(d: Int, i: Int, n: Int) = _state.update { st ->
        val dim = st.dims.getOrNull(d) ?: return@update st
        val s = dim.slots.getOrNull(i) ?: return@update st
        val max = st.board.count(s.player, s.from) - used(st, s.player, s.from, except = d to i)
        val c = n.coerceIn(1, maxOf(1, max))
        st.copy(dims = st.dims.toMutableList().also { it[d] = dim.copy(slots = dim.slots.toMutableList().also { l -> l[i] = s.copy(count = c) }) })
    }

    /** Picks the slot the board edits; [i] = its slots' size: the next tap picks other checkers. */
    fun editSlotAt(d: Int, i: Int) = _state.update { it.copy(editDim = d, editing = i.coerceIn(0, it.dims.getOrNull(d)?.slots?.size ?: 0)) }

    fun removeSlot(d: Int, i: Int) = _state.update { st ->
        val dim = st.dims.getOrNull(d) ?: return@update st
        val slots = dim.slots.filterIndexed { k, _ -> k != i }
        st.copy(dims = st.dims.toMutableList().also { it[d] = dim.copy(slots = slots) }, editDim = d, editing = if (slots.isEmpty()) 0 else slots.lastIndex)
    }

    /** Several checkers: the next tap on the board picks more checkers. */
    fun addSlot(d: Int) = _state.update { it.copy(editDim = d, editing = it.dims.getOrNull(d)?.slots?.size ?: 0) }

    // ---- the analysis ----

    /** Makes the versions and analyzes them one by one. */
    fun start() {
        val st = _state.value
        if (st.running) return
        st.problem?.let { msg -> _state.update { it.copy(message = msg) }; return }
        val (versions, axes) = DeepDive.plan(st.base, st.changes)
        if (versions.size < 2) { _state.update { it.copy(message = "That makes fewer than two versions to compare.") }; return }
        val first = versions.indexOfFirst { it.isBase }.coerceAtLeast(0)
        _state.update { withLosses(it.copy(versions = versions, axes = axes, results = List(versions.size) { null }, selected = first, primary = 0, saved = false)) }
        ensure()
    }

    /** The followed decisions legal in version [i]. */
    private fun legalIn(st: DeepDiveState, i: Int): List<Tracked> = st.tracked.filterNotNull().filter { DeepDive.isLegal(st.versions[i].position, it) }

    /** Version [i] needs (more) analysis: none yet, or a legal play it does not have and was not asked to keep. */
    private fun needsWork(st: DeepDiveState, i: Int): Boolean {
        val r = st.results.getOrNull(i)
        if (r?.failed == true) return false
        val legal = legalIn(st, i)
        if (legal.isEmpty()) return false
        val a = r?.analysis ?: return true
        val c = a.checker ?: return false
        return legal.any { t -> t is Tracked.Play && t !in r.kept && DeepDive.playLoss(c, st.versions[i].position.board, t.move) == null }
    }

    /** Analyzes, one by one, what the versions still need (a play added to the legend later is filled in). */
    private fun ensure() {
        if (job?.isActive == true) return
        _state.update { it.copy(running = true) }
        job = viewModelScope.launch {
            val s = app.settings.settings.first()
            while (true) {
                val st = _state.value
                val engine = st.engine ?: s.analysisEngine
                val i = st.versions.indices.firstOrNull { needsWork(st, it) } ?: break
                val v = st.versions[i]
                val legal = legalIn(st, i)
                val match = v.position.setup.matchLength > 0
                val r = try {
                    val keep = legal.filterIsInstance<Tracked.Play>().map { it.move }
                    val a = app.engine.run(engine, null, positionAnalysis(v.position, engine, st.checkerDepth ?: s.checkerDepth(match), st.cubeDepth ?: s.cubeDepth(match), keep))
                    VersionResult(a, legal.toSet())
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    AppLog.error("Deep dive: version ${v.label} failed", e)
                    VersionResult(failed = true)
                }
                _state.update { withLosses(it.copy(results = it.results.toMutableList().also { l -> if (i < l.size) l[i] = r })) }
            }
            AppLog.i("Deep dive: ${_state.value.versions.size} versions done")
            _state.update { it.copy(running = false) }
        }
    }

    /** [st] with the losses of every followed decision in every version worked out. */
    private fun withLosses(st: DeepDiveState): DeepDiveState = st.copy(
        losses = st.versions.indices.map { i ->
            val v = st.versions[i]
            val r = st.results.getOrNull(i)
            st.tracked.map { t ->
                when {
                    t == null -> Loss.Pending
                    !DeepDive.isLegal(v.position, t) -> Loss.Illegal
                    r == null || r.analysis == null -> if (r?.failed == true) Loss.Illegal else Loss.Pending
                    t is Tracked.Play -> r.analysis.checker?.let { DeepDive.playLoss(it, v.position.board, t.move) }?.let { Loss.Value(it) } ?: Loss.Pending
                    t is Tracked.Cube -> r.analysis.cube?.let { e -> DeepDive.cubeLoss(e, t.kind, v.position.beavers)?.let { Loss.Value(it) } ?: Loss.Illegal } ?: Loss.Pending
                    else -> Loss.Pending
                }
            }
        },
    )

    fun stop() {
        job?.cancel()
        job = null
        _state.update { it.copy(running = false) }
    }

    /** Back to the set-up: the results are dropped. */
    fun reset() {
        stop()
        _state.update { it.copy(versions = emptyList(), axes = emptyList(), results = emptyList(), losses = emptyList(), selected = 0, saved = false) }
    }

    fun select(i: Int) = _state.update { it.copy(selected = i.coerceIn(0, maxOf(0, it.versions.lastIndex))) }

    /** Shows value [j] of the second change: the version there at the same step of the first, or its nearest. */
    fun selectSlice(j: Int) = _state.update { st ->
        val sec = st.secondary ?: return@update st
        val at = st.versions.getOrNull(st.selected)?.coords?.get(st.primary) ?: 0
        val here = st.versions.indices.filter { st.versions[it].coords[sec] == j }
        val pick = here.minByOrNull { kotlin.math.abs(st.versions[it].coords[st.primary] - at) } ?: return@update st
        st.copy(selected = pick)
    }

    /** The second change runs along the chart, the first picks the slice. */
    fun swapAxes() = _state.update { st -> if (st.dims.size < 2) st else st.copy(primary = 1 - st.primary) }

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
                Which.NOT_BEST -> !st.firstBest(i)
                Which.SHOWN -> i == st.selected
            }
        }
        if (picked.isEmpty()) { _state.update { it.copy(message = "No versions to save.") }; return }
        viewModelScope.launch {
            val items = picked.map { PositionSeries.Item(st.versions[it].position, bottomDecides = true) }
            val setId = app.sets.create(name, items, fromMatch = false, bottomName = "", topName = "", matchLength = 0)
            val ids = app.sets.positionIds(setId)
            picked.forEachIndexed { k, i ->
                val a = st.results.getOrNull(i)?.analysis ?: return@forEachIndexed
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
