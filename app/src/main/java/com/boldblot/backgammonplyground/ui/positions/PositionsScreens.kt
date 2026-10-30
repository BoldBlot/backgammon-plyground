package com.boldblot.backgammonplyground.ui.positions

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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Label
import com.boldblot.backgammonplyground.core.Labels
import com.boldblot.backgammonplyground.ui.components.LabelFilterButton
import com.boldblot.backgammonplyground.ui.components.LabelTags
import com.boldblot.backgammonplyground.ui.components.LabelsDialog
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.School
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
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
import com.boldblot.backgammonplyground.data.ImportPreview
import com.boldblot.backgammonplyground.data.PositionsRepository.Companion.UNCATEGORIZED
import com.boldblot.backgammonplyground.data.SavedPosition
import com.boldblot.backgammonplyground.data.Settings
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

/** A screen title with an "Experimental" tag. */
@Composable
internal fun Title(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.padding(start = 8.dp)) {
            Text(
                "Experimental", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

// ---- My positions: categories and quiz ------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MyPositionsScreen(app: PlyGroundApp, onBack: () -> Unit, onCategory: (Long?) -> Unit, onQuiz: (List<Long>) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var categories by remember { mutableStateOf<List<CategoryCount>?>(null) }
    var uncategorized by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<CategoryCount?>(null) }
    var deleting by remember { mutableStateOf<CategoryCount?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<ImportPreview?>(null) }
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
            .onFailure { Toast.makeText(context, it.message ?: "Could not read that file.", Toast.LENGTH_LONG).show() }
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
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("New folder") }, onClick = { menu = false; newCategory = true })
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
            Text(
                "Experimental: this part of the app may still change. Save positions from your blunder list, a game " +
                    "review or Analyze Position (\"Save to My positions\"), or import a file, then quiz yourself on them.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { onQuiz(emptyList()) }, enabled = total > 0, modifier = Modifier.padding(top = 12.dp)) {
                Icon(Icons.Default.School, null)
                Spacer(Modifier.width(6.dp))
                Text("Quiz…")
            }
            Spacer(Modifier.height(12.dp))
            CategoryRow("All positions", total, onClick = { onCategory(null) })
            cats.forEachIndexed { i, c ->
                CategoryRow(c.name, c.count, onClick = { onCategory(c.id) }) { close ->
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { close(); renaming = c })
                    if (i > 0) DropdownMenuItem(text = { Text("Move up") }, onClick = { close(); scope.launch { app.positions.moveCategory(c.id, -1); refresh++ } })
                    if (i < cats.lastIndex) DropdownMenuItem(text = { Text("Move down") }, onClick = { close(); scope.launch { app.positions.moveCategory(c.id, 1); refresh++ } })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { close(); deleting = c })
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPositionsScreen(app: PlyGroundApp, categoryId: Long?, onBack: () -> Unit, onOpen: (Long) -> Unit, onQuiz: (List<Long>) -> Unit) {
    var all by remember { mutableStateOf<List<SavedPosition>?>(null) }
    var filter by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var name by remember { mutableStateOf("") }
    val settings by app.settings.settings.collectAsState(initial = Settings())
    LaunchedEffect(categoryId) {
        name = when (categoryId) {
            null -> "All positions"
            UNCATEGORIZED -> "No folder"
            else -> app.positions.categories().firstOrNull { it.id == categoryId }?.name ?: ""
        }
        all = app.positions.positions(categoryId)
    }
    val shownFilter = settings.visible(filter)
    val list = all?.filter { shownFilter.isEmpty() || Labels.matches(settings.visible(it.labels), shownFilter) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Title(name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (!all.isNullOrEmpty()) LabelFilterButton(settings.labelKeys, filter, { filter = it }, compact = true)
                    if (!all.isNullOrEmpty()) TextButton(onClick = { onQuiz(listOfNotNull(categoryId)) }) { Text("Quiz") }
                },
            )
        },
    ) { padding ->
        val l = list
        when {
            l == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            l.isEmpty() -> Text(if (all.isNullOrEmpty()) "No positions here yet." else "No positions pass the filter.", Modifier.padding(padding).padding(24.dp))
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp)) {
                items(l, key = { it.id }) { p -> PositionRow(p, settings) { onOpen(p.id) } }
            }
        }
    }
}

@Composable
private fun PositionRow(p: SavedPosition, settings: Settings, onClick: () -> Unit) {
    val sp = p.position
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BoardView(
                board = sp.board, modifier = Modifier.width(120.dp).aspectRatio(1.38f), colors = boardColorsFor(settings),
                mirrored = settings.mirrorBoard, dice = listOfNotNull(sp.dice?.let { DiceView(Player.HUMAN, it.highFirst) }),
                cube = sp.setup.cube, cubeOffered = sp.doubled, showNumbers = false,
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(contextText(sp), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (p.entity.note.isNotBlank()) {
                    Text(p.entity.note, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                p.answer?.let { Text("My answer: ${answerText(sp, it)}", style = MaterialTheme.typography.labelSmall) }
                LabelTags(settings.visible(p.labels), Modifier.padding(top = 2.dp))
                p.entity.lastRight?.let {
                    Text(if (it) "Last quiz: right" else "Last quiz: missed", style = MaterialTheme.typography.labelSmall, color = if (it) ArrowColors.best else MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ---- one position --------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositionDetailScreen(app: PlyGroundApp, id: Long, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val settings by app.settings.settings.collectAsState(initial = Settings())
    var refresh by remember { mutableIntStateOf(0) }
    var p by remember { mutableStateOf<SavedPosition?>(null) }
    var selected by remember { mutableStateOf<Int?>(0) }
    var analyzing by remember { mutableStateOf(false) }
    var noting by remember { mutableStateOf(false) }
    var answering by remember { mutableStateOf(false) }
    var filing by remember { mutableStateOf(false) }
    var labelling by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(id, refresh) { p = app.positions.position(id) }
    fun analyze() {
        val cur = p ?: return
        analyzing = true
        scope.launch {
            val s = app.settings.settings.first()
            runCatching { analyzePosition(app, cur.position, s) }
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
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Copy XGID") }, onClick = {
                                menu = false
                                p?.let { clipboard.setText(AnnotatedString(it.position.xgid)) }
                            })
                            DropdownMenuItem(text = { Text("Share") }, onClick = {
                                menu = false
                                scope.launch { sharePositions(context, "Position", app.positions.export(emptyList(), listOf(id))) }
                            })
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
        val arrows = a?.checker?.candidates?.getOrNull(selected ?: -1)?.let { arrowsFor(Player.HUMAN, it.move, ArrowColors.best) }.orEmpty()
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val board: @Composable (Modifier) -> Unit = { m ->
                BoardView(
                    board = sp.board, modifier = m, colors = colors, mirrored = settings.mirrorBoard,
                    dice = listOfNotNull(sp.dice?.let { DiceView(Player.HUMAN, it.highFirst) }),
                    cube = sp.setup.cube, cubeOffered = sp.doubled,
                    pips = listOf(sp.board.pipCount(Player.HUMAN), sp.board.pipCount(Player.CPU)), arrows = arrows,
                )
            }
            val details: @Composable () -> Unit = {
                Text(contextText(sp), style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(cur.entity.source.ifBlank { null }, a?.label?.ifBlank { null }).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (cur.entity.note.isNotBlank()) Text(cur.entity.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                cur.answer?.let { Text("★ My answer: ${answerText(sp, it)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)) }
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
                }
                LabelTags(settings.visible(cur.labels), Modifier.padding(bottom = 4.dp)) { labelling = true }
                when {
                    analyzing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  Analyzing…")
                    }
                    a?.checker != null -> {
                        if (a.checker.candidates.isEmpty()) Text("No legal move with this roll.")
                        val mine = cur.answer?.move?.let { m -> a.checker.candidates.indexOfFirst { sp.board.applyMove(Player.HUMAN, it.move) == sp.board.applyMove(Player.HUMAN, m) } }
                        CandidateList(
                            board = sp.board, player = Player.HUMAN, analysis = a.checker, factor = factorOf(sp),
                            highlightIndex = mine?.takeIf { it >= 0 }, maxRows = 10, selectedIndex = selected, detailed = true,
                            moverColor = colors.human, opponentColor = colors.cpu, onSelect = { i, _ -> selected = i },
                            checkIndex = mine?.takeIf { it >= 0 },
                        )
                    }
                    a?.cube != null -> CubeTable(a.cube, if (sp.doubled) DecisionKind.TAKE else null, factorOf(sp), sp.beavers, mine = cur.answer?.cube)
                }
                TextButton(onClick = { analyze() }, enabled = !analyzing) { Text("Analyze again (Settings → Analysis)") }
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
    onDismiss: () -> Unit, onSet: (MyAnswer?) -> Unit,
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
        title = { Text("My answer") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("The quiz counts your answer as the right one. Add a note to say where it comes from.", style = MaterialTheme.typography.bodySmall)
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
                if (current != null) TextButton(onClick = { onSet(null) }) { Text("Engine's best") }
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
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
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
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    CategoriesDialog(app, initial = emptySet(), positionId = null, withNote = true, title = "Save to My positions", onDismiss = onDismiss) { ids, note ->
        onDismiss()
        app.appScope.launch {
            val id = app.positions.save(position, source, note, ids, labels = labels)
            val s = app.settings.settings.first()
            runCatching { analyzePosition(app, position, s) }.onSuccess { app.positions.setAnalysis(id, it) }
        }
        Toast.makeText(context, "Saved to My positions", Toast.LENGTH_SHORT).show()
    }
}

// ---- quiz --------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(vm: QuizViewModel, onBack: () -> Unit) {
    val st by vm.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Title(if (st.finished || st.total == 0) "Quiz" else "Quiz · ${st.answered + if (st.result == null) 1 else 0}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                st.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                st.total == 0 -> Text("No positions to ask.", Modifier.padding(24.dp))
                st.finished -> QuizSummary(st, vm, onBack)
                else -> QuizContent(st, vm)
            }
        }
    }
}

@Composable
private fun QuizSummary(st: QuizState, vm: QuizViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Done", style = MaterialTheme.typography.titleLarge)
        Text("${st.total} position${if (st.total == 1) "" else "s"}, ${st.answered} answer${if (st.answered == 1) "" else "s"}.")
        Text("Right: ${st.right} of ${st.answered}")
        if (st.answered > 0) Text("Average error: ${fmt(1000.0 * st.totalError / st.answered)} thousandths of a point")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::start) { Text("Again") }
            OutlinedButton(onClick = onBack) { Text("Done") }
        }
    }
}

@Composable
private fun QuizContent(st: QuizState, vm: QuizViewModel) {
    val sp = st.current ?: return
    val p = sp.position
    val r = st.result
    val colors = boardColorsFor(st.settings)
    val arrows = if (r == null) emptyList() else buildList {
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
    val board: @Composable (Modifier) -> Unit = { m ->
        BoardView(
            board = if (r != null) p.board else st.board, modifier = m, colors = colors, mirrored = st.settings.mirrorBoard,
            dice = listOfNotNull(p.dice?.let { DiceView(Player.HUMAN, st.diceOrder, if (r != null) st.diceOrder.map { 0f } else st.diceDim, error = st.diceError) }),
            cube = p.setup.cube, cubeOffered = p.doubled,
            movable = if (interactive) st.movable else emptySet(),
            pips = listOf(p.board.pipCount(Player.HUMAN), p.board.pipCount(Player.CPU)),
            arrows = arrows,
            onTap = if (interactive) vm::tap else null, onDrag = if (interactive) vm::drag else null,
            onDiceTap = if (interactive) vm::tapDice else null,
        )
    }
    val panel: @Composable () -> Unit = {
        Text(contextText(p), style = MaterialTheme.typography.titleSmall)
        if (sp.entity.note.isNotBlank() && r != null) Text(sp.entity.note, style = MaterialTheme.typography.bodySmall)
        when {
            st.busy -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("  Analyzing…")
            }
            r != null -> QuizResultCard(sp, r, vm)
            p.dice != null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                OutlinedButton(onClick = vm::undo, enabled = st.steps.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Undo")
                }
                Button(onClick = vm::done, enabled = st.moveComplete) { Text("Done") }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                cubeActions(p).forEach { k -> FilledTonalButton(onClick = { vm.answerCube(k) }) { Text(cubeLabel(k)) } }
            }
        }
        st.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable(onClick = vm::dismissMessage)) }
        Text(
            "${st.queue.size - if (r != null) 1 else 0} left · right ${st.right} of ${st.answered}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize().padding(6.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { panel() }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1.4f).fillMaxHeight(), contentAlignment = Alignment.Center) { board(Modifier.aspectRatio(1.38f)) }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                board(Modifier.fillMaxWidth().aspectRatio(1.38f))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 6.dp)) { panel() }
            }
        }
    }
}

@Composable
private fun QuizResultCard(sp: SavedPosition, r: QuizResult, vm: QuizViewModel) {
    val p = sp.position
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (r.right) "Right" else "Not this time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = if (r.right) ArrowColors.best else MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f),
                )
                Button(onClick = vm::next) { Text("Next") }
            }
            val you = decisionText(p, r.move, r.cube)
            Text("You: $you", style = MaterialTheme.typography.bodyMedium)
            if (r.mine != null) Text("★ Your saved answer: ${r.mine}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            val best = decisionText(p, r.bestMove, r.bestCube)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Engine: $best", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                SeverityBadge(r.severity, r.error)
            }
            if (r.equity != null && r.bestEquity != null) {
                Text("Equity: yours ${eq(r.equity)}, best ${eq(r.bestEquity)}", style = MaterialTheme.typography.labelMedium)
            }
            r.analysis.cube?.let { CubeTable(it, if (p.doubled) DecisionKind.TAKE else null, factorOf(p), p.beavers) }
            r.analysis.checker?.let { c ->
                CandidateList(
                    board = p.board, player = Player.HUMAN, analysis = c, factor = factorOf(p),
                    highlightIndex = r.move?.let { m -> c.candidates.indexOfFirst { p.board.applyMove(Player.HUMAN, it.move) == p.board.applyMove(Player.HUMAN, m) }.takeIf { it >= 0 } },
                    maxRows = 5,
                )
            }
        }
    }
}
