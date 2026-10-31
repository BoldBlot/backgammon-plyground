package com.boldblot.backgammonplyground.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boldblot.backgammonplyground.core.Labels
import com.boldblot.backgammonplyground.core.QuizFilter

/**
 * The Filter button of the blunder list, My positions and the quiz: a menu of [keys] (label keys
 * in use) by group, ticked when in [selected]. Within a group the ticks mean "any of them",
 * across groups "all of them".
 */
@Composable
fun LabelFilterButton(
    keys: List<String>, selected: Set<String>, onChange: (Set<String>) -> Unit, modifier: Modifier = Modifier,
    /** An icon (in a top bar) instead of a chip. */
    compact: Boolean = false,
    /** Also the quiz answers ([QuizFilter]), for saved positions. */
    quiz: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val quizKeys = if (quiz) QuizFilter.entries.map { it.key } else emptyList()
    val on = selected.intersect((keys + quizKeys).toSet())
    Box(modifier) {
        if (compact) {
            IconButton(onClick = { open = true }) {
                Icon(
                    Icons.Default.FilterList, if (on.isEmpty()) "Filter" else "Filter (${on.size} on)",
                    tint = if (on.isEmpty()) LocalContentColor.current else MaterialTheme.colorScheme.primary,
                )
            }
        } else FilterChip(
            selected = on.isNotEmpty(), onClick = { open = true },
            label = { Text(if (on.isEmpty()) "Filter" else "Filter (${on.size})") },
            leadingIcon = { Icon(Icons.Default.FilterList, null) },
        )
        DropdownMenu(open, onDismissRequest = { open = false }) {
            if (on.isNotEmpty()) {
                DropdownMenuItem(text = { Text("Clear filter") }, onClick = { onChange(emptySet()); open = false })
                HorizontalDivider()
            }
            keys.groupBy { Labels.group(it) }.forEach { (group, inGroup) ->
                Text(
                    group.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
                )
                inGroup.forEach { k ->
                    DropdownMenuItem(
                        text = { Text(Labels.title(k)) },
                        leadingIcon = { Checkbox(checked = k in on, onCheckedChange = null) },
                        onClick = { onChange(if (k in on) on - k else on + k) },
                    )
                }
            }
            if (quiz) QuizFilter.entries.groupBy { it.group }.forEach { (group, inGroup) ->
                Text(
                    group.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
                )
                inGroup.forEach { f ->
                    DropdownMenuItem(
                        text = { Text(f.title) },
                        leadingIcon = { Checkbox(checked = f.key in on, onCheckedChange = null) },
                        onClick = { onChange(if (f.key in on) on - f.key else on + f.key) },
                    )
                }
            }
        }
    }
}

/** A position's labels as small tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelTags(labels: Collection<String>, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    if (labels.isEmpty()) return
    FlowRow(
        modifier.let { if (onClick != null) it.clickable(onClick = onClick) else it },
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Labels.sorted(labels).forEach { k ->
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    Labels.title(k), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Ticks a position's labels: [keys] are the labels in use, [initial] the position's. */
@Composable
fun LabelsDialog(keys: List<String>, initial: Set<String>, onDismiss: () -> Unit, onDone: (Set<String>) -> Unit) {
    var chosen by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Labels") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                keys.groupBy { Labels.group(it) }.forEach { (group, inGroup) ->
                    Text(
                        group.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    inGroup.forEach { k ->
                        Row(
                            Modifier.fillMaxWidth().clickable { chosen = if (k in chosen) chosen - k else chosen + k },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = k in chosen, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                            Text(Labels.title(k))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(chosen) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
