package com.boldblot.backgammonplyground.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Undo and Done as small chips (beside the board in landscape, under it in portrait), in negative colours
 * (bright with dark text in dark mode, dark with light text in light mode) so they stand apart from the
 * other chips. Both always show, greyed out until a checker has moved (Undo) or the move is complete (Done).
 */
@Composable
fun MoveChips(canUndo: Boolean, canDone: Boolean, onUndo: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val colors = negativeChipColors()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AssistChip(
            onClick = onUndo, enabled = canUndo, label = { Text("Undo", maxLines = 1) }, colors = colors, border = null,
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(AssistChipDefaults.IconSize)) },
        )
        AssistChip(
            onClick = onDone, enabled = canDone, label = { Text("Done", maxLines = 1) }, colors = colors, border = null,
            leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(AssistChipDefaults.IconSize)) },
        )
    }
}

/** The mark for a position: on, the player's next decision is marked for review. */
@Composable
fun MarkChip(on: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.FilterChip(
        selected = on, onClick = onClick, enabled = enabled, modifier = modifier,
        label = { Icon(BlotIcon, if (on) "Marked" else "Mark this position", Modifier.size(AssistChipDefaults.IconSize)) },
    )
}

/** A blot, the mark on a position: a round splash with two drops beside it. */
val BlotIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("Blot", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathData {
                val lobes = 7
                val cx = 11f
                val cy = 13f
                fun at(angle: Double, r: Float) = (cx + r * kotlin.math.cos(angle)).toFloat() to (cy + r * kotlin.math.sin(angle)).toFloat()
                val step = 2 * Math.PI / lobes
                val (sx, sy) = at(0.0, 5.6f)
                moveTo(sx, sy)
                for (i in 0 until lobes) {
                    val a = i * step
                    val (c1x, c1y) = at(a + step * 0.18, 11.5f)
                    val (c2x, c2y) = at(a + step * 0.82, 11.5f)
                    val (ex, ey) = at(a + step, 5.6f)
                    curveTo(c1x, c1y, c2x, c2y, ex, ey)
                }
                close()
                moveTo(21.4f, 5f)
                arcToRelative(1.4f, 1.4f, 0f, true, true, -2.8f, 0f)
                arcToRelative(1.4f, 1.4f, 0f, true, true, 2.8f, 0f)
                close()
                moveTo(22.6f, 20.5f)
                arcToRelative(1f, 1f, 0f, true, true, -2f, 0f)
                arcToRelative(1f, 1f, 0f, true, true, 2f, 0f)
                close()
            },
            fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
        )
    }.build()
}

/** Chip colours in negative (as Undo and Done): dark with light text in light mode, bright with dark text in dark mode. */
@Composable
fun negativeChipColors(): androidx.compose.material3.ChipColors {
    val scheme = MaterialTheme.colorScheme
    return AssistChipDefaults.assistChipColors(
        containerColor = scheme.inverseSurface, labelColor = scheme.inverseOnSurface, leadingIconContentColor = scheme.inverseOnSurface,
        disabledContainerColor = scheme.inverseSurface.copy(alpha = 0.25f),
        disabledLabelColor = scheme.onSurface.copy(alpha = 0.45f), disabledLeadingIconContentColor = scheme.onSurface.copy(alpha = 0.45f),
    )
}
