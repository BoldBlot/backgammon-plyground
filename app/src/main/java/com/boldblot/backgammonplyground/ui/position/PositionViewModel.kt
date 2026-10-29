package com.boldblot.backgammonplyground.ui.position

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.AnalysisSettings
import com.boldblot.backgammonplyground.core.BAR
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.CHECKERS_PER_SIDE
import com.boldblot.backgammonplyground.core.CheckerAnalysis
import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.CubeEval
import com.boldblot.backgammonplyground.core.CubeState
import com.boldblot.backgammonplyground.core.Dice
import com.boldblot.backgammonplyground.core.DiceSource
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.EngineKind
import com.boldblot.backgammonplyground.core.MatchController
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.PositionIds
import com.boldblot.backgammonplyground.core.PositionSetup
import com.boldblot.backgammonplyground.core.StudyPosition
import com.boldblot.backgammonplyground.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PositionState(
    val board: Board = Board.INITIAL,
    /** Whose checkers a tap on a point places. */
    val brush: Int = Player.HUMAN,
    val onRoll: Int = Player.HUMAN,
    /** Dice of the player on roll; both null = cube decision. */
    val dice: List<Int?> = listOf(null, null),
    /** 0 = money. */
    val matchLength: Int = 0,
    val score: List<Int> = listOf(0, 0),
    val crawford: Boolean = true,
    val jacoby: Boolean = true,
    val cube: CubeState = CubeState(),
    val analyzing: Boolean = false,
    val checker: CheckerAnalysis? = null,
    val cubeEval: CubeEval? = null,
    /** Checker candidate whose arrows are shown. */
    val selected: Int = 0,
    val message: String? = null,
    val settings: Settings = Settings(),
    /** Whether there is an edit to undo. */
    val canUndo: Boolean = false,
    /** Engine and level chosen on this screen; null = the analysis depths in Settings. */
    /** The engine picked on this screen (null: the analysis engine from Settings). */
    val engineChoice: EngineKind? = null,
    val level: Pair<Depth, Int>? = null,
) {
    /** Checker depth and cube plies used by the next analysis. */
    val engine: EngineKind get() = engineChoice ?: settings.analysisEngine
    val checkerDepth: Depth get() = level?.first ?: settings.checkerDepth
    val cubePlies: Int get() = level?.second ?: settings.cubePlies

    /** The parts of the position the user edits (what Undo restores). */
    val edited: Edited get() = Edited(board, onRoll, dice, matchLength, score, crawford, jacoby, cube)

    val hasDice: Boolean get() = dice[0] != null && dice[1] != null
    val crawfordPossible: Boolean get() = matchLength > 0 && score.any { it == matchLength - 1 }
    val isCrawford: Boolean get() = crawfordPossible && crawford

    /** Why the position cannot be analyzed yet, or null. */
    val problem: String?
        get() = when {
            board.onBoard(Player.HUMAN) == 0 -> "Place at least one of your checkers."
            board.onBoard(Player.CPU) == 0 -> "Place at least one of your opponent's checkers."
            (dice[0] == null) != (dice[1] == null) -> "Choose both dice, or none for a cube decision."
            matchLength > 0 && score.any { it >= matchLength } -> "Scores must be below the match length."
            else -> null
        }

    fun ctx(): CubeContext = CubeContext(
        matchLength = matchLength, score = score, crawford = isCrawford, jacoby = jacoby,
        cube = if (isCrawford) CubeState() else cube, onRoll = onRoll,
    )
}

data class Edited(
    val board: Board,
    val onRoll: Int,
    val dice: List<Int?>,
    val matchLength: Int,
    val score: List<Int>,
    val crawford: Boolean,
    val jacoby: Boolean,
    val cube: CubeState,
)

/** Position editor and analysis. */
class PositionViewModel(val app: PlyGroundApp) : ViewModel() {

    private val _state = MutableStateFlow(PositionState())
    val state: StateFlow<PositionState> = _state.asStateFlow()

    init {
        viewModelScope.launch { app.settings.settings.collect { s -> _state.update { it.copy(settings = s) } } }
    }

    private val history = ArrayDeque<Edited>()
    private var lastKey: String? = null
    private var lastAt = 0L

    /**
     * Applies an edit: drops analysis results (they belong to the old position) and remembers the
     * position before it for Undo. Steps of one drag ([key] repeated within a moment) undo together.
     */
    private fun edit(key: String? = null, f: (PositionState) -> PositionState) {
        val now = System.currentTimeMillis()
        val before = _state.value.edited
        val joins = key != null && key == lastKey && now - lastAt < GESTURE_MILLIS
        _state.update { f(it.copy(checker = null, cubeEval = null, selected = 0, message = null)) }
        lastKey = key
        lastAt = now
        if (!joins && _state.value.edited != before) {
            history.addLast(before)
            if (history.size > MAX_UNDO) history.removeFirst()
        }
        _state.update { it.copy(canUndo = history.isNotEmpty()) }
    }

    /** Restores the position before the last edit. */
    fun undo() {
        val e = history.removeLastOrNull() ?: return
        lastKey = null
        _state.update {
            it.copy(
                board = e.board, onRoll = e.onRoll, dice = e.dice, matchLength = e.matchLength,
                score = e.score, crawford = e.crawford, jacoby = e.jacoby, cube = e.cube,
                checker = null, cubeEval = null, selected = 0, message = null, canUndo = history.isNotEmpty(),
            )
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun setBrush(p: Int) = _state.update { it.copy(brush = p) }

    /**
     * A tap on a point (human orientation) at checker [slot]: sets the brush player's checkers
     * there to slot + 1, removes the top checker when it is tapped, adds one when the fifth
     * checker of a tall stack is tapped, and replaces the opponent's checkers.
     */
    fun tapPoint(target: Int, slot: Int) = edit { st ->
        val p = st.brush
        val other = Player.other(p)
        val idx = if (p == Player.HUMAN) target else 23 - target
        val n = st.board.count(p, idx)
        val o = st.board.count(other, 23 - idx)
        var want = when {
            o > 0 -> slot + 1
            slot >= 4 && n >= 5 -> n + 1
            n == slot + 1 -> slot
            else -> slot + 1
        }
        val available = CHECKERS_PER_SIDE - (st.board.onBoard(p) - n)
        val capped = want > available
        want = minOf(want, available)
        val b = setCount(setCount(st.board, other, 23 - idx, 0), p, idx, want)
        st.copy(board = b, message = if (capped) "All 15 checkers are on the board." else null)
    }

    /**
     * A drag along point [target] (human orientation): adds ([delta] > 0) or removes checkers of the
     * side already there, or of the brush player on an empty point.
     */
    fun swipePoint(target: Int, delta: Int) = edit("point $target") { st ->
        val mine = st.board.count(Player.HUMAN, target)
        val theirs = st.board.count(Player.CPU, 23 - target)
        val p = when { mine > 0 -> Player.HUMAN; theirs > 0 -> Player.CPU; else -> st.brush }
        val idx = if (p == Player.HUMAN) target else 23 - target
        val n = st.board.count(p, idx)
        val available = CHECKERS_PER_SIDE - (st.board.onBoard(p) - n)
        val want = (n + delta).coerceIn(0, available)
        st.copy(
            board = setCount(st.board, p, idx, want),
            message = if (n + delta > available) "All 15 checkers are on the board." else null,
        )
    }

    /** A drag along [player]'s half of the bar: adds or removes [delta] checkers there. */
    fun swipeBar(player: Int, delta: Int) = edit("bar $player") { st ->
        val n = st.board.bar(player)
        val available = CHECKERS_PER_SIDE - (st.board.onBoard(player) - n)
        st.copy(board = setCount(st.board, player, BAR, (n + delta).coerceIn(0, available)))
    }

    /** A tap on [player]'s half of the bar at [slot] (from the centre): removes from there or adds one. */
    fun tapBar(player: Int, slot: Int) = edit { st ->
        val n = st.board.bar(player)
        val want = if (slot < n) slot else minOf(n + 1, n + CHECKERS_PER_SIDE - st.board.onBoard(player))
        st.copy(board = setCount(st.board, player, BAR, want))
    }

    fun clear() = edit { it.copy(board = Board.fromSides(IntArray(25), IntArray(25))) }
    fun startPosition() = edit { it.copy(board = Board.INITIAL) }

    /** Exchanges the two sides (your checkers become your opponent's and vice versa). */
    fun swapSides() = edit { st ->
        val me = IntArray(25) { st.board.count(Player.CPU, it) }
        val opp = IntArray(25) { st.board.count(Player.HUMAN, it) }
        st.copy(
            board = Board.fromSides(me, opp),
            score = st.score.reversed(),
            cube = if (st.cube.owner == -1) st.cube else st.cube.copy(owner = Player.other(st.cube.owner)),
        )
    }

    fun setOnRoll(p: Int) = edit { it.copy(onRoll = p) }
    fun setDie(i: Int, v: Int?) = edit { st -> st.copy(dice = st.dice.toMutableList().also { it[i] = v }) }
    fun clearDice() = edit { it.copy(dice = listOf(null, null)) }

    fun setMatchLength(n: Int) = edit { st ->
        st.copy(matchLength = n, score = if (n == 0) listOf(0, 0) else st.score.map { minOf(it, n - 1) })
    }

    fun setScore(player: Int, v: Int) = edit { st ->
        st.copy(score = st.score.toMutableList().also { it[player] = v.coerceIn(0, maxOf(0, st.matchLength - 1)) })
    }

    fun setCrawford(on: Boolean) = edit { it.copy(crawford = on) }
    fun setJacoby(on: Boolean) = edit { it.copy(jacoby = on) }

    fun setCube(value: Int, owner: Int) = edit { st ->
        st.copy(cube = if (value <= 1) CubeState() else CubeState(value, if (owner == -1) Player.HUMAN else owner))
    }

    fun select(i: Int) = _state.update { it.copy(selected = i) }

    /**
     * Loads a position from an XGID or a GNU Backgammon ID ([PositionIds]); returns why it could
     * not be read, or null. One undo step brings back the position before.
     */
    fun import(text: String): String? {
        val s = runCatching { PositionIds.parse(text) }.getOrElse { return it.message ?: "Could not read that position." }
        edit {
            it.copy(
                board = s.board, onRoll = s.onRoll,
                dice = listOf(s.dice?.d1, s.dice?.d2), matchLength = s.matchLength, score = s.score,
                crawford = s.crawford, jacoby = s.jacoby, cube = s.cube,
            )
        }
        _state.update {
            it.copy(message = "Imported the ${s.format}." + if (s.doubled) " A double was on offer: this is the cube decision." else "")
        }
        return null
    }

    /** The position as an XGID or a GNU ID ("PositionID:MatchID"), for XG or gnubg. */
    fun exportId(xgid: Boolean): String {
        val st = _state.value
        val s = PositionSetup(
            board = st.board, onRoll = st.onRoll, dice = if (st.hasDice) Dice(st.dice[0]!!, st.dice[1]!!) else null,
            cube = if (st.isCrawford) CubeState() else st.cube, matchLength = st.matchLength, score = st.score,
            crawford = st.isCrawford, jacoby = st.jacoby, format = "",
        )
        return if (xgid) PositionIds.toXgid(s) else PositionIds.toGnu(s)
    }

    /** The position for My positions (the player on roll at the bottom), or null while it has a problem. */
    fun study(): StudyPosition? {
        val st = _state.value
        if (st.problem != null) return null
        val s = PositionSetup(
            board = st.board, onRoll = st.onRoll, dice = if (st.hasDice) Dice(st.dice[0]!!, st.dice[1]!!) else null,
            cube = if (st.isCrawford) CubeState() else st.cube, matchLength = st.matchLength, score = st.score,
            crawford = st.isCrawford, jacoby = st.jacoby, format = "XGID",
        )
        return StudyPosition(StudyPosition.turned(s, st.onRoll), doubled = false, beavers = false)
    }

    fun setEngine(e: EngineKind) = _state.update { it.copy(engineChoice = e) }

    /** Sets the level of the next analysis (not an edit: the position and its results stay). */
    fun setLevel(checker: Depth, cubePlies: Int) = _state.update { it.copy(level = checker to cubePlies) }

    fun analyze() {
        val st = _state.value
        if (st.analyzing) return
        st.problem?.let { msg -> _state.update { it.copy(message = msg) }; return }
        _state.update { it.copy(analyzing = true, message = null) }
        viewModelScope.launch {
            val plies = st.checkerDepth.code
            val cubePlies = Depth(st.cubePlies).code
            val ctx = st.ctx()
            val dice = if (st.hasDice) Dice(st.dice[0]!!, st.dice[1]!!) else null
            val result = runCatching {
                app.engine.run(st.engine) {
                    if (dice != null) MatchController(this, DiceSource.secure(), AnalysisSettings(plies = plies, cubePlies = cubePlies))
                        .analyzeChecker(st.board, ctx, dice, null, plies) to null
                    else null to cubeDecision(st.board, ctx, cubePlies)
                }
            }
            _state.update {
                result.fold(
                    onSuccess = { (c, e) ->
                        it.copy(
                            analyzing = false, checker = c, cubeEval = e, selected = 0,
                            message = if (c != null && c.candidates.isEmpty()) "No legal move with this roll." else null,
                        )
                    },
                    onFailure = { e -> it.copy(analyzing = false, message = "Analysis failed: ${e.message}") },
                )
            }
        }
    }

    companion object {
        private const val MAX_UNDO = 200
        private const val GESTURE_MILLIS = 1500L

        fun setCount(b: Board, player: Int, index: Int, n: Int): Board {
            val me = IntArray(25) { b.count(Player.HUMAN, it) }
            val opp = IntArray(25) { b.count(Player.CPU, it) }
            (if (player == Player.HUMAN) me else opp)[index] = n
            return Board.fromSides(me, opp)
        }
    }
}
