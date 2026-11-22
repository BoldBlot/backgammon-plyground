package com.boldblot.backgammonplyground.ui.deepdive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.boldblot.backgammonplyground.core.CubeState
import com.boldblot.backgammonplyground.core.DecisionKind
import com.boldblot.backgammonplyground.core.DeepDive
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.data.ErrorUnit
import com.boldblot.backgammonplyground.data.Settings
import com.boldblot.backgammonplyground.ui.board.BoardColors
import com.boldblot.backgammonplyground.ui.board.BoardView
import com.boldblot.backgammonplyground.ui.board.DiceView
import com.boldblot.backgammonplyground.ui.board.PointLabel
import com.boldblot.backgammonplyground.ui.board.boardColorsFor
import com.boldblot.backgammonplyground.ui.components.LocalErrorUnit
import com.boldblot.backgammonplyground.ui.components.err
import kotlin.math.ceil
import kotlin.math.roundToInt

private val BestFill = Color(0xFF1F7A4A)
private val SmallFill = Color(0xFFD9A925)
private val BigFill = Color(0xFFC0392B)
private val GreyFill = Color(0xFF5F5F5F)

private fun fillOf(o: Outcome?): Color = when {
    o == null || !o.legal || o.loss == null -> GreyFill
    o.isBest -> BestFill
    o.loss < com.boldblot.backgammonplyground.core.XgRules.ERROR_THRESHOLD -> SmallFill
    else -> BigFill
}

private fun inkOf(o: Outcome?): Color = if (fillOf(o) == SmallFill) Color.Black else Color.White

/** The loss as the pills show it: "best", "-0.041" (or "-41.0"), "[!]" where the tracked decision is not legal. */
private fun lossText(o: Outcome?, unit: ErrorUnit): String = when {
    o == null -> "…"
    !o.legal -> "[!]"
    o.loss == null -> "?"
    o.isBest -> "best"
    else -> err(-o.loss, unit)
}

/** Shorter, for the board: "0", ".041" (or "41"). */
private fun boardText(o: Outcome?, unit: ErrorUnit): String = when {
    o == null -> "…"
    !o.legal -> "[!]"
    o.loss == null -> "?"
    o.isBest -> "0"
    unit == ErrorUnit.MEQ -> (o.loss * 1000).roundToInt().toString()
    else -> String.format(java.util.Locale.US, "%.3f", o.loss).removePrefix("0")
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
                val side = minOf(420.dp, maxWidth * 0.45f)
                Row(Modifier.fillMaxSize().padding(6.dp)) {
                    Column(Modifier.width(side).fillMaxHeight()) {
                        top()
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { Panel(st, vm) }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        DiveBoard(st, vm, colors, settings.mirrorBoard, Modifier.aspectRatio(1.38f))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                    top()
                    DiveBoard(st, vm, colors, settings.mirrorBoard, Modifier.fillMaxWidth().aspectRatio(1.38f))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { Panel(st, vm) }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
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
    val checkers = st.mode == DiveMode.CHECKER || st.mode == DiveMode.CHECKERS
    val slot = if (setup && checkers) st.slots.getOrNull(st.editing) else null
    fun human(player: Int, i: Int) = if (player == Player.HUMAN) i else 23 - i
    val labels: Map<Int, PointLabel> = when {
        setup || !checkers -> emptyMap()
        !st.ranked -> st.versions.indices.mapNotNull { i ->
            val spot = st.versions[i].spots.firstOrNull() ?: return@mapNotNull null
            val o = st.outcomes.getOrNull(i)
            spot to PointLabel(boardText(o, unit), fillOf(o), inkOf(o), ring = i == st.selected)
        }.toMap()
        // Several checkers: each slot's letter where its checker stands (letters sharing a point together).
        else -> v?.spots?.withIndex()?.groupBy({ it.value }, { "ABCDEF"[it.index] })
            ?.mapValues { (_, letters) -> PointLabel(letters.joinToString(""), Color(0xFFF1EFE9), Color.Black, ring = true) }.orEmpty()
    }
    BoardView(
        board = p.board, modifier = modifier, colors = colors, mirrored = mirrored,
        dice = p.dice?.let { listOf(DiceView(Player.HUMAN, it.highFirst)) } ?: emptyList(),
        cube = if (crawford) CubeState() else s.cube, cubeOffered = p.doubled, crawford = crawford,
        pips = listOf(p.board.pipCount(Player.HUMAN), p.board.pipCount(Player.CPU)),
        movable = slot?.let { setOf(human(it.player, it.from)) } ?: emptySet(),
        selected = slot?.let { human(it.player, it.from) },
        destinations = slot?.let { sl -> sl.targets.filter { it != sl.from }.map { human(sl.player, it) }.toSet() } ?: emptySet(),
        dragPlayer = slot?.player ?: Player.HUMAN,
        onTap = if (setup && checkers) vm::tapPoint else null,
        pointLabels = labels,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Panel(st: DeepDiveState, vm: DeepDiveViewModel) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Following", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val t = st.tracked
        if (t is DeepDive.Tracked.Cube && !st.hasResults) {
            val options = if (st.base.doubled) listOf(DecisionKind.TAKE, DecisionKind.PASS) else listOf(DecisionKind.NO_DOUBLE, DecisionKind.DOUBLE)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (k in options) FilterChip(
                    selected = t.kind == k, onClick = { vm.setTracked(k) },
                    label = { Text(com.boldblot.backgammonplyground.ui.components.describeAction(k, st.board, Player.HUMAN, null)) },
                )
            }
        } else {
            Text(st.trackedText, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace))
        }
        if (!st.hasResults) Setup(st, vm) else Results(st, vm)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Setup(st: DeepDiveState, vm: DeepDiveViewModel) {
    Text("What to change", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (m in DiveMode.entries) FilterChip(selected = st.mode == m, onClick = { vm.setMode(m) }, label = { Text(m.label) })
    }
    when (st.mode) {
        DiveMode.CHECKER, DiveMode.CHECKERS -> SlotsEditor(st, vm)
        DiveMode.SCORE -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = st.scorePlayer == Player.CPU, onClick = { vm.setScorePlayer(Player.CPU) }, label = { Text("Opponent's score") })
                FilterChip(selected = st.scorePlayer == Player.HUMAN, onClick = { vm.setScorePlayer(Player.HUMAN) }, label = { Text("Your score") })
            }
            Text(
                "From ${st.base.setup.matchLength}-away down to 1-away (Crawford and post-Crawford), then a money game. The other score stays as it is.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    val problem = st.problem
    if (problem != null && st.slots.isNotEmpty() || problem != null && st.mode != DiveMode.CHECKER && st.mode != DiveMode.CHECKERS) Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    val n = st.planned
    if (n > DeepDive.MAX_VERSIONS) Text("Only the first ${DeepDive.MAX_VERSIONS} are analyzed.", style = MaterialTheme.typography.bodySmall)
    Button(onClick = vm::start, enabled = problem == null && n >= 2, modifier = Modifier.fillMaxWidth()) {
        Text(if (problem == null && n >= 2) "Analyze ${minOf(n, DeepDive.MAX_VERSIONS)} versions" else "Analyze")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotsEditor(st: DeepDiveState, vm: DeepDiveViewModel) {
    val several = st.mode == DiveMode.CHECKERS
    st.slots.forEachIndexed { i, s ->
        val who = if (s.player == Player.HUMAN) "Your checker" else "Opponent's checker"
        val points = s.targets.sortedDescending().joinToString(", ") { DeepDive.shown(s.player, it).toString() }
        Row(
            Modifier.fillMaxWidth()
                .background(if (i == st.editing) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(10.dp))
                .clickable { vm.editSlotAt(i) }.padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                (if (several) "${"ABCDEF"[i]}  " else "") + "$who on ${DeepDive.shown(s.player, s.from)} → $points",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.removeSlot(i) }) { Icon(Icons.Default.Close, "Remove") }
        }
    }
    val current = st.slots.getOrNull(st.editing)
    if (current == null) {
        Text("Tap a checker on the board: yours or your opponent's.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text("Tap the points it may stand on.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = vm::allOpenPoints) { Text("All open points") }
            TextButton(onClick = vm::clearPoints) { Text("Clear") }
            if (several && st.slots.size < 3) TextButton(onClick = vm::addSlot) { Text("Add a checker") }
        }
    }
}

@Composable
private fun Results(st: DeepDiveState, vm: DeepDiveViewModel) {
    val unit = LocalErrorUnit.current
    val n = st.versions.size
    val done = st.outcomes.count { it != null }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val legal = st.outcomes.count { it?.legal == true }
        val best = st.outcomes.count { it?.isBest == true }
        Text(
            if (st.running) "Analyzing: $done of $n" else "${st.trackedText} is best in $best of $legal versions" + if (legal < n) " (not legal in ${n - legal})" else "",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
        )
        if (st.running) TextButton(onClick = vm::stop) { Text("Stop") } else TextButton(onClick = vm::reset) { Text("Change") }
    }
    if (st.ranked) RankedBars(st, vm::select, unit) else LossChart(st, vm::select, unit)
    val v = st.versions.getOrNull(st.selected)
    if (v != null) {
        val o = st.outcomes.getOrNull(st.selected)
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(v.label + if (v.isBase) " (as it is)" else "", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        o == null -> "Not analyzed yet"
                        !o.legal -> "[!] ${st.trackedText} cannot be played here"
                        else -> "Best: ${o.best}"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${st.trackedText}  ", style = MaterialTheme.typography.bodyMedium)
                    LossPill(o, unit)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.openInAnalysis(st.selected) }) { Text("Open in analysis") }
                }
            }
        }
    }
    if (!st.ranked) {
        HorizontalDivider()
        st.versions.forEachIndexed { i, ver ->
            val o = st.outcomes.getOrNull(i)
            Row(
                Modifier.fillMaxWidth()
                    .background(if (i == st.selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { vm.select(i) }.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(ver.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(if (o?.legal == true) o.best else "", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                LossPill(o, unit)
            }
        }
    }
}

@Composable
private fun LossPill(o: Outcome?, unit: ErrorUnit) {
    Surface(shape = RoundedCornerShape(50), color = fillOf(o)) {
        Text(
            lossText(o, unit), color = inkOf(o), modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

/** A step for the loss axis: 0.01 up to 0.1, 0.05 above. */
private fun axisMax(max: Float): Float {
    val step = if (max > 0.1f) 0.05f else 0.01f
    return maxOf(step, ceil(max / step - 1e-4f) * step)
}

/**
 * The loss of the tracked decision across the versions: 0 (best) at the top, a red band where it is not best, a gap
 * marked [!] where it is not legal. A tap picks the nearest version.
 */
@Composable
private fun LossChart(st: DeepDiveState, onSelect: (Int) -> Unit, unit: ErrorUnit) {
    val n = st.versions.size
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val faint = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val error = MaterialTheme.colorScheme.error
    val accent = MaterialTheme.colorScheme.primary
    val losses = st.outcomes.map { o -> if (o?.legal == true) o.loss else null }
    val yMax = axisMax(losses.filterNotNull().maxOrNull() ?: 0f)
    Text("Loss of ${st.trackedText}", style = MaterialTheme.typography.labelMedium, color = muted)
    Canvas(
        Modifier.fillMaxWidth().height(190.dp).pointerInput(n) {
            detectTapGestures { pos ->
                val left = 56.dp.toPx(); val right = size.width - 12.dp.toPx()
                val step = if (n > 1) (right - left) / (n - 1) else 0f
                onSelect(if (step == 0f) 0 else ((pos.x - left) / step).roundToInt().coerceIn(0, n - 1))
            }
        },
    ) {
        val left = 56.dp.toPx(); val right = size.width - 12.dp.toPx()
        val top = 18.dp.toPx(); val bottom = size.height - 24.dp.toPx()
        val step = if (n > 1) (right - left) / (n - 1) else 0f
        fun x(i: Int) = left + i * step
        fun y(v: Float) = top + (v.coerceAtMost(yMax) / yMax) * (bottom - top)
        val small = TextStyle(color = muted, fontSize = 10.sp)
        // the grid: 0 (best) at the top
        for (k in 0..2) {
            val v = yMax * k / 2
            drawLine(if (k == 0) muted else faint, Offset(left, y(v)), Offset(right, y(v)), strokeWidth = 1f)
            val label = measurer.measure(if (k == 0) "0" else err(-v, unit), small)
            drawText(label, topLeft = Offset(left - label.size.width - 12.dp.toPx(), y(v) - label.size.height / 2))
        }
        // red bands where the tracked decision is not best
        var i = 0
        while (i < n) {
            if ((losses[i] ?: 0f) > DeepDive.TIE) {
                var j = i
                while (j + 1 < n && (losses[j + 1] ?: 0f) > DeepDive.TIE) j++
                val a = if (i == 0) left else x(i) - step / 2
                val b = if (j == n - 1) right else x(j) + step / 2
                drawRect(BigFill.copy(alpha = 0.14f), Offset(a, top), Size(b - a, bottom - top))
                i = j + 1
            } else i++
        }
        // the selected version
        drawLine(muted.copy(alpha = 0.6f), Offset(x(st.selected), top), Offset(x(st.selected), bottom), strokeWidth = 1.5f)
        // the line, broken where there is no loss (not legal, not analyzed yet)
        for (k in 0 until n - 1) {
            val a = losses[k] ?: continue
            val b = losses[k + 1] ?: continue
            drawLine(ink.copy(alpha = 0.8f), Offset(x(k), y(a)), Offset(x(k + 1), y(b)), strokeWidth = 2.dp.toPx())
        }
        val every = maxOf(1, ceil(n / 12f).toInt())
        for (k in 0 until n) {
            val o = st.outcomes.getOrNull(k)
            val l = losses[k]
            if (o != null && !o.legal) {
                val t = measurer.measure("[!]", TextStyle(color = error, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                drawText(t, topLeft = Offset(x(k) - t.size.width / 2, top + 2.dp.toPx()))
            } else if (l != null) {
                val r = (if (k == st.selected) 6 else 4).dp.toPx()
                drawCircle(fillOf(o), r, Offset(x(k), y(l)))
                if (k == st.selected) drawCircle(accent, r + 2.dp.toPx(), Offset(x(k), y(l)), style = Stroke(width = 2.dp.toPx()))
            }
            if (k % every == 0 || k == st.selected) {
                val base = st.versions[k].isBase
                val t = measurer.measure(st.versions[k].short, TextStyle(color = if (k == st.selected || base) ink else muted, fontSize = 10.sp, fontWeight = if (base) FontWeight.Bold else FontWeight.Normal))
                drawText(t, topLeft = Offset(x(k) - t.size.width / 2, bottom + 4.dp.toPx()))
            }
        }
    }
}

/** Several checkers: no natural order, so the versions ranked by the loss (not legal last). */
@Composable
private fun RankedBars(st: DeepDiveState, onSelect: (Int) -> Unit, unit: ErrorUnit) {
    val order = st.versions.indices.sortedWith(compareBy({ st.outcomes.getOrNull(it)?.legal != true }, { st.outcomes.getOrNull(it)?.loss ?: Float.MAX_VALUE }))
    val max = axisMax(st.outcomes.mapNotNull { if (it?.legal == true) it.loss else null }.maxOrNull() ?: 0f)
    Text("Loss of ${st.trackedText}, smallest first", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in order) {
            val o = st.outcomes.getOrNull(i)
            val v = st.versions[i]
            Row(
                Modifier.fillMaxWidth()
                    .background(if (i == st.selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onSelect(i) }.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(v.short + if (v.isBase) " (now)" else "", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.width(130.dp))
                Box(Modifier.weight(1f).height(10.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp))) {
                    val f = ((o?.loss ?: 0f) / max).coerceIn(0.03f, 1f)
                    if (o?.legal == true && o.loss != null) Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(fillOf(o), RoundedCornerShape(5.dp)))
                }
                LossPill(o, unit)
            }
        }
    }
}

@Composable
private fun SaveDialog(st: DeepDiveState, onDismiss: () -> Unit, onSave: (String, DeepDiveViewModel.Which) -> Unit) {
    var name by remember { mutableStateOf("Deep dive: ${st.trackedText} (${st.mode.label.lowercase()})") }
    var which by remember { mutableStateOf(DeepDiveViewModel.Which.ALL) }
    val notBest = st.outcomes.count { it?.isBest != true }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to a set") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("A new set in the Match Library, with the analyses.", style = MaterialTheme.typography.bodySmall)
                for ((w, label) in listOf(
                    DeepDiveViewModel.Which.ALL to "All ${st.versions.size} versions",
                    DeepDiveViewModel.Which.NOT_BEST to "Where ${st.trackedText} is not best ($notBest)",
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
