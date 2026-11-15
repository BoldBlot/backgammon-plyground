package com.boldblot.backgammonplyground.ui.position

import com.boldblot.backgammonplyground.engine.Waiting
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
import com.boldblot.backgammonplyground.core.CubeChoice
import com.boldblot.backgammonplyground.core.Move
import com.boldblot.backgammonplyground.core.PlayEntry
import com.boldblot.backgammonplyground.core.Step
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.PositionIds
import com.boldblot.backgammonplyground.core.PositionSetup
import com.boldblot.backgammonplyground.core.StudyPosition
import com.boldblot.backgammonplyground.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
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
    val level: Pair<Depth, Depth>? = null,
    /** A cube decision with a double on offer: the player on roll has doubled, the other player takes or passes. */
    val offered: Boolean = false,
    /** "Your play": the board takes the play of the player on roll instead of edits. */
    val playMode: Boolean = false,
    /** Steps of the play being entered (the player on roll's numbering). */
    val steps: List<Step> = emptyList(),
    /** The decision entered: a complete play ([Move.NONE] when the roll cannot be played)… */
    val play: Move? = null,
    /** …or the cube decision. */
    val cubeChoice: CubeChoice? = null,
) {
    /** Checker depth and cube plies used by the next analysis. */
    val engine: EngineKind get() = engineChoice ?: settings.analysisEngine
    val checkerDepth: Depth get() = level?.first ?: settings.checkerDepth(matchLength > 0)
    val cubeDepth: Depth get() = level?.second ?: settings.cubeDepth(matchLength > 0)

    /** The parts of the position the user edits (what Undo restores). */
    val edited: Edited get() = Edited(board, onRoll, dice, matchLength, score, crawford, jacoby, cube, offered)

    val hasDice: Boolean get() = dice[0] != null && dice[1] != null
    val crawfordPossible: Boolean get() = matchLength > 0 && score.any { it == matchLength - 1 }
    val isCrawford: Boolean get() = crawfordPossible && crawford

    /** A double on offer: only for a cube decision. */
    val isOffered: Boolean get() = offered && !hasDice

    /** Whether the player on roll may double: not in the Crawford game, nor with the cube on the other side. */
    val canDouble: Boolean get() = !isCrawford && (cube.value <= 1 || cube.owner == onRoll || cube.owner == -1)

    /** The play entry for the dice, or null for a cube decision. */
    val entry: PlayEntry? get() = if (hasDice) PlayEntry(board, onRoll, dice[0]!!, dice[1]!!) else null

    /** The board as shown: during "Your play", with the steps entered so far. */
    val shownBoard: Board get() = if (playMode && play == null && steps.isNotEmpty()) entry?.boardAfter(steps) ?: board else board

    /** The index of the play entered among the analysis's candidates, or null. */
    val playIndex: Int? get() {
        val a = checker ?: return null
        val p = play ?: return null
        val after = runCatching { board.applyMove(onRoll, p) }.getOrNull() ?: return null
        return a.candidates.indexOfFirst { runCatching { board.applyMove(onRoll, it.move) }.getOrNull() == after }.takeIf { it >= 0 }
    }

    /** Why the position cannot be analyzed yet, or null. */
    val problem: String?
        get() = when {
            board.onBoard(Player.HUMAN) == 0 -> "Place at least one of your checkers."
            board.onBoard(Player.CPU) == 0 -> "Place at least one of your opponent's checkers."
            (dice[0] == null) != (dice[1] == null) -> "Choose both dice, or none for a cube decision."
            isOffered && !canDouble -> if (isCrawford) "No doubling in the Crawford game." else "The player on roll can't double: the cube is on the other side."
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
    val offered: Boolean,
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
        _state.update { f(it.copy(checker = null, cubeEval = null, selected = 0, message = null, steps = emptyList(), play = null, cubeChoice = null)) }
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
                score = e.score, crawford = e.crawford, jacoby = e.jacoby, cube = e.cube, offered = e.offered,
                checker = null, cubeEval = null, selected = 0, message = null, canUndo = history.isNotEmpty(),
                steps = emptyList(), play = null, cubeChoice = null,
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

    /** A cube decision: the player on roll decides ([on] false), or has doubled and the other player answers. */
    fun setOffered(on: Boolean) = edit { it.copy(offered = on) }

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
                crawford = s.crawford, jacoby = s.jacoby, cube = s.cube, offered = s.doubled && s.dice == null,
            )
        }
        _state.update {
            it.copy(message = "Imported the ${s.format}." + if (s.doubled) " A double is on offer: take or pass." else "")
        }
        return null
    }

    /**
     * A position another app sent (an XGID): set up as an import, out of "Your play". One that cannot be read
     * leaves an empty board and says why.
     */
    fun openSent(text: String) {
        _state.update { it.copy(playMode = false) }
        val error = if (text.isBlank()) "No position came with the request." else import(text.trim())
        if (error != null) {
            clear()
            _state.update { it.copy(message = "The position sent could not be read: $error") }
        }
    }

    /** The position as an XGID or a GNU ID ("PositionID:MatchID"), for XG or gnubg. */
    fun exportId(xgid: Boolean): String {
        val st = _state.value
        val s = PositionSetup(
            board = st.board, onRoll = st.onRoll, dice = if (st.hasDice) Dice(st.dice[0]!!, st.dice[1]!!) else null,
            cube = if (st.isCrawford) CubeState() else st.cube, matchLength = st.matchLength, score = st.score,
            crawford = st.isCrawford, jacoby = st.jacoby, format = "", doubled = st.isOffered,
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
        // With a double on offer the player answering it decides (at the bottom); otherwise the player on roll.
        return StudyPosition(StudyPosition.turned(s, if (st.isOffered) Player.other(st.onRoll) else st.onRoll), doubled = st.isOffered, beavers = false)
    }

    /** Switches between setting the position up and entering the decision ("Your play"). */
    fun setPlayMode(on: Boolean) {
        _state.update { st ->
            val e = st.entry
            // A roll that cannot be played is its own decision.
            if (on && e != null && !e.options.canMove && st.play == null) st.copy(playMode = true, play = Move.NONE, message = "No legal move with this roll.")
            else st.copy(playMode = on)
        }
    }

    /** "Your play": a tap on [target] (human orientation) moves a checker there, or makes an empty point. */
    fun tapPlay(target: Int) {
        val st = _state.value
        val e = st.entry ?: return
        if (!st.playMode || st.play != null || st.analyzing) return
        if (e.isPointTap(st.steps, target)) {
            val plays = e.pointPlays(st.steps, target)
            when {
                plays.isEmpty() -> return
                plays.size == 1 -> setSteps(plays.single().steps)
                // Several ways to make the point: the engine's 1-ply best, as in a game.
                else -> viewModelScope.launch {
                    val ranked = runCatching {
                        app.engine.run(st.engine) { rankMoves(st.board, st.ctx(), e.d1, e.d2, Depth(1).code, null).candidates }
                    }.getOrDefault(emptyList())
                    val pick = ranked.firstNotNullOfOrNull { c ->
                        val after = st.board.applyMove(st.onRoll, c.move)
                        plays.firstOrNull { st.board.applyMove(st.onRoll, it.move) == after }
                    } ?: plays.first()
                    if (_state.value.steps == st.steps && _state.value.play == null) setSteps(pick.steps)
                }
            }
            return
        }
        e.tap(st.steps, target)?.let(::setSteps)
    }

    /** "Your play": a checker dragged from [from] to [to] (human orientation). */
    fun dragPlay(from: Int, to: Int) {
        val st = _state.value
        val e = st.entry ?: return
        if (!st.playMode || st.play != null || st.analyzing) return
        e.drag(st.steps, from, to)?.let(::setSteps)
    }

    /** Takes back the last checker moved in the play being entered (or the last of the complete play). */
    fun undoPlayStep() {
        val st = _state.value
        if (st.analyzing || st.steps.isEmpty()) return
        _state.update { it.copy(steps = it.steps.dropLast(1), play = null, selected = 0) }
    }

    /** Clears the decision entered. */
    fun clearDecision() = _state.update { it.copy(steps = emptyList(), play = if (it.entry?.options?.canMove == false) Move.NONE else null, cubeChoice = null, selected = 0) }

    /** The cube decision entered ([c] null clears it). */
    fun setCubeChoice(c: CubeChoice?) = _state.update { it.copy(cubeChoice = c) }

    private fun setSteps(steps: List<Step>) {
        val e = _state.value.entry ?: return
        val play = e.move(steps)
        _state.update { it.copy(steps = steps, play = play) }
        if (play != null) afterPlay()
    }

    /**
     * A play entered after an analysis: shown when the analysis searched it as deeply as the best play,
     * otherwise the position is analyzed again with it (as a game's analysis would).
     */
    private fun afterPlay() {
        val st = _state.value
        val a = st.checker ?: return
        if (a.candidates.isEmpty()) return
        val i = st.playIndex
        if (i != null && a.candidates[i].plies == a.candidates[0].plies) select(i) else analyze()
    }

    fun setEngine(e: EngineKind) = _state.update { it.copy(engineChoice = e) }

    /** Sets the level of the next analysis (not an edit: the position and its results stay). */
    fun setLevel(checker: Depth, cube: Depth) = _state.update { it.copy(level = checker to cube) }

    /** Analyzes the position; returns the analysis job (null when it cannot start). */
    fun analyze(): Job? {
        val st = _state.value
        if (st.analyzing) return null
        st.problem?.let { msg -> _state.update { it.copy(message = msg) }; return null }
        if (st.playMode && st.hasDice && st.play == null && st.steps.isNotEmpty()) {
            _state.update { it.copy(message = "Finish your play first, or clear it.") }
            return null
        }
        _state.update { it.copy(analyzing = true, message = null) }
        return viewModelScope.launch {
            val plies = st.checkerDepth.code
            val cubePlies = st.cubeDepth.code
            val ctx = st.ctx()
            val dice = if (st.hasDice) Dice(st.dice[0]!!, st.dice[1]!!) else null
            val result = runCatching {
                app.engine.run(st.engine, Waiting.ANALYSIS) {
                    if (dice != null) MatchController(this, DiceSource.secure(), AnalysisSettings(plies = plies, cubePlies = cubePlies))
                        .analyzeChecker(st.board, ctx, dice, st.play?.takeIf { !it.isEmpty }, plies) to null
                    else null to cubeDecision(st.board, ctx, cubePlies)
                }
            }
            _state.update {
                result.fold(
                    onSuccess = { (c, e) ->
                        // The play entered (if any) is the one shown: its arrows and its row.
                        val withResult = it.copy(checker = c, cubeEval = e)
                        it.copy(
                            analyzing = false, checker = c, cubeEval = e, selected = withResult.playIndex ?: 0,
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
