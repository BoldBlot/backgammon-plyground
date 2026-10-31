package com.boldblot.backgammonplyground.ui.review

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.StudyPosition
import com.boldblot.backgammonplyground.ui.positions.SaveToPositionsDialog
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.CpuLevel
import com.boldblot.backgammonplyground.core.CubeState
import com.boldblot.backgammonplyground.core.DecisionKind
import com.boldblot.backgammonplyground.core.DecisionRecord
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.Reanalysis
import com.boldblot.backgammonplyground.core.GameState
import com.boldblot.backgammonplyground.core.MoveNotation
import com.boldblot.backgammonplyground.core.PerformanceTotals
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.Severity
import com.boldblot.backgammonplyground.core.Summaries
import com.boldblot.backgammonplyground.core.XgRules
import com.boldblot.backgammonplyground.data.GameEntity
import com.boldblot.backgammonplyground.data.MatchEntity
import com.boldblot.backgammonplyground.data.Settings
import com.boldblot.backgammonplyground.ui.board.ArrowColors
import com.boldblot.backgammonplyground.ui.board.BoardView
import com.boldblot.backgammonplyground.ui.board.DiceView
import com.boldblot.backgammonplyground.ui.board.MoveArrow
import com.boldblot.backgammonplyground.ui.board.arrowsFor
import com.boldblot.backgammonplyground.ui.board.boardColorsFor
import com.boldblot.backgammonplyground.ui.components.CandidateList
import com.boldblot.backgammonplyground.ui.components.CubeTable
import com.boldblot.backgammonplyground.ui.components.Labelled
import com.boldblot.backgammonplyground.ui.components.PlayedBestBar
import com.boldblot.backgammonplyground.ui.components.SeverityBadge
import com.boldblot.backgammonplyground.ui.components.decisionCube
import com.boldblot.backgammonplyground.ui.components.describeAction
import com.boldblot.backgammonplyground.ui.components.fmt
import com.boldblot.backgammonplyground.ui.components.severityColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReviewFilter(val label: String) { ALL("All"), MINE("Mine"), MISTAKES("My mistakes") }

data class ReviewState(
    val loading: Boolean = true,
    val match: MatchEntity? = null,
    val games: List<Pair<GameEntity, GameState>> = emptyList(),
    val gameIndex: Int = 0,
    val filter: ReviewFilter = ReviewFilter.ALL,
    /** Index into the filtered decision list. */
    val position: Int = 0,
    /** Candidate whose arrow is shown; null = played and best together. */
    val candidate: Int? = null,
    /** Arrows, the bare position, or the position after the move (chosen in the list, or played). */
    val view: ReviewView = ReviewView.ARROWS,
    /** The selected decision's analysis is closed (tapped again), so the whole list shows. */
    val collapsed: Boolean = false,
    val settings: Settings = Settings(),
    /** Analyzing again: decisions done and in all; null = not running. */
    val progress: Pair<Int, Int>? = null,
    val message: String? = null,
) {
    val factor: Float get() = XgRules.errorFactor(match?.length ?: 0)
    val game: GameState? get() = games.getOrNull(gameIndex)?.second

    val decisions: List<IndexedValue<DecisionRecord>>
        get() {
            // Obvious, error-free "no double" decisions (not counted by XG) are left out of the list.
            val all = game?.decisions?.withIndex()?.filterNot { (_, d) ->
                d.kind == DecisionKind.NO_DOUBLE && !d.counted && (d.error ?: 0f) < 0.0005f
            } ?: return emptyList()
            return when (filter) {
                ReviewFilter.ALL -> all
                ReviewFilter.MINE -> all.filter { it.value.player == Player.HUMAN }
                ReviewFilter.MISTAKES -> all.filter {
                    it.value.player == Player.HUMAN && severityOf(it.value).atLeast(Severity.ERROR)
                }
            }
        }

    val current: DecisionRecord? get() = decisions.getOrNull(position)?.value

    /** A decision tried again is graded by its first try. */
    fun severityOf(d: DecisionRecord): Severity = XgRules.severity((d.scoredError ?: 0f) * factor)

    private fun isMistake(d: DecisionRecord) = d.player == Player.HUMAN && severityOf(d).atLeast(Severity.ERROR)

    /** The human's next error or blunder (game index, list position): later in this game, or in a later game of the match. */
    val nextMistake: Pair<Int, Int>?
        get() {
            val list = decisions
            (position + 1 until list.size).firstOrNull { isMistake(list[it].value) }?.let { return gameIndex to it }
            for (gi in gameIndex + 1 until games.size) {
                val other = copy(gameIndex = gi, position = 0).decisions
                other.indices.firstOrNull { isMistake(other[it].value) }?.let { return gi to it }
            }
            return null
        }

    /**
     * True for a game played after the Crawford game. A .mat file cannot say that the
     * Crawford game is over, so on its own such a game is read as the Crawford game.
     */
    val isPostCrawford: Boolean
        get() {
            val m = match ?: return false
            val (e, g) = games.getOrNull(gameIndex) ?: return false
            return m.length > 0 && m.crawford && !g.crawford &&
                (e.scoreBeforeHuman == m.length - 1 || e.scoreBeforeCpu == m.length - 1)
        }
}

class ReviewViewModel(
    val app: PlyGroundApp,
    private val matchId: Long,
    /** Game to open (its number); null = the last one. */
    private val startGame: Int? = null,
    /** Open on "My mistakes". */
    mistakesOnly: Boolean = false,
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewState(filter = if (mistakesOnly) ReviewFilter.MISTAKES else ReviewFilter.ALL))
    val state: StateFlow<ReviewState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val match = app.repository.match(matchId)
            val games = app.repository.games(matchId)
            val index = games.indexOfFirst { it.first.number == startGame }.takeIf { it >= 0 } ?: (games.size - 1).coerceAtLeast(0)
            _state.update { it.copy(loading = false, match = match, games = games, gameIndex = index) }
        }
        viewModelScope.launch { app.settings.settings.collect { s -> _state.update { it.copy(settings = s) } } }
    }

    fun selectGame(i: Int) = _state.update { it.copy(gameIndex = i, position = 0, candidate = null, collapsed = false) }
    fun setFilter(f: ReviewFilter) = _state.update { it.copy(filter = f, position = 0, candidate = null, collapsed = false) }
    fun go(pos: Int) = _state.update {
        it.copy(position = pos.coerceIn(0, (it.decisions.size - 1).coerceAtLeast(0)), candidate = null, collapsed = false)
    }

    /** A tap on a decision in the list: opens it, or closes its analysis when it is open already. */
    fun tapDecision(pos: Int) {
        if (pos == _state.value.position) _state.update { it.copy(collapsed = !it.collapsed) } else go(pos)
    }
    fun selectCandidate(i: Int?) = _state.update { it.copy(candidate = i) }
    fun setView(v: ReviewView) = _state.update { it.copy(view = v) }

    /**
     * The game being reviewed (or the whole match) in the .mat format read by eXtreme
     * Gammon and GNU Backgammon: file name and content.
     */
    fun exportMat(wholeMatch: Boolean): Pair<String, String>? {
        val s = _state.value
        val match = s.match ?: return null
        if (s.games.isEmpty()) return null
        return MatFiles.matchFile(
            match, s.games, only = if (wholeMatch) null else s.games.getOrNull(s.gameIndex) ?: return null,
            playerName = s.settings.playerName,
        )
    }

    private var reanalysis: Job? = null

    /** Analyzes the game shown (or the whole match) again; the results replace the stored analysis. */
    fun reanalyze(r: ReanalyzeRequest) {
        val s = _state.value
        val match = s.match ?: return
        if (reanalysis?.isActive == true) return
        val targets = if (r.wholeMatch) s.games.indices.toList() else listOf(s.gameIndex)
        val re = Reanalysis(app.engine.of(r.engine), r.checker.code, Depth(r.cubePlies).code)
        val total = targets.sumOf { i -> s.games[i].second.decisions.count { re.wanted(it, r.cpu) } }
        reanalysis = viewModelScope.launch {
            var done = 0
            var games = 0
            _state.update { it.copy(progress = 0 to total, message = null) }
            try {
                for (i in targets) {
                    val (e, g) = _state.value.games[i]
                    val decisions = g.decisions.toMutableList()
                    for ((k, d) in g.decisions.withIndex()) {
                        if (!re.wanted(d, r.cpu)) continue
                        decisions[k] = app.engine.run { re.decision(d, match.length, match.jacoby) }
                        done++
                        _state.update { it.copy(progress = done to total) }
                    }
                    val g2 = g.copy(decisions = decisions)
                    val e2 = app.repository.saveReanalyzedGame(e, g2, match.length)
                    games++
                    _state.update { st -> st.copy(games = st.games.toMutableList().also { it[i] = e2 to g2 }) }
                }
                val depth = "${r.engine.label} ${r.checker.label()}, cube ${r.cubePlies}-ply"
                _state.update { it.copy(progress = null, message = "Analyzed again: $depth.") }
            } catch (c: kotlinx.coroutines.CancellationException) {
                _state.update { it.copy(progress = null, message = "Stopped. $games of ${targets.size} games were analyzed again.") }
                throw c
            } catch (t: Throwable) {
                _state.update { it.copy(progress = null, message = "Analysis failed: ${t.message}") }
            }
        }
    }

    fun stopReanalysis() { reanalysis?.cancel() }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /** Next decision of the human with an error or blunder, in a later game of the match after this game's last. */
    fun nextMistake() {
        val (game, pos) = _state.value.nextMistake ?: return
        _state.update { it.copy(gameIndex = game, position = pos, candidate = null, collapsed = false) }
    }
}

@Composable
fun ReviewScreen(vm: ReviewViewModel, onBack: () -> Unit) {
    val st by vm.state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        when {
            st.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            st.games.isEmpty() -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No finished games in this match yet.")
                TextButton(onClick = onBack) { Text("Back") }
            }
            else -> ReviewContent(st, vm, onBack)
        }
    }
}

private fun title(m: MatchEntity?): String = when {
    m == null -> "Review"
    m.length == 0 -> "Money session"
    else -> "${m.length}-point match"
}

@Composable
private fun ReviewContent(st: ReviewState, vm: ReviewViewModel, onBack: () -> Unit) {
    val capture = rememberBoardCapture()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sideWidth = minOf(390.dp, maxWidth * 0.42f)
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize().padding(6.dp)) {
                Column(Modifier.width(sideWidth).fillMaxHeight()) {
                    Header(st, vm, onBack, capture)
                    DecisionList(st, vm, Modifier.weight(1f))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ReviewBoard(st, Modifier.aspectRatio(1.38f).then(capture.modifier))
                    }
                    BottomBar(st, vm)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                Header(st, vm, onBack, capture)
                ReviewBoard(st, Modifier.fillMaxWidth().aspectRatio(1.38f).then(capture.modifier))
                BottomBar(st, vm)
                DecisionList(st, vm, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Header(st: ReviewState, vm: ReviewViewModel, onBack: () -> Unit, capture: BoardCapture) {
    var ask by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(
            title(st.match), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = { ask = true }, enabled = st.progress == null) {
            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Analyze again", maxLines = 1)
        }
        ShareMenu(st, vm, capture)
    }
    st.progress?.let { (done, total) ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Analyzing… $done / $total decisions", style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(progress = { if (total == 0) 0f else done / total.toFloat() }, modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = vm::stopReanalysis) { Text("Stop") }
        }
    }
    st.message?.let { msg ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(msg, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::dismissMessage) { Text("OK") }
        }
    }
    if (ask) {
        ReanalyzeDialog(
            st.settings, gameNumber = st.games.getOrNull(st.gameIndex)?.first?.number,
            onDismiss = { ask = false },
            onStart = { ask = false; vm.reanalyze(it) },
        )
    }
    // One row: the game picker (a menu, so a long match takes no extra row) and the filters.
    var summary by remember { mutableStateOf(false) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (st.games.size > 1) GamePicker(st, vm)
        FilterChip(
            selected = false, onClick = { summary = true }, label = { Text("Summary") },
            leadingIcon = { Icon(Icons.Default.BarChart, null, Modifier.size(18.dp)) },
        )
        ReviewFilter.entries.forEach { f ->
            FilterChip(selected = st.filter == f, onClick = { vm.setFilter(f) }, label = { Text(f.label) })
        }
    }
    st.match?.let { m ->
        if (summary) SavedSummaryDialog(
            vm.app, m.id, gameNumber = st.games.getOrNull(st.gameIndex)?.first?.number, startWithMatch = false,
            onDismiss = { summary = false },
        )
    }
}

@Composable
private fun GamePicker(st: ReviewState, vm: ReviewViewModel) {
    var open by remember { mutableStateOf(false) }
    fun label(e: GameEntity) = "Game ${e.number} (${e.scoreBeforeHuman}-${e.scoreBeforeCpu})"
    Box {
        FilterChip(
            selected = true, onClick = { open = true },
            label = { Text(st.games.getOrNull(st.gameIndex)?.first?.let(::label) ?: "Game") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, "Choose game") },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            st.games.forEachIndexed { i, (e, _) ->
                DropdownMenuItem(
                    text = { Text(label(e), fontWeight = if (i == st.gameIndex) FontWeight.SemiBold else FontWeight.Normal) },
                    onClick = { open = false; vm.selectGame(i) },
                )
            }
        }
    }
}

/**
 * Sharing: the position shown (XGID, board picture, board with the top five plays), and the game or
 * match as a .mat file, shared or saved, for analysis in XG or gnubg.
 */
@Composable
private fun ShareMenu(st: ReviewState, vm: ReviewViewModel, capture: BoardCapture) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<String?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MatFiles.MIME)) { uri ->
        val content = pending
        pending = null
        if (uri != null && content != null) MatFiles.save(context, uri, content)
    }
    val gameNumber = st.games.getOrNull(st.gameIndex)?.first?.number
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.Share, "Share") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val d = st.current
            val m = st.match
            if (d != null && m != null) {
                SharePositionItems(d, m.length, m.jacoby, PositionShare.title(d, m.length), capture) { open = false }
                HorizontalDivider()
            }
            Text(
                "Export (.mat, for eXtreme Gammon / GNU Backgammon)",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            for (whole in listOf(false, true)) {
                val what = if (whole) "whole match" else "game $gameNumber"
                DropdownMenuItem(
                    text = { Text("Share $what…") },
                    onClick = { open = false; vm.exportMat(whole)?.let { (name, text) -> MatFiles.share(context, name, text) } },
                )
                DropdownMenuItem(
                    text = { Text("Save $what…") },
                    onClick = {
                        open = false
                        vm.exportMat(whole)?.let { (name, text) -> pending = text; saver.launch(name) }
                    },
                )
            }
            if (st.isPostCrawford) {
                Text(
                    "This game was played after the Crawford game. Export the whole match so that XG and gnubg know that.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).width(260.dp),
                )
            }
        }
    }
}

/** Arrows for the current decision: the chosen candidate, or played (severity colour) + best (green). */
private fun arrowsOf(st: ReviewState): List<MoveArrow> = st.current?.let { decisionArrows(it, st.candidate, st.factor) } ?: emptyList()

/** Arrows for [d]: candidate [selected], or (null) the played move in its severity colour plus the best in green. */
internal fun decisionArrows(d: DecisionRecord, selected: Int?, factor: Float): List<MoveArrow> {
    val a = d.checker ?: return d.move?.let { arrowsFor(d.player, it, ArrowColors.neutral) } ?: emptyList()
    if (a.candidates.isEmpty()) return emptyList()
    if (selected != null) {
        val c = a.candidates.getOrNull(selected) ?: return emptyList()
        val sev = XgRules.severity((a.bestEquity - c.equity) * factor)
        return arrowsFor(d.player, c.move, ArrowColors.forSeverity(sev))
    }
    val best = a.candidates.first()
    val played = a.candidates.getOrNull(a.playedIndex)
    val bestArrows = arrowsFor(d.player, best.move, ArrowColors.best)
    if (played == null || a.playedIndex == 0) return bestArrows
    return bestArrows + arrowsFor(d.player, played.move, ArrowColors.forSeverity(XgRules.severity((d.error ?: 0f) * factor)))
}

@Composable
private fun ReviewBoard(st: ReviewState, modifier: Modifier) {
    val d = st.current
    val (board, after) = d?.let { reviewBoard(it, st.view, st.candidate) } ?: ((st.game?.board ?: Board.INITIAL) to false)
    val dice = d?.dice?.takeIf { !after }?.let { DiceView(d.player, it.highFirst) }
    BoardView(
        board = board, modifier = modifier,
        colors = boardColorsFor(st.settings),
        mirrored = st.settings.mirrorBoard,
        dice = listOfNotNull(dice),
        cube = d?.let { decisionCube(it).first } ?: CubeState(),
        cubeOffered = d?.let { decisionCube(it).second } ?: false,
        crawford = d?.crawford ?: st.game?.crawford ?: false,
        crawfordSign = st.settings.crawfordSign,
        pips = listOf(board.pipCount(Player.HUMAN), board.pipCount(Player.CPU)),
        arrows = if (st.view == ReviewView.ARROWS) arrowsOf(st) else emptyList(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BottomBar(st: ReviewState, vm: ReviewViewModel) {
    val d = st.current
    var saving by remember { mutableStateOf(false) }
    val study = remember(d, st.match) { st.match?.let { m -> d?.let { StudyPosition.of(it, m.length, m.jacoby) } } }
    if (saving && study != null) {
        val labels = d?.let { dec -> com.boldblot.backgammonplyground.core.Labels.of(dec, st.match?.length ?: 0, opponent = dec.player != Player.HUMAN).mapTo(HashSet()) { it.name } }
        SaveToPositionsDialog(vm.app, study, "Game review", labels, d?.let { com.boldblot.backgammonplyground.core.MyAnswer.played(it) }) { saving = false }
    }
    val a = d?.checker
    if (d != null && a != null && a.candidates.isNotEmpty()) {
        val played = a.candidates.getOrNull(a.playedIndex)
        val best = a.candidates.first()
        PlayedBestBar(
            playedText = played?.let { MoveNotation.format(d.board, d.player, it.move) } ?: "?",
            playedError = d.error?.times(st.factor), playedSeverity = st.severityOf(d),
            bestText = MoveNotation.format(d.board, d.player, best.move), bestEquity = best.equity,
            modifier = Modifier.padding(top = 4.dp),
            onPlayed = { vm.selectCandidate(a.playedIndex) },
            onBest = { vm.selectCandidate(0) },
        )
    }
    val n = st.decisions.size
    FlowRow(
        Modifier.fillMaxWidth(), itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.go(st.position - 1) }, enabled = st.position > 0) { Icon(Icons.AutoMirrored.Filled.NavigateBefore, "Previous") }
            Text(if (n == 0) "0 / 0" else "${st.position + 1} / $n", style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = { vm.go(st.position + 1) }, enabled = st.position < n - 1) { Icon(Icons.AutoMirrored.Filled.NavigateNext, "Next") }
            TextButton(onClick = vm::nextMistake, enabled = st.nextMistake != null) { Text("Next mistake") }
        }
        if (d != null) ReviewViewChips(st.view, d.move != null, vm::setView)
        if (study != null) SaveButton { saving = true }
    }
}

/**
 * All decisions of the game in one scrolling list (Galaxy style). The selected decision opens in place
 * with its analysis, so list and analysis share the whole height (no tabs, no second small window).
 */
@Composable
private fun DecisionList(st: ReviewState, vm: ReviewViewModel, modifier: Modifier) {
    val colors = boardColorsFor(st.settings)
    val listState = rememberLazyListState()
    // keep one decision of context above the selected one, its analysis right below
    LaunchedEffect(st.position, st.gameIndex, st.filter) {
        if (st.decisions.isNotEmpty()) listState.animateScrollToItem((st.position - 1).coerceAtLeast(0))
    }
    LazyColumn(
        modifier.fillMaxWidth().padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)),
        state = listState,
    ) {
        if (st.decisions.isEmpty()) {
            item {
                Text(
                    if (st.filter == ReviewFilter.MISTAKES) "No errors or blunders in this game. Well played!" else "No decisions.",
                    Modifier.padding(10.dp),
                )
            }
        }
        itemsIndexed(st.decisions) { pos, (_, d) ->
            val sev = st.severityOf(d)
            val selected = pos == st.position
            Column(Modifier.background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { vm.tapDecision(pos) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).background(if (d.player == Player.HUMAN) colors.human else colors.cpu, RoundedCornerShape(5.dp)))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        d.dice?.toString() ?: "",
                        Modifier.width(26.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        describeAction(d.kind, d.board, d.player, d.move),
                        Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    val scored = d.scoredError
                    if (scored != null && sev != Severity.NONE && !selected) {
                        Box(Modifier.size(9.dp).background(severityColor(sev), RoundedCornerShape(5.dp)))
                    }
                    if (scored != null && selected) SeverityBadge(sev, scored * st.factor)
                }
                if (selected && !st.collapsed) DecisionAnalysis(st, vm, d, Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp))
            }
        }
        item { GameSummary(st, Modifier.padding(6.dp)) }
    }
}

/** The selected decision's analysis, shown under its row in [DecisionList]. */
@Composable
private fun DecisionAnalysis(st: ReviewState, vm: ReviewViewModel, d: DecisionRecord, modifier: Modifier) {
    val colors = boardColorsFor(st.settings)
    Column(modifier) {
        val notes = buildList {
            if (d.hinted) add("hint used, not counted")
            if (d.auto) add("played by autofinish, not counted")
            if (d.error != null && !d.counted && !d.hinted) add("obvious decision, not counted in PR")
            if (d.kind == DecisionKind.CHECKER && d.checker?.legalMoves == 1) add("forced move")
            d.firstTry?.let { t ->
                val first = describeAction(t.kind, d.board, d.player, t.move)
                val sev = st.severityOf(d).label.lowercase()
                val tries = if (t.retries == 1) "tried again" else "tried ${t.retries} more times"
                add("first try: $first ($sev, counted in PR), $tries; the analysis below is for the last try")
            }
        }
        if (notes.isNotEmpty()) {
            Text(notes.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        when {
            d.checker != null && d.checker.candidates.isNotEmpty() -> CandidateList(
                board = d.board, player = d.player, analysis = d.checker, factor = st.factor,
                highlightIndex = d.checker.playedIndex, maxRows = 8, selectedIndex = st.candidate, detailed = true,
                moverColor = if (d.player == Player.HUMAN) colors.human else colors.cpu,
                opponentColor = if (d.player == Player.HUMAN) colors.cpu else colors.human,
                onSelect = { i, _ -> vm.selectCandidate(if (st.candidate == i) null else i) },
            )
            d.cubeEval != null -> CubeTable(d.cubeEval, d.kind, st.factor, d.beavers)
            d.kind == DecisionKind.CHECKER -> Text("No legal move.")
            else -> Text("Not analyzed.")
        }
    }
}

@Composable
private fun GameSummary(st: ReviewState, modifier: Modifier = Modifier) {
    val g = st.game ?: return
    if (g.result == null) return
    val s = Summaries.of(g, st.match?.length ?: 0)
    Column(
        modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(10.dp),
    ) {
        Text("Game summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        val r = g.result
        val how = when {
            r.byPass -> "double passed"
            r.resigned -> "${r.type.name.lowercase().replace("single", "single game")} resigned"
            else -> r.type.name.lowercase().replace("single", "single game")
        }
        Labelled("Result", "${if (r.winner == Player.HUMAN) "You" else "Computer"} +${r.points} ($how)")
        PerfRows("You", s.performance[0])
        PerfRows("Computer", s.performance[1])
    }
}

@Composable
private fun PerfRows(who: String, p: PerformanceTotals) {
    Labelled(
        "$who PR (checker / cube)",
        "${p.pr?.let { fmt(it) } ?: "–"}  (${p.checkerPr?.let { fmt(it) } ?: "–"} / ${p.cubePr?.let { fmt(it) } ?: "–"})",
    )
    Labelled(
        "$who errors / blunders",
        "${p.checkerErrors + p.doubleErrors + p.takeErrors} / ${p.checkerBlunders + p.doubleBlunders + p.takeBlunders}",
    )
}
