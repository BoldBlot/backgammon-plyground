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
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.Labels
import com.boldblot.backgammonplyground.core.QuizFilter
import com.boldblot.backgammonplyground.data.CategoryCount
import com.boldblot.backgammonplyground.data.PositionsRepository.Companion.UNCATEGORIZED
import com.boldblot.backgammonplyground.data.SavedPosition
import com.boldblot.backgammonplyground.data.Settings
import com.boldblot.backgammonplyground.ui.components.LabelFilterButton

/**
 * What a quiz asks: the positions of [folders] (empty = all positions; [UNCATEGORIZED] for those
 * in none) that pass [filter], at most [limit] of them (0 = all), in [order].
 */
data class QuizSpec(
    val folders: List<Long> = emptyList(),
    val filter: Set<String> = emptySet(),
    val limit: Int = 0,
    val order: QuizOrder = QuizOrder.RANDOM,
) {
    /** For the quiz route (and the saved quick quiz). */
    fun encode(): String = listOf(
        folders.joinToString(","), filter.joinToString("\n"), limit.toString(), order.code,
    ).joinToString(";") { Uri.encode(it) }

    /** The positions [all] (already those of [folders]) that the quiz asks, before [limit] and order. */
    fun pick(all: List<SavedPosition>, s: Settings): List<SavedPosition> {
        return all.filter { passesFilter(it, filter, s) }
    }

    companion object {
        fun decode(text: String): QuizSpec {
            val p = text.split(';').map { Uri.decode(it) }
            return QuizSpec(
                folders = p.getOrNull(0).orEmpty().split(',').mapNotNull { it.toLongOrNull() },
                filter = p.getOrNull(1).orEmpty().split('\n').filter { it.isNotBlank() }.toSet(),
                limit = p.getOrNull(2)?.toIntOrNull() ?: 0,
                order = QuizOrder.entries.firstOrNull { it.code == p.getOrNull(3) } ?: QuizOrder.RANDOM,
            )
        }

        /** Quick Quiz until one is saved: all positions, 10 of them, in random order. */
        val QUICK_DEFAULT = QuizSpec(limit = 10)

        /** The saved quick quiz ([Settings.quickQuiz]), or [QUICK_DEFAULT]. */
        fun quick(s: Settings): QuizSpec = s.quickQuiz.takeIf { it.isNotBlank() }?.let(::decode) ?: QUICK_DEFAULT
    }
}

/** The order of a quiz's questions; [code] is stored in the quiz route and the saved quick quiz. */
enum class QuizOrder(val label: String, val code: String) {
    RANDOM("Random", "random"), NEWEST("Newest first", "new"), WORST("Worst blunders", "worst");

    /** [ps] in this order. Worst blunders: the biggest error in the game first; positions without one last, in random order. */
    fun sort(ps: List<SavedPosition>): List<SavedPosition> = when (this) {
        RANDOM -> ps.shuffled()
        NEWEST -> ps.sortedByDescending { it.entity.savedAt }
        WORST -> {
            val err = ps.associate { sp -> sp.id to sp.played?.let { answerError(sp.position, sp.analysis, it) }?.takeIf { it > 0f } }
            val (known, unknown) = ps.partition { err[it.id] != null }
            known.sortedByDescending { err[it.id] } + unknown.shuffled()
        }
    }
}

/** The positions of [folders] (empty = all of them). */
suspend fun quizPositions(app: PlyGroundApp, folders: List<Long>): List<SavedPosition> =
    if (folders.isEmpty()) app.positions.positions(null) else app.positions.positionsIn(folders)

/** The quiz menu: folders, a label filter, how many questions and in what order. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuizMenuScreen(app: PlyGroundApp, preselected: List<Long>, onBack: () -> Unit, preFilter: Set<String> = emptySet(), onStart: (QuizSpec) -> Unit) {
    val settings by app.settings.settings.collectAsState(initial = null)
    var categories by remember { mutableStateOf<List<CategoryCount>?>(null) }
    var uncategorized by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var folders by rememberSaveable { mutableStateOf(preselected.toSet()) }
    var filter by rememberSaveable { mutableStateOf(preFilter) }
    var limit by rememberSaveable { mutableIntStateOf(10) }
    var order by rememberSaveable { mutableStateOf(QuizOrder.RANDOM) }
    var savedNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var matching by remember { mutableStateOf<Int?>(null) }
    // The positions of the ticked folders, before the filter.
    var pool by remember { mutableStateOf<List<SavedPosition>>(emptyList()) }
    LaunchedEffect(Unit) {
        categories = app.positions.categories()
        uncategorized = app.positions.uncategorizedCount()
        total = app.positions.positions(null).size
    }
    val s = settings
    LaunchedEffect(folders) { pool = quizPositions(app, folders.toList()) }
    LaunchedEffect(pool, filter, s) {
        if (s != null) matching = QuizSpec(folders.toList(), filter).pick(pool, s).size
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Title("Quiz") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    Button(
                        onClick = { onStart(QuizSpec(folders.toList(), filter, limit, order)) },
                        enabled = (matching ?: 0) > 0, modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Start") }
                },
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
                "Only the positions with these labels and quiz answers: within a group any of them, across groups all of them.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LabelFilterButton(
                s.labelKeys, filter, { filter = it }, Modifier.padding(top = 4.dp), quiz = true,
                count = { f -> pool.count { passesFilter(it, f, s) } }, total = pool.size, ids = true,
            )
            if (s.visible(filter).isNotEmpty()) Text(Labels.folderName(s.visible(filter)), style = MaterialTheme.typography.bodySmall)
            QuizFilter.text(filter).takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(12.dp))
            Text("Questions", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(10 to "10", 20 to "20", 30 to "30", 0 to "All").forEach { (n, label) ->
                    FilterChip(selected = limit == n, onClick = { limit = n }, label = { Text(label) })
                }
            }
            Text("Order", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuizOrder.entries.forEach { o ->
                    FilterChip(selected = order == o, onClick = { order = o }, label = { Text(o.label) })
                }
            }
            Text("Quick quiz", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                "The Quick quiz button on the main screen starts the quiz saved here (until you save one: all positions, 10, random).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = {
                    val spec = QuizSpec(folders.toList(), filter, limit, order)
                    scope.launch { app.settings.update { it.copy(quickQuiz = spec.encode()) } }
                    savedNote = "Saved as the quick quiz."
                }, label = { Text("Save as quick quiz") })
                AssistChip(onClick = {
                    val q = QuizSpec.quick(s)
                    // Folders deleted since it was saved are left out.
                    val known = cats.map { it.id }.toSet() + UNCATEGORIZED
                    folders = q.folders.filter { it in known }.toSet(); filter = q.filter; limit = q.limit; order = q.order
                    savedNote = if (s.quickQuiz.isBlank()) "No quick quiz saved yet: the default is loaded." else "Quick quiz loaded."
                }, label = { Text("Load quick quiz") })
            }
            savedNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(12.dp))
            val n = matching
            val asked = if (n == null) null else if (limit > 0) minOf(limit, n) else n
            Text(
                when {
                    n == null -> ""
                    n == 0 -> "No positions match."
                    asked != n -> "$n positions match; the quiz asks $asked."
                    else -> if (n == 1) "1 position matches." else "$n positions match."
                },
                style = MaterialTheme.typography.bodyMedium,
            )

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
