package com.boldblot.backgammonplyground.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.CubeState
import com.boldblot.backgammonplyground.core.DecisionKind
import com.boldblot.backgammonplyground.core.Feedback
import com.boldblot.backgammonplyground.core.GameState
import com.boldblot.backgammonplyground.core.GameSummary
import com.boldblot.backgammonplyground.core.MatchState
import com.boldblot.backgammonplyground.core.MoveNotation
import com.boldblot.backgammonplyground.core.PerformanceTotals
import com.boldblot.backgammonplyground.core.Phase
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.Severity
import com.boldblot.backgammonplyground.core.WinType
import com.boldblot.backgammonplyground.core.XgRules
import com.boldblot.backgammonplyground.data.GameEntity
import com.boldblot.backgammonplyground.data.MeterMode
import com.boldblot.backgammonplyground.data.TrainingMode
import com.boldblot.backgammonplyground.ui.board.ArrowColors
import com.boldblot.backgammonplyground.ui.board.BoardGeometry
import com.boldblot.backgammonplyground.ui.board.BoardView
import com.boldblot.backgammonplyground.ui.board.DiceView
import com.boldblot.backgammonplyground.ui.board.arrowsFor
import com.boldblot.backgammonplyground.ui.board.boardColorsFor
import com.boldblot.backgammonplyground.ui.components.CandidateList
import com.boldblot.backgammonplyground.ui.components.CubeTable
import com.boldblot.backgammonplyground.ui.components.PlayedBestBar
import com.boldblot.backgammonplyground.ui.components.SeverityBadge
import com.boldblot.backgammonplyground.ui.components.describeAction
import com.boldblot.backgammonplyground.ui.components.eq
import com.boldblot.backgammonplyground.ui.components.fmt
import com.boldblot.backgammonplyground.ui.components.pct
import com.boldblot.backgammonplyground.ui.components.scrollIndicator
import com.boldblot.backgammonplyground.ui.review.GameStepper
import com.boldblot.backgammonplyground.ui.review.SavedGameRows
import com.boldblot.backgammonplyground.ui.review.SavedSummaryDialog
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun GameScreen(
    vm: GameViewModel,
    onBack: () -> Unit,
    onReview: (matchId: Long) -> Unit,
    /** Opens the review on [gameNumber]'s errors and blunders. */
    onReviewMistakes: (matchId: Long, gameNumber: Int) -> Unit = { id, _ -> onReview(id) },
    /** Opens Settings; Back returns to the match. */
    onSettings: () -> Unit = {},
) {
    val st by vm.state.collectAsState()
    val m = st.match
    var confirmAbandon by remember { mutableStateOf(false) }
    var chooseResign by remember { mutableStateOf(false) }
    // A money session just ended with its mistakes held back ("after the match" training): its summary.
    var endedSession by remember { mutableStateOf<Long?>(null) }
    fun endSession(id: Long, finishedGames: Boolean, end: () -> Unit) {
        end()
        if (finishedGames && st.settings.trainingMode == TrainingMode.AFTER_GAME) endedSession = id else onBack()
    }

    Box(Modifier.fillMaxSize()) {
        when {
            st.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            m == null -> NoMatch(onBack)
            else -> GameContent(st, m, vm, onBack, onAbandon = { confirmAbandon = true }, onResign = { chooseResign = true }, onSettings = onSettings)
        }
        if (st.autoFinishing) AutoFinishOverlay(vm::stopAutoFinishAndPlay)
    }

    if (chooseResign && m != null) {
        ResignDialog(m, onDismiss = { chooseResign = false }, onResign = { chooseResign = false; vm.resign(it) })
    }

    val offer = m?.game?.resignOffer
    if (m != null && offer != null && m.game.turn == Player.CPU && !st.busy && !st.autoFinishing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Computer resigns") },
            text = { Text("The computer offers to resign ${GameViewModel.resignText(offer, m)}.") },
            confirmButton = { TextButton(onClick = { vm.answerResign(true) }) { Text("Accept") } },
            dismissButton = { TextButton(onClick = { vm.answerResign(false) }) { Text("Reject") } },
        )
    }

    if (confirmAbandon && m != null) {
        AlertDialog(
            onDismissRequest = { confirmAbandon = false },
            title = { Text(if (m.rules.isMoney) "End session?" else "Abandon match?") },
            text = {
                Text(
                    if (m.rules.isMoney) "Finished games are kept in your statistics."
                    else "Finished games stay in your statistics. The match will not count as won or lost and will not change your rating.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmAbandon = false
                    if (m.rules.isMoney) endSession(m.id, m.gameNumber > 1, vm::abandonMatch) else { vm.abandonMatch(); onBack() }
                }) { Text("Yes") }
            },
            dismissButton = { TextButton(onClick = { confirmAbandon = false }) { Text("Cancel") } },
        )
    }

    st.gameOver?.let { info ->
        GameOverDialog(info, st.inStats, st.settings.gameSummary, st.analysisLeft, vm, onReview, onReviewMistakes, onBack) {
            endSession(info.matchId, true, vm::endSession)
        }
    }
    endedSession?.let { id ->
        SavedSummaryDialog(
            vm.app, id, gameNumber = null, startWithMatch = true, onDismiss = { endedSession = null; onBack() },
            onReviewMistakes = { game -> endedSession = null; onReviewMistakes(id, game) },
        )
    }
}

/** Lets the human choose how much to resign: a single game, a gammon or a backgammon. */
@Composable
private fun ResignDialog(m: MatchState, onDismiss: () -> Unit, onResign: (WinType) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resign") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The computer accepts unless it has at least a 1% chance of winning more.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                WinType.entries.forEach { type ->
                    OutlinedButton(onClick = { onResign(type) }, modifier = Modifier.fillMaxWidth()) {
                        Text(GameViewModel.resignText(type, m).replaceFirstChar { it.uppercase() })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun title(m: MatchState): String =
    if (m.rules.isMoney) "Money game" else "${m.rules.length}-point match"

private fun subtitle(m: MatchState, inStats: Boolean): String =
    "vs ${m.opponentLabel} · game ${m.gameNumber}" + (if (m.game.crawford) " · Crawford" else "") +
        if (inStats) "" else " · not in statistics"

@Composable
private fun NoMatch(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No match in progress.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onBack) { Text("Back to menu") }
    }
}

@Composable
private fun GameMenu(st: GameUiState, m: MatchState, vm: GameViewModel, onAbandon: () -> Unit, onResign: () -> Unit, onSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf(false) }
    if (summary) SavedSummaryDialog(vm.app, m.id, gameNumber = null, startWithMatch = false, onDismiss = { summary = false })
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "Game options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(if (st.canResign) "Resign…" else "Resign at my turn…") }, enabled = st.canResign || st.canQueueResign,
                leadingIcon = { Icon(Icons.Default.Flag, null) },
                onClick = { open = false; onResign() },
            )
            DropdownMenuItem(
                text = { Text("Autofinish game") }, enabled = st.canAutoFinish,
                leadingIcon = { Icon(Icons.Default.FastForward, null) },
                onClick = { open = false; vm.startAutoFinish() },
            )
            if (m.gameNumber > 1) {
                DropdownMenuItem(
                    text = { Text("Last game's summary") },
                    leadingIcon = { Icon(Icons.Default.Summarize, null) },
                    onClick = { open = false; summary = true },
                )
            }
            DropdownMenuItem(
                text = { Text(if (st.inStats) "Exclude from statistics" else "Count in statistics again") },
                leadingIcon = { Icon(Icons.Default.BarChart, null) },
                onClick = { open = false; vm.toggleInStats() },
            )
            DropdownMenuItem(
                text = { Text("Settings") },
                leadingIcon = { Icon(Icons.Default.Settings, null) },
                onClick = { open = false; onSettings() },
            )
            DropdownMenuItem(
                text = { Text(if (m.rules.isMoney) "End session" else "Abandon match") },
                onClick = { open = false; onAbandon() },
            )
            if (com.boldblot.backgammonplyground.BuildConfig.DEBUG) {
                DropdownMenuItem(text = { Text("Auto-play (debug)") }, onClick = { open = false; vm.setAutoPlay(true) })
                DropdownMenuItem(text = { Text("Stop auto-play (debug)") }, onClick = { open = false; vm.setAutoPlay(false) })
                DropdownMenuItem(text = { Text("Next roll 3-3 (debug)") }, onClick = { open = false; vm.forceDouble(3) })
            }
        }
    }
}

/**
 * While autofinish plays: a tap anywhere stops it. The notice sits below the board in portrait and
 * in the middle of the side column in landscape (where the buttons were), never over the board.
 */
@Composable
private fun AutoFinishOverlay(onStop: () -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxSize().clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null, onClick = onStop,
        ),
    ) {
        val landscape = maxWidth > maxHeight
        Row(
            Modifier.align(if (landscape) Alignment.CenterStart else Alignment.BottomCenter)
                .padding(if (landscape) 6.dp else 16.dp)
                .then(if (landscape) Modifier.widthIn(max = RAIL_MIN_WIDTH) else Modifier)
                .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.FastForward, null, tint = MaterialTheme.colorScheme.inverseOnSurface)
            Spacer(Modifier.width(8.dp))
            Text("Autofinish · tap anywhere to stop", color = MaterialTheme.colorScheme.inverseOnSurface)
        }
    }
}

/** True when an analysis panel (practice prompt or hint) should be visible. */
private fun hasPanel(st: GameUiState) = st.feedback != null || st.hint != null || st.cubeHint != null

@Composable
private fun GameContent(
    st: GameUiState, m: MatchState, vm: GameViewModel, onBack: () -> Unit, onAbandon: () -> Unit, onResign: () -> Unit, onSettings: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            // Landscape: side column | board. The board always has the same size and place (the
            // largest that fits next to the rail); the practice prompt and hints replace the rail
            // in the side column, so opening them never shrinks or moves the board.
            val pad = 6.dp
            val gap = 6.dp
            val boardWidth = minOf(
                (maxHeight - pad * 2) * BOARD_ASPECT,
                maxWidth - pad * 2 - gap - RAIL_MIN_WIDTH,
            )
            Row(Modifier.fillMaxSize().padding(pad)) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    if (hasPanel(st)) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { Panels(st, m, vm) }
                    } else {
                        Rail(st, m, vm, onBack, onAbandon, onResign, onSettings, Modifier.widthIn(max = RAIL_MAX_WIDTH).fillMaxSize())
                    }
                }
                Spacer(Modifier.width(gap))
                Box(Modifier.width(boardWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    BoardArea(st, m, vm, Modifier.fillMaxWidth().aspectRatio(BOARD_ASPECT))
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    MenuButton(onBack)
                    Column(Modifier.weight(1f)) {
                        Text(title(m), style = MaterialTheme.typography.titleMedium)
                        Text(subtitle(m, st.inStats), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    GameMenu(st, m, vm, onAbandon, onResign, onSettings)
                }
                Column(Modifier.padding(horizontal = 6.dp)) {
                    PlayerStrip(st, m, Player.CPU)
                    BoardArea(st, m, vm, Modifier.fillMaxWidth().aspectRatio(BOARD_ASPECT))
                    PlayerStrip(st, m, Player.HUMAN)
                    Meter(st, vm, twoLines = false)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 6.dp)) {
                    StatusText(st)
                    Actions(st, m, vm, vertical = false, onResign = onResign)
                    Panels(st, m, vm)
                }
            }
        }
    }
}

/** Back to the main menu (a labelled button, not an arrow that looks like Undo). */
@Composable
private fun MenuButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
        Icon(Icons.Default.Home, null)
        Spacer(Modifier.width(4.dp))
        Text("Menu")
    }
}

private const val BOARD_ASPECT = 1.38f
private val RAIL_MIN_WIDTH = 172.dp
private val RAIL_MAX_WIDTH = 240.dp

/** Landscape side rail: players, match info and controls (Backgammon Galaxy style). */
@Composable
private fun Rail(
    st: GameUiState, m: MatchState, vm: GameViewModel, onBack: () -> Unit, onAbandon: () -> Unit, onResign: () -> Unit,
    onSettings: () -> Unit, modifier: Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MenuButton(onBack)
            Spacer(Modifier.weight(1f))
            GameMenu(st, m, vm, onAbandon, onResign, onSettings)
        }
        PlayerCard(st, m, Player.CPU)
        Spacer(Modifier.height(6.dp))
        Text(title(m), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle(m, st.inStats), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
            StatusText(st)
            Meter(st, vm, twoLines = true)
            Actions(st, m, vm, vertical = true, onResign = onResign)
        }
        PlayerCard(st, m, Player.HUMAN)
    }
}

@Composable
private fun StatusText(st: GameUiState) {
    val text = st.busyText ?: st.message ?: return
    Text(
        text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun PlayerCard(st: GameUiState, m: MatchState, player: Int) {
    val colors = boardColorsFor(st.settings)
    val toAct = toAct(m, player)
    val name = if (player == Player.HUMAN) "You" else "Computer"
    val bg = if (toAct) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    Column(
        Modifier.fillMaxWidth().background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(if (player == Player.HUMAN) colors.human else colors.cpu, RoundedCornerShape(7.dp)))
            Spacer(Modifier.width(6.dp))
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = if (toAct) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
            Text(scoreText(m, player), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private fun toAct(m: MatchState, player: Int): Boolean = when (m.game.phase) {
    Phase.DOUBLED -> m.game.responder == player
    Phase.BEAVERED -> m.game.turn == player
    Phase.OVER, Phase.OPENING -> false
    else -> m.game.turn == player
}

private fun scoreText(m: MatchState, player: Int): String = if (m.rules.isMoney) {
    val net = m.score[player] - m.score[Player.other(player)]
    "${if (net > 0) "+" else ""}$net"
} else "${m.score[player]}/${m.rules.length}"

@Composable
private fun PlayerStrip(st: GameUiState, m: MatchState, player: Int) {
    val colors = boardColorsFor(st.settings)
    val toAct = toAct(m, player)
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(if (player == Player.HUMAN) colors.human else colors.cpu, RoundedCornerShape(6.dp)))
        Spacer(Modifier.width(6.dp))
        Text(
            if (player == Player.HUMAN) "You" else "Computer",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (toAct) FontWeight.Bold else FontWeight.Normal,
        )
        if (toAct) Text("  ●", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.weight(1f))
        Text(scoreText(m, player), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BoardArea(st: GameUiState, m: MatchState, vm: GameViewModel, modifier: Modifier) {
    val g = m.game
    val showBoard = st.preview ?: st.board
    val previewing = st.preview != null
    val dice = buildList {
        if (previewing) return@buildList
        // The computer's last roll stays visible (greyed) until the human rolls or doubles, or the
        // computer doubles.
        if (g.offeredCube == null) st.cpuLastDice?.let { add(DiceView(Player.CPU, it.highFirst, greyed = true)) }
        if (g.phase == Phase.MOVING && g.dice != null) {
            if (g.turn == Player.HUMAN) {
                add(DiceView(Player.HUMAN, st.diceOrder, st.diceDim, error = st.diceError))
            } else {
                add(DiceView(Player.CPU, g.dice.highFirst))
            }
        }
    }
    val interactive = !previewing && g.phase == Phase.MOVING && g.turn == Player.HUMAN && !st.busy
    Column(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            BoardView(
                board = showBoard,
                modifier = Modifier.fillMaxSize(),
                colors = boardColorsFor(st.settings),
                mirrored = st.settings.mirrorBoard,
                dice = dice,
                cube = offeredCube(g),
                cubeOffered = g.offeredCube != null,
                cubeAside = passShown(st, m),
                movable = if (interactive) st.movable else emptySet(),
                lastCpuSteps = if (previewing) emptyList() else st.lastCpuSteps,
                pips = if (st.settings.showPips) listOf(showBoard.pipCount(Player.HUMAN), showBoard.pipCount(Player.CPU)) else null,
                arrows = if (previewing) st.previewArrows else emptyList(),
                onTap = if (interactive) vm::tap else null,
                onDrag = if (interactive) vm::drag else null,
                onDiceTap = if (interactive) vm::tapDice else null,
            )
            // On-board buttons in the human's half of the board.
            val w = maxWidth
            val margin = w * 0.018f
            val tray = w * 0.075f
            val bar = w * 0.065f
            val pointW = (w - tray - margin * 2 - bar) / 12
            val mirrored = st.settings.mirrorBoard
            val halfLeft = if (mirrored) margin + tray else margin + pointW * 6 + bar
            val buttonH = buttonHeight(w, maxHeight)
            Box(
                Modifier.offset(x = halfLeft).width(pointW * 6).fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) { OnBoardButtons(st, m, vm, pointW, buttonH) }
            // Pass to a double goes in the computer's half, next to the offered cube.
            if (passShown(st, m)) {
                val geo = BoardGeometry(w.value, maxHeight.value, mirrored)
                Box(Modifier.offset(x = geo.cubeAside().second.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    AskTwice(
                        st.settings.confirmPass, "Pass", "Pass?", onAct = { vm.respond(false) },
                        onStopAsking = { vm.stopConfirming(double = false) },
                    ) { text, onClick ->
                        FilledTonalButton(
                            onClick = onClick, modifier = Modifier.width(geo.passW.dp).height(buttonH),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                        ) { Text(text, style = buttonLabel(pointW), maxLines = 1) }
                    }
                }
            }
            // Signs in the middle of the board, centred on the bar: a cube mistake, or a turn without
            // a choice. They go just below the centre line, or to the nearest free spot so that they
            // cover neither the Roll / Double buttons nor checkers, dice or the cube.
            val buttonsShown = !st.busy && st.preview == null && !hasPanel(st) && !st.autoFinishing &&
                ((g.phase == Phase.PRE_ROLL && g.turn == Player.HUMAN) || (g.phase == Phase.DOUBLED && g.responder == Player.HUMAN) ||
                    (g.phase == Phase.BEAVERED && g.turn == Player.HUMAN))
            val boardW = maxWidth
            val boardH = maxHeight
            Box(Modifier.width(barCenterXOf(boardW, mirrored) * 2).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .wrapContentWidth(unbounded = true)
                        .layout { measurable, constraints ->
                            val p = measurable.measure(constraints)
                            val geo = BoardGeometry(boardW.toPx(), boardH.toPx(), mirrored)
                            val top = signTop(
                                geo, showBoard, dice, offeredCube(g), g.offeredCube != null, buttonsShown,
                                p.width.toFloat(), p.height.toFloat(),
                                buttonH.toPx() / 2, 4.dp.toPx(),
                            )
                            layout(p.width, p.height) { p.place(0, (top - (geo.height - p.height) / 2).roundToInt()) }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CubeMistakeSign(st, m)
                    BoardNoticeSign(st)
                }
            }
        }
        if (st.busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp)) else Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun OnBoardButtons(st: GameUiState, m: MatchState, vm: GameViewModel, pointW: Dp, height: Dp) {
    val g = m.game
    if (st.busy || st.preview != null || hasPanel(st) || st.autoFinishing) return
    // A pair of buttons stays within the four middle points, clear of the stacks on the outer
    // ones (the 6- and 19-points at the start); on a narrow board with a smaller label.
    val pairMod = Modifier.width(pointW * 2 - 4.dp).height(height)
    // Small side padding unless the buttons have room to spare (a big landscape board).
    val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = if (pointW < 48.dp) 2.dp else 14.dp, vertical = 0.dp)
    val label = buttonLabel(pointW)
    when {
        g.phase == Phase.PRE_ROLL && g.turn == Player.HUMAN -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (g.mayDouble(m.rules, m.score)) {
                AskTwice(
                    // On a narrow (portrait) board the value does not fit; the cube shows it.
                    st.settings.confirmDouble, "Double", if (pointW < 48.dp) "Double?" else "Double to ${g.cube.value * 2}?", onAct = vm::double,
                    onStopAsking = { vm.stopConfirming(double = true) },
                ) { text, onClick ->
                    FilledTonalButton(onClick = onClick, modifier = pairMod, contentPadding = pad) { Text(text, style = label, maxLines = 1) }
                }
            }
            Button(onClick = vm::roll, modifier = pairMod, contentPadding = pad) { Text("Roll", style = label, maxLines = 1) }
        }
        // Pass is in the computer's half (see BoardArea).
        g.phase == Phase.DOUBLED && g.responder == Player.HUMAN -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.respond(true) }, modifier = pairMod, contentPadding = pad) {
                Text("Take ${g.cube.value * 2}", style = label, maxLines = 1)
            }
            if (m.rules.beaversAllowed) {
                FilledTonalButton(onClick = { vm.respond(true, beaver = true) }, modifier = pairMod, contentPadding = pad) {
                    Text("Beaver ${g.cube.value * 4}", style = label, maxLines = 1)
                }
            }
        }
        // The cube on the board shows the value at stake.
        g.phase == Phase.BEAVERED && g.turn == Player.HUMAN -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.answerBeaver(false) }, modifier = pairMod, contentPadding = pad) { Text("Play on", style = label, maxLines = 1) }
            FilledTonalButton(onClick = { vm.answerBeaver(true) }, modifier = pairMod, contentPadding = pad) {
                Text("Raccoon", style = label, maxLines = 1)
            }
        }
        else -> Unit
    }
}

/**
 * Double and Pass: with [confirm] the first tap turns the button into [question] and a second tap
 * within 3 seconds acts; meanwhile a small "Don't ask again" line under it turns the question off
 * (the second tap is still needed).
 */
@Composable
private fun AskTwice(
    confirm: Boolean, label: String, question: String, onAct: () -> Unit, onStopAsking: () -> Unit,
    button: @Composable (text: String, onClick: () -> Unit) -> Unit,
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3000)
            armed = false
        }
    }
    // The button keeps its size and place; the "Don't ask again" line hangs below it, centred.
    androidx.compose.ui.layout.Layout(
        content = {
            button(if (armed) question else label) {
                if (confirm && !armed) armed = true else {
                    armed = false
                    onAct()
                }
            }
            if (armed && confirm) {
                Row(
                    Modifier
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
                        .toggleable(false, role = Role.Checkbox) { onStopAsking() }
                        .padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = false, onCheckedChange = null, modifier = Modifier.size(28.dp).padding(4.dp))
                    Text("Don't ask again", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                }
            }
        },
    ) { measurables, constraints ->
        val b = measurables[0].measure(constraints)
        val line = measurables.getOrNull(1)?.measure(androidx.compose.ui.unit.Constraints())
        layout(b.width, b.height) {
            b.place(0, 0)
            line?.place((b.width - line.width) / 2, b.height + 2.dp.roundToPx())
        }
    }
}

/**
 * Height of the on-board buttons on a board [w] × [h]: 40 dp, or less (down to 24 dp) to fit
 * between two five-checker stacks, as on a portrait board.
 */
private fun buttonHeight(w: Dp, h: Dp): Dp {
    val geo = BoardGeometry(w.value, h.value)
    val gap = geo.surfaceBottom - geo.surfaceTop - 20 * geo.radius
    return (gap - 4f).dp.coerceIn(24.dp, 40.dp)
}

/** The on-board buttons' label: smaller, without letter spacing, on a narrow (portrait) board. */
@Composable
private fun buttonLabel(pointW: Dp): TextStyle =
    if (pointW < 36.dp) MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp) else MaterialTheme.typography.labelLarge

/** The computer doubles the human: Pass shows in the computer's half, the offered cube beside it. */
private fun passShown(st: GameUiState, m: MatchState): Boolean =
    !st.busy && st.preview == null && !hasPanel(st) && !st.autoFinishing && m.game.phase == Phase.DOUBLED && m.game.responder == Player.HUMAN



/** The cube to draw: while a double (or beaver) is offered, the value before the offer (the board shows it doubled). */
private fun offeredCube(g: GameState): CubeState = g.offeredCube?.let { CubeState(it / 2, g.cube.owner) } ?: g.cube

/** x of the bar's centre for a board [w] wide (as in [BoardGeometry]; [mirrored]: tray on the left). */
private fun barCenterXOf(w: Dp, mirrored: Boolean): Dp {
    val margin = w * 0.018f
    val bar = w * 0.065f
    val tray = w * 0.075f
    val pointW = (w - tray - margin * 2 - bar) / 12
    return (if (mirrored) tray else 0.dp) + margin + pointW * 6 + bar / 2
}

/**
 * Top of the board signs ([w] × [h] px, centred on the bar): just below the centre line if
 * that is free, otherwise the nearest free spot (below preferred) that covers neither the
 * on-board buttons ([buttons], [buttonHalf] high on each side of the centre) nor checkers on
 * the points under the sign, bar checkers, dice or the cube. If nothing is free: the spot
 * that covers least, the buttons counting most.
 */
private fun signTop(
    g: BoardGeometry, board: Board, dice: List<DiceView>, cube: CubeState, cubeOffered: Boolean,
    buttons: Boolean, w: Float, h: Float, buttonHalf: Float, gap: Float,
): Float {
    val x0 = g.barCenterX - w / 2
    val x1 = g.barCenterX + w / 2
    // (top, bottom, weight): what the sign should not cover, by importance
    val blocked = ArrayList<Triple<Float, Float, Float>>()
    fun block(left: Float, right: Float, top: Float, bottom: Float, weight: Float = 1f) {
        if (right > x0 && left < x1) blocked += Triple(top, bottom, weight)
    }
    val mid = g.height / 2
    val r = g.radius
    for (i in 0 until 24) {
        val n = board.count(Player.HUMAN, i) + board.count(Player.CPU, 23 - i)
        if (n == 0) continue
        val left = g.columnLeft(g.column(i))
        val extent = minOf(n, 5) * 2 * r
        if (g.isTop(i)) block(left, left + g.pointW, g.surfaceTop, g.surfaceTop + extent)
        else block(left, left + g.pointW, g.surfaceBottom - extent, g.surfaceBottom)
    }
    val humanBar = minOf(board.bar(Player.HUMAN), 4)
    val cpuBar = minOf(board.bar(Player.CPU), 4)
    if (humanBar > 0) block(g.barCenterX - r, g.barCenterX + r, mid - 1.2f * r - humanBar * 2 * r, mid - 1.2f * r)
    if (cpuBar > 0) block(g.barCenterX - r, g.barCenterX + r, mid + 1.2f * r, mid + 1.2f * r + cpuBar * 2 * r)
    for (d in dice) for (rect in g.diceRects(d.owner)) block(rect.left, rect.right, rect.top, rect.bottom)
    val s = g.barW * 0.86f
    val cubeY = when {
        cubeOffered -> mid - s / 2
        cube.owner == Player.HUMAN -> g.height - g.vMargin - s
        cube.owner == Player.CPU -> g.vMargin
        else -> mid - s / 2
    }
    val cubeX = if (cubeOffered) g.columnLeft(3) - s / 2 else g.barCenterX - s / 2
    block(cubeX, cubeX + s, cubeY, cubeY + s)
    if (buttons) block(g.columnLeft(6), g.playRight, mid - buttonHalf, mid + buttonHalf, weight = 4f)

    // The free spot nearest to just below the centre (below preferred); if there is none, the
    // spot that covers least (the buttons count most).
    val preferred = mid + gap
    fun cost(y: Float): Float {
        var covered = 0f
        for ((t, b, w) in blocked) covered += w * (minOf(y + h, b + gap) - maxOf(y, t - gap)).coerceAtLeast(0f)
        val distance = kotlin.math.abs(y - preferred) * if (y < preferred) 1.3f else 1f
        return covered * 1000f + distance
    }
    var best = preferred
    var bestCost = Float.MAX_VALUE
    var y = g.surfaceTop
    while (y + h <= g.surfaceBottom) {
        val c = cost(y)
        if (c < bestCost) { bestCost = c; best = y }
        y += 2f
    }
    return best
}

/**
 * A button-shaped sign on the board while the practice prompt is about one of the human's cube
 * decisions (yellow for an error, red for a blunder), as the prompt below the board is easy to
 * miss. Play goes on as usual; a tap shows the cube analysis.
 */
@Composable
private fun CubeMistakeSign(st: GameUiState, m: MatchState) {
    val fb = st.feedback ?: return
    val d = fb.decision
    val eval = d.cubeEval ?: return
    if (d.checker != null || st.preview != null || st.autoFinishing) return
    // "Try again": the sign names the mistake but keeps the answer back.
    val hidden = st.retry?.revealed == false
    var open by remember(fb) { mutableStateOf(false) }
    val (container, content) = when (fb.severity) {
        Severity.BLUNDER -> ArrowColors.blunder to Color.White
        Severity.ERROR -> CUBE_ERROR_YELLOW to Color.Black
        else -> ArrowColors.inaccuracy to Color.Black
    }
    Button(
        onClick = { if (!hidden) open = true },
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        shape = RoundedCornerShape(SIGN_CORNER),
        contentPadding = SIGN_PADDING,
        modifier = Modifier.defaultMinSize(minHeight = 1.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
    ) {
        SignText("Cube ${fb.severity.label.lowercase()}", if (hidden) null else eq(-fb.weightedError))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("You: ${describeAction(d.kind, d.board, Player.HUMAN, d.move)}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SeverityBadge(fb.severity, fb.weightedError)
                    Spacer(Modifier.height(8.dp))
                    CubeTable(eval, d.kind, XgRules.errorFactor(m.rules.length), d.beavers)
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}

/** Yellow of the cube-error sign (the orange arrow colour reads as red at a glance). */
private val CUBE_ERROR_YELLOW = Color(0xFFFFC928)
private val NO_MOVE_GREY = Color(0xFFE6E9EC)
private val FORCED_BLUE = Color(0xFF9CCBFF)
private val SIGN_CORNER = 10.dp
private val SIGN_PADDING = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)

/** Two lines of a board sign: a bold title and a detail line (equity lost, the move, the roll). */
@Composable
private fun SignText(title: String, detail: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * "You can't move" / "Computer can't move" / "Forced move" in the middle of the board for a
 * moment, styled like the cube sign. Not clickable: taps go through to the board.
 */
@Composable
private fun BoardNoticeSign(st: GameUiState) {
    val n = st.notice ?: return
    if (st.preview != null || st.autoFinishing) return
    val container = if (n.kind == BoardNotice.Kind.FORCED) FORCED_BLUE else NO_MOVE_GREY
    Box(
        Modifier
            .shadow(3.dp, RoundedCornerShape(SIGN_CORNER))
            .background(container, RoundedCornerShape(SIGN_CORNER))
            .padding(SIGN_PADDING),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.Black) { SignText(n.title, n.detail) }
    }
}

@Composable
private fun Panels(st: GameUiState, m: MatchState, vm: GameViewModel) {
    val g = m.game
    // Practice-mode prompt has priority over everything else.
    st.feedback?.let { FeedbackCard(st, m, vm); return }

    st.hint?.let { h ->
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hint (not counted in statistics)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::closeHint) { Text("Close") }
                }
                CandidateList(
                    board = g.board, player = Player.HUMAN, analysis = h, factor = XgRules.errorFactor(m.rules.length),
                    highlightIndex = null, selectedIndex = st.previewCandidate, detailed = true,
                    moverColor = boardColorsFor(st.settings).human, opponentColor = boardColorsFor(st.settings).cpu,
                    onSelect = { i, c -> vm.preview(g.board, arrowsFor(Player.HUMAN, c.move, ArrowColors.best), i) },
                )
                if (st.preview != null) TextButton(onClick = { vm.preview(null) }) { Text("Clear arrows") }
            }
        }
    }
    st.cubeHint?.let { c ->
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cube hint (not counted)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::closeHint) { Text("Close") }
                }
                val kind = when (g.phase) {
                    Phase.DOUBLED -> DecisionKind.TAKE
                    Phase.BEAVERED -> DecisionKind.RACCOON
                    else -> null
                }
                CubeTable(c, kind, XgRules.errorFactor(m.rules.length), m.rules.beaversAllowed)
            }
        }
    }
}

/** Off-board controls: undo / done / hint (and cube actions as a fallback). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(st: GameUiState, m: MatchState, vm: GameViewModel, vertical: Boolean, onResign: () -> Unit) {
    val g = m.game
    val humanTurn = g.turn == Player.HUMAN
    if (st.feedback != null || st.autoFinishing) return
    // The computer's turn (but not its double, which the human answers here).
    if (st.busy || (!humanTurn && g.phase != Phase.DOUBLED)) {
        QueuedResign(st, vm, onResign)
        return
    }
    val moving = g.phase == Phase.MOVING && humanTurn && !st.noLegalMove
    val canHint = (g.phase == Phase.MOVING && humanTurn && !st.noLegalMove) ||
        (g.phase == Phase.PRE_ROLL && humanTurn && g.mayDouble(m.rules, m.score)) ||
        (g.phase == Phase.DOUBLED && g.responder == Player.HUMAN) ||
        (g.phase == Phase.BEAVERED && humanTurn)
    val content: @Composable () -> Unit = {
        if (moving) {
            OutlinedButton(onClick = vm::undo, enabled = st.steps.isNotEmpty(), modifier = if (vertical) Modifier.fillMaxWidth() else Modifier) {
                Icon(Icons.AutoMirrored.Filled.Undo, null)
                Spacer(Modifier.width(4.dp))
                Text("Undo")
            }
            Button(onClick = vm::confirmMove, enabled = st.moveComplete, modifier = if (vertical) Modifier.fillMaxWidth() else Modifier) { Text("Done") }
        }
        if (canHint) {
            TextButton(onClick = vm::hint, modifier = if (vertical) Modifier.fillMaxWidth() else Modifier) {
                Icon(Icons.Default.Lightbulb, null)
                Spacer(Modifier.width(4.dp))
                Text("Hint")
            }
        }
        if (st.canResign) {
            if (vertical) {
                TextButton(onClick = onResign, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Flag, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Resign")
                }
            } else {
                IconButton(onClick = onResign) { Icon(Icons.Default.Flag, "Resign", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        // "Keep rolling": shown where the human could double, and while it is on (to switch it off).
        if (st.keepRolling || (g.phase == Phase.PRE_ROLL && humanTurn && g.mayDouble(m.rules, m.score))) {
            FilterChip(selected = st.keepRolling, onClick = vm::toggleKeepRolling, label = { Text("Keep rolling") })
        }
        if (st.canAutoFinish && humanTurn) {
            TextButton(onClick = vm::startAutoFinish, modifier = if (vertical) Modifier.fillMaxWidth() else Modifier) {
                Icon(Icons.Default.FastForward, null)
                Spacer(Modifier.width(4.dp))
                Text("Autofinish")
            }
        }
    }
    if (vertical) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    } else {
        // Wraps onto a second line when the buttons don't fit (large font or display size).
        FlowRow(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
    if (moving && st.steps.isEmpty()) {
        Text(
            "Tap a checker to move it by the left die (or drag it). Tap the dice to swap them, and to finish your move.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** While the human cannot resign right away: resign at the next turn, or withdraw that. */
@Composable
private fun QueuedResign(st: GameUiState, vm: GameViewModel, onResign: () -> Unit) {
    when {
        st.queuedResign != null -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Flag, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text("Resigning at your turn", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::cancelQueuedResign) { Text("Cancel") }
        }
        st.canQueueResign -> TextButton(onClick = onResign) {
            Icon(Icons.Default.Flag, null)
            Spacer(Modifier.width(4.dp))
            Text("Resign at my turn")
        }
    }
}

/**
 * Winning chances as XG shows them: one bar, from the left your backgammons, gammons and single
 * wins, then the computer's single wins, gammons and backgammons to the right edge. The numbers
 * show with [MeterMode.BAR_AND_NUMBERS], or in [MeterMode.BAR] after a tap until your next decision.
 */
@Composable
private fun Meter(st: GameUiState, vm: GameViewModel, twoLines: Boolean) {
    val mode = st.settings.meter
    val p = st.meter ?: return
    if (mode == MeterMode.OFF) return
    val colors = boardColorsFor(st.settings)
    val win = p[0]
    val lose = 1 - p[0]
    val mine = listOf(p[2] to 1f, p[1] - p[2] to 0.75f, win - p[1] to 0.45f)
    val theirs = listOf(lose - p[3] to 0.45f, p[3] - p[4] to 0.75f, p[4] to 1f)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp)
            .then(if (mode == MeterMode.BAR) Modifier.clickable(onClick = vm::toggleMeterNumbers) else Modifier),
    ) {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            for ((w, a) in mine) if (w > 0.0005f) Box(Modifier.weight(w).fillMaxHeight().background(colors.human.copy(alpha = a)))
            Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface))
            for ((w, a) in theirs) if (w > 0.0005f) Box(Modifier.weight(w).fillMaxHeight().background(colors.cpu.copy(alpha = a)))
        }
        if (mode == MeterMode.BAR_AND_NUMBERS || st.meterNumbers) {
            val you = "You ${pct(win)} (G ${pct(p[1])}, B ${pct(p[2])})"
            val cpu = "Computer ${pct(lose)} (G ${pct(p[3])}, B ${pct(p[4])})"
            val style = MaterialTheme.typography.labelSmall
            if (twoLines) {
                Text(you, style = style, modifier = Modifier.padding(top = 2.dp))
                Text(cpu, style = style)
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Text(you, style = style, modifier = Modifier.weight(1f))
                    Text(cpu, style = style, textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun FeedbackCard(st: GameUiState, m: MatchState, vm: GameViewModel) {
    val fb = st.feedback ?: return
    if (st.retry?.revealed == false) {
        TryAgainCard(fb, vm)
        return
    }
    val d = fb.decision
    val factor = XgRules.errorFactor(m.rules.length)
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeverityBadge(fb.severity, fb.weightedError)
                Spacer(Modifier.weight(1f))
                Button(onClick = vm::dismissFeedback) { Text("Continue") }
            }
            Text(
                "You played: ${describeAction(d.kind, d.board, Player.HUMAN, d.move)}",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(6.dp))
            when {
                d.checker != null -> {
                    val a = d.checker
                    val colors = boardColorsFor(st.settings)
                    val played = a.candidates.getOrNull(a.playedIndex)
                    val best = a.candidates.firstOrNull()
                    fun showBoth() = vm.preview(
                        d.board,
                        (best?.let { arrowsFor(Player.HUMAN, it.move, ArrowColors.best) } ?: emptyList()) +
                            (played?.let { arrowsFor(Player.HUMAN, it.move, ArrowColors.forSeverity(fb.severity)) } ?: emptyList()),
                        -1,
                    )
                    if (played != null && best != null) {
                        PlayedBestBar(
                            playedText = MoveNotation.format(d.board, Player.HUMAN, played.move),
                            playedError = fb.weightedError, playedSeverity = fb.severity,
                            bestText = MoveNotation.format(d.board, Player.HUMAN, best.move), bestEquity = best.equity,
                            onPlayed = { vm.preview(d.board, arrowsFor(Player.HUMAN, played.move, ArrowColors.forSeverity(fb.severity)), a.playedIndex) },
                            onBest = { vm.preview(d.board, arrowsFor(Player.HUMAN, best.move, ArrowColors.best), 0) },
                            stacked = true,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    CandidateList(
                        board = d.board, player = Player.HUMAN, analysis = a, factor = factor,
                        highlightIndex = a.playedIndex, maxRows = 5, selectedIndex = st.previewCandidate,
                        detailed = true, moverColor = colors.human, opponentColor = colors.cpu,
                        onSelect = { i, c ->
                            val sev = XgRules.severity((a.bestEquity - c.equity) * factor)
                            vm.preview(d.board, arrowsFor(Player.HUMAN, c.move, ArrowColors.forSeverity(sev)), i)
                        },
                    )
                    OutlinedButton(onClick = { showBoth() }) { Text("Played vs best") }
                }
                d.cubeEval != null -> {
                    CubeTable(d.cubeEval, d.kind, factor, d.beavers)
                }
                else -> Unit
            }
        }
    }
}

/** "Try again" training: names the mistake (not its size or the answer) and offers another try. */
@Composable
private fun TryAgainCard(fb: Feedback, vm: GameViewModel) {
    val d = fb.decision
    val what = when (fb.severity) {
        Severity.BLUNDER -> "a blunder"
        Severity.ERROR -> "an error"
        else -> "not the best play"
    }
    val (container, content) = when (fb.severity) {
        Severity.BLUNDER -> ArrowColors.blunder to Color.White
        Severity.ERROR -> CUBE_ERROR_YELLOW to Color.Black
        else -> ArrowColors.inaccuracy to Color.Black
    }
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                fb.severity.label,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = content,
                modifier = Modifier.background(container, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
            )
            Text(
                "${describeAction(d.kind, d.board, Player.HUMAN, d.move)} was $what. Try the decision again: your first try is what counts.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = vm::tryAgain) { Text("Try again") }
                OutlinedButton(onClick = vm::showAnswer) { Text("Show answer") }
                TextButton(onClick = vm::dismissFeedback) { Text("Continue") }
            }
        }
    }
}

/**
 * End-of-game summary: result, score and both players' PR (with XG's rating), errors and
 * blunders, luck (with gnubg's luck rating), for this game or for the whole match / session
 * so far (a switch at the top; the match is shown first once it is over). The body scrolls,
 * so nothing is cut off in landscape or with a large font; the buttons always stay visible.
 */
@Composable
private fun GameOverDialog(
    info: GameOverInfo, inStats: Boolean, gameSummary: Boolean, analysisLeft: Int, vm: GameViewModel, onReview: (Long) -> Unit,
    onReviewMistakes: (matchId: Long, gameNumber: Int) -> Unit, onBack: () -> Unit, onEndSession: () -> Unit,
) {
    val won = info.result.winner == Player.HUMAN
    val kind = when (info.result.type) {
        WinType.SINGLE -> if (info.result.byPass) "by a pass" else "a single game"
        WinType.GAMMON -> "a gammon"
        WinType.BACKGAMMON -> "a backgammon"
    }
    val title = when {
        info.matchOver -> if (info.matchWinner == Player.HUMAN) "You win the match!" else "Computer wins the match"
        won -> "You win $kind"
        else -> "Computer wins $kind"
    }
    val report = info.match?.takeIf { it.games > 1 }
    val matchName = if (info.isMoney) "Session" else "Match"
    var showMatch by remember(info) { mutableStateOf(info.matchOver && report != null) }
    // The match's saved games, for stepping through their summaries; this game is shown from [info].
    var saved by remember(info) { mutableStateOf<List<Pair<GameEntity, GameSummary>>>(emptyList()) }
    LaunchedEffect(info) { if (report != null) saved = vm.app.repository.gameSummaries(info.matchId) }
    var shown by remember(info) { mutableStateOf(info.gameNumber) }
    // At the end of a match with the summary after each game turned off: a box to turn it back on.
    val offerSummary = remember(info) { info.matchOver && !gameSummary }
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(12.dp).widthIn(max = 640.dp).fillMaxWidth(),
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp)) {
                val scroll = rememberScrollState()
                Column(
                    Modifier.weight(1f, fill = false)
                        .scrollIndicator(scroll, MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.surfaceContainerHigh)
                        .verticalScroll(scroll)
                        .padding(end = 6.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        (if (info.matchOver) "Last game: ${if (won) "you" else "computer"}" else if (won) "You" else "Computer") +
                            " +${info.result.points} point${if (info.result.points > 1) "s" else ""}" +
                            (if (info.result.resigned) "  (${if (won) "computer" else "you"} resigned)" else "") +
                            " · " + (if (info.isMoney) "session" else "score") + ": you ${info.score[0]}, computer ${info.score[1]}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    if (report != null) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            SegmentedButton(
                                selected = !showMatch, onClick = { showMatch = false },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                            ) { Text("Game $shown", maxLines = 1) }
                            SegmentedButton(
                                selected = showMatch, onClick = { showMatch = true },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                            ) { Text("$matchName (${report.games} games)", maxLines = 1) }
                        }
                    }
                    val index = saved.indexOfFirst { it.first.number == shown }
                    if (report != null && !showMatch && saved.size > 1 && index >= 0) {
                        GameStepper(shown, index, saved.size) { shown = saved[it].first.number }
                    }
                    if (analysisLeft > 0) {
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "$analysisLeft decision${if (analysisLeft > 1) "s" else ""} still to analyze. " +
                                    if (info.matchOver) "PR and mistakes fill in when done." else "You can play on; they fill in when done.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (info.offerAnalysis && analysisLeft == 0) {
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Played without analysis (Settings → Training).",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(onClick = { vm.analyzeMatchNow(info.matchId) }) { Text("Analyze now") }
                        }
                    }
                    if (info.afterMatch && analysisLeft == 0 && !info.offerAnalysis) {
                        Text(
                            when (info.mistakes) {
                                0 -> "No errors or blunders this match. Well played!"
                                1 -> "You made 1 error or blunder this match."
                                else -> "You made ${info.mistakes} errors and blunders this match."
                            },
                            style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    if (report != null && showMatch) {
                        SummaryRow(
                            if (info.matchLength > 0) "${info.matchLength}-point match" else "Money session",
                            "You", "Computer", header = true,
                        )
                        SummaryRow("Games won", report.gamesWon[0].toString(), report.gamesWon[1].toString(), strong = true)
                        PerformanceRows(report.human, report.cpu)
                    } else if (shown != info.gameNumber && index >= 0) {
                        SavedGameRows(saved[index].first, saved[index].second)
                    } else {
                        SummaryRow(if (report != null) "Game ${info.gameNumber}" else "This game", "You", "Computer", header = true)
                        PerformanceRows(info.gamePerformance, info.cpuPerformance)
                    }
                    val before = info.match?.eloBefore
                    val after = info.match?.eloAfter
                    if (info.matchOver && before != null && after != null) {
                        val change = after - before
                        Text(
                            "Your FIBS rating: ${fmt(before, 1)} → ${fmt(after, 1)} (${if (change >= 0) "+" else "−"}${fmt(kotlin.math.abs(change), 1)})",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (!inStats) {
                        Text(
                            "This ${if (info.isMoney) "session" else "match"} is not counted in your statistics.",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Text(
                        "Luck: each player's rolls compared with the average roll in the same position, so both " +
                            "players can be lucky; the net luck shows who was luckier.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                    )
                    if (!info.matchOver) {
                        Row(
                            Modifier.fillMaxWidth().toggleable(!gameSummary, role = Role.Checkbox) { vm.setGameSummary(!it) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = !gameSummary, onCheckedChange = null)
                            Text(
                                "Don't show this after each game (Settings → After each game)",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    } else if (offerSummary) {
                        Row(
                            Modifier.fillMaxWidth().toggleable(gameSummary, role = Role.Checkbox) { vm.setGameSummary(it) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = gameSummary, onCheckedChange = null)
                            Text(
                                "Show the summary after each game",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.End) {
                    if (info.afterMatch && (info.mistakes > 0 || analysisLeft > 0)) {
                        TextButton(onClick = { onReviewMistakes(info.matchId, info.reviewGame) }, enabled = analysisLeft == 0) { Text("Review my mistakes") }
                    } else {
                        TextButton(onClick = { onReview(info.matchId) }) { Text("Review") }
                    }
                    if (info.isMoney && !info.matchOver) {
                        TextButton(onClick = onEndSession) { Text("End session") }
                    }
                    if (info.matchOver) {
                        TextButton(onClick = { vm.endSession(); onBack() }) { Text("Done") }
                    } else {
                        TextButton(onClick = vm::nextGame) { Text("Next game") }
                    }
                }
            }
        }
    }
}

/** PR, rating, luck and error counts of both players. */
@Composable
internal fun PerformanceRows(you: PerformanceTotals, cpu: PerformanceTotals) {
    SummaryRow("PR", you.pr?.let { fmt(it) } ?: "–", cpu.pr?.let { fmt(it) } ?: "–", strong = true)
    SummaryRow("Rating", you.pr?.let { XgRules.ratingName(it) } ?: "–", cpu.pr?.let { XgRules.ratingName(it) } ?: "–")
    SummaryRow("Luck", eq(you.luck), eq(cpu.luck), strong = true)
    SummaryRow("Luck rating", XgRules.luckRating(you.luck, you.rolls) ?: "–", XgRules.luckRating(cpu.luck, cpu.rolls) ?: "–")
    // the difference, shown on the side of the luckier player
    val net = you.luck - cpu.luck
    val even = kotlin.math.abs(net) < 0.0005
    SummaryRow(
        "Net luck (luckier side)",
        if (even) "even" else if (net > 0) eq(net) else "",
        if (even) "even" else if (net < 0) eq(-net) else "",
        strong = true,
    )
    SummaryRow("Checker errors / blunders", "${you.checkerErrors} / ${you.checkerBlunders}", "${cpu.checkerErrors} / ${cpu.checkerBlunders}")
    SummaryRow(
        "Cube errors / blunders",
        "${you.doubleErrors + you.takeErrors} / ${you.doubleBlunders + you.takeBlunders}",
        "${cpu.doubleErrors + cpu.takeErrors} / ${cpu.doubleBlunders + cpu.takeBlunders}",
    )
}

/** A row of the game summary: label, the human's value, the computer's value. */
@Composable
internal fun SummaryRow(label: String, you: String, cpu: String, header: Boolean = false, strong: Boolean = false) {
    val labelStyle = if (header) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
    val valueStyle = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
    val weight = if (header || strong) FontWeight.SemiBold else FontWeight.Normal
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, Modifier.weight(1.6f), style = labelStyle,
            color = if (header) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(you, Modifier.weight(1f).padding(start = 8.dp), style = valueStyle, fontWeight = weight, textAlign = TextAlign.End)
        Text(cpu, Modifier.weight(1f).padding(start = 8.dp), style = valueStyle, fontWeight = weight, textAlign = TextAlign.End)
    }
}
