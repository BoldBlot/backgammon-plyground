package com.boldblot.backgammonplyground.ui.positions

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
    var list by remember { mutableStateOf<List<SavedPosition>?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(refresh) { list = app.positions.learned() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Title("Learned" + (list?.let { " (${it.size})" } ?: "")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { menu = true }, enabled = !list.isNullOrEmpty()) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Put all back") }, onClick = {
                            menu = false
                            scope.launch { app.positions.unlearn(list.orEmpty().map { it.id }); refresh++ }
                        })
                        DropdownMenuItem(text = { Text("Delete all") }, onClick = { menu = false; confirmClear = true })
                    }
                },
            )
        },
    ) { padding ->
        val l = list
        val s = settings
        if (l == null || s == null) {
            CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Text(
                    "Positions you learned in the quiz (Settings → My positions → Quiz → Move to Learned). They are out of the quiz " +
                        "and the folders; put one back to have it asked again. The newest ${s.learnedKeep} are kept, older ones are deleted.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                if (l.isEmpty()) Text("Nothing learned yet.", Modifier.padding(horizontal = 16.dp))
            }
            items(l, key = { it.id }) { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { PositionRow(p, s) { onOpen(p.id) } }
                    IconButton(onClick = { scope.launch { app.positions.unlearn(listOf(p.id)); refresh++ } }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "Put back in My positions")
                    }
                    IconButton(onClick = { scope.launch { app.positions.remove(p.id); refresh++ } }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Delete all learned positions?") },
        text = { Text("They are deleted for good.") },
        confirmButton = {
            TextButton(onClick = {
                confirmClear = false
                scope.launch { list.orEmpty().forEach { app.positions.remove(it.id) }; refresh++ }
            }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}
