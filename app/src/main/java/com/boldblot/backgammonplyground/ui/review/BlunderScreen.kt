package com.boldblot.backgammonplyground.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.StudyPosition
import com.boldblot.backgammonplyground.ui.positions.SaveToPositionsDialog
import com.boldblot.backgammonplyground.core.CpuLevel
import com.boldblot.backgammonplyground.core.Opponent
import com.boldblot.backgammonplyground.core.DecisionKind
import com.boldblot.backgammonplyground.core.DecisionRecord
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.Reanalysis
import com.boldblot.backgammonplyground.core.MoveNotation
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.Severity
import com.boldblot.backgammonplyground.core.XgRules
import com.boldblot.backgammonplyground.data.BlunderEntity
import com.boldblot.backgammonplyground.data.SavedBlunder
import com.boldblot.backgammonplyground.data.Settings
import com.boldblot.backgammonplyground.ui.board.BoardView
import com.boldblot.backgammonplyground.ui.board.DiceView
import com.boldblot.backgammonplyground.ui.board.boardColorsFor
import com.boldblot.backgammonplyground.ui.components.CandidateList
import com.boldblot.backgammonplyground.ui.components.CubeTable
import com.boldblot.backgammonplyground.ui.components.PlayedBestBar
import com.boldblot.backgammonplyground.ui.components.SeverityBadge
import com.boldblot.backgammonplyground.ui.components.decisionCube
import com.boldblot.backgammonplyground.ui.components.describeAction
import com.boldblot.backgammonplyground.ui.components.eq
import com.boldblot.backgammonplyground.ui.components.severityColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** "5-pt match vs World Class · 2-1 · 11 Oct 2026" for a saved blunder. */
private fun context(b: SavedBlunder): String {
    val e = b.entity
    val d = b.decision
    val level = Opponent.name(e.cpuLevel)
    val what = (if (e.opponent) "Opponent's · " else "") + if (e.matchLength == 0) "Money" else "${e.matchLength}-pt match"
    val score = if (e.matchLength == 0) "" else " · score ${d.score[0]}-${d.score[1]}" + if (d.crawford) " Crawford" else ""
    val cube = if (d.cube.value > 1) " · cube ${d.cube.value}" else ""
    return "$what vs $level$score$cube · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(e.playedAt))}"
}

/**
 * New errors as a list; a tap opens one. With [selection] (ids, null = not selecting) each row has a
 * check box and a tap selects; a long press starts selecting ([onLongClick]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BlunderList(
    blunders: List<SavedBlunder>, onOpen: (Long) -> Unit, modifier: Modifier = Modifier, filtered: Boolean = false,
    selection: Set<Long>? = null, onLongClick: ((Long) -> Unit)? = null,
) {
    if (blunders.isEmpty()) {
        Text(
            if (filtered) "No errors pass the filter." else "No new errors. The mistakes you make (Settings → Analysis says which) " +
                "wait here until you add them to My positions or delete them.",
            modifier.padding(24.dp),
        )
        return
    }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        items(blunders, key = { it.entity.id }) { b ->
            val d = b.decision
            val id = b.entity.id
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .combinedClickable(onLongClick = onLongClick?.let { { it(id) } }) { onOpen(id) },
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selection != null) Checkbox(checked = id in selection, onCheckedChange = { onOpen(id) })
                    val sev = XgRules.severity(b.entity.error)
                    Box(Modifier.size(10.dp).background(severityColor(sev), RoundedCornerShape(5.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            (d.dice?.let { "$it  " } ?: "") + describeAction(d.kind, d.board, d.player, d.move),
                            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            context(b), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                        )
                    }
                    Text(eq(-b.entity.error), fontFamily = FontFamily.Monospace, color = severityColor(sev))
                }
            }
        }
    }
}

data class BlunderState(
    val loading: Boolean = true,
    val blunders: List<SavedBlunder> = emptyList(),
    val index: Int = 0,
    /** Candidate whose arrow is shown; null = played and best together. */
    val candidate: Int? = null,
    /** Arrows, the bare position, or the position after the move (chosen in the list, or played). */
    val view: ReviewView = ReviewView.ARROWS,
    val settings: Settings = Settings(),
    /** Analyzing the shown blunder again. */
    val analyzing: Boolean = false,
    val message: String? = null,
) {
    val current: SavedBlunder? get() = blunders.getOrNull(index)
    /** The shown decision's size as last analyzed (analyzing again can change it). */
    val severity: Severity get() = XgRules.severity(current?.entity?.error ?: 0f)
    val factor: Float get() = XgRules.errorFactor(current?.entity?.matchLength ?: 0)
}

class BlunderViewModel(val app: PlyGroundApp, private val startId: Long) : ViewModel() {

    private val _state = MutableStateFlow(BlunderState())
    val state: StateFlow<BlunderState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = app.settings.settings.first()
            val list = app.repository.blunders(s.blunderSort, s.visible(s.blunderFilter))
            val i = list.indexOfFirst { it.entity.id == startId }.coerceAtLeast(0)
            _state.update { it.copy(loading = false, blunders = list, index = i) }
        }
        viewModelScope.launch { app.settings.settings.collect { s -> _state.update { it.copy(settings = s) } } }
    }

    fun go(i: Int) = _state.update {
        it.copy(index = i.coerceIn(0, (it.blunders.size - 1).coerceAtLeast(0)), candidate = null)
    }

    fun selectCandidate(i: Int?) = _state.update { it.copy(candidate = i) }
    fun setView(v: ReviewView) = _state.update { it.copy(view = v) }

    /** Analyzes the shown blunder again; the new analysis replaces the saved one. */
    fun reanalyze(r: ReanalyzeRequest) {
        val b = _state.value.current ?: return
        if (_state.value.analyzing) return
        _state.update { it.copy(analyzing = true, message = null) }
        viewModelScope.launch {
            val result = runCatching {
                val jacoby = app.repository.match(b.entity.matchId)?.jacoby ?: true
                val re = Reanalysis(app.engine.of(r.engine), r.checker.code, Depth(r.cubePlies).code)
                val d = app.engine.run { re.decision(b.decision, b.entity.matchLength, jacoby) }
                SavedBlunder(app.repository.saveReanalyzedBlunder(b.entity, d), d)
            }
            _state.update { st ->
                result.fold(
                    onSuccess = { nb ->
                        st.copy(
                            analyzing = false, candidate = null,
                            blunders = st.blunders.map { if (it.entity.id == nb.entity.id) nb else it },
                            message = "Analyzed again: ${r.engine.label} ${r.checker.label()}, cube ${r.cubePlies}-ply.",
                        )
                    },
                    onFailure = { e -> st.copy(analyzing = false, message = "Analysis failed: ${e.message}") },
                )
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /** Deletes the shown error from New errors; returns it for undo. */
    fun removeCurrent(): BlunderEntity? {
        val b = _state.value.current ?: return null
        _state.update {
            val list = it.blunders - b
            it.copy(blunders = list, index = it.index.coerceAtMost((list.size - 1).coerceAtLeast(0)), candidate = null)
        }
        viewModelScope.launch { app.repository.removeBlunder(b.entity.id) }
        return b.entity
    }

    fun undoRemove(e: BlunderEntity) {
        viewModelScope.launch {
            app.repository.restoreBlunder(e)
            val s = _state.value.settings
            val list = app.repository.blunders(s.blunderSort, s.visible(s.blunderFilter))
            _state.update { s -> s.copy(blunders = list, index = list.indexOfFirst { it.entity.id == e.id }.coerceAtLeast(0)) }
        }
    }
}

@Composable
fun BlunderScreen(vm: BlunderViewModel, onBack: () -> Unit) {
    val st by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var removed by remember { mutableStateOf<BlunderEntity?>(null) }
    LaunchedEffect(removed) {
        val e = removed ?: return@LaunchedEffect
        val r = snackbar.showSnackbar("Deleted from New errors", actionLabel = "Undo", withDismissAction = true)
        if (r == SnackbarResult.ActionPerformed) vm.undoRemove(e)
        removed = null
    }
    val onRemove = { removed = vm.removeCurrent() }
    Box(Modifier.fillMaxSize()) {
        val b = st.current
        when {
            st.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            b == null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No new errors left.")
                TextButton(onClick = onBack) { Text("Back") }
            }
            else -> BlunderContent(st, b, vm, onBack, onRemove)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun BlunderContent(st: BlunderState, b: SavedBlunder, vm: BlunderViewModel, onBack: () -> Unit, onRemove: () -> Unit) {
    var saving by remember { mutableStateOf(false) }
    val jacoby by produceState(true, b.entity.matchId) { value = vm.app.repository.match(b.entity.matchId)?.jacoby ?: true }
    val study = remember(b, jacoby) { StudyPosition.of(b.decision, b.entity.matchLength, jacoby) }
    if (saving && study != null) SaveToPositionsDialog(vm.app, study, "Blunder list", b.labels, com.boldblot.backgammonplyground.core.MyAnswer.played(b.decision)) { saving = false }
    val onSave = { saving = true }.takeIf { study != null }
    val capture = rememberBoardCapture()
    val share: @Composable () -> Unit = {
        var open by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { open = true }) { Icon(Icons.Default.Share, "Share") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                SharePositionItems(b.decision, b.entity.matchLength, jacoby, PositionShare.title(b.decision, b.entity.matchLength), capture) { open = false }
            }
        }
    }
    // Keep: on to the next error (back to the list after the last one).
    val onKeep = { if (st.index < st.blunders.size - 1) vm.go(st.index + 1) else onBack() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            val sideWidth = minOf(390.dp, maxWidth * 0.42f)
            Row(Modifier.fillMaxSize().padding(6.dp)) {
                Column(Modifier.width(sideWidth).fillMaxHeight()) {
                    TopRow(onBack, onRemove, share)
                    ReviewViewChips(st.view, b.decision.move != null, vm::setView, onSave, Modifier.fillMaxWidth().padding(bottom = 4.dp))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { Detail(st, b, vm) }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Board(st, b.decision, Modifier.aspectRatio(1.38f).then(capture.modifier))
                    }
                    Bar(st, b.decision, vm, onKeep, onSave, landscape = true)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                TopRow(onBack, onRemove, share)
                Board(st, b.decision, Modifier.fillMaxWidth().aspectRatio(1.38f).then(capture.modifier))
                Bar(st, b.decision, vm, onKeep, onSave, landscape = false)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { Detail(st, b, vm) }
            }
        }
    }
}

@Composable
private fun TopRow(onBack: () -> Unit, onRemove: () -> Unit, share: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text("New errors", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        share()
        OutlinedButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Delete")
        }
    }
}

@Composable
private fun Board(st: BlunderState, d: DecisionRecord, modifier: Modifier) {
    val (board, after) = reviewBoard(d, st.view, st.candidate)
    val dice = d.dice?.takeIf { !after }?.let { DiceView(d.player, it.highFirst) }
    BoardView(
        board = board, modifier = modifier,
        colors = boardColorsFor(st.settings),
        mirrored = st.settings.mirrorBoard,
        dice = listOfNotNull(dice),
        cube = decisionCube(d).first,
        cubeOffered = decisionCube(d).second,
        crawford = d.crawford,
        crawfordSign = st.settings.crawfordSign,
        pips = listOf(board.pipCount(Player.HUMAN), board.pipCount(Player.CPU)),
        arrows = if (st.view == ReviewView.ARROWS) decisionArrows(d, st.candidate, st.factor) else emptyList(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bar(st: BlunderState, d: DecisionRecord, vm: BlunderViewModel, onKeep: () -> Unit, onSave: (() -> Unit)?, landscape: Boolean) {
    val a = d.checker
    if (a != null && a.candidates.isNotEmpty()) {
        val played = a.candidates.getOrNull(a.playedIndex)
        val best = a.candidates.first()
        PlayedBestBar(
            playedText = played?.let { MoveNotation.format(d.board, d.player, it.move) } ?: "?",
            playedError = d.error?.times(st.factor), playedSeverity = st.severity,
            bestText = MoveNotation.format(d.board, d.player, best.move), bestEquity = best.equity,
            modifier = Modifier.padding(top = 4.dp),
            onPlayed = { vm.selectCandidate(a.playedIndex) },
            onBest = { vm.selectCandidate(0) },
        )
    } else if (landscape) {
        KeepRoom(false) { EmptyPlayedBestBar() }
    }
    FlowRow(
        Modifier.fillMaxWidth(), itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.go(st.index - 1) }, enabled = st.index > 0) { Icon(Icons.AutoMirrored.Filled.NavigateBefore, "Previous") }
            Text("${st.index + 1} / ${st.blunders.size}", style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = { vm.go(st.index + 1) }, enabled = st.index < st.blunders.size - 1) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, "Next")
            }
            Button(onClick = onKeep) { Text(if (st.index < st.blunders.size - 1) "Keep, next" else "Keep") }
        }
        if (!landscape) ReviewViewChips(st.view, d.move != null, vm::setView, onSave)
    }
}

@Composable
private fun Detail(st: BlunderState, b: SavedBlunder, vm: BlunderViewModel) {
    val d = b.decision
    val colors = boardColorsFor(st.settings)
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "You: ${describeAction(d.kind, d.board, d.player, d.move)}" + (d.dice?.let { "  ($it)" } ?: ""),
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
            )
            SeverityBadge(st.severity, b.entity.error)
        }
        Text(context(b), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        var ask by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { ask = true }, enabled = !st.analyzing) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Analyze again")
            }
            if (st.analyzing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            st.message?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f).clickable(onClick = vm::dismissMessage))
            }
        }
        if (ask) ReanalyzeDialog(st.settings, gameNumber = null, onDismiss = { ask = false }, onStart = { ask = false; vm.reanalyze(it) })
        Spacer(Modifier.height(4.dp))
        when {
            d.checker != null && d.checker.candidates.isNotEmpty() -> CandidateList(
                board = d.board, player = d.player, analysis = d.checker, factor = st.factor,
                highlightIndex = d.checker.playedIndex, maxRows = 8, selectedIndex = st.candidate, detailed = true,
                moverColor = colors.human, opponentColor = colors.cpu,
                onSelect = { i, _ -> vm.selectCandidate(if (st.candidate == i) null else i) },
            )
            d.cubeEval != null -> CubeTable(d.cubeEval, d.kind, st.factor, d.beavers)
        }
    }
}
