package com.boldblot.backgammonplyground.ui.positions

import com.boldblot.backgammonplyground.ui.components.LabelFilterButton

import androidx.compose.material.icons.automirrored.filled.HelpOutline

import androidx.compose.runtime.saveable.rememberSaveable

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.data.SavedPosition
import kotlinx.coroutines.launch

/**
 * Learned (from My positions): the positions the quiz moved out once their balance reached the threshold.
 * They are out of the quiz and the folders, like a bin: put one back and it returns to its folders; the
 * newest [com.boldblot.backgammonplyground.data.Settings.learnedKeep] are kept, older ones are deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnedScreen(app: PlyGroundApp, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsState(initial = null)
    var refresh by remember { mutableIntStateOf(0) }
    var all by remember { mutableStateOf<List<SavedPosition>?>(null) }
    // Positions to delete, waiting for the confirmation.
    var deleteIds by remember { mutableStateOf<List<Long>?>(null) }
    // Positions to put back, waiting for the answer about their counters.
    var putBack by remember { mutableStateOf<List<Long>?>(null) }
    var selected by remember { mutableStateOf<Set<Long>?>(null) }
    var help by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    BackHandler(enabled = selected != null) { selected = null }
    LaunchedEffect(refresh) { all = app.positions.learned() }
    val s = settings
    val filter = s?.learnedFilter.orEmpty()
    // The list as filtered (Settings.learnedFilter) and sorted (Settings.learnedSort), as in the folders.
    val list = if (s == null) null else all?.filter { passesFilter(it, filter, s) }?.let { l ->
        s.learnedSort.sort(
            l, error = { sp -> sp.played?.let { answerError(sp.position, sp.analysis, it) } }, balance = { it.quizBalance },
            answered = { it.entity.quizRight + it.entity.quizWrong + it.entity.quizNeutral > 0 },
        )
    }
    FolderScaffold(
        title = "Learned" + (list?.let { " (${it.size})" } ?: ""), onBack = onBack, railRight = s?.railRight ?: false, snackbar = snackbar,
        topActions = { IconButton(onClick = { help = !help }) { Icon(Icons.AutoMirrored.Filled.HelpOutline, "Help") } },
        options = if (s == null || all.isNullOrEmpty()) null else {
            {
                SortChip(com.boldblot.backgammonplyground.data.PositionsSort.entries, s.learnedSort, { it.label }) { o ->
                    scope.launch { app.settings.update { it.copy(learnedSort = o) } }
                }
                LabelFilterButton(
                    s.labelKeys, filter, { f -> scope.launch { app.settings.update { it.copy(learnedFilter = f) } } }, quiz = true,
                    count = { f -> all.orEmpty().count { passesFilter(it, f, s) } }, total = all.orEmpty().size, ids = true,
                )
            }
        },
        actions = selected?.let { sel ->
            {
                val shown = list.orEmpty().map { it.id }.toSet()
                val allOn = sel.containsAll(shown) && shown.isNotEmpty()
                SelectionActions(sel.size, allOn, onAll = { selected = if (allOn) emptySet() else shown }, onDone = { selected = null }) {
                    FolderAction("Put back", Icons.AutoMirrored.Filled.Undo, enabled = sel.isNotEmpty()) { putBack = sel.toList() }
                    FolderAction("Delete", Icons.Default.Delete, enabled = sel.isNotEmpty()) { deleteIds = sel.toList() }
                }
            }
        } ?: if (list.isNullOrEmpty()) null else {
            {
                // What the list shows: everything, or what passes the filter.
                FolderAction(if (filter.isEmpty()) "Put all back" else "Put ${list.size} back", Icons.AutoMirrored.Filled.Undo) { putBack = list.map { it.id } }
                FolderAction(if (filter.isEmpty()) "Delete all" else "Delete ${list.size}", Icons.Default.Delete) { deleteIds = list.map { it.id } }
            }
        },
    ) { area ->
        val l = list
        when {
            l == null || s == null -> ListLoading(area)
            else -> LazyColumn(area) {
                if (help) item {
                    Text(
                        "Positions you learned in the quiz (Settings → Quiz → Move to Learned). They are out of the quiz " +
                            "and the folders; put one back to have it asked again. The newest ${s.learnedKeep} are kept, older ones are deleted.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                if (l.isEmpty()) item { EmptyListText("Nothing learned yet.", filtered = !all.isNullOrEmpty()) }
                items(l, key = { it.id }) { p ->
                    val sel = selected
                    fun toggle() { selected = sel?.let { if (p.id in it) it - p.id else it + p.id } }
                    PositionRow(
                        p, s, selection = sel?.let { p.id in it },
                        onLongClick = { if (sel == null) selected = setOf(p.id) else toggle() },
                        actions = {
                            RowAction(Icons.AutoMirrored.Filled.Undo, "Put back in My positions") { putBack = listOf(p.id) }
                            RowAction(Icons.Default.Delete, "Delete") {
                                scope.launch {
                                    val d = app.positions.removeForUndo(p.id)
                                    refresh++
                                    if (d != null) offerUndo(scope, snackbar, "Position deleted.") { app.positions.restore(d); refresh++ }
                                }
                            }
                        },
                    ) {
                        if (sel != null) toggle() else { BrowseList.ids = l.map { it.id }; onOpen(p.id) }
                    }
                }
            }
        }
    }
    putBack?.let { ids ->
        PutBackDialog(ids.size, onDismiss = { putBack = null }) { reset ->
            putBack = null
            selected = null
            scope.launch { app.positions.unlearn(ids, reset); refresh++ }
        }
    }
    deleteIds?.let { ids ->
        val n = ids.size
        AlertDialog(
            onDismissRequest = { deleteIds = null },
            title = { Text("Delete $n learned position${if (n == 1) "" else "s"}?") },
            text = { Text("They are deleted for good.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteIds = null
                    selected = null
                    scope.launch { ids.forEach { app.positions.remove(it) }; refresh++ }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteIds = null }) { Text("Cancel") } },
        )
    }
}

/**
 * Asked when positions leave Learned: whether their quiz counters (right, missed, balance) start again from zero,
 * or stay as they were (a position already one answer from Learned again would go back at once).
 */
@Composable
fun PutBackDialog(count: Int, onDismiss: () -> Unit, onChoose: (resetCounters: Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Put back in My positions?" else "Put $count positions back in My positions?") },
        text = {
            Text(
                "Reset the counters of right and missed answers and the balance? Reset: ${if (count == 1) "it starts" else "they start"} " +
                    "again from zero. Keep: ${if (count == 1) "it keeps" else "they keep"} the counts ${if (count == 1) "it" else "they"} had.",
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onChoose(true) }) { Text("Reset counters") }
                TextButton(onClick = { onChoose(false) }) { Text("Keep counters") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
