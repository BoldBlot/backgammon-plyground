package com.boldblot.backgammonplyground.ui.deepdive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boldblot.backgammonplyground.core.CubeEval
import com.boldblot.backgammonplyground.core.CubeState
import com.boldblot.backgammonplyground.core.DecisionKind
import com.boldblot.backgammonplyground.core.DeepDive
import com.boldblot.backgammonplyground.core.DeepDive.Tracked
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.data.ErrorUnit
import com.boldblot.backgammonplyground.data.Settings
import com.boldblot.backgammonplyground.ui.board.BoardColors
import com.boldblot.backgammonplyground.ui.board.BoardView
import com.boldblot.backgammonplyground.ui.board.DiceView
import com.boldblot.backgammonplyground.ui.board.PointLabel
import com.boldblot.backgammonplyground.ui.board.arrowsFor
import com.boldblot.backgammonplyground.ui.board.boardColorsFor
import com.boldblot.backgammonplyground.ui.components.LocalErrorUnit
import com.boldblot.backgammonplyground.ui.components.eq
import com.boldblot.backgammonplyground.ui.components.err
import kotlin.math.ceil
import kotlin.math.roundToInt

private val BestFill = Color(0xFF1F7A4A)
private val SmallFill = Color(0xFFD9A925)
private val BigFill = Color(0xFFC0392B)
private val GreyFill = Color(0xFF5F5F5F)
private val ErrorRed = Color(0xFFD32F2F)

private fun fillOf(l: Loss?): Color = when {
    l !is Loss.Value -> GreyFill
    l.best -> BestFill
    l.v < com.boldblot.backgammonplyground.core.XgRules.ERROR_THRESHOLD -> SmallFill
    else -> BigFill
}

private fun inkOf(l: Loss?): Color = if (fillOf(l) == SmallFill) Color.Black else Color.White

/** The loss as the pills show it: "best", "-0.041" (or "-41.0"), "[!]" where the decision cannot be made. */
private fun lossText(l: Loss?, unit: ErrorUnit): String = when (l) {
    null, Loss.Pending -> "…"
    Loss.Illegal -> "[!]"
    is Loss.Value -> if (l.best) "best" else err(-l.v, unit)
}

/** Shorter, for the board: "0", ".041" (or "41"). */
private fun boardText(l: Loss?, unit: ErrorUnit): String = when (l) {
    null, Loss.Pending -> "…"
    Loss.Illegal -> "[!]"
    is Loss.Value -> when {
        l.best -> "0"
        unit == ErrorUnit.MEQ -> (l.v * 1000).roundToInt().toString()
        else -> String.format(java.util.Locale.US, "%.3f", l.v).removePrefix("0")
    }
}

@Composable
fun DeepDiveScreen(vm: DeepDiveViewModel, onBack: () -> Unit) {
    val st by vm.state.collectAsState()
    val settings by vm.app.settings.settings.collectAsState(initial = Settings())
    val colors = boardColorsFor(settings)
    val snackbar = remember { SnackbarHostState() }
    var saving by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var leaveAfterSave by remember { mutableStateOf(false) }
    LaunchedEffect(st.message) {
        val m = st.message ?: return@LaunchedEffect
        snackbar.showSnackbar(m, withDismissAction = true)
        vm.dismissMessage()
    }
    val back = { if (st.hasResults && !st.saved) leaving = true else { vm.stop(); onBack() } }
    BackHandler(onBack = back)
    if (saving) SaveDialog(st, onDismiss = { saving = false; leaveAfterSave = false }) { name, which ->
        vm.save(name, which) { saving = false; if (leaveAfterSave) onBack() }
    }
    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text("Keep the results?") },
            text = { Text("A deep dive is not kept unless you save it to a set in the Match Library.") },
            confirmButton = { TextButton(onClick = { leaving = false; leaveAfterSave = true; saving = true }) { Text("Save to a set…") } },
            dismissButton = { TextButton(onClick = { leaving = false; vm.stop(); onBack() }) { Text("Discard") } },
        )
    }
    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val top: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Text("Deep dive", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (st.hasResults && !st.running) TextButton(onClick = { saving = true }) { Text("Save to set") }
                }
            }
            if (maxWidth > maxHeight) {
                // The board a little smaller than elsewhere: the legend fits under it and the chart gets the room.
                val boardH = maxHeight * 0.74f
                val boardW = minOf(boardH * 1.38f, maxWidth * 0.52f)
                Row(Modifier.fillMaxSize().padding(6.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        top()
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { Panel(st, vm) }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.width(boardW).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                        DiveBoard(st, vm, colors, settings.mirrorBoard, Modifier.fillMaxWidth().aspectRatio(1.38f))
                        Legend(st, vm, Modifier.padding(top = 6.dp))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                    top()
                    DiveBoard(st, vm, colors, settings.mirrorBoard, Modifier.fillMaxWidth().aspectRatio(1.38f))
                    Legend(st, vm, Modifier.padding(vertical = 4.dp))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { Panel(st, vm) }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** The colour slots: the decisions followed. A tap opens the plays (or cube actions) to pick from. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(st: DeepDiveState, vm: DeepDiveViewModel, modifier: Modifier = Modifier) {
    val unit = LocalErrorUnit.current
    val slots = (0 until DeepDive.MAX_TRACKED).filter { k -> k == 0 || st.tracked[k] != null || st.tracked[k - 1] != null }
        .take(maxOf(1, st.options.size))
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (k in slots) {
            val t = st.tracked[k]
            var open by remember { mutableStateOf(false) }
            Box {
                Row(
                    Modifier.border(2.dp, TrackColors[k], RoundedCornerShape(10.dp)).clickable { open = true }.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(12.dp).background(TrackColors[k], CircleShape))
                    Text(
                        t?.let { st.text(it) } ?: "+ Add",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = if (t is Tracked.Play) FontFamily.Monospace else FontFamily.Default),
                        maxLines = 1,
                    )
                    if (t != null && st.hasResults) {
                        val l = st.losses.getOrNull(st.selected)?.getOrNull(k)
                        Text(lossText(l, unit), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DropdownMenu(open, onDismissRequest = { open = false }) {
                    for (o in st.options) DropdownMenuItem(
                        text = { Text(st.text(o), fontFamily = if (o is Tracked.Play) FontFamily.Monospace else FontFamily.Default) },
                        onClick = { open = false; vm.setTracked(k, o) },
                    )
                    if (k > 0 && t != null) DropdownMenuItem(text = { Text("Remove") }, onClick = { open = false; vm.setTracked(k, null) })
                }
            }
        }
    }
}

@Composable
private fun DiveBoard(st: DeepDiveState, vm: DeepDiveViewModel, colors: BoardColors, mirrored: Boolean, modifier: Modifier) {
    val unit = LocalErrorUnit.current
    val v = st.versions.getOrNull(st.selected)
    val p = v?.position ?: st.base
    val s = p.setup
    val crawford = s.matchLength > 0 && s.crawford
    val setup = !st.hasResults
    val editDim = st.dims.getOrNull(st.editDim)
    val slot = if (setup && editDim?.checkers == true) editDim.slots.getOrNull(st.editing) else null
    fun human(player: Int, i: Int) = if (player == Player.HUMAN) i else 23 - i
    // Spots of the versions: the checker changes' slots in order; the primary change's start at [offset].
    val checkerDims = st.dims.withIndex().filter { it.value.checkers }
    val offset = checkerDims.takeWhile { it.index != st.primary }.sumOf { it.value.slots.size }
    val primaryDim = st.dims.getOrNull(st.primary)
    val labels: Map<Int, PointLabel> = when {
        setup || checkerDims.isEmpty() -> emptyMap()
        primaryDim?.checkers == true && primaryDim.slots.size == 1 -> st.row.mapNotNull { i ->
            if (i == null) return@mapNotNull null
            val spot = st.versions[i].spots.getOrNull(offset) ?: return@mapNotNull null
            val l = st.losses.getOrNull(i)?.getOrNull(0)
            spot to PointLabel(boardText(l, unit), fillOf(l), inkOf(l), ring = i == st.selected)
        }.toMap()
        // Otherwise each slot's letter where its checkers stand (letters sharing a point together).
        else -> v?.spots?.withIndex()?.groupBy({ it.value }, { "ABCDEFGH"[it.index] })
            ?.mapValues { (_, letters) -> PointLabel(letters.joinToString(""), Color(0xFFF1EFE9), Color.Black, ring = true) }.orEmpty()
    }
    // The followed plays as arrows in their colours, where they can be played.
    val arrows = st.tracked.withIndex().flatMap { (k, t) ->
        if (t is Tracked.Play && DeepDive.isLegal(p, t)) arrowsFor(Player.HUMAN, t.move, TrackColors[k]) else emptyList()
    }
    BoardView(
        board = p.board, modifier = modifier, colors = colors, mirrored = mirrored,
        dice = p.dice?.let { listOf(DiceView(Player.HUMAN, it.highFirst)) } ?: emptyList(),
        cube = if (crawford) CubeState() else s.cube, cubeOffered = p.doubled, crawford = crawford,
        pips = listOf(p.board.pipCount(Player.HUMAN), p.board.pipCount(Player.CPU)),
        arrows = if (slot != null) emptyList() else arrows,
        movable = slot?.let { setOf(human(it.player, it.from)) } ?: emptySet(),
        selected = slot?.let { human(it.player, it.from) },
        destinations = slot?.let { sl -> sl.targets.filter { it != sl.from }.map { human(sl.player, it) }.toSet() } ?: emptySet(),
        dragPlayer = slot?.player ?: Player.HUMAN,
        onTap = if (setup && editDim?.checkers == true) vm::tapPoint else null,
        pointLabels = labels,
    )
}

@Composable
private fun Panel(st: DeepDiveState, vm: DeepDiveViewModel) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnalysisLine(st, vm)
        if (!st.hasResults) Setup(st, vm) else Results(st, vm)
    }
}

/** The analysis every version gets, and Change (after a run: every version analyzed again, asked first). */
@Composable
private fun AnalysisLine(st: DeepDiveState, vm: DeepDiveViewModel) {
    val engine = st.engine ?: return
    val checker = st.checkerDepth ?: return
    val cube = st.cubeDepth ?: return
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Analysis: ${engine.label} · checker ${checker.label()} · cube ${cube.label(cube = true)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { open = true }) { Text("Change") }
    }
    if (open) {
        var e by remember { mutableStateOf(engine) }
        var c by remember { mutableStateOf(checker) }
        var k by remember { mutableStateOf(cube) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Analysis") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.boldblot.backgammonplyground.ui.components.AnalysisLevelPicker(
                        engine = e, onEngine = { e = it }, checker = c, cube = k, onChange = { a, b -> c = a; k = b },
                        match = st.base.setup.matchLength > 0,
                    )
                    if (st.hasResults) Text(
                        "The ${st.versions.size} versions are analyzed again.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { open = false; vm.setAnalysis(e, c, k) }) { Text(if (st.hasResults) "Analyze again" else "OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Setup(st: DeepDiveState, vm: DeepDiveViewModel) {
    Text(
        "The decisions followed are under the board: tap a colour to pick another, + Add to follow more.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    st.dims.forEachIndexed { d, dim ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (d == 0) "What to change" else "And", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (d == 1) IconButton(onClick = vm::removeDim) { Icon(Icons.Default.Close, "Remove the second change") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (m in DiveMode.entries) FilterChip(selected = dim.mode == m, onClick = { vm.setMode(d, m) }, label = { Text(m.label) })
        }
        when (dim.mode) {
            DiveMode.CHECKER, DiveMode.CHECKERS -> SlotsEditor(st, vm, d)
            DiveMode.SCORE -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = dim.scorePlayer == Player.CPU, onClick = { vm.setScorePlayer(d, Player.CPU) }, label = { Text("Opponent's score") })
                    FilterChip(selected = dim.scorePlayer == Player.HUMAN, onClick = { vm.setScorePlayer(d, Player.HUMAN) }, label = { Text("Your score") })
                }
                ScoreRange(st, vm, d)
            }
            DiveMode.CUBE -> Text(
                "The cube centred, then owned at 2, 4 and 8 by " + when {
                    st.base.doubled -> "the doubler."
                    st.base.dice == null -> "you."
                    else -> "you and by your opponent."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (st.dims.size == 1) TextButton(onClick = vm::addDim) {
        Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add a second change")
    }
    val problem = st.problem
    val tapHint = st.dims.any { it.checkers && it.slots.isEmpty() }
    if (problem != null && !tapHint) Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    val n = st.planned
    if (n > DeepDive.MAX_VERSIONS) Text("Only the first ${DeepDive.MAX_VERSIONS} are analyzed.", style = MaterialTheme.typography.bodySmall)
    Button(onClick = vm::start, enabled = problem == null && n >= 2, modifier = Modifier.fillMaxWidth()) {
        Text(if (problem == null && n >= 2) "Analyze ${minOf(n, DeepDive.MAX_VERSIONS)} versions" else "Analyze")
    }
}

/** The score's range: from n-away down to m-away (1-away: Crawford and post-Crawford), and a money game. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoreRange(st: DeepDiveState, vm: DeepDiveViewModel, d: Int) {
    val dim = st.dims[d]
    val n = st.base.setup.matchLength
    if (n == 0) return
    val from = if (dim.from <= 0) n else dim.from.coerceIn(1, n)
    val to = dim.to.coerceIn(1, from)
    val twoScores = st.dims.count { it.mode == DiveMode.SCORE } == 2
    FlowRow(verticalArrangement = Arrangement.Center, itemVerticalAlignment = Alignment.CenterVertically) {
        Text("From", style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = { vm.setScoreRange(d, from - 1, to, dim.money) }, enabled = from > to) { Icon(Icons.Default.Remove, "Fewer") }
        Text("$from-away", style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = { vm.setScoreRange(d, from + 1, to, dim.money) }, enabled = from < n) { Icon(Icons.Default.Add, "More") }
        Spacer(Modifier.width(8.dp))
        Text("to", style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = { vm.setScoreRange(d, from, to - 1, dim.money) }, enabled = to > 1) { Icon(Icons.Default.Remove, "Fewer") }
        Text("$to-away", style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = { vm.setScoreRange(d, from, to + 1, dim.money) }, enabled = to < from) { Icon(Icons.Default.Add, "More") }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = dim.pre, onClick = { vm.setScoreKinds(d, !dim.pre, dim.crawford, dim.post) }, label = { Text("Pre-Crawford") })
        FilterChip(selected = dim.crawford, enabled = to == 1, onClick = { vm.setScoreKinds(d, dim.pre, !dim.crawford, dim.post) }, label = { Text("Crawford game") })
        FilterChip(selected = dim.post, enabled = to == 1, onClick = { vm.setScoreKinds(d, dim.pre, dim.crawford, !dim.post) }, label = { Text("Post-Crawford") })
    }
    if (!twoScores) Row(verticalAlignment = Alignment.CenterVertically) {
        Text("And a money game", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = dim.money, onCheckedChange = { vm.setScoreRange(d, from, to, it) })
    }
    Text(
        "Pre-Crawford: 2-away and more. The Crawford game and post-Crawford: 1-away (the range must reach it). " +
            "The other score stays as it is" + (if (twoScores) ", except where both change; both 1-away is always post-Crawford." else "."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotsEditor(st: DeepDiveState, vm: DeepDiveViewModel, d: Int) {
    val dim = st.dims[d]
    val several = dim.mode == DiveMode.CHECKERS
    val editingHere = st.editDim == d
    dim.slots.forEachIndexed { i, s ->
        val who = if (s.player == Player.HUMAN) "Your" else "Opponent's"
        val what = if (s.count == 1) "$who checker" else "$who ${s.count} checkers"
        val points = s.targets.sortedDescending().joinToString(", ") { DeepDive.shown(s.player, it).toString() }
        Row(
            Modifier.fillMaxWidth()
                .background(if (editingHere && i == st.editing) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(10.dp))
                .clickable { vm.editSlotAt(d, i) }.padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                (if (several) "${"ABCDEF"[i]}  " else "") + "$what on ${DeepDive.shown(s.player, s.from)} → $points",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
            // How many checkers move together (a stack).
            IconButton(onClick = { vm.setCount(d, i, s.count - 1) }, enabled = s.count > 1) { Icon(Icons.Default.Remove, "Fewer checkers") }
            Text("${s.count}", style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = { vm.setCount(d, i, s.count + 1) }, enabled = s.count < st.board.count(s.player, s.from)) { Icon(Icons.Default.Add, "More checkers") }
            IconButton(onClick = { vm.removeSlot(d, i) }) { Icon(Icons.Default.Close, "Remove") }
        }
    }
    val current = if (editingHere) dim.slots.getOrNull(st.editing) else null
    when {
        dim.slots.isEmpty() || (editingHere && st.editing == dim.slots.size) -> Text(
            "Tap a checker on the board: yours or your opponent's.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { vm.addSlot(d) },
        )
        current != null -> {
            Text("Tap the points the checkers may stand on; − and + set how many move together.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = vm::allOpenPoints) { Text("All open points") }
                TextButton(onClick = vm::clearPoints) { Text("Clear") }
                if (several && dim.slots.size < 3) TextButton(onClick = { vm.addSlot(d) }) { Text("Add checkers") }
            }
        }
        else -> TextButton(onClick = { vm.editSlotAt(d, maxOf(0, dim.slots.lastIndex)) }) { Text("Edit on the board") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Results(st: DeepDiveState, vm: DeepDiveViewModel) {
    val unit = LocalErrorUnit.current
    val n = st.versions.size
    val done = st.losses.count { row -> row.withIndex().none { (k, l) -> st.tracked[k] != null && l == Loss.Pending } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val legal = st.losses.count { it.getOrNull(0) != Loss.Illegal }
        val best = st.versions.indices.count { st.firstBest(it) }
        Text(
            if (st.running) "Analyzing: $done of $n" else "${st.firstText} is best in $best of $legal versions" + if (legal < n) " (not legal in ${n - legal})" else "",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
        )
        if (st.running) TextButton(onClick = vm::stop) { Text("Stop") } else TextButton(onClick = vm::reset) { Text("Change") }
    }
    val sec = st.secondary
    if (sec != null) {
        // Two changes: which one runs along the chart, and the value of the other it shows.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Along the chart: ${st.dims[st.primary].mode.label.lowercase()}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::swapAxes) { Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Swap") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            st.axes[sec].forEachIndexed { j, label ->
                FilterChip(selected = st.slice == j, onClick = { vm.selectSlice(j) }, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
            }
        }
    }
    if (st.ranked) RankedBars(st, vm, unit) else LossChart(st, vm::select, unit)
    if (sec != null) BestGrid(st, vm::select)
    if (!st.ranked) {
        HorizontalDivider()
        for (i in st.row.filterNotNull()) {
            val ver = st.versions[i]
            Row(
                Modifier.fillMaxWidth()
                    .background(if (i == st.selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { vm.select(i) }.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(ver.label + if (ver.isBase) " (as it is)" else "", style = MaterialTheme.typography.bodySmall)
                    Text(st.bestText(i), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TrackedPills(st, i, unit)
            }
            if (i == st.selected) VersionDetails(st, vm, i, unit)
        }
    }
}

private fun pctText(v: Double) = String.format(java.util.Locale.US, "%.1f%%", v * 100)

/**
 * Under the version tapped: the best decision and each followed one with its equity, loss and match winning chances
 * (match play), the cube actions for a cube decision, and Open in analysis.
 */
@Composable
private fun VersionDetails(st: DeepDiveState, vm: DeepDiveViewModel, i: Int, unit: ErrorUnit) {
    val v = st.versions[i]
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val match = v.position.setup.matchLength > 0
            @Composable
            fun line(dot: Color?, text: String, equity: Float?, loss: Loss?) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (dot != null) Box(Modifier.size(8.dp).background(dot, CircleShape)) else Spacer(Modifier.width(8.dp))
                    Text(text, style = mono, modifier = Modifier.weight(1f), maxLines = 1)
                    if (equity != null) Text(eq(equity), style = mono)
                    if (match && equity != null) DeepDive.mwc(v.position, equity)?.let { Text("MWC ${pctText(it)}", style = mono) }
                    if (loss != null) Surface(shape = RoundedCornerShape(50), color = fillOf(loss)) {
                        Text(lossText(loss, unit), color = inkOf(loss), style = mono, modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp))
                    }
                }
            }
            val best = st.bestText(i)
            if (best.isEmpty()) Text("Not analyzed yet", style = MaterialTheme.typography.bodySmall)
            else line(null, "Best: $best", st.equityOf(i, -1), null)
            st.tracked.forEachIndexed { k, t ->
                if (t != null) line(TrackColors[k], st.text(t), st.equityOf(i, k), st.losses.getOrNull(i)?.getOrNull(k))
            }
            st.results.getOrNull(i)?.analysis?.cube?.let { CubeRows(st, it, v) }
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.openInAnalysis(i) }) { Text("Open in analysis") }
            }
        }
    }
}

/** One pill per followed decision, each after a dot of its colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackedPills(st: DeepDiveState, i: Int, unit: ErrorUnit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        st.tracked.forEachIndexed { k, t ->
            if (t == null) return@forEachIndexed
            val l = st.losses.getOrNull(i)?.getOrNull(k)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(TrackColors[k], CircleShape))
                Spacer(Modifier.width(3.dp))
                Surface(shape = RoundedCornerShape(50), color = fillOf(l)) {
                    Text(
                        lossText(l, unit), color = inkOf(l), modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        }
    }
}

/** The cube actions of the version shown, each followed one marked in its colour. */
@Composable
private fun CubeRows(st: DeepDiveState, e: CubeEval, v: com.boldblot.backgammonplyground.core.DeepDive.Version) {
    val doubled = v.position.doubled
    val rows: List<Triple<String, Float, List<DecisionKind>>> = if (doubled) listOf(
        Triple("Take", -e.doubleTake, listOf(DecisionKind.TAKE)),
        Triple("Pass", -e.doublePass, listOf(DecisionKind.PASS)),
    ) else listOf(
        Triple("No double", e.noDouble, listOf(DecisionKind.NO_DOUBLE)),
        Triple("Double, take", e.doubleTake, if (e.doubleTake <= e.doublePass) listOf(DecisionKind.DOUBLE) else emptyList()),
        Triple("Double, pass", e.doublePass, if (e.doublePass < e.doubleTake) listOf(DecisionKind.DOUBLE) else emptyList()),
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for ((label, value, kinds) in rows) {
            val k = st.tracked.indexOfFirst { it is Tracked.Cube && it.kind in kinds }
            val c = if (k >= 0) TrackColors[k] else Color.Transparent
            Row(
                Modifier.fillMaxWidth().background(c.copy(alpha = if (k >= 0) 0.18f else 0f), RoundedCornerShape(6.dp))
                    .border(if (k >= 0) 2.dp else 0.dp, c, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(eq(value), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                DeepDive.mwc(v.position, value)?.let {
                    Text("  MWC ${pctText(it)}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
            }
        }
    }
}

/** A step for the loss axis: 0.01 up to 0.1, 0.05 above. */
private fun axisMax(max: Float): Float {
    val step = if (max > 0.1f) 0.05f else 0.01f
    return maxOf(step, ceil(max / step - 1e-4f) * step)
}

/**
 * The loss of each followed decision across the primary change (at the slice of the second): 0 (best) at the top, a
 * line per decision in its colour, broken where it cannot be decided ([!]); with one decision a red band where it is
 * not best. A tap picks the nearest version.
 */
@Composable
private fun LossChart(st: DeepDiveState, onSelect: (Int) -> Unit, unit: ErrorUnit) {
    val row = st.row
    val n = row.size
    val axis = st.axes.getOrNull(st.primary).orEmpty()
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val faint = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val ks = st.tracked.indices.filter { st.tracked[it] != null }
    fun loss(j: Int, k: Int): Loss? = row[j]?.let { st.losses.getOrNull(it)?.getOrNull(k) }
    val values = ks.flatMap { k -> (0 until n).mapNotNull { j -> (loss(j, k) as? Loss.Value)?.v } }
    val yMax = axisMax(values.maxOrNull() ?: 0f)
    val selJ = row.indexOf(st.selected)
    Text(if (ks.size == 1) "Loss of ${st.firstText}" else "Loss of each play, in its colour", style = MaterialTheme.typography.labelMedium, color = muted)
    Canvas(
        Modifier.fillMaxWidth().height(208.dp).pointerInput(row) {
            detectTapGestures { pos ->
                val left = 56.dp.toPx(); val right = size.width - 12.dp.toPx()
                val step = if (n > 1) (right - left) / (n - 1) else 0f
                val j = if (step == 0f) 0 else ((pos.x - left) / step).roundToInt().coerceIn(0, n - 1)
                row.getOrNull(j)?.let(onSelect)
            }
        },
    ) {
        if (n == 0) return@Canvas
        val left = 56.dp.toPx(); val right = size.width - 12.dp.toPx()
        val top = 18.dp.toPx(); val bottom = size.height - 32.dp.toPx()
        val step = if (n > 1) (right - left) / (n - 1) else 0f
        fun x(j: Int) = left + j * step
        fun y(v: Float) = top + (v.coerceAtMost(yMax) / yMax) * (bottom - top)
        val small = TextStyle(color = muted, fontSize = 10.sp)
        for (g in 0..2) {
            val v = yMax * g / 2
            drawLine(if (g == 0) muted else faint, Offset(left, y(v)), Offset(right, y(v)), strokeWidth = 1f)
            val label = measurer.measure(if (g == 0) "0" else err(-v, unit), small)
            drawText(label, topLeft = Offset(left - label.size.width - 12.dp.toPx(), y(v) - label.size.height / 2))
        }
        // One decision: a red band where it is not best.
        if (ks.size == 1) {
            var j = 0
            while (j < n) {
                val l = loss(j, ks[0])
                if (l is Loss.Value && !l.best) {
                    var e = j
                    while (e + 1 < n && (loss(e + 1, ks[0]) as? Loss.Value)?.best == false) e++
                    val a = if (j == 0) left else x(j) - step / 2
                    val b = if (e == n - 1) right else x(e) + step / 2
                    drawRect(BigFill.copy(alpha = 0.14f), Offset(a, top), Size(b - a, bottom - top))
                    j = e + 1
                } else j++
            }
        }
        if (selJ >= 0) drawLine(muted.copy(alpha = 0.6f), Offset(x(selJ), top), Offset(x(selJ), bottom), strokeWidth = 1.5f)
        for ((slot, k) in ks.withIndex()) {
            val c = if (ks.size == 1) ink.copy(alpha = 0.8f) else TrackColors[k]
            for (j in 0 until n - 1) {
                val a = (loss(j, k) as? Loss.Value)?.v ?: continue
                val b = (loss(j + 1, k) as? Loss.Value)?.v ?: continue
                drawLine(c, Offset(x(j), y(a)), Offset(x(j + 1), y(b)), strokeWidth = 2.dp.toPx())
            }
            for (j in 0 until n) {
                when (val l = loss(j, k)) {
                    Loss.Illegal -> {
                        val t = measurer.measure("[!]", TextStyle(color = if (ks.size == 1) ErrorRed else TrackColors[k], fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        drawText(t, topLeft = Offset(x(j) - t.size.width / 2, top + 2.dp.toPx() + slot * 13.dp.toPx()))
                    }
                    is Loss.Value -> {
                        val r = (if (row[j] == st.selected) 5f else 3.5f).dp.toPx()
                        drawCircle(if (ks.size == 1) fillOf(l) else TrackColors[k], r, Offset(x(j), y(l.v)))
                        if (row[j] == st.selected) drawCircle(accent, r + 2.dp.toPx(), Offset(x(j), y(l.v)), style = Stroke(width = 1.5f.dp.toPx()))
                    }
                    else -> {}
                }
            }
        }
        val every = maxOf(1, ceil(n / 12f).toInt())
        for (j in 0 until n) {
            if (j % every != 0 && j != selJ) continue
            val base = row[j]?.let { st.versions[it].isBase } == true
            val t = measurer.measure(axis.getOrElse(j) { "" }.replace(" ", "\n"), TextStyle(color = if (j == selJ || base) ink else muted, fontSize = 10.sp, fontWeight = if (base) FontWeight.Bold else FontWeight.Normal))
            drawText(t, topLeft = Offset(x(j) - t.size.width / 2, bottom + 4.dp.toPx()))
        }
    }
}

/**
 * Two changes at a glance: the primary across, the second down, each cell in the colour of the followed decision that
 * is best there (the first such), grey where none is, empty where the version cannot be. A tap shows it.
 */
@Composable
private fun BestGrid(st: DeepDiveState, onSelect: (Int) -> Unit) {
    val sec = st.secondary ?: return
    val cols = st.axes[st.primary]
    val rows = st.axes[sec]
    val at = HashMap<Pair<Int, Int>, Int>()
    st.versions.forEachIndexed { i, v -> at[v.coords[st.primary] to v.coords[sec]] = i }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text("Which followed play is best (grey: none of them)", style = MaterialTheme.typography.labelMedium, color = muted)
    // The whole grid fits the panel: the cells share the width left by the row labels.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labelW = 64.dp
        val cell = ((maxWidth - labelW - 2.dp * cols.size) / cols.size).coerceIn(12.dp, 44.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                Spacer(Modifier.width(labelW))
                for (c in cols) Text(
                    c.replace(" ", "\n"), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp), color = muted,
                    maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.width(cell),
                )
            }
            rows.forEachIndexed { r, label ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1, modifier = Modifier.width(labelW))
                    cols.indices.forEach { c ->
                        val i = at[c to r]
                        val fill = if (i == null) Color.Transparent else {
                            val ls = st.losses.getOrNull(i).orEmpty()
                            val k = ls.indices.firstOrNull { st.tracked[it] != null && (ls[it] as? Loss.Value)?.best == true }
                            when {
                                k != null -> TrackColors[k]
                                ls.indices.any { st.tracked[it] != null && ls[it] == Loss.Pending } -> MaterialTheme.colorScheme.surfaceVariant
                                else -> GreyFill
                            }
                        }
                        Box(
                            Modifier.size(width = cell, height = 24.dp).background(fill, RoundedCornerShape(4.dp))
                                .then(if (i == st.selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(4.dp)) else Modifier)
                                .then(if (i != null) Modifier.clickable { onSelect(i) } else Modifier),
                        )
                    }
                }
            }
        }
    }
}

/** Several checkers along the chart: no natural order, so the versions ranked by the first decision's loss. */
@Composable
private fun RankedBars(st: DeepDiveState, vm: DeepDiveViewModel, unit: ErrorUnit) {
    val onSelect: (Int) -> Unit = vm::select
    val ids = st.row.filterNotNull()
    fun first(i: Int) = st.losses.getOrNull(i)?.getOrNull(0)
    val order = ids.sortedWith(compareBy({ first(it) !is Loss.Value }, { (first(it) as? Loss.Value)?.v ?: Float.MAX_VALUE }))
    val max = axisMax(ids.mapNotNull { (first(it) as? Loss.Value)?.v }.maxOrNull() ?: 0f)
    Text("Loss of ${st.firstText}, smallest first", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in order) {
            val l = first(i)
            val v = st.versions[i]
            Column(
                Modifier.fillMaxWidth()
                    .background(if (i == st.selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onSelect(i) }.padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        st.axes[st.primary].getOrElse(v.coords[st.primary]) { v.short } + if (v.isBase) " (now)" else "",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.width(130.dp),
                    )
                    Box(Modifier.weight(1f).height(10.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp))) {
                        if (l is Loss.Value) Box(Modifier.fillMaxWidth((l.v / max).coerceIn(0.03f, 1f)).fillMaxHeight().background(fillOf(l), RoundedCornerShape(5.dp)))
                    }
                }
                TrackedPills(st, i, unit)
            }
            if (i == st.selected) VersionDetails(st, vm, i, unit)
        }
    }
}

@Composable
private fun SaveDialog(st: DeepDiveState, onDismiss: () -> Unit, onSave: (String, DeepDiveViewModel.Which) -> Unit) {
    var name by remember { mutableStateOf("Deep dive: ${st.firstText} (${st.dims.joinToString(" × ") { it.mode.label.lowercase() }})") }
    var which by remember { mutableStateOf(DeepDiveViewModel.Which.ALL) }
    val notBest = st.versions.indices.count { !st.firstBest(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to a set") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("A new set in the Match Library, with the analyses.", style = MaterialTheme.typography.bodySmall)
                for ((w, label) in listOf(
                    DeepDiveViewModel.Which.ALL to "All ${st.versions.size} versions",
                    DeepDiveViewModel.Which.NOT_BEST to "Where ${st.firstText} is not best ($notBest)",
                    DeepDiveViewModel.Which.SHOWN to "Only the one shown",
                )) {
                    Row(
                        Modifier.fillMaxWidth().selectable(which == w, role = Role.RadioButton) { which = w },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = which == w, onClick = null, modifier = Modifier.padding(8.dp).size(20.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, which) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
