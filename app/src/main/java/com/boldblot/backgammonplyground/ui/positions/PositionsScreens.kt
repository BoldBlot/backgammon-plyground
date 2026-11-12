package com.boldblot.backgammonplyground.ui.positions

import com.boldblot.backgammonplyground.engine.Waiting
import androidx.compose.material.icons.filled.Refresh
import com.boldblot.backgammonplyground.ui.review.ReanalyzeDialog
import com.boldblot.backgammonplyground.ui.review.ReanalyzeRequest
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.boldblot.backgammonplyground.core.XgRules
import com.boldblot.backgammonplyground.core.UNKNOWN_ENGINE
import com.boldblot.backgammonplyground.ui.position.PositionIdImportDialog
import com.boldblot.backgammonplyground.ui.components.LocalErrorUnit
import com.boldblot.backgammonplyground.ui.components.errWithUnit
import com.boldblot.backgammonplyground.ui.review.BoardCapture
import com.boldblot.backgammonplyground.ui.review.ReviewView
import com.boldblot.backgammonplyground.ui.review.ReviewViewChips
import com.boldblot.backgammonplyground.ui.review.SharePositionItems
import com.boldblot.backgammonplyground.ui.review.rememberBoardCapture
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.automirrored.filled.Label
import com.boldblot.backgammonplyground.core.Labels
import com.boldblot.backgammonplyground.core.Move
import com.boldblot.backgammonplyground.ui.components.LabelFilterButton
import com.boldblot.backgammonplyground.ui.components.LabelTags
import com.boldblot.backgammonplyground.ui.components.scrollbar
import com.boldblot.backgammonplyground.ui.components.LabelsDialog
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.DecisionKind
import com.boldblot.backgammonplyground.core.MoveGenerator
import com.boldblot.backgammonplyground.core.MoveNotation
import com.boldblot.backgammonplyground.core.MyAnswer
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.Severity
import com.boldblot.backgammonplyground.core.StudyPosition
import com.boldblot.backgammonplyground.data.CategoryCount
import com.boldblot.backgammonplyground.data.ErrorsMode
import com.boldblot.backgammonplyground.data.ImportPreview
import com.boldblot.backgammonplyground.data.PositionsRepository.Companion.UNCATEGORIZED
import com.boldblot.backgammonplyground.data.SavedPosition
import com.boldblot.backgammonplyground.data.Settings
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.ui.board.ArrowColors
import com.boldblot.backgammonplyground.ui.board.BoardView
import com.boldblot.backgammonplyground.ui.board.DiceView
import com.boldblot.backgammonplyground.ui.board.arrowsFor
import com.boldblot.backgammonplyground.ui.board.boardColorsFor
import com.boldblot.backgammonplyground.ui.components.CandidateList
import com.boldblot.backgammonplyground.ui.components.CubeTable
import com.boldblot.backgammonplyground.ui.components.SeverityBadge
import com.boldblot.backgammonplyground.ui.components.eq
import com.boldblot.backgammonplyground.ui.components.fmt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/** A My positions screen title (one line). */
@Composable
internal fun Title(text: String) {
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** What My positions can import (⋮ → Import a file…, or a file opened or shared with PlyGround). */
private const val IMPORT_FORMATS =
    "Import (⋮ → Import a file…) reads a Backgammon PlyGround positions file (.plyground.json, made with ⋮ → Export…), " +
        "or a text file with one position per line: an XGID (XGID=…) or a GNU Backgammon Position ID, with its Match ID " +
        "if it has one (4HPwATDgc/ABMA:cAkAAAAAAAAA). Lines that are not positions are skipped."

// ---- My positions: categories and quiz ------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MyPositionsScreen(
    app: PlyGroundApp, onBack: () -> Unit, onCategory: (Long?) -> Unit, onQuiz: (List<Long>) -> Unit, onNewErrors: () -> Unit, onNewMarked: () -> Unit,
    onLearned: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var categories by remember { mutableStateOf<List<CategoryCount>?>(null) }
    var uncategorized by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<CategoryCount?>(null) }
    var deleting by remember { mutableStateOf<CategoryCount?>(null) }
    var analyzingFolder by remember { mutableStateOf<CategoryCount?>(null) }
    val folderRun by FolderAnalysis.progress.collectAsState()
    val folderSettings by app.settings.settings.collectAsState(initial = Settings())
    var exporting by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<ImportPreview?>(null) }
    var importingId by remember { mutableStateOf(false) }
    var savingImported by remember { mutableStateOf<StudyPosition?>(null) }
    LaunchedEffect(refresh) {
        categories = app.positions.categories()
        uncategorized = app.positions.uncategorizedCount()
        total = app.positions.positions(null).size
    }
    // A file opened with PlyGround (from another app) or picked with Import.
    val pending by app.pendingImport.collectAsState()
    LaunchedEffect(pending) {
        val text = pending ?: return@LaunchedEffect
        app.pendingImport.value = null
        runCatching { app.positions.preview(text) }
            .onSuccess { importing = it }
            .onFailure { Toast.makeText(context, (it.message ?: "Could not read that file.") + " " + IMPORT_FORMATS, Toast.LENGTH_LONG).show() }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            app.pendingImport.value = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }.getOrNull()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Title("My positions") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { help = !help }) { Icon(Icons.AutoMirrored.Filled.HelpOutline, "Help") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("New folder") }, onClick = { menu = false; newCategory = true })
                            DropdownMenuItem(text = { Text("Import XGID…") }, onClick = { menu = false; importingId = true })
                            DropdownMenuItem(text = { Text("Import a file…") }, onClick = { menu = false; pick.launch(arrayOf("*/*")) })
                            DropdownMenuItem(text = { Text("Export…") }, enabled = total > 0, onClick = { menu = false; exporting = true })
                        }
                    }
                },
            )
        },
    ) { padding ->
        val cats = categories
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (cats == null) {
                CircularProgressIndicator()
                return@Column
            }
            if (help) {
                Text(
                    "Save positions from New errors, a game " +
                        "review or Analyze Position (\"Save to My positions\"), or import a file, then quiz yourself on them.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    IMPORT_FORMATS,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            val newErrors by produceState(0, refresh) { value = app.repository.blunderCount() }
            val newMarked by produceState(0, refresh) { value = app.repository.markedCount() }
            val learned by produceState(0, refresh) { value = app.positions.learned().size }
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onQuiz(emptyList()) }, enabled = total > 0) {
                    Icon(Icons.Default.School, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Quiz…")
                }
                // The errors from games, waiting to be added to a folder or deleted. With the automatic
                // methods: the errors they left out (the newest ones), to add by hand.
                val manual by produceState(true) { app.settings.settings.collect { value = it.errorsMode == ErrorsMode.MANUAL } }
                FilledTonalButton(onClick = onNewErrors) {
                    Icon(Icons.Default.Inbox, null)
                    Spacer(Modifier.width(6.dp))
                    Text("New errors ($newErrors)")
                }
                // The positions marked while playing, waiting to be added to a folder or deleted.
                if (newMarked > 0) FilledTonalButton(onClick = onNewMarked) {
                    Icon(com.boldblot.backgammonplyground.ui.components.BlotIcon, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Marked ($newMarked)", maxLines = 1, softWrap = false)
                }
                // The quiz's learned positions, a bin they can be put back from.
                if (learned > 0) OutlinedButton(onClick = onLearned) {
                    Icon(Icons.Default.TaskAlt, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Learned ($learned)", maxLines = 1, softWrap = false)
                }
            }
            // The average of the errors the saved positions were played with (those from games, whose play is known).
            val average by produceState<Float?>(null, refresh, total) { value = libraryAverageError(app) }
            average?.let {
                Text(
                    "Average error of saved positions: ${errWithUnit(it.toDouble(), LocalErrorUnit.current)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            folderRun?.let { r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        "  Analyzing \u201c${r.folder}\u201d again: ${r.done} of ${r.total}",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { FolderAnalysis.stop() }) { Text("Stop") }
                }
            }
            CategoryRow("All positions", total, onClick = { onCategory(null) })
            cats.forEachIndexed { i, c ->
                CategoryRow(c.name, c.count, onClick = { onCategory(c.id) }) { close ->
                    val favourites = c.filter == com.boldblot.backgammonplyground.data.PositionsRepository.FAVOURITES
                    if (!favourites) DropdownMenuItem(text = { Text("Rename") }, onClick = { close(); renaming = c })
                    DropdownMenuItem(
                        text = { Text("Analyze again") }, enabled = folderRun == null && c.count > 0,
                        onClick = { close(); analyzingFolder = c },
                    )
                    if (i > 0) DropdownMenuItem(text = { Text("Move up") }, onClick = { close(); scope.launch { app.positions.moveCategory(c.id, -1); refresh++ } })
                    if (i < cats.lastIndex) DropdownMenuItem(text = { Text("Move down") }, onClick = { close(); scope.launch { app.positions.moveCategory(c.id, 1); refresh++ } })
                    if (!favourites) DropdownMenuItem(text = { Text("Delete") }, onClick = { close(); deleting = c })
                }
            }
            if (uncategorized > 0) CategoryRow("No folder", uncategorized, onClick = { onCategory(UNCATEGORIZED) })
            TextButton(onClick = { newCategory = true }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("New folder")
            }
        }
    }
    analyzingFolder?.let { c ->
        ReanalyzeDialog(folderSettings, gameNumber = null, onDismiss = { analyzingFolder = null }, match = true, onStart = { r ->
            analyzingFolder = null
            FolderAnalysis.start(app, c.id, c.name, r.engine, r.checker, r.cube)
        })
    }
    if (newCategory) {
        NameDialog("New folder", "") { name ->
            newCategory = false
            if (!name.isNullOrBlank()) scope.launch { app.positions.createCategory(name); refresh++ }
        }
    }
    renaming?.let { c ->
        NameDialog("Rename folder", c.name) { name ->
            renaming = null
            if (!name.isNullOrBlank()) scope.launch { app.positions.renameCategory(c.id, name); refresh++ }
        }
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${c.name}\"?") },
            text = { Text("Its positions stay in My positions (in their other folders, or in none).") },
            confirmButton = { TextButton(onClick = { deleting = null; scope.launch { app.positions.deleteCategory(c.id); refresh++ } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    if (exporting) {
        ExportDialog(categories.orEmpty(), uncategorized, onDismiss = { exporting = false }) { ids ->
            exporting = false
            scope.launch {
                val json = app.positions.export(ids)
                val names = categories.orEmpty().filter { it.id in ids }.map { it.name }
                sharePositions(context, if (names.size == 1) names.single() else "My positions", json)
            }
        }
    }
    // An XGID or GNU ID typed or pasted: saved like a position from Analyze Position (analyzed after saving).
    if (importingId) {
        PositionIdImportDialog(onDismiss = { importingId = false }) { text ->
            runCatching { StudyPosition.parse(text) }.fold(
                onSuccess = { savingImported = it; null },
                onFailure = { it.message ?: "Could not read that position." },
            )
        }
    }
    savingImported?.let { SaveToPositionsDialog(app, it, "Imported XGID", onSaved = { refresh++ }) { savingImported = null } }
    importing?.let { preview ->
        ImportDialog(preview, onDismiss = { importing = null }) { cats, other ->
            importing = null
            scope.launch {
                val added = app.positions.import(preview, cats, other)
                Toast.makeText(context, "Imported $added new position${if (added == 1) "" else "s"}.", Toast.LENGTH_LONG).show()
                refresh++
            }
        }
    }
}

@Composable
private fun CategoryRow(name: String, count: Int, onClick: () -> Unit, menu: (@Composable (close: () -> Unit) -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (menu != null) {
            Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "Folder options") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
    HorizontalDivider()
}

/** Asks for a name; null = cancelled. */
@Composable
private fun NameDialog(title: String, initial: String, onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text("OK") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}

@Composable
private fun ExportDialog(categories: List<CategoryCount>, uncategorized: Int, onDismiss: () -> Unit, onExport: (List<Long>) -> Unit) {
    var chosen by remember { mutableStateOf(categories.filter { it.count > 0 }.map { it.id }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export positions") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "One file with the positions as XGIDs, their folders, notes, your answers and the analysis. " +
                        "Share it by Bluetooth, Quick Share, email or any other app; PlyGround imports it.",
                    style = MaterialTheme.typography.bodySmall,
                )
                categories.filter { it.count > 0 }.forEach { c ->
                    CheckRow("${c.name} (${c.count})", c.id in chosen) { chosen = if (c.id in chosen) chosen - c.id else chosen + c.id }
                }
                if (uncategorized > 0) {
                    CheckRow("No folder ($uncategorized)", UNCATEGORIZED in chosen) {
                        chosen = if (UNCATEGORIZED in chosen) chosen - UNCATEGORIZED else chosen + UNCATEGORIZED
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onExport(chosen.toList()) }, enabled = chosen.isNotEmpty()) { Text("Export") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ImportDialog(preview: ImportPreview, onDismiss: () -> Unit, onImport: (Set<String>, String?) -> Unit) {
    var chosen by remember { mutableStateOf(preview.categories.toSet()) }
    var other by remember { mutableStateOf("Imported") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import positions") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "${preview.positions.size} position${if (preview.positions.size == 1) "" else "s"}" +
                        (if (preview.skipped > 0) " (${preview.skipped} line${if (preview.skipped == 1) "" else "s"} could not be read)" else "") +
                        ". Positions you already have are not added again.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (preview.categories.isNotEmpty()) {
                    Text("Folders to import:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    preview.categories.forEach { c ->
                        val n = preview.positions.count { c in it.categories }
                        CheckRow("$c ($n)", c in chosen) { chosen = if (c in chosen) chosen - c else chosen + c }
                    }
                }
                if (preview.uncategorized > 0) {
                    OutlinedTextField(
                        value = other, onValueChange = { other = it.take(40) }, singleLine = true,
                        label = { Text("Folder for the ${preview.uncategorized} without one") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(chosen, other.takeIf { it.isNotBlank() }) }, enabled = preview.positions.isNotEmpty()) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox) { onToggle() }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Opens the share sheet for an exported positions file. */
fun sharePositions(context: Context, name: String, json: String) {
    val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val clean = name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "Positions" }
    val file = File(dir, "$clean.plyground.json").also { it.writeText(json) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Backgammon PlyGround positions: $name")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Export $name"))
}

// ---- a category's positions --------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CategoryPositionsScreen(app: PlyGroundApp, categoryId: Long?, onBack: () -> Unit, onOpen: (Long) -> Unit, onQuiz: (List<Long>, Set<String>) -> Unit) {
    var all by remember { mutableStateOf<List<SavedPosition>?>(null) }
    var filter by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var name by remember { mutableStateOf("") }
    val settings by app.settings.settings.collectAsState(initial = Settings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Selecting positions (a long press starts it): null = not selecting.
    var selected by remember { mutableStateOf<Set<Long>?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    BackHandler(enabled = selected != null) { selected = null }
    // A real folder (not All positions or No folder) can let positions go.
    val folder = categoryId?.takeIf { it != UNCATEGORIZED }
    LaunchedEffect(categoryId, reload) {
        name = when (categoryId) {
            null -> "All positions"
            UNCATEGORIZED -> "No folder"
            else -> app.positions.categories().firstOrNull { it.id == categoryId }?.name ?: ""
        }
        all = app.positions.positions(categoryId)
    }
    var sortMenu by remember { mutableStateOf(false) }
    val list = all?.filter { passesFilter(it, filter, settings) }?.let { l ->
        // Newest is the stored order; Biggest puts the largest error made in the game first, positions without one last (newest first).
        if (settings.positionsSort == com.boldblot.backgammonplyground.data.BlunderSort.BIGGEST) {
            l.sortedByDescending { sp -> sp.played?.let { answerError(sp.position, sp.analysis, it) } ?: -1f }
        } else l
    }
    Scaffold(
        topBar = {
            val sel = selected
            if (sel != null) TopAppBar(
                title = { Text("${sel.size} selected") },
                navigationIcon = { IconButton(onClick = { selected = null }) { Icon(Icons.Default.Close, "Stop selecting") } },
                actions = {
                    val shown = list.orEmpty().map { it.id }.toSet()
                    TextButton(onClick = { selected = if (sel.containsAll(shown) && shown.isNotEmpty()) emptySet() else shown }) {
                        Text(if (sel.containsAll(shown) && shown.isNotEmpty()) "None" else "All")
                    }
                    if (folder != null) {
                        IconButton(onClick = {
                            val ids = sel
                            selected = null
                            scope.launch {
                                app.positions.removeFromFolder(ids, folder)
                                reload++
                                Toast.makeText(context, "Removed ${ids.size} from \u201c$name\u201d.", Toast.LENGTH_SHORT).show()
                            }
                        }, enabled = sel.isNotEmpty()) { Icon(Icons.Default.FolderOff, "Remove from this folder") }
                    }
                    IconButton(onClick = { deleting = true }, enabled = sel.isNotEmpty()) { Icon(Icons.Default.Delete, "Delete from My positions") }
                },
            ) else TopAppBar(
                title = { Title(name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    val shown = all
                    if (!shown.isNullOrEmpty()) Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.SwapVert, "Sort order") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            com.boldblot.backgammonplyground.data.BlunderSort.entries.forEach { o ->
                                DropdownMenuItem(
                                    text = { Text(o.label) },
                                    leadingIcon = { if (settings.positionsSort == o) Icon(Icons.Default.Check, null) },
                                    onClick = { sortMenu = false; scope.launch { app.settings.update { it.copy(positionsSort = o) } } },
                                )
                            }
                        }
                    }
                    if (!shown.isNullOrEmpty()) LabelFilterButton(
                        settings.labelKeys, filter, { filter = it }, compact = true, quiz = true,
                        count = { f -> shown.count { passesFilter(it, f, settings) } }, total = shown.size, ids = true,
                    )
                    if (!shown.isNullOrEmpty()) androidx.compose.material3.AssistChip(
                        onClick = { onQuiz(listOfNotNull(categoryId), filter) }, label = { Text("Quiz") },
                        colors = com.boldblot.backgammonplyground.ui.components.negativeChipColors(), border = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
            )
        },
    ) { padding ->
        val l = list
        when {
            l == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            l.isEmpty() -> Text(if (all.isNullOrEmpty()) "No positions here yet." else "No positions pass the filter.", Modifier.padding(padding).padding(24.dp))
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp)) {
                items(l, key = { it.id }) { p ->
                    val sel = selected
                    fun toggle() { selected = sel?.let { if (p.id in it) it - p.id else it + p.id } }
                    PositionRow(
                        p, settings, selection = sel?.let { p.id in it },
                        onLongClick = { if (sel == null) selected = setOf(p.id) else toggle() },
                    ) { if (sel != null) toggle() else onOpen(p.id) }
                }
            }
        }
    }
    if (deleting) {
        val n = selected?.size ?: 0
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete $n position${if (n == 1) "" else "s"}?") },
            text = { Text("They leave My positions and all their folders. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selected.orEmpty()
                    deleting = false
                    selected = null
                    scope.launch {
                        ids.forEach { app.positions.remove(it) }
                        reload++
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PositionRow(
    p: SavedPosition, settings: Settings,
    /** Selecting: whether this position is selected (null = not selecting). */
    selection: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
    /** In a quiz's results: whether the last answer to it was right (a green check or a red X on the right). */
    quizAnswer: Boolean? = null,
    onClick: () -> Unit,
) {
    val sp = p.position
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(onLongClick = onLongClick, onClick = onClick)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selection != null) Checkbox(checked = selection, onCheckedChange = null, modifier = Modifier.padding(end = 6.dp))
            BoardView(
                board = sp.board, modifier = Modifier.width(120.dp).aspectRatio(1.38f), colors = boardColorsFor(settings),
                mirrored = settings.mirrorBoard, dice = listOfNotNull(sp.dice?.let { DiceView(Player.HUMAN, it.highFirst) }),
                cube = sp.setup.cube, cubeOffered = sp.doubled, showNumbers = false,
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                ContextText(sp, boardColorsFor(settings), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                if (p.entity.note.isNotBlank()) {
                    Text(p.entity.note, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                p.played?.let { Text("Played: ${answerWithError(sp, p.analysis, it, LocalErrorUnit.current)}", style = MaterialTheme.typography.labelSmall) }
                p.answer?.let { Text("My answer: ${answerText(sp, it)}", style = MaterialTheme.typography.labelSmall) }
                LabelTags(settings.visible(p.labels), Modifier.padding(top = 2.dp))
                p.entity.lastRight?.let {
                    Text(
                        "Last quiz: " + (p.lastAnswer?.let { a -> "${answerText(sp, a)} · " } ?: "") + if (it) "right" else "missed",
                        style = MaterialTheme.typography.labelSmall, color = if (it) ArrowColors.best else MaterialTheme.colorScheme.error,
                    )
                }
                if (p.entity.quizRight + p.entity.quizWrong > 0) {
                    Text("Quiz: ${quizCountText(p)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            quizAnswer?.let { right ->
                Icon(
                    if (right) Icons.Default.Check else Icons.Default.Close, if (right) "Right" else "Missed",
                    Modifier.padding(start = 6.dp).size(28.dp), tint = if (right) ArrowColors.best else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ---- one position --------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositionDetailScreen(app: PlyGroundApp, id: Long, onBack: () -> Unit, onSettings: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by app.settings.settings.collectAsState(initial = Settings())
    var refresh by remember { mutableIntStateOf(0) }
    var p by remember { mutableStateOf<SavedPosition?>(null) }
    var selected by remember { mutableStateOf<Int?>(0) }
    var analyzing by remember { mutableStateOf(false) }
    var noting by remember { mutableStateOf(false) }
    var answering by remember { mutableStateOf(false) }
    var settingPlayed by remember { mutableStateOf(false) }
    var filing by remember { mutableStateOf(false) }
    var puttingBack by remember { mutableStateOf(false) }
    var labelling by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var reanalyzing by remember { mutableStateOf(false) }
    val capture = rememberBoardCapture()
    LaunchedEffect(id, refresh) { p = app.positions.position(id) }
    /** Analyzes the position with [r]'s engine and depths, or as in Settings → Analysis without [r]. */
    fun analyze(r: ReanalyzeRequest? = null) {
        val cur = p ?: return
        analyzing = true
        scope.launch {
            val s = app.settings.settings.first()
            runCatching {
                if (r == null) analyzePosition(app, cur.position, s, Waiting.ANALYSIS)
                else analyzePosition(app, cur.position, r.engine, r.checker, r.cube, Waiting.ANALYSIS)
            }
                .onSuccess { app.positions.setAnalysis(cur.id, it); refresh++ }
                .onFailure { Toast.makeText(context, "Analysis failed: ${it.message}", Toast.LENGTH_LONG).show() }
            analyzing = false
        }
    }
    // A position saved without analysis (an import) is analyzed when opened.
    LaunchedEffect(p?.id, p?.analysis == null) { if (p != null && p?.analysis == null && !analyzing) analyze() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Title("Position") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    // Share: the XGID, the board, or the board with the top 5 / the cube actions.
                    Box {
                        IconButton(onClick = { sharing = true }, enabled = p != null) { Icon(Icons.Default.Share, "Share") }
                        DropdownMenu(expanded = sharing, onDismissRequest = { sharing = false }) {
                            p?.let { cur ->
                                SharePositionItems(
                                    cur.position, cur.analysis, answerIndex = null, answerCube = null, capture = capture,
                                    noAnalysis = {
                                        Toast.makeText(context, "Analyzing the position; share again when it is done.", Toast.LENGTH_SHORT).show()
                                        if (!analyzing) analyze()
                                    },
                                ) { sharing = false }
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Analyze again") }, enabled = p != null && !analyzing,
                                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                onClick = { menu = false; reanalyzing = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { menu = false; onSettings() },
                            )
                            DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; removing = true })
                        }
                    }
                },
            )
        },
    ) { padding ->
        val cur = p ?: return@Scaffold
        val sp = cur.position
        val a = cur.analysis
        val colors = boardColorsFor(settings)
        // The selected play's arrows take the colour of its error level, as in the review.
        val arrows = a?.checker?.let { ch ->
            ch.candidates.getOrNull(selected ?: -1)?.let {
                arrowsFor(Player.HUMAN, it.move, ArrowColors.forSeverity(XgRules.severity((ch.bestEquity - it.equity) * factorOf(sp))))
            }
        }.orEmpty()
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val board: @Composable (Modifier) -> Unit = { m ->
                BoardView(
                    board = sp.board, modifier = m.then(capture.modifier), colors = colors, mirrored = settings.mirrorBoard,
                    dice = listOfNotNull(sp.dice?.let { DiceView(Player.HUMAN, it.highFirst) }),
                    cube = sp.setup.cube, cubeOffered = sp.doubled,
                    pips = listOf(sp.board.pipCount(Player.HUMAN), sp.board.pipCount(Player.CPU)), arrows = arrows,
                )
            }
            val details: @Composable () -> Unit = {
                ContextText(sp, colors, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(cur.entity.source.ifBlank { null }, a?.label?.ifBlank { null }).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (cur.entity.note.isNotBlank()) Text(cur.entity.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                // What was played in the game (set by hand when the position came without it).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Played in the game: " + (cur.played?.let { answerWithError(sp, a, it, LocalErrorUnit.current) } ?: "not known"),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { settingPlayed = true }) { Text(if (cur.played == null) "Set" else "Change") }
                }
                cur.answer?.let { Text("★ My answer: ${answerText(sp, it)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)) }
                cur.entity.lastRight?.let { right ->
                    Text(
                        "Last quiz answer: " + (cur.lastAnswer?.let { answerWithError(sp, a, it, LocalErrorUnit.current) + " · " } ?: "") + if (right) "right" else "missed",
                        style = MaterialTheme.typography.bodyMedium, color = if (right) ArrowColors.best else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (cur.entity.quizRight + cur.entity.quizWrong > 0) {
                    Text("Quiz answers: ${quizCountText(cur)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Chips on one line (they wrap to a second row only with a very large font).
                FlowRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = false, onClick = { answering = true }, enabled = a != null,
                        leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) },
                        label = { Text("Set my answer", maxLines = 1, softWrap = false) },
                    )
                    FilterChip(
                        selected = false, onClick = { noting = true },
                        leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)) },
                        label = { Text("Note", maxLines = 1, softWrap = false) },
                    )
                    FilterChip(
                        selected = false, onClick = { filing = true },
                        leadingIcon = { Icon(Icons.Default.Folder, null, Modifier.size(18.dp)) },
                        label = { Text("Folders", maxLines = 1, softWrap = false) },
                    )
                    FilterChip(
                        selected = false, onClick = { labelling = true },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null, Modifier.size(18.dp)) },
                        label = { Text("Labels", maxLines = 1, softWrap = false) },
                    )
                    // The star: in Favourites, which keeps it out of Learned.
                    val favourite by produceState(false, cur.id, refresh) { value = app.positions.isFavourite(cur.id) }
                    FilterChip(
                        selected = favourite,
                        onClick = { scope.launch { app.positions.setFavourite(cur.id, !favourite); refresh++ } },
                        leadingIcon = { Icon(if (favourite) Icons.Default.Star else Icons.Default.StarBorder, null, Modifier.size(18.dp)) },
                        label = { Text("Favourite", maxLines = 1, softWrap = false) },
                    )
                    // Learned: on for the positions in Learned. Turning it on sends the position there (out of the quiz and
                    // the folder counts); turning it off puts it back in My positions, in the folders it was in.
                    val learned = cur.entity.learnedAt != null
                    FilterChip(
                        selected = learned, enabled = !favourite,
                        onClick = {
                            if (learned) puttingBack = true else {
                                scope.launch { app.positions.learn(cur.id, settings.learnedKeep); refresh++ }
                                Toast.makeText(context, "Moved to Learned", Toast.LENGTH_SHORT).show()
                            }
                        },
                        leadingIcon = { Icon(Icons.Default.TaskAlt, null, Modifier.size(18.dp)) },
                        label = { Text("Learned", maxLines = 1, softWrap = false) },
                    )
                }
                LabelTags(settings.visible(cur.labels), Modifier.padding(bottom = 4.dp)) { labelling = true }
                when {
                    analyzing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  Analyzing…")
                    }
                    a?.checker != null -> {
                        if (a.checker.candidates.isEmpty()) Text("No legal move with this roll.")
                        fun indexOf(m: Move) = a.checker.candidates.indexOfFirst { sp.board.applyMove(Player.HUMAN, it.move) == sp.board.applyMove(Player.HUMAN, m) }
                        val mine = cur.answer?.move?.let(::indexOf)
                        val played = cur.played?.move?.let(::indexOf)
                        CandidateList(
                            board = sp.board, player = Player.HUMAN, analysis = a.checker, factor = factorOf(sp),
                            highlightIndex = played?.takeIf { it >= 0 }, maxRows = 10, selectedIndex = selected, detailed = true,
                            moverColor = colors.human, opponentColor = colors.cpu, moverRim = colors.humanRim, opponentRim = colors.cpuRim, onSelect = { i, _ -> selected = i },
                            checkIndex = mine?.takeIf { it >= 0 }, playedLabel = "played",
                        )
                    }
                    a?.cube != null -> CubeTable(a.cube, if (sp.doubled) DecisionKind.TAKE else null, factorOf(sp), sp.beavers, mine = cur.answer?.cube, played = cur.played?.cube, colors = colors, rollerHuman = !sp.doubled)
                }
            }
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize().padding(6.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { details() }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1.3f).fillMaxHeight(), contentAlignment = Alignment.Center) { board(Modifier.aspectRatio(1.38f)) }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                    board(Modifier.fillMaxWidth().aspectRatio(1.38f))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 6.dp)) { details() }
                }
            }
        }
        if (noting) {
            NoteDialog(cur.entity.note) { note ->
                noting = false
                if (note != null) scope.launch { app.positions.setNote(cur.id, note); refresh++ }
            }
        }
        if (answering && a != null) {
            AnswerDialog(sp, a.checker, cur.answer, onDismiss = { answering = false }) { ans ->
                answering = false
                scope.launch { app.positions.setAnswer(cur.id, ans); refresh++ }
            }
        }
        if (settingPlayed) {
            AnswerDialog(
                sp, a?.checker, cur.played, onDismiss = { settingPlayed = false },
                title = "Played in the game", help = "What was played in the game this position comes from, for reference.",
                clearLabel = "Not known",
            ) { ans ->
                settingPlayed = false
                scope.launch { app.positions.setPlayed(cur.id, ans); refresh++ }
            }
        }
        if (puttingBack) {
            PutBackDialog(1, onDismiss = { puttingBack = false }) { reset ->
                puttingBack = false
                scope.launch { app.positions.unlearn(listOf(cur.id), reset); refresh++ }
                Toast.makeText(context, "Back in My positions", Toast.LENGTH_SHORT).show()
            }
        }
        if (filing) {
            CategoriesDialog(app, initial = null, positionId = cur.id, onDismiss = { filing = false }) { ids, _ ->
                filing = false
                scope.launch { app.positions.setCategories(cur.id, ids) }
            }
        }
        if (labelling) {
            LabelsDialog(settings.labelKeys, settings.visible(cur.labels), onDismiss = { labelling = false }) { chosen ->
                labelling = false
                // Labels turned off in Settings stay on the position, hidden.
                val hidden = cur.labels - settings.labelKeys.toSet()
                scope.launch { app.positions.setLabels(cur.id, chosen + hidden); refresh++ }
            }
        }
        if (reanalyzing) {
            ReanalyzeDialog(settings, gameNumber = null, onDismiss = { reanalyzing = false }, match = (p?.position?.ctx()?.matchLength ?: 0) > 0, onStart = { reanalyzing = false; analyze(it) })
        }
        if (removing) {
            AlertDialog(
                onDismissRequest = { removing = false },
                title = { Text("Remove this position?") },
                text = { Text("It leaves My positions and all its folders.") },
                confirmButton = { TextButton(onClick = { removing = false; scope.launch { app.positions.remove(cur.id); onBack() } }) { Text("Remove") } },
                dismissButton = { TextButton(onClick = { removing = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun NoteDialog(initial: String, onDone: (String?) -> Unit) {
    var note by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text("Note") },
        text = {
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(500) }, minLines = 3,
                label = { Text("Why you saved it, or where the answer comes from") }, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onDone(note) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}

/**
 * The player's own right answer: a cube action, or a play (the engine's candidates first, then
 * every other legal play). "Engine's best" clears it.
 */
@Composable
private fun AnswerDialog(
    p: StudyPosition, checker: com.boldblot.backgammonplyground.core.CheckerAnalysis?, current: MyAnswer?,
    onDismiss: () -> Unit,
    title: String = "My answer",
    help: String = "The quiz counts your answer as the right one. Add a note to say where it comes from.",
    /** The button that clears it. */
    clearLabel: String = "Engine's best",
    onSet: (MyAnswer?) -> Unit,
) {
    val plays = remember(p) {
        val dice = p.dice ?: return@remember emptyList()
        val listed = checker?.candidates?.map { it.move }.orEmpty()
        val seen = listed.map { p.board.applyMove(Player.HUMAN, it) }.toMutableSet()
        listed + MoveGenerator.legalMoves(p.board, Player.HUMAN, dice.d1, dice.d2).filter { seen.add(p.board.applyMove(Player.HUMAN, it)) }
    }
    var pickedMove by remember { mutableStateOf(current?.move) }
    var pickedCube by remember { mutableStateOf(current?.cube) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            val scroll = rememberScrollState()
            Column(Modifier.heightIn(max = 420.dp).scrollbar(scroll).verticalScroll(scroll).padding(end = 8.dp)) {
                Text(help, style = MaterialTheme.typography.bodySmall)
                if (p.dice != null) {
                    plays.forEachIndexed { i, m ->
                        val picked = pickedMove?.let { p.board.applyMove(Player.HUMAN, it) == p.board.applyMove(Player.HUMAN, m) } == true
                        val eqText = checker?.candidates?.getOrNull(i)?.takeIf { i < (checker.candidates.size) }?.let { "  ${fmt(it.equity.toDouble(), 3)}" } ?: ""
                        Row(Modifier.fillMaxWidth().clickable { pickedMove = m }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = picked, onClick = { pickedMove = m })
                            Text(MoveNotation.format(p.board, Player.HUMAN, m) + eqText)
                        }
                        if (checker != null && i == checker.candidates.size - 1 && plays.size > checker.candidates.size) {
                            Text("Other plays", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                } else {
                    cubeActions(p).forEach { k ->
                        Row(Modifier.fillMaxWidth().clickable { pickedCube = k }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = pickedCube == k, onClick = { pickedCube = k })
                            Text(cubeLabel(k))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSet(if (p.dice != null) pickedMove?.let { MyAnswer(move = it) } else pickedCube?.let { MyAnswer(cube = it) }) },
                enabled = pickedMove != null || pickedCube != null,
            ) { Text("Set") }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSet(null) }) { Text(clearLabel) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/**
 * Categories to tick (with "New category…"), for saving a position ([initial] ids) or changing a
 * saved one's ([positionId]); with [withNote] also a note. Gives the ticked ids and the note.
 */
@Composable
private fun CategoriesDialog(
    app: PlyGroundApp, initial: Set<Long>?, positionId: Long?, withNote: Boolean = false, title: String = "Folders",
    onDismiss: () -> Unit, onDone: (Set<Long>, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var cats by remember { mutableStateOf<List<CategoryCount>>(emptyList()) }
    var chosen by remember { mutableStateOf(initial ?: emptySet()) }
    var newName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        cats = app.positions.categories()
        if (positionId != null) chosen = app.positions.categoriesOf(positionId).toSet()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            val scroll = rememberScrollState()
            Column(Modifier.heightIn(max = 460.dp).scrollbar(scroll).verticalScroll(scroll).padding(end = 8.dp)) {
                cats.forEach { c -> CheckRow(c.name, c.id in chosen) { chosen = if (c.id in chosen) chosen - c.id else chosen + c.id } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it.take(40) }, singleLine = true,
                        label = { Text("New folder") }, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        val n = newName.trim()
                        if (n.isNotEmpty()) scope.launch {
                            val id = cats.firstOrNull { it.name.equals(n, ignoreCase = true) }?.id ?: app.positions.createCategory(n)
                            cats = app.positions.categories()
                            chosen = chosen + id
                            newName = ""
                        }
                    }, enabled = newName.isNotBlank()) { Icon(Icons.Default.Add, "Add folder") }
                }
                if (withNote) {
                    OutlinedTextField(
                        value = note, onValueChange = { note = it.take(500) }, minLines = 2,
                        label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(chosen, note) }) { Text(if (withNote) "Save" else "OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * "Save to My positions" for [position] (null: nothing to save): pick categories and a note; the
 * position is analyzed at the depths in Settings once saved.
 */
@Composable
fun SaveToPositionsDialog(
    app: PlyGroundApp, position: StudyPosition, source: String,
    /** The decision's labels from its game (error size, whose it was); null = from the position. */
    labels: Set<String>? = null,
    /** What was played in the game. */
    played: MyAnswer? = null,
    /** Called once the position is saved (before its analysis). */
    onSaved: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    CategoriesDialog(app, initial = emptySet(), positionId = null, withNote = true, title = "Save to My positions", onDismiss = onDismiss) { ids, note ->
        onDismiss()
        app.appScope.launch {
            val id = app.positions.save(position, source, note, ids, labels = labels, played = played)
            onSaved()
            val s = app.settings.settings.first()
            runCatching { analyzePosition(app, position, s) }.onSuccess { app.positions.setAnalysis(id, it) }
        }
        Toast.makeText(context, "Saved to My positions", Toast.LENGTH_SHORT).show()
    }
}

// ---- quiz --------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(vm: QuizViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit = {}, onSettings: () -> Unit = {}) {
    val st by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(st.lastRemoved) {
        val d = st.lastRemoved ?: return@LaunchedEffect
        // Undo stays up for 5 seconds (a snackbar with an action would otherwise wait for a tap).
        val r = kotlinx.coroutines.withTimeoutOrNull(5_000) {
            snackbar.showSnackbar("Deleted from My positions", actionLabel = "Undo", withDismissAction = true, duration = androidx.compose.material3.SnackbarDuration.Indefinite)
        }
        if (r == SnackbarResult.ActionPerformed) vm.undoDelete(d)
    }
    val title = if (st.finished || st.total == 0) "Quiz" else "Quiz · ${st.answered + if (st.result == null) 1 else 0}"
    // In landscape the title goes into the side panel, so the board has the whole height and sits in the middle.
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val playing = !st.loading && st.total > 0 && !st.finished
    // Finish: ends the quiz early with its summary.
    val finish: @Composable () -> Unit = { if (playing && st.answered > 0) FilterChip(selected = false, onClick = vm::finish, label = { Text("Finish") }) }
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Title(title)
            Spacer(Modifier.weight(1f))
            finish()
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (!landscape) TopAppBar(
                title = { Title(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { finish(); IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (landscape && !playing) Box(Modifier.align(Alignment.TopStart)) { header() }
            Box(Modifier.fillMaxSize().padding(top = if (landscape && !playing) 48.dp else 0.dp)) {
                when {
                    st.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    st.total == 0 -> Text("No positions to ask.", Modifier.padding(24.dp))
                    st.finished -> QuizSummary(st, vm, onBack, onOpen)
                    else -> QuizContent(st, vm, header.takeIf { landscape })
                }
            }
        }
    }
}

@Composable
private fun QuizSummary(st: QuizState, vm: QuizViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    // A learned position tapped in the list below, to put back.
    var puttingBack by remember { mutableStateOf<Long?>(null) }
    puttingBack?.let { id ->
        PutBackDialog(1, onDismiss = { puttingBack = null }) { reset -> puttingBack = null; vm.putBack(id, reset) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Done", style = MaterialTheme.typography.titleLarge)
        Text(
            "${st.total} position${if (st.total == 1) "" else "s"}, ${st.answered} answer${if (st.answered == 1) "" else "s"}" +
                if (st.stoppedEarly) " (finished early)." else ".",
        )
        Text("Right: ${st.right} of ${st.answered}")
        if (st.answered > 0) Text("Average error: ${errWithUnit(st.totalError.toDouble() / st.answered, LocalErrorUnit.current)}")
        val manual = st.deleted - st.learned
        // The Learned chip always moves a position to Learned; the balance threshold may delete it instead (Settings).
        val byHand = st.history.count { it.result.learnedByHand }
        val moved = if (st.settings.quizLearned) st.learned else byHand
        if (moved > 0) Text("Moved to Learned: $moved")
        if (st.learned - moved > 0) Text("Learned and deleted: ${st.learned - moved}")
        if (manual > 0) Text("Deleted: $manual")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Repeat: the same cards; Another: as many new ones with the same settings, while there are some.
            Button(onClick = vm::repeat) { Text("Repeat") }
            val more = remember(st.history) { vm.anotherCount() }
            if (more > 0) Button(onClick = vm::another) { Text("Another $more") }
            OutlinedButton(onClick = onBack) { Text("Done") }
        }
        // The quiz's positions, as a folder lists them (the last answer to each; deleted ones left out).
        val cards = st.history.reversed().distinctBy { it.position.id }.reversed().filter { !it.result.deleted && it.result.removed == null }
        if (cards.isNotEmpty()) {
            Text("Positions in this quiz", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            cards.forEach { c -> PositionRow(c.position, st.settings, quizAnswer = c.result.right) { onOpen(c.position.id) } }
        }
        // The positions that went to Learned in this quiz: tap one to put it back in My positions.
        val learned = st.history.reversed().distinctBy { it.position.id }.reversed()
            .filter { it.result.deleted && it.result.removed == null && (it.result.learnedByHand || st.settings.quizLearned) }
        if (learned.isNotEmpty()) {
            Text("Learned", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text("Tap one to put it back in My positions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            learned.forEach { c -> PositionRow(c.position, st.settings, quizAnswer = c.result.right) { puttingBack = c.position.id } }
        }
    }
}

@Composable
private fun QuizContent(st: QuizState, vm: QuizViewModel, header: (@Composable () -> Unit)? = null) {
    // The arrows can show an answered card again (view only).
    val past = st.viewing?.let { st.history.getOrNull(it) }
    val sp = past?.position ?: st.current ?: return
    val p = sp.position
    val r = past?.result ?: st.result
    val colors = boardColorsFor(st.settings)
    val capture = rememberBoardCapture()
    val checker = r?.analysis?.checker
    val picked = st.candidate?.let { checker?.candidates?.getOrNull(it) }
    // After an answer: the position after the play picked in the list (or the answer), or the position itself.
    val afterMove = if (r != null && st.view == ReviewView.AFTER) (picked?.move ?: r.move) else null
    val arrows = if (r == null || st.view != ReviewView.ARROWS) emptyList() else if (picked != null && checker != null) {
        arrowsFor(Player.HUMAN, picked.move, ArrowColors.forSeverity(XgRules.severity((checker.bestEquity - picked.equity) * r.factor)))
    } else buildList {
        val right = when {
            r.mine != null -> sp.answer?.move
            else -> r.bestMove
        }
        right?.let { addAll(arrowsFor(Player.HUMAN, it, ArrowColors.best)) }
        val played = r.move
        if (played != null && (right == null || p.board.applyMove(Player.HUMAN, played) != p.board.applyMove(Player.HUMAN, right))) {
            addAll(arrowsFor(Player.HUMAN, played, ArrowColors.forSeverity(r.severity.takeIf { it != Severity.NONE } ?: Severity.INACCURACY)))
        }
    }
    val interactive = r == null && !st.busy && p.dice != null
    val diceOrder = if (past != null) p.dice?.highFirst ?: emptyList() else st.diceOrder
    val board: @Composable (Modifier) -> Unit = { m ->
        val shown = when {
            afterMove != null -> p.board.applyMove(Player.HUMAN, afterMove)
            r != null -> p.board
            else -> st.board
        }
        BoardView(
            board = shown, modifier = m.then(capture.modifier), colors = colors, mirrored = st.settings.mirrorBoard,
            dice = listOfNotNull(p.dice?.takeIf { afterMove == null }?.let { DiceView(Player.HUMAN, diceOrder, if (r != null) diceOrder.map { 0f } else st.diceDim, error = st.diceError) }),
            cube = p.setup.cube, cubeOffered = p.doubled,
            crawford = p.setup.crawford, crawfordSign = st.settings.crawfordSign,
            dmp = com.boldblot.backgammonplyground.core.Scoring.isDmp(p.setup.matchLength, p.setup.score, p.setup.crawford),
            movable = if (interactive) st.movable else emptySet(),
            pips = listOf(shown.pipCount(Player.HUMAN), shown.pipCount(Player.CPU)),
            arrows = arrows,
            onTap = if (interactive) vm::tap else null, onDrag = if (interactive) vm::drag else null,
            // Once answered, a tap on the dice (or where they would be) goes to the next card.
            onDiceTap = if (interactive || (r != null && !st.busy)) vm::tapDice else null,
        )
    }
    // [wide]: landscape, beside the board (Undo and Done as chips there, as in a game).
    val panel: @Composable (wide: Boolean) -> Unit = { wide ->
        ContextText(p, colors, style = MaterialTheme.typography.titleSmall)
        // Back and forth through the cards answered this round (above the answer, so they stay in one place).
        if (st.history.isNotEmpty()) {
            val at = st.viewing ?: st.history.size
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::back, enabled = at > 0) { Icon(Icons.AutoMirrored.Filled.NavigateBefore, "Previous card") }
                Text(
                    if (st.viewing != null) "Card ${at + 1} of ${st.history.size} answered" else "Answered cards: ${st.history.size}",
                    style = MaterialTheme.typography.labelMedium,
                )
                IconButton(onClick = vm::forward, enabled = st.viewing != null) { Icon(Icons.AutoMirrored.Filled.NavigateNext, "Next card") }
                if (st.viewing != null) androidx.compose.material3.AssistChip(
                    onClick = { repeat(st.history.size) { vm.forward() } }, label = { Text("Back to Quiz", maxLines = 1) },
                    colors = com.boldblot.backgammonplyground.ui.components.negativeChipColors(), border = null,
                )
            }
        }
        // The position's labels, after the answer (some, like Blunder, would give it away).
        if (r != null && st.settings.quizLabels) LabelTags(st.settings.visible(sp.labels), Modifier.padding(vertical = 4.dp))
        if (sp.entity.note.isNotBlank() && r != null) Text(sp.entity.note, style = MaterialTheme.typography.bodySmall)
        when {
            st.busy -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("  Analyzing…")
            }
            r != null -> QuizResultCard(st, sp, r, vm, capture, readOnly = past != null)
            // Undo and Done as chips in both orientations, as in a game, centred in the panel.
            p.dice != null -> Box(Modifier.fillMaxWidth().padding(vertical = if (wide) 2.dp else 6.dp), contentAlignment = Alignment.Center) {
                com.boldblot.backgammonplyground.ui.components.MoveChips(
                    canUndo = st.steps.isNotEmpty(), canDone = st.moveComplete, onUndo = vm::undo, onDone = vm::done,
                )
            }
            p.doubled && st.firstCube != null -> Column(Modifier.padding(vertical = 6.dp)) {
                Text("You: ${cubeLabel(st.firstCube)}. Was the opponent's double right?", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { vm.answerDoubleClaim(true) }) { Text("Yes, a double") }
                    FilledTonalButton(onClick = { vm.answerDoubleClaim(false) }) { Text("No, not a double") }
                }
            }
            p.doubled -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                cubeActions(p).forEach { k -> FilledTonalButton(onClick = { vm.answerCube(k) }) { Text(cubeLabel(k)) } }
            }
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                // Settings → Quiz: the full decision, or the cube action alone (with Too good).
                when (st.settings.quizMyCube) {
                    com.boldblot.backgammonplyground.data.QuizMyCube.FULL ->
                        CubeAnswer.entries.forEach { c -> FilledTonalButton(onClick = { vm.answerCube(c) }) { Text(c.label) } }
                    com.boldblot.backgammonplyground.data.QuizMyCube.THREE -> {
                        FilledTonalButton(onClick = { vm.answerCube(CubeAnswer.NO_DOUBLE) }) { Text("No double") }
                        FilledTonalButton(onClick = { vm.answerCube(CubeAnswer.DOUBLE_TAKE, partial = true) }) { Text("Double") }
                        FilledTonalButton(onClick = { vm.answerCube(CubeAnswer.TOO_GOOD) }) { Text("Too good") }
                    }
                    com.boldblot.backgammonplyground.data.QuizMyCube.TWO -> {
                        FilledTonalButton(onClick = { vm.answerCube(CubeAnswer.NO_DOUBLE, partial = true) }) { Text("No double") }
                        FilledTonalButton(onClick = { vm.answerCube(CubeAnswer.DOUBLE_TAKE, partial = true) }) { Text("Double") }
                    }
                }
            }
        }
        st.message?.let {
            Text(
                it, color = if (it.startsWith("Analysis failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable(onClick = vm::dismissMessage),
            )
        }
        Text(
            "${st.queue.size - if (r != null) 1 else 0} left · right ${st.right} of ${st.answered}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize().padding(6.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    header?.invoke()
                    panel(true)
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1.4f).fillMaxHeight(), contentAlignment = Alignment.Center) { board(Modifier.aspectRatio(1.38f)) }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                board(Modifier.fillMaxWidth().aspectRatio(1.38f))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 6.dp)) { panel(false) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuizResultCard(st: QuizState, sp: SavedPosition, r: QuizResult, vm: QuizViewModel, capture: BoardCapture, readOnly: Boolean = false) {
    val p = sp.position
    var filing by remember { mutableStateOf(false) }
    var puttingBack by remember { mutableStateOf(false) }
    if (puttingBack) {
        PutBackDialog(1, onDismiss = { puttingBack = false }) { reset -> puttingBack = false; vm.toggleLearned(reset) }
    }
    if (filing) {
        CategoriesDialog(vm.app, initial = null, positionId = sp.id, title = "Move to folder", onDismiss = { filing = false }) { ids, _ ->
            filing = false
            vm.moveToFolders(sp.id, ids)
        }
    }
    val answerIndex = r.move?.let { m -> r.analysis.checker?.candidates?.indexOfFirst { p.board.applyMove(Player.HUMAN, it.move) == p.board.applyMove(Player.HUMAN, m) }?.takeIf { it >= 0 } }
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (r.right) "Right" else "Not this time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = if (r.right) ArrowColors.best else MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f),
                )
                var sharing by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { sharing = true }) { Icon(Icons.Default.Share, "Share") }
                    DropdownMenu(expanded = sharing, onDismissRequest = { sharing = false }) {
                        SharePositionItems(p, r.analysis, answerIndex, r.cube, capture) { sharing = false }
                    }
                }
                if (!r.deleted && r.removed == null) IconButton(onClick = { filing = true }) { Icon(Icons.Default.Folder, "Move to folder") }
                if (!readOnly && !r.deleted && r.removed == null) IconButton(onClick = vm::deleteCurrent) { Icon(Icons.Default.Delete, "Delete from My positions") }
                if (!readOnly) Button(onClick = vm::next) { Text("Next") }
            }
            if (!readOnly && r.removed == null && (!r.deleted || r.learnedByHand)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = r.learnedByHand, onClick = { if (r.learnedByHand) puttingBack = true else vm.toggleLearned() }, enabled = !r.favourite,
                        leadingIcon = { Icon(Icons.Default.TaskAlt, null, Modifier.size(18.dp)) },
                        label = { Text("Learned", maxLines = 1, softWrap = false) },
                    )
                    FilterChip(
                        selected = r.favourite, onClick = vm::toggleFavourite,
                        leadingIcon = { Icon(if (r.favourite) Icons.Default.Star else Icons.Default.StarBorder, null, Modifier.size(18.dp)) },
                        label = { Text("Favourite", maxLines = 1, softWrap = false) },
                    )
                }
            }
            val you = r.youText ?: r.cubeAnswer?.label ?: decisionText(p, r.move, r.cube)
            Text("You: $you", style = MaterialTheme.typography.bodyMedium)
            if (r.doubleClaim != null) {
                fun yn(b: Boolean) = if (b) "yes" else "no"
                Text(
                    "Was the double right? You: ${yn(r.doubleClaim)}" + (r.doubleRight?.let { " · engine: ${yn(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (r.doubleRight == null || r.doubleRight == r.doubleClaim) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
            }
            if (r.counts != null) Text("This position: ${r.counts}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.learnedByHand) {
                Text("Moved to Learned.", style = MaterialTheme.typography.bodyMedium, color = ArrowColors.best)
            } else if (r.deleted) {
                Text(
                    if (st.settings.quizLearned) "Learned: its balance reached the threshold, so it moved to Learned (My positions → Learned; Settings → Quiz)."
                    else "Learned: its balance reached the threshold, so it was deleted from My positions (Settings → Quiz).",
                    style = MaterialTheme.typography.bodyMedium, color = ArrowColors.best,
                )
            }
            if (r.removed != null) Text("Deleted from My positions.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.mine != null) Text("★ Your saved answer: ${r.mine}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (r.played != null) Text("In the game: ${r.played}", style = MaterialTheme.typography.bodyMedium)
            val best = r.bestText ?: r.bestCubeAnswer?.label ?: decisionText(p, r.bestMove, r.bestCube)
            val checker = r.analysis.checker?.takeIf { it.candidates.isNotEmpty() }
            val playedMove = r.move
            if (checker != null && playedMove != null) {
                // The played and best plays as in the match review: a tap shows that play on the board and in the list.
                com.boldblot.backgammonplyground.ui.components.PlayedBestBar(
                    playedText = com.boldblot.backgammonplyground.core.MoveNotation.format(p.board, Player.HUMAN, playedMove),
                    playedError = r.error / r.factor, playedSeverity = r.severity,
                    bestText = com.boldblot.backgammonplyground.core.MoveNotation.format(p.board, Player.HUMAN, checker.candidates.first().move),
                    bestEquity = checker.candidates.first().equity,
                    onPlayed = { answerIndex?.let { vm.selectCandidate(it) } },
                    onBest = { vm.selectCandidate(0) },
                )
            } else if ((r.cubeAnswer ?: r.cube) != null) {
                com.boldblot.backgammonplyground.ui.components.PlayedBestBar(
                    playedText = r.cubeAnswer?.label ?: r.cube?.let { cubeLabel(it) } ?: "?",
                    playedError = r.error / r.factor, playedSeverity = r.severity,
                    bestText = best, bestEquity = null,
                )
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Engine: $best", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                SeverityBadge(r.severity, r.error, factor = r.factor)
            }
            if (r.equity != null && r.bestEquity != null) {
                Text("Equity: yours ${eq(r.equity)}, best ${eq(r.bestEquity)}", style = MaterialTheme.typography.labelMedium)
            }
            // The analysis behind the verdict, and another one at other depths or with another engine.
            var asking by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Analysis: ${r.analysis.label.ifBlank { UNKNOWN_ENGINE }}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                if (st.reanalyzing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else if (!readOnly) TextButton(onClick = { asking = true }) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Analyze again")
                }
            }
            if (asking) ReanalyzeDialog(st.settings, gameNumber = null, onDismiss = { asking = false }, match = p.ctx().matchLength > 0, onStart = { asking = false; vm.reanalyze(it) })
            r.analysis.cube?.let { CubeTable(it, if (p.doubled) DecisionKind.TAKE else null, factorOf(p), p.beavers, colors = boardColorsFor(st.settings), rollerHuman = !p.doubled) }
            r.analysis.checker?.let { c ->
                ReviewViewChips(st.view, true, vm::setView, null, Modifier.fillMaxWidth())
                CandidateList(
                    board = p.board, player = Player.HUMAN, analysis = c, factor = factorOf(p),
                    highlightIndex = answerIndex, maxRows = 5, selectedIndex = st.candidate,
                    onSelect = { i, _ -> vm.selectCandidate(if (st.candidate == i) null else i) },
                )
            }
        }
    }
}

/** [contextParts] as one line, with a checker by each player's score. */
@Composable
private fun ContextText(
    p: StudyPosition, colors: com.boldblot.backgammonplyground.ui.board.BoardColors,
    style: androidx.compose.ui.text.TextStyle, maxLines: Int = Int.MAX_VALUE,
) {
    val (before, score, after) = contextParts(p)
    com.boldblot.backgammonplyground.ui.components.ScoreText(before, score, after, colors, style = style, maxLines = maxLines, matchLength = p.setup.matchLength)
}
