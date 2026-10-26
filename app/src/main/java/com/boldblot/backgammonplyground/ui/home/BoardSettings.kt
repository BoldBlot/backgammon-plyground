package com.boldblot.backgammonplyground.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.data.BoardTheme
import com.boldblot.backgammonplyground.data.CUSTOM_SLOTS
import com.boldblot.backgammonplyground.data.CheckerSide
import com.boldblot.backgammonplyground.data.CheckerStyle
import com.boldblot.backgammonplyground.data.CustomBoard
import com.boldblot.backgammonplyground.data.Settings
import com.boldblot.backgammonplyground.ui.board.BoardPalette
import com.boldblot.backgammonplyground.ui.board.BoardPreview
import com.boldblot.backgammonplyground.ui.board.BoardThemes
import com.boldblot.backgammonplyground.ui.board.BoardView
import com.boldblot.backgammonplyground.ui.board.DiceView
import com.boldblot.backgammonplyground.ui.board.Swatches
import com.boldblot.backgammonplyground.ui.board.argb

/** Settings → Board: the boards with previews, checker colour and style, bear-off side, the designer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoardSettings(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    var designer by rememberSaveable { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoardTheme.entries.filter { it != BoardTheme.CUSTOM }.forEach { t ->
            BoardTile(t.label, BoardThemes.palette(t), s, selected = s.boardTheme == t) { update { it.copy(boardTheme = t) } }
        }
        s.customBoards.forEachIndexed { i, b ->
            if (b != null) BoardTile(b.name, BoardThemes.palette(b), s, selected = s.boardTheme == BoardTheme.CUSTOM && s.customSlot == i) {
                update { it.copy(boardTheme = BoardTheme.CUSTOM, customSlot = i) }
            }
        }
    }
    val p = BoardThemes.palette(s)
    Text("My checkers", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CheckerSide.entries.forEach { side ->
            FilterChip(
                selected = s.checkerSide == side, onClick = { update { it.copy(checkerSide = side) } },
                label = { Text(if (side == CheckerSide.FIRST) p.firstName else p.secondName) },
                leadingIcon = { Dot(if (side == CheckerSide.FIRST) p.first else p.second) },
            )
        }
    }
    Text("Checker style", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CheckerStyle.entries.forEach { st ->
            FilterChip(selected = s.checkerStyle == st, onClick = { update { it.copy(checkerStyle = st) } }, label = { Text(st.label) })
        }
    }
    Text("Bear off direction", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = s.mirrorBoard, onClick = { update { it.copy(mirrorBoard = true) } }, label = { Text("Left") })
        FilterChip(selected = !s.mirrorBoard, onClick = { update { it.copy(mirrorBoard = false) } }, label = { Text("Right") })
    }
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { designer = true }) {
            Icon(Icons.Default.Palette, null)
            Spacer(Modifier.width(6.dp))
            Text("Board designer")
        }
        Text(
            "Experimental", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
    if (designer) BoardDesigner(s, update) { designer = false }
}

@Composable
private fun BoardTile(name: String, p: BoardPalette, s: Settings, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier.width(88.dp).clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoardPreview(p.colors(s.checkerSide, s.checkerStyle), Modifier.fillMaxWidth().aspectRatio(1.15f).clip(RoundedCornerShape(4.dp)))
        Text(
            name, style = MaterialTheme.typography.labelMedium, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun Dot(c: Color, size: Int = 16, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).let { m -> if (onClick != null) m.clickable(onClick = onClick) else m }
            .border(
                if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                CircleShape,
            )
            .padding(if (selected) 4.dp else 0.dp).clip(CircleShape),
    ) { Surface(Modifier.fillMaxSize(), color = c) {} }
}

/** One colourable part of a design. */
private class Part(val label: String, val get: (CustomBoard) -> Long, val set: (CustomBoard, Long) -> CustomBoard)

private val parts = listOf(
    Part("Frame and bar", { it.frame }) { b, v -> b.copy(frame = v) },
    Part("Playing surface", { it.surface }) { b, v -> b.copy(surface = v) },
    Part("Light points", { it.pointLight }) { b, v -> b.copy(pointLight = v) },
    Part("Dark points", { it.pointDark }) { b, v -> b.copy(pointDark = v) },
    Part("Your checkers", { it.mine }) { b, v -> b.copy(mine = v) },
    Part("Opponent's checkers", { it.theirs }) { b, v -> b.copy(theirs = v) },
    Part("Point numbers", { it.numbers }) { b, v -> b.copy(numbers = v) },
    Part("Move highlight", { it.highlight }) { b, v -> b.copy(highlight = v) },
)

/**
 * The board designer (experimental): a colour for each part of the board from [Swatches], saved in
 * one of [CUSTOM_SLOTS] slots; a saved design shows up in the board list. A new design starts from
 * the board in use.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardDesigner(s: Settings, update: ((Settings) -> Settings) -> Unit, onDismiss: () -> Unit) {
    fun startFor(slot: Int) = s.customBoards[slot] ?: BoardThemes.toCustom(BoardThemes.palette(s), "My board ${slot + 1}")
    var slot by remember { mutableIntStateOf(if (s.boardTheme == BoardTheme.CUSTOM) s.customSlot else s.customBoards.indexOfFirst { it == null }.coerceAtLeast(0)) }
    var draft by remember { mutableStateOf(startFor(slot)) }
    var part by remember { mutableIntStateOf(0) }
    val colors = BoardThemes.palette(draft).colors(CheckerSide.FIRST, s.checkerStyle)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
                val wide = maxWidth > maxHeight
                val preview: @Composable (Modifier) -> Unit = { m ->
                    BoardView(
                        Board.INITIAL, m.aspectRatio(1.38f, matchHeightConstraintsFirst = wide), colors = colors, mirrored = s.mirrorBoard,
                        dice = listOf(DiceView(0, listOf(6, 5))),
                    )
                }
                val controls: @Composable (Modifier) -> Unit = { m ->
                    Column(m.verticalScroll(rememberScrollState())) {
                        Text("Save slot", style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (i in 0 until CUSTOM_SLOTS) {
                                FilterChip(
                                    selected = slot == i, onClick = { slot = i; draft = startFor(i) },
                                    label = { Text(s.customBoards[i]?.name ?: "Slot ${i + 1} (empty)", maxLines = 1) },
                                )
                            }
                        }
                        OutlinedTextField(
                            draft.name, { draft = draft.copy(name = it.take(24)) }, label = { Text("Name") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        )
                        parts.forEachIndexed { i, pt ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { part = i }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Dot(argb(pt.get(draft)), size = 24)
                                Text(
                                    pt.label + (Swatches.nameOf(pt.get(draft))?.let { " · $it" } ?: ""),
                                    Modifier.padding(start = 10.dp).weight(1f),
                                    color = if (part == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (part == i) {
                                FlowRow(
                                    Modifier.padding(start = 4.dp, bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Swatches.all.forEach { (_, v) ->
                                        Dot(argb(v), size = 34, selected = pt.get(draft) == v) { draft = pt.set(draft, v) }
                                    }
                                }
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Board designer", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Experimental", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                    }
                    if (wide) {
                        Row(Modifier.weight(1f).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.weight(1.2f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) { preview(Modifier.fillMaxHeight()) }
                            controls(Modifier.weight(1f))
                        }
                    } else {
                        preview(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        controls(Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        if (s.customBoards[slot] != null) {
                            TextButton(onClick = {
                                val deleted = slot
                                update { st ->
                                    val inUse = st.boardTheme == BoardTheme.CUSTOM && st.customSlot == deleted
                                    st.copy(
                                        customBoards = st.customBoards.toMutableList().also { it[deleted] = null },
                                        boardTheme = if (inUse) BoardTheme.GRAY else st.boardTheme,
                                    )
                                }
                                draft = BoardThemes.toCustom(BoardThemes.palette(s), "My board ${deleted + 1}")
                            }) { Text("Delete") }
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val saved = draft.copy(name = draft.name.trim().ifEmpty { "My board ${slot + 1}" })
                            val i = slot
                            update { st ->
                                st.copy(
                                    customBoards = st.customBoards.toMutableList().also { it[i] = saved },
                                    boardTheme = BoardTheme.CUSTOM, customSlot = i,
                                )
                            }
                            onDismiss()
                        }) { Text("Save and use") }
                    }
                }
            }
        }
    }
}
