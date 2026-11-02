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
 * Landscape Undo and Done as small chips in the side column, in negative colours (bright with dark
 * text in dark mode, dark with light text in light mode) so they stand apart from the other chips.
 * Undo shows once a checker has moved, Done once the move is complete.
 */
@Composable
fun MoveChips(canUndo: Boolean, canDone: Boolean, onUndo: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = AssistChipDefaults.assistChipColors(
        containerColor = scheme.inverseSurface, labelColor = scheme.inverseOnSurface, leadingIconContentColor = scheme.inverseOnSurface,
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (canUndo) AssistChip(
            onClick = onUndo, label = { Text("Undo", maxLines = 1) }, colors = colors, border = null,
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(AssistChipDefaults.IconSize)) },
        )
        if (canDone) AssistChip(
            onClick = onDone, label = { Text("Done", maxLines = 1) }, colors = colors, border = null,
            leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(AssistChipDefaults.IconSize)) },
        )
    }
}
