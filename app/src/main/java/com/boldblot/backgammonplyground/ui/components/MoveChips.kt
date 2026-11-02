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
    val scheme = MaterialTheme.colorScheme
    val colors = AssistChipDefaults.assistChipColors(
        containerColor = scheme.inverseSurface, labelColor = scheme.inverseOnSurface, leadingIconContentColor = scheme.inverseOnSurface,
        disabledContainerColor = scheme.inverseSurface.copy(alpha = 0.25f),
        disabledLabelColor = scheme.onSurface.copy(alpha = 0.45f), disabledLeadingIconContentColor = scheme.onSurface.copy(alpha = 0.45f),
    )
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
