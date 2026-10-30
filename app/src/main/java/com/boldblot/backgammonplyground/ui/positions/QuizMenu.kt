package com.boldblot.backgammonplyground.ui.positions

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.Labels
import com.boldblot.backgammonplyground.data.CategoryCount
import com.boldblot.backgammonplyground.data.PositionsRepository.Companion.UNCATEGORIZED
import com.boldblot.backgammonplyground.data.SavedPosition
import com.boldblot.backgammonplyground.data.Settings
import com.boldblot.backgammonplyground.ui.components.LabelFilterButton

/**
 * What a quiz asks: the positions of [folders] (empty = all positions; [UNCATEGORIZED] for those
 * in none) that pass [filter], at most [limit] of them (0 = all), newest first or in random order.
 */
data class QuizSpec(
    val folders: List<Long> = emptyList(),
    val filter: Set<String> = emptySet(),
    val limit: Int = 0,
    val newestFirst: Boolean = false,
) {
    /** For the quiz route. */
    fun encode(): String = listOf(
        folders.joinToString(","), filter.joinToString("\n"), limit.toString(), if (newestFirst) "new" else "random",
    ).joinToString(";") { Uri.encode(it) }

    /** The positions [all] (already those of [folders]) that the quiz asks, before [limit] and order. */
    fun pick(all: List<SavedPosition>, s: Settings): List<SavedPosition> {
        val f = s.visible(filter)
        return all.filter { f.isEmpty() || Labels.matches(s.visible(it.labels), f) }
    }

    companion object {
        fun decode(text: String): QuizSpec {
            val p = text.split(';').map { Uri.decode(it) }
            return QuizSpec(
                folders = p.getOrNull(0).orEmpty().split(',').mapNotNull { it.toLongOrNull() },
                filter = p.getOrNull(1).orEmpty().split('\n').filter { it.isNotBlank() }.toSet(),
                limit = p.getOrNull(2)?.toIntOrNull() ?: 0,
                newestFirst = p.getOrNull(3) == "new",
            )
        }
    }
}

/** The positions of [folders] (empty = all of them). */
suspend fun quizPositions(app: PlyGroundApp, folders: List<Long>): List<SavedPosition> =
    if (folders.isEmpty()) app.positions.positions(null) else app.positions.positionsIn(folders)

/** The quiz menu: folders, a label filter, how many questions and in what order. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuizMenuScreen(app: PlyGroundApp, preselected: List<Long>, onBack: () -> Unit, onStart: (QuizSpec) -> Unit) {
    val settings by app.settings.settings.collectAsState(initial = null)
    var categories by remember { mutableStateOf<List<CategoryCount>?>(null) }
    var uncategorized by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var folders by rememberSaveable { mutableStateOf(preselected.toSet()) }
    var filter by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var limit by rememberSaveable { mutableIntStateOf(0) }
    var newestFirst by rememberSaveable { mutableStateOf(false) }
    var matching by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        categories = app.positions.categories()
        uncategorized = app.positions.uncategorizedCount()
        total = app.positions.positions(null).size
    }
    val s = settings
    LaunchedEffect(folders, filter, s) {
        if (s != null) matching = QuizSpec(folders.toList(), filter).pick(quizPositions(app, folders.toList()), s).size
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Title("Quiz") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        val cats = categories
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (cats == null || s == null) {
                CircularProgressIndicator()
                return@Column
            }
            Text("Folders", style = MaterialTheme.typography.titleMedium)
            FolderRow("All positions", total, folders.isEmpty()) { folders = emptySet() }
            cats.filter { it.count > 0 }.forEach { c ->
                FolderRow(c.name, c.count, c.id in folders) { folders = if (c.id in folders) folders - c.id else folders + c.id }
            }
            if (uncategorized > 0) {
                FolderRow("No folder", uncategorized, UNCATEGORIZED in folders) {
                    folders = if (UNCATEGORIZED in folders) folders - UNCATEGORIZED else folders + UNCATEGORIZED
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Labels", style = MaterialTheme.typography.titleMedium)
            Text(
                "Only the positions with these labels: within a group any of them, across groups all of them.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LabelFilterButton(s.labelKeys, filter, { filter = it }, Modifier.padding(top = 4.dp))
            if (filter.isNotEmpty()) Text(Labels.folderName(s.visible(filter)), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Text("Questions", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to "All", 10 to "10", 20 to "20").forEach { (n, label) ->
                    FilterChip(selected = limit == n, onClick = { limit = n }, label = { Text(label) })
                }
            }
            Text("Order", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !newestFirst, onClick = { newestFirst = false }, label = { Text("Random") })
                FilterChip(selected = newestFirst, onClick = { newestFirst = true }, label = { Text("Newest first") })
            }
            Spacer(Modifier.height(12.dp))
            val n = matching
            val asked = if (n == null) null else if (limit > 0) minOf(limit, n) else n
            Text(
                when {
                    n == null -> ""
                    n == 0 -> "No positions match."
                    asked != n -> "$n positions match; the quiz asks $asked."
                    else -> "$n position${if (n == 1) "" else "s"} match."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { onStart(QuizSpec(folders.toList(), filter, limit, newestFirst)) },
                enabled = (n ?: 0) > 0, modifier = Modifier.padding(top = 8.dp),
            ) { Text("Start the quiz") }
        }
    }
}

@Composable
private fun FolderRow(name: String, count: Int, checked: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(8.dp))
        Text("$name ($count)")
    }
}
