package com.boldblot.backgammonplyground.ui.home

import com.boldblot.backgammonplyground.AppLog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.platform.LocalContext
import com.boldblot.backgammonplyground.ui.review.MatFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.CpuLevel
import com.boldblot.backgammonplyground.core.EngineKind
import com.boldblot.backgammonplyground.core.MatchRules
import com.boldblot.backgammonplyground.core.MistakeRate
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.Opponent
import com.boldblot.backgammonplyground.core.Labels
import com.boldblot.backgammonplyground.core.AutoLabel
import com.boldblot.backgammonplyground.data.CategoryCount
import com.boldblot.backgammonplyground.core.MatImport
import com.boldblot.backgammonplyground.data.GameKey
import com.boldblot.backgammonplyground.ui.components.LabelFilterButton
import com.boldblot.backgammonplyground.core.Elo
import com.boldblot.backgammonplyground.core.PerformanceTotals
import com.boldblot.backgammonplyground.data.AnalysisTiming
import com.boldblot.backgammonplyground.data.AnimationSpeed
import com.boldblot.backgammonplyground.data.AppJson
import com.boldblot.backgammonplyground.data.AutoFinishSpeed
import com.boldblot.backgammonplyground.data.BackgroundPriority
import com.boldblot.backgammonplyground.data.BlunderListLevel
import com.boldblot.backgammonplyground.data.BlunderSort
import com.boldblot.backgammonplyground.data.CpuArrows
import com.boldblot.backgammonplyground.data.FeedbackLevel
import com.boldblot.backgammonplyground.data.MatchEntity
import com.boldblot.backgammonplyground.data.MatchStatus
import com.boldblot.backgammonplyground.data.MeterMode
import com.boldblot.backgammonplyground.data.QuizCorrect
import com.boldblot.backgammonplyground.data.SavedBlunder
import com.boldblot.backgammonplyground.data.Settings
import com.boldblot.backgammonplyground.data.TrainingMode
import com.boldblot.backgammonplyground.engine.EngineService
import com.boldblot.backgammonplyground.ui.components.EngineChips
import com.boldblot.backgammonplyground.ui.components.FOUR_PLY_CHECKER_NOTE
import com.boldblot.backgammonplyground.ui.components.PRUNING
import com.boldblot.backgammonplyground.ui.components.PRUNING_NOTE
import com.boldblot.backgammonplyground.ui.components.PlyChips
import com.boldblot.backgammonplyground.ui.components.fmt
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.boldblot.backgammonplyground.ui.review.BlunderList
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    hasMatch: Boolean,
    onContinue: () -> Unit,
    onNewMatch: () -> Unit,
    onPosition: () -> Unit,
    onMyPositions: () -> Unit = {},
    onStats: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    Scaffold { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val title: @Composable () -> Unit = {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(com.boldblot.backgammonplyground.R.drawable.plyground_logo),
                    contentDescription = null, modifier = Modifier.size(104.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Backgammon PlyGround", style = MaterialTheme.typography.headlineLarge, fontFamily = com.boldblot.backgammonplyground.ui.theme.Archivo,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    "Play a world-class backgammon engine, review every game, learn from every mistake",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Version ${com.boldblot.backgammonplyground.BuildConfig.VERSION_NAME} (build ${com.boldblot.backgammonplyground.BuildConfig.BUILD_LABEL}) · by Bold Blot · boldblot.com",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val buttons: @Composable () -> Unit = {
                val w = Modifier.widthIn(max = 360.dp).fillMaxWidth()
                val main = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = com.boldblot.backgammonplyground.ui.theme.BrandRed, contentColor = androidx.compose.ui.graphics.Color.White,
                )
                if (hasMatch) {
                    Button(onClick = onContinue, modifier = w, colors = main) { Text("Continue match") }
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = onNewMatch, modifier = w) { Text("New match") }
                } else {
                    Button(onClick = onNewMatch, modifier = w, colors = main) { Text("New match") }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onPosition, modifier = w) { Text("Analyze Position") }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onMyPositions, modifier = w) { Text("My positions (experimental)") }
                Spacer(Modifier.height(10.dp))

                OutlinedButton(onClick = onStats, modifier = w) { Text("Statistics") }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onHistory, modifier = w) { Text("Match Library") }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onSettings, modifier = w) { Text("Settings") }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onAbout) { Text("About") }
            }
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { title() }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { buttons() }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    title()
                    Spacer(Modifier.height(32.dp))
                    buttons()
                }
            }
        }
    }
}

/** Match lengths offered as chips (any other length can be typed). */
private val LENGTHS = listOf(0, 1, 3, 5, 7, 9, 11, 13, 15, 17, 21, 25)

/** Longest match that can be typed in. */
private const val MAX_LENGTH = 99

/** New match options. Remembers the choices in settings. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewMatchScreen(app: PlyGroundApp, hasMatch: Boolean, onStart: (MatchRules, CpuLevel, MistakeRate?, EngineKind) -> Unit, onBack: () -> Unit) {
    val settings by app.settings.settings.collectAsState(initial = null)
    val s = settings ?: return
    var length by remember(s) { mutableStateOf(s.matchLength) }
    // The opponents are PR levels: PR <1 (Maximum), PR <2 (Strong), or the mistake-rate opponent.
    var level by remember(s) { mutableStateOf(if (s.cpuLevel == CpuLevel.MAXIMUM || s.cpuLevel == CpuLevel.STRONG) s.cpuLevel else CpuLevel.MISTAKES) }
    var cube by remember(s) { mutableStateOf(s.useCube) }
    var jacoby by remember(s) { mutableStateOf(s.jacoby) }
    var crawford by remember(s) { mutableStateOf(s.crawford) }
    var beavers by remember(s) { mutableStateOf(s.beavers) }
    var raccoons by remember(s) { mutableStateOf(s.raccoons) }
    var mistakes by remember(s) { mutableStateOf(s.mistakes) }
    var engine by remember(s) { mutableStateOf(s.opponentEngine) }
    var confirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun start() {
        scope.launch {
            app.settings.update {
                it.copy(
                    matchLength = length, cpuLevel = level, useCube = cube, jacoby = jacoby, crawford = crawford,
                    beavers = beavers, raccoons = raccoons, mistakes = mistakes, opponentEngine = engine,
                )
            }
            onStart(MatchRules(length = length, useCube = cube, jacoby = jacoby, crawford = crawford, beavers = beavers, raccoons = raccoons), level, mistakes, engine)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New match") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                // Always visible (the options below scroll, especially in landscape).
                actions = {
                    Button(onClick = { if (hasMatch) confirm = true else start() }, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Start")
                    }
                },
            )
        },
    ) { padding ->
        val lengthAndOptions: @Composable () -> Unit = {
            Text("Length", style = MaterialTheme.typography.titleMedium)
            // Any other length can be typed in below the chips.
            var typed by remember(s) { mutableStateOf(if (s.matchLength in LENGTHS) "" else s.matchLength.toString()) }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 6.dp),
            ) {
                LENGTHS.forEach { n ->
                    FilterChip(selected = length == n, onClick = { length = n; typed = "" }, label = { Text(if (n == 0) "Money" else "$n pt") })
                }
            }
            OutlinedTextField(
                value = typed,
                onValueChange = { t ->
                    typed = t.filter(Char::isDigit).take(2)
                    typed.toIntOrNull()?.takeIf { it in 1..MAX_LENGTH }?.let { length = it }
                },
                label = { Text("Other length (points)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                supportingText = typed.toIntOrNull()?.takeIf { it !in 1..MAX_LENGTH }?.let { { Text("1 to $MAX_LENGTH points") } },
                modifier = Modifier.width(220.dp).padding(bottom = 4.dp),
            )
            Toggle("Doubling cube", cube) { cube = it }
            if (length == 0) {
                Toggle("Jacoby rule (gammons count only after a double)", jacoby, enabled = cube) { jacoby = it }
                Toggle("Beavers (take and redouble at once, keeping the cube)", beavers, enabled = cube) { beavers = it }
                Toggle("Raccoons (redouble a beaver once more)", raccoons, enabled = cube && beavers) { raccoons = it }
            } else Toggle("Crawford rule", crawford, enabled = cube) { crawford = it }
        }
        val strength: @Composable () -> Unit = {
            EngineChips(engine, { engine = it }, Modifier.padding(bottom = 4.dp))
            LevelRow("PR <1 · World champion", "Checker 3-ply, all moves, cube 3-ply · no mistakes", level == CpuLevel.MAXIMUM) { level = CpuLevel.MAXIMUM }
            LevelRow("PR <2 · World class", "Checker 2-ply, all moves, cube 2-ply · no mistakes · faster", level == CpuLevel.STRONG) { level = CpuLevel.STRONG }
            val preset = MistakeRate.presets.firstOrNull { it.rate == mistakes }
            var custom by remember { mutableStateOf(preset == null) }
            MistakeRate.presets.forEach { p ->
                val r = p.rate
                LevelRow(
                    "${p.name} · ${p.description}",
                    r.settingsText,
                    level == CpuLevel.MISTAKES && mistakes == r && !custom,
                ) { level = CpuLevel.MISTAKES; mistakes = r; custom = false }
            }
            val customOn = level == CpuLevel.MISTAKES && (custom || preset == null)
            LevelRow("Custom", mistakes.settingsText, customOn) { level = CpuLevel.MISTAKES; custom = true }
            if (customOn) {
                Column(Modifier.padding(start = 48.dp, end = 8.dp)) {
                    Text("Errors: ${MistakeRate.pct(mistakes.errors)} of decisions", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = mistakes.errors * 100, valueRange = 0f..50f, steps = 49,
                        onValueChange = { mistakes = mistakes.copy(errors = Math.round(it) / 100f) },
                    )
                    Text("Blunders: ${MistakeRate.pct(mistakes.blunders)} of decisions", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = mistakes.blunders * 100, valueRange = 0f..25f, steps = 49,
                        onValueChange = { mistakes = mistakes.copy(blunders = Math.round(it * 2) / 200f) },
                    )
                    Text("Largest mistake: %.2f equity".format(mistakes.maxMistake), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = mistakes.maxMistake, valueRange = 0.10f..0.60f, steps = 9,
                        onValueChange = { mistakes = mistakes.copy(maxMistake = Math.round(it * 20) / 20f) },
                    )
                    Text("Checker play depth", style = MaterialTheme.typography.bodySmall)
                    PlyChips(mistakes.checkerPlies, { mistakes = mistakes.copy(checkerPlies = it) })
                    if (mistakes.checkerPlies == 4) {
                        Text(FOUR_PLY_CHECKER_NOTE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    Text("Cube depth", style = MaterialTheme.typography.bodySmall)
                    PlyChips(mistakes.cubePlies, { mistakes = mistakes.copy(cubePlies = it) })
                    Text(
                        "It picks its mistakes from its own analysis at these depths: shallower is faster, " +
                            "and makes a few more small errors of its own.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (level == CpuLevel.MISTAKES) {
                Text(
                    "About PR %.1f, rating about %.0f (estimates).".format(mistakes.estimatedPr, Elo.cpuRating(level, mistakes)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 48.dp),
                )
            }
        }
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { lengthAndOptions() }
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { strength() }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    strength()
                    Spacer(Modifier.height(10.dp))
                    lengthAndOptions()
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Replace the current match?") },
            text = { Text("The match in progress will be abandoned. Its finished games stay in your statistics.") },
            confirmButton = { TextButton(onClick = { confirm = false; start() }) { Text("Start new") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

/** An opponent; its settings are shown only while it is selected. */
@Composable
private fun LevelRow(label: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(label)
            if (selected) Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    // The whole row switches (not only the switch itself).
    Row(
        Modifier.fillMaxWidth().toggleable(value, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Switch(checked = value, onCheckedChange = null, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    app: PlyGroundApp, onReview: (Long) -> Unit, onBlunder: (Long) -> Unit, onBack: () -> Unit,
    /** An imported match's games, for the background analysis. */
    onImported: (List<GameKey>) -> Unit,
) {
    var matches by remember { mutableStateOf<List<MatchEntity>?>(null) }
    var blunders by remember { mutableStateOf<List<SavedBlunder>?>(null) }
    var toDelete by remember { mutableStateOf<MatchEntity?>(null) }
    var summaryOf by remember { mutableStateOf<MatchEntity?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var clear by remember { mutableStateOf<ClearKind?>(null) }
    // Selecting matches for export (Matches tab).
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var exporting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val settings by app.settings.settings.collectAsState(initial = null)
    val sort = settings?.blunderSort ?: BlunderSort.BIGGEST
    // The blunder list's filter (labels turned off in Settings are left out) and what passes it.
    val filter = settings?.let { it.visible(it.blunderFilter) } ?: emptySet()
    val shown = blunders?.filter { filter.isEmpty() || Labels.matches(it.labels, filter) }
    var clearShown by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Importing a .mat file: picked here, or opened with PlyGround from another app.
    var importing by remember { mutableStateOf<MatImport.ParsedMatch?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    fun readMatch(text: String) {
        runCatching { MatImport.parse(text) }
            .onSuccess { importing = it }
            .onFailure { importError = it.message ?: "The file could not be read." }
    }
    val pendingMatch by app.pendingMatch.collectAsState()
    LaunchedEffect(pendingMatch) {
        pendingMatch?.let { text ->
            app.pendingMatch.value = null
            tab = 0
            readMatch(text)
        }
    }
    val matchPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }.getOrNull()
            if (text == null) importError = "The file could not be read." else readMatch(text)
        }
    }
    suspend fun reload() {
        matches = app.repository.history()
        blunders = app.repository.blunders(sort)
    }
    LaunchedEffect(sort) { reload() }
    /** The selected matches as .mat files (matches whose records were cleared have none). */
    suspend fun selectedFiles(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        MatFiles.unique(
            matches.orEmpty().filter { it.id in selected }.sortedBy { it.startedAt }.mapNotNull { m ->
                app.repository.games(m.id).takeIf { it.isNotEmpty() }?.let { MatFiles.matchFile(m, it, playerName = settings?.playerName ?: "Player") }
            },
        )
    }
    fun export(action: suspend (List<Pair<String, String>>) -> Unit) {
        if (exporting) return
        exporting = true
        scope.launch {
            try {
                action(selectedFiles())
            } finally {
                exporting = false
            }
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) export { files ->
            val n = withContext(Dispatchers.IO) { MatFiles.saveToFolder(context, tree, files) }
            Toast.makeText(context, "Saved $n of ${files.size} matches.", Toast.LENGTH_SHORT).show()
            selecting = false
            selected = emptySet()
        }
    }
    Scaffold(
        topBar = {
            if (selecting && tab == 0) TopAppBar(
                title = { Text("${selected.size} selected") },
                navigationIcon = {
                    IconButton(onClick = { selecting = false; selected = emptySet() }) { Icon(Icons.Default.Close, "Stop selecting") }
                },
                actions = {
                    val all = matches.orEmpty().map { it.id }.toSet()
                    TextButton(onClick = { selected = if (selected == all) emptySet() else all }) {
                        Text(if (selected == all && all.isNotEmpty()) "None" else "All")
                    }
                    IconButton(onClick = { export { MatFiles.shareMany(context, it) } }, enabled = selected.isNotEmpty() && !exporting) {
                        Icon(Icons.Default.Share, "Share as .mat files")
                    }
                    IconButton(onClick = { folderPicker.launch(null) }, enabled = selected.isNotEmpty() && !exporting) {
                        Icon(Icons.Default.Folder, "Save .mat files to a folder")
                    }
                },
            ) else TopAppBar(
                title = { Text("Match Library") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (tab == 0) {
                        IconButton(onClick = { matchPicker.launch(arrayOf("*/*")) }) { Icon(Icons.Default.FileOpen, "Import a match (.mat)") }
                    }
                    if (tab == 0 && !matches.isNullOrEmpty()) {
                        IconButton(onClick = { selecting = true }) { Icon(Icons.Default.Share, "Export matches") }
                    }
                    if (tab == 1) {
                        IconButton(onClick = { adding = true }, enabled = !shown.isNullOrEmpty()) {
                            Icon(Icons.Default.LibraryAdd, "Add the blunders shown to My positions")
                        }
                    }
                    // Clears what the open tab shows (with a filter on, only the blunders that pass it).
                    val empty = if (tab == 0) matches.isNullOrEmpty() else shown.isNullOrEmpty()
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(
                            onClick = { if (tab == 0) menu = true else if (filter.isEmpty()) clear = ClearKind.BLUNDERS else clearShown = true },
                            enabled = !empty,
                        ) {
                            Icon(Icons.Default.DeleteSweep, null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (tab == 0) "Clear…" else if (filter.isEmpty()) ClearKind.BLUNDERS.button else "Clear ${shown?.size ?: 0}")
                        }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            for (k in listOf(ClearKind.GAME_RECORDS, ClearKind.STATISTICS, ClearKind.MATCHES)) {
                                DropdownMenuItem(text = { Text(k.title) }, onClick = { menu = false; clear = k })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Matches") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("My blunders" + (shown?.let { " (${it.size})" } ?: "")) })
            }
            val list = matches
            when {
                tab == 1 -> shown?.let {
                    Column {
                        if (!blunders.isNullOrEmpty()) {
                            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                BlunderSort.entries.forEach { o ->
                                    FilterChip(
                                        selected = sort == o, label = { Text(o.label) },
                                        onClick = { scope.launch { app.settings.update { s -> s.copy(blunderSort = o) } } },
                                    )
                                }
                                LabelFilterButton(settings?.labelKeys.orEmpty().filterNot { Labels.isMine(it) }, filter, { f ->
                                    scope.launch { app.settings.update { s -> s.copy(blunderFilter = f) } }
                                })
                            }
                        }
                        BlunderList(it, onBlunder, filtered = filter.isNotEmpty())
                    }
                }
                list == null -> Unit
                list.isEmpty() -> Text("No games played yet.", Modifier.padding(24.dp))
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    if (selecting) item {
                        Text(
                            "Tap matches to select them, then share them or save them to a folder as .mat files " +
                                "(one per match) for eXtreme Gammon or GNU Backgammon.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                    items(list, key = { it.id }) { m ->
                        val checked = m.id in selected
                        fun toggle() { selected = if (checked) selected - m.id else selected + m.id }
                        HistoryRow(
                            m, { if (selecting) toggle() else onReview(m.id) }, { toDelete = m },
                            onSummary = { summaryOf = m },
                            selection = if (selecting) checked else null,
                            onToggleStats = {
                                scope.launch {
                                    app.repository.setInStats(m.id, !m.inStats)
                                    matches = app.repository.history()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    if (clearShown) {
        val n = shown?.size ?: 0
        AlertDialog(
            onDismissRequest = { clearShown = false },
            title = { Text("Clear $n blunder${if (n == 1) "" else "s"}?") },
            text = { Text("The blunders the filter shows are removed from the list; the others stay. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    clearShown = false
                    val ids = shown.orEmpty().map { it.entity.id }
                    scope.launch {
                        app.repository.removeBlunders(ids)
                        reload()
                    }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { clearShown = false }) { Text("Cancel") } },
        )
    }
    if (adding) AddBlundersDialog(app, shown.orEmpty(), filter, onDismiss = { adding = false })
    importing?.let { parsed ->
        MatchImportDialog(parsed, onDismiss = { importing = null }) { you, both ->
            importing = null
            scope.launch {
                runCatching { MatImport.replay(parsed, you) }
                    .onSuccess { im ->
                        val keys = app.repository.importMatch(im, both)
                        onImported(keys)
                        reload()
                        Toast.makeText(context, "Imported ${im.games.size} game${if (im.games.size == 1) "" else "s"}; analyzing in the background.", Toast.LENGTH_LONG).show()
                    }
                    .onFailure { importError = it.message ?: "The match could not be read." }
            }
        }
    }
    importError?.let { msg ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text("Import failed") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { importError = null }) { Text("OK") } },
        )
    }
    clear?.let { kind ->
        ClearDialog(kind, onDismiss = { clear = null }) { days ->
            clear = null
            scope.launch {
                kind.run(app, days)
                reload()
            }
        }
    }
    summaryOf?.let { m ->
        com.boldblot.backgammonplyground.ui.review.SavedSummaryDialog(app, m.id, gameNumber = null, startWithMatch = true, onDismiss = { summaryOf = null })
    }
    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this match?") },
            text = { Text("Its games will be removed from history and statistics. Its blunders stay in your blunder list.") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    scope.launch {
                        app.repository.deleteMatch(m.id)
                        matches = app.repository.history()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HistoryRow(
    m: MatchEntity, onClick: () -> Unit, onDelete: () -> Unit,
    /** Selecting for export: whether this match is selected (null = not selecting). */
    selection: Boolean? = null,
    onToggleStats: () -> Unit,
    onSummary: () -> Unit,
) {
    val perf = m.humanPerformance?.let { runCatching { AppJson.decodeFromString<PerformanceTotals>(it) }.getOrNull() }
    val level = Opponent.name(m.cpuLevel)
    val result = when {
        m.status == MatchStatus.IN_PROGRESS -> "in progress"
        m.status == MatchStatus.ABANDONED -> "abandoned"
        m.length == 0 -> "session ${if (m.humanScore >= m.cpuScore) "+" else ""}${m.humanScore - m.cpuScore}"
        m.winner == 0 -> "won"
        else -> "lost"
    }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selection != null) Checkbox(checked = selection, onCheckedChange = { onClick() })
            Column(Modifier.weight(1f)) {
                Text(
                    "${if (m.length == 0) "Money" else "${m.length}-pt match"} vs $level · $result",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(m.startedAt))} · " +
                        "score ${m.humanScore}-${m.cpuScore} · ${m.games} game${if (m.games == 1) "" else "s"}" +
                        (if (m.imported) " · imported" else "") + if (m.inStats) "" else " · not in statistics",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            perf?.pr?.let { Text("PR ${fmt(it)}", style = MaterialTheme.typography.titleSmall) }
            var menu by remember { mutableStateOf(false) }
            if (selection == null) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Match options") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Summary") }, leadingIcon = { Icon(Icons.Default.BarChart, null) },
                        onClick = { menu = false; onSummary() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (m.inStats) "Exclude from statistics" else "Count in statistics") },
                        onClick = { menu = false; onToggleStats() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

/** The pages of Settings, each opened from the Settings list. */
enum class SettingsGroup(val title: String) {
    TRAINING("Training"), ANALYSIS("Analysis"), ENGINE("Advanced engine settings"), PLAY("Play"), DISPLAY("Display"),
    POSITIONS("My positions"), DATA("Data and help"),
}

/** One line under a group's name in the Settings list: its main values. */
private fun SettingsGroup.summary(s: Settings): String = when (this) {
    SettingsGroup.TRAINING -> listOf(
        s.trainingMode.label,
        if (s.quietPlay) "analysis ${s.analysisTiming.label.lowercase()}" else s.feedback.label.lowercase(),
    ).joinToString(" · ")
    SettingsGroup.ANALYSIS -> "${s.analysisEngine.label} · ${s.checkerPlies}-ply checker play · ${s.cubePlies}-ply cube"
    SettingsGroup.ENGINE -> if (s.analysisEngine != EngineKind.PURETD) {
        "Background analysis shares the game's engine (${s.analysisEngine.label})"
    } else {
        val cores = Runtime.getRuntime().availableProcessors()
        "Game ${threadsOf(s.gameThreads, cores, false)} · analysis ${threadsOf(s.backgroundThreads, cores, true)}, " +
            "${s.backgroundPriority.label.lowercase()} priority" + if (s.analyzeDuringPlay) "" else ", between games"
    }
    SettingsGroup.PLAY -> listOfNotNull(
        "auto-roll".takeIf { s.autoRoll }, "forced moves".takeIf { s.autoForced }, "bear-off".takeIf { s.autoBearOff },
        when {
            s.confirmDouble && s.confirmPass -> "confirm double and pass"
            s.confirmDouble -> "confirm double"
            s.confirmPass -> "confirm pass"
            else -> null
        },
    ).joinToString(" · ").replaceFirstChar { it.uppercase() }.ifEmpty { "Nothing automatic" }
    SettingsGroup.DISPLAY -> listOfNotNull(
        s.boardTheme.label, "pip counts".takeIf { s.showPips }, "meter: ${s.meter.label.lowercase()}".takeIf { s.meter != MeterMode.OFF },
    ).joinToString(" · ")
    SettingsGroup.POSITIONS -> "Quiz, labels (${s.labelKeys.size} in use), folders linked to the blunder list"
    SettingsGroup.DATA -> "Your name in exported files, clearing data, error report"
}

/** Settings: the list of groups ([group] null) or one group's page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    app: PlyGroundApp, onBack: () -> Unit, onSpeedTest: () -> Unit = {},
    group: SettingsGroup? = null, onGroup: (SettingsGroup) -> Unit = {},
) {
    val settings by app.settings.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var clear by remember { mutableStateOf<ClearKind?>(null) }
    fun update(f: (Settings) -> Settings) = scope.launch { app.settings.update(f) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.title ?: "Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            when (group) {
                null -> SettingsGroup.entries.forEach { g ->
                    Column(Modifier.fillMaxWidth().clickable { onGroup(g) }.padding(vertical = 12.dp)) {
                        Text(g.title, style = MaterialTheme.typography.titleMedium)
                        Text(g.summary(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
                SettingsGroup.TRAINING -> TrainingSettings(s, ::update)
                SettingsGroup.ANALYSIS -> AnalysisSettings(s, ::update, onSpeedTest)
                SettingsGroup.ENGINE -> EngineSettings(s, ::update)
                SettingsGroup.PLAY -> PlaySettings(s, ::update)
                SettingsGroup.DISPLAY -> DisplaySettings(s, ::update)
                SettingsGroup.POSITIONS -> PositionsSettings(app, s, ::update)
                SettingsGroup.DATA -> DataSettings(s, ::update) { clear = it }
            }
        }
    }
    clear?.let { kind ->
        ClearDialog(kind, onDismiss = { clear = null }) { days ->
            clear = null
            scope.launch { kind.run(app, days) }
        }
    }
}

@Composable
private fun Note(text: String) =
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** A label and a row of chips, one per choice. */
@Composable
private fun <T> Chips(label: String, options: List<T>, selected: T, name: (T) -> String, enabled: Boolean = true, onPick: (T) -> Unit) {
    Text(
        label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp),
        color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options) { o -> FilterChip(selected = selected == o, onClick = { onPick(o) }, enabled = enabled, label = { Text(name(o)) }) }
    }
}

@Composable
private fun TrainingSettings(s: Settings, update: (f: (Settings) -> Settings) -> Unit) {
    Header("Training mode")
    TrainingMode.entries.forEach { t ->
        LevelRow(t.label, t.description, s.trainingMode == t) { update { it.copy(trainingMode = t) } }
    }
    Header("Which mistakes")
    val during = s.trainingMode != TrainingMode.AFTER_GAME
    FeedbackLevel.entries.forEach { f -> Radio(f.label, s.feedback == f, enabled = during) { update { it.copy(feedback = f) } } }
    if (s.trainingMode == TrainingMode.IMMEDIATE) {
        Toggle("Wait for me before the computer continues", s.pauseOnMistake) { v -> update { it.copy(pauseOnMistake = v) } }
    }
    if (during) {
        Toggle("Show the best move's arrows with my move", s.bestMoveArrows) { v -> update { it.copy(bestMoveArrows = v) } }
        Note("Otherwise the board shows only your move; pick the best move in the list to see it.")
    }
    if (!during) Note("After the match, the review lists your errors and blunders.")
    Note("Errors and blunders use eXtreme Gammon's thresholds: more than 0.020 and 0.080 equity (×1.5 in 1-point matches).")
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Header("Analysis during play")
    if (s.quietPlay) {
        AnalysisTiming.entries.forEach { t ->
            LevelRow(t.label, t.description, s.analysisTiming == t) { update { it.copy(analysisTiming = t) } }
        }
    }
    Note(
        if (s.quietPlay) "Applies while nothing is shown during play: after the match, or immediately with Which mistakes set to Off."
        else "Your decisions are analyzed as you play, to show your mistakes. With \"Show mistakes after the match\", or " +
            "Which mistakes set to Off, the analysis can run in the background instead, or not at all.",
    )
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Header("Blunder list")
    Chips("Add to my blunder list", BlunderListLevel.entries, s.blunderList, { it.label }) { v -> update { it.copy(blunderList = v) } }
    Note("Applies to your decisions from now on; the list keeps what it has.")
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Header("After each game")
    Toggle("Show the summary after each game", s.gameSummary) { v -> update { it.copy(gameSummary = v) } }
    Note("PR, rating, luck and errors for both players. The end of a match always shows it, and the game's ⋮ menu has the last game's summary.")
}

/** Settings → My positions: the quiz, the labels (automatic ones on or off, the player's own) and the linked folders. */
@Composable
private fun PositionsSettings(app: PlyGroundApp, s: Settings, update: (f: (Settings) -> Settings) -> Unit) {
    val scope = rememberCoroutineScope()
    Header("Quiz")
    Chips("A right answer is", QuizCorrect.entries, s.quizCorrect, { it.label }) { v -> update { it.copy(quizCorrect = v) } }
    Note("Not an error: anything within 0.020 of the best play (or your own answer, when you set one).")
    Toggle("Repeat missed positions until all are right", s.quizRepeat) { v -> update { it.copy(quizRepeat = v) } }
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Header("Automatic labels")
    Note(
        "Every blunder and saved position gets labels from what it is, for the Filter menus of My blunders, My positions " +
            "and the quiz. A position's labels can be changed on it. Turn off the ones you don't use: they leave the menus " +
            "and the positions' tags, and come back if you turn them on again. The themes are guesses by rule of thumb.",
    )
    AutoLabel.entries.groupBy { it.group }.forEach { (group, labels) ->
        Text(group.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        labels.forEach { l ->
            val on = l.name !in s.labelsOff
            Row(
                Modifier.fillMaxWidth().toggleable(on, role = Role.Switch) { v ->
                    update { it.copy(labelsOff = if (v) it.labelsOff - l.name else it.labelsOff + l.name) }
                }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(l.title)
                    Text(l.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = on, onCheckedChange = null)
            }
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Header("My labels")
    Note("Your own labels: put them on a saved position with its Labels button. They are in the Filter menus of My positions and the quiz.")
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    s.myLabels.forEach { name ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f))
            IconButton(onClick = { renaming = name }) { Icon(Icons.Default.Edit, "Rename $name") }
            IconButton(onClick = { deleting = name }) { Icon(Icons.Default.Delete, "Delete $name") }
        }
    }
    var newLabel by remember { mutableStateOf("") }
    fun taken(n: String) = s.myLabels.any { it.equals(n, ignoreCase = true) } || AutoLabel.entries.any { it.title.equals(n, ignoreCase = true) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newLabel, onValueChange = { newLabel = it.replace("\n", "").take(30) }, singleLine = true,
            label = { Text("New label") }, modifier = Modifier.weight(1f),
        )
        IconButton(onClick = {
            val n = newLabel.trim()
            if (n.isNotEmpty() && !taken(n)) update { it.copy(myLabels = it.myLabels + n) }
            newLabel = ""
        }, enabled = newLabel.isNotBlank() && !taken(newLabel.trim())) { Icon(Icons.Default.Add, "Add label") }
    }
    renaming?.let { old ->
        var text by remember(old) { mutableStateOf(old) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename label") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it.replace("\n", "").take(30) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val n = text.trim()
                    renaming = null
                    if (n.isNotEmpty() && n != old && !taken(n)) {
                        update { it.copy(myLabels = it.myLabels.map { l -> if (l == old) n else l }) }
                        scope.launch { app.positions.renameLabel(old, n) }
                    }
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { name ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \u201c$name\u201d?") },
            text = { Text("The label is taken off every position that has it. The positions stay.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    update { it.copy(myLabels = it.myLabels - name) }
                    scope.launch { app.positions.deleteLabel(name) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Header("Folders linked to the blunder list")
    Note(
        "Checker blunders and Cube blunders, and the folders the library button on My blunders makes: each one is linked to " +
            "a filter. Switched on, a folder also takes every new blunder that passes its filter.",
    )
    var linked by remember { mutableStateOf<List<CategoryCount>>(emptyList()) }
    LaunchedEffect(Unit) { linked = app.positions.categories().filter { it.filter != null } }
    linked.forEach { c ->
        Row(
            Modifier.fillMaxWidth().toggleable(c.autoAdd, role = Role.Switch) { v ->
                scope.launch {
                    app.positions.setAutoAdd(c.id, v)
                    linked = app.positions.categories().filter { it.filter != null }
                }
            }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(c.name)
                Text(
                    "Filter: ${Labels.folderName(Labels.decode(c.filter.orEmpty()))} · ${c.count} position${if (c.count == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = c.autoAdd, onCheckedChange = null)
        }
    }
}

@Composable
private fun AnalysisSettings(s: Settings, update: (f: (Settings) -> Settings) -> Unit, onSpeedTest: () -> Unit) {
    Text("Engine", style = MaterialTheme.typography.bodyMedium)
    EngineChips(s.analysisEngine, { e -> update { it.copy(analysisEngine = e) } }, Modifier.padding(vertical = 4.dp))
    Note(
        "Analyzes your games, hints and positions, and gives PR, errors and luck. The computer plays with " +
            "the engine chosen in New match. Analyze again in the review can use another engine.",
    )
    Chips("Checker play depth", (1..4).toList(), s.checkerPlies, { "$it-ply" }) { n -> update { it.copy(checkerPlies = n) } }
    if (s.checkerPlies == 4) {
        Text(FOUR_PLY_CHECKER_NOTE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
    if (s.checkerPlies > 1) {
        Chips("Pruning", PRUNING, s.moveFilter, { if (it == MoveFilter.ALL) "Off" else "On" }) { f -> update { it.copy(moveFilter = f) } }
        Text(PRUNING_NOTE, style = MaterialTheme.typography.labelSmall)
    }
    Chips("Cube decision depth", (1..4).toList(), s.cubePlies, { "$it-ply" }) { n -> update { it.copy(cubePlies = n) } }
    Note(
        "XG's ply numbering: 1-ply is the network alone, each further ply looks one roll deeper. Deeper is " +
            "stronger but slower; the speed test times each depth on this phone and recommends one.",
    )
    OutlinedButton(onClick = onSpeedTest, modifier = Modifier.padding(top = 4.dp)) { Text("Speed test and recommendation") }
    Toggle("Analyze the computer's decisions too", s.cpuAnalysis) { v -> update { it.copy(cpuAnalysis = v) } }
    Note(
        "Used for the computer's PR and its moves in review. Off (the default) makes the computer move " +
            "sooner and the phone stay cooler; it then has no PR. Analyze again in the review can still " +
            "analyze its moves later.",
    )
}

/** "8 threads (all)", "2 threads": a thread setting (0 = its default) on a phone with [cores] cores. */
private fun threadsOf(n: Int, cores: Int, background: Boolean): String {
    val v = if (n > 0) n.coerceAtMost(cores) else if (background) EngineService.defaultBackgroundThreads(cores) else cores
    return "$v thread${if (v == 1) "" else "s"}" + if (v == cores) " (all)" else ""
}

/** 1, 2, 4, … up to [cores], and [cores] itself. */
private fun threadChoices(cores: Int): List<Int> =
    (generateSequence(1) { it * 2 }.takeWhile { it < cores }.toList() + cores).distinct()

/**
 * The phone's cores by top speed, fastest first ("2 at 4.5 GHz, 6 at 3.5 GHz"), read from the
 * kernel's cpufreq files (any app may read them); null where the phone hides them.
 */
private fun coreSpeeds(cores: Int): String? = runCatching<String?> {
    val khz = (0 until cores).map { i ->
        java.io.File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLong()
    }
    if (khz.any { it <= 0 }) return null
    khz.groupingBy { it }.eachCount().entries.sortedByDescending { it.key }
        .joinToString(", ") { (f, n) -> "$n at ${"%.1f".format(java.util.Locale.ROOT, f / 1_000_000.0)} GHz" }
}.getOrNull()

/**
 * Advanced engine settings: how the game's searches and the background analysis share the
 * phone's cores. Only PureTD can run a second copy for the analysis, so the settings unlock with
 * PureTD as the analysis engine (the button switches to it).
 */
@Composable
private fun EngineSettings(s: Settings, update: (f: (Settings) -> Settings) -> Unit) {
    val cores = remember { Runtime.getRuntime().availableProcessors() }
    val speeds = remember { coreSpeeds(cores) }
    Text(
        "What this does: when nothing about your decisions is shown during play, they are analyzed in the background. " +
            "With PureTD as the analysis engine, that analysis runs on a second copy of PureTD on threads of its own, " +
            "so the computer's moves, your rolls and hints never wait for it. These settings decide how many of the " +
            "phone's cores each side may use, and how much the analysis gives way to the game.",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        "Why it is here: phones differ. The defaults suit most of them; if the game feels slow while the analysis " +
            "runs, or the phone gets warm, give the analysis fewer threads or a lower priority.",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
    )
    Text(
        "This phone: $cores cores" + (speeds?.let { " ($it)" } ?: "") + ".",
        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp),
    )
    val unlocked = s.analysisEngine == EngineKind.PURETD
    if (!unlocked) {
        Card(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "Multithreaded analysis is currently available only with PureTD. With ${s.analysisEngine.label} " +
                        "analyzing (Settings → Analysis), the background analysis shares the game's engine, so it runs " +
                        "only between games, while a summary is open.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = { update { it.copy(analysisEngine = EngineKind.PURETD) } }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Override analysis engine: use PureTD")
                }
                Note("You can still play against any engine (New match).")
            }
        }
    }
    val choices = threadChoices(cores)
    fun shown(n: Int, background: Boolean) =
        if (n > 0) n.coerceAtMost(cores) else if (background) EngineService.defaultBackgroundThreads(cores) else cores
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Chips("Game threads", choices, shown(s.gameThreads, false), { if (it == cores) "$it (all)" else "$it" }, enabled = unlocked) { n ->
        update { it.copy(gameThreads = if (n == cores) 0 else n) }
    }
    Note("For the computer's moves, hints and the analysis shown during play. Default: all cores.")
    Chips("Background analysis threads", choices, shown(s.backgroundThreads, true), { if (it == cores) "$it (all)" else "$it" }, enabled = unlocked) { n ->
        update { it.copy(backgroundThreads = if (n == EngineService.defaultBackgroundThreads(cores)) 0 else n) }
    }
    Note(
        "Default: ${EngineService.defaultBackgroundThreads(cores)}. More threads finish the queue sooner. Together the two may ask " +
            "for more threads than there are cores; the game still comes first with Low priority.",
    )
    Toggle("Analyze during play", s.analyzeDuringPlay, enabled = unlocked) { v -> update { it.copy(analyzeDuringPlay = v) } }
    Note("Off: the analysis waits for the end of each game and runs while the summary is open.")
    Chips("Background analysis priority", BackgroundPriority.entries, s.backgroundPriority, { it.label }, enabled = unlocked) { v ->
        update { it.copy(backgroundPriority = v) }
    }
    Note(
        "Low (the default): Android gives the game's searches the cores first, and may keep the analysis on the " +
            "slower cores. Normal: the analysis finishes sooner but competes with the game. Android always keeps the " +
            "phone itself responsive.",
    )
}

@Composable
private fun PlaySettings(s: Settings, update: (f: (Settings) -> Settings) -> Unit) {
    Toggle("Roll automatically when I can't double", s.autoRoll) { v -> update { it.copy(autoRoll = v) } }
    Note("In a game, the \"Keep rolling\" button beside the board also rolls when you could double, until you turn it off, the cube changes hands or the game ends.")
    Toggle("Submit my move as soon as it is complete", s.autoConfirm) { v -> update { it.copy(autoConfirm = v) } }
    Toggle("Play forced moves automatically", s.autoForced) { v -> update { it.copy(autoForced = v) } }
    Toggle("Bear off automatically when contact is broken", s.autoBearOff) { v -> update { it.copy(autoBearOff = v) } }
    Note("Plays the analysis engine's best bear-off for you; these moves are not counted in your PR. Cube decisions stay yours.")
    Toggle("Confirm before I double", s.confirmDouble) { v -> update { it.copy(confirmDouble = v) } }
    Toggle("Confirm before I pass", s.confirmPass) { v -> update { it.copy(confirmPass = v) } }
    Note("A bar across the board asks (\"Double to 2?\", \"Pass and lose 1 point?\") with Cancel and Double or Pass.")
    Chips("Autofinish: pause after each move", AutoFinishSpeed.entries, s.autoFinishSpeed, { it.label }) { a -> update { it.copy(autoFinishSpeed = a) } }
    Note(
        "Autofinish (in the game) lets the engine at 3-ply play your side to the end of the game; " +
            "tap the screen to stop. Its moves are not counted in your statistics.",
    )
}

@Composable
private fun DisplaySettings(s: Settings, update: (f: (Settings) -> Settings) -> Unit) {
    Toggle("Show pip counts", s.showPips) { v -> update { it.copy(showPips = v) } }
    Chips("Winning chances meter (a hint!)", MeterMode.entries, s.meter, { it.label }) { v -> update { it.copy(meter = v) } }
    Note("Your chances on the left, the computer's on the right; the darker parts are gammons and backgammons. With Bar, tap it to see the numbers.")
    Chips("Arrows for the computer's move", CpuArrows.entries, s.cpuArrows, { it.label }) { v -> update { it.copy(cpuArrows = v) } }
    Chips("Computer move animation", AnimationSpeed.entries, s.animation, { it.label }) { a -> update { it.copy(animation = a) } }
    Toggle("Crawford sign", s.crawfordSign) { v -> update { it.copy(crawfordSign = v) } }
    Note("In the Crawford game nobody may double, so a CRAWFORD sign stands on the bar instead of the cube. Off: the bar is left empty.")
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Header("Board")
    BoardSettings(s) { f -> update(f) }
}

@Composable
private fun DataSettings(s: Settings, update: (f: (Settings) -> Settings) -> Unit, onClear: (ClearKind) -> Unit) {
    Header("Exported files")
    var name by remember { mutableStateOf(s.playerName) }
    OutlinedTextField(
        value = name,
        onValueChange = { v -> name = v.take(30); update { it.copy(playerName = name) } },
        label = { Text("Your name in exported files") },
        supportingText = { Text("Shown in .mat files for eXtreme Gammon and GNU Backgammon; the computer is named after its engine and PR, e.g. \"PureTD PR 4\" or \"GNU BG PR <1\".") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Header("Data")
    ClearKind.entries.forEach { k ->
        OutlinedButton(onClick = { onClear(k) }, modifier = Modifier.padding(vertical = 2.dp)) {
            Icon(Icons.Default.DeleteSweep, null)
            Spacer(Modifier.width(6.dp))
            Text(k.title)
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Header("Problems")
    val context = LocalContext.current
    OutlinedButton(onClick = { AppLog.sendReport(context) }) { Text("Send error report") }
    Note(
        "Opens your email app with a message to ${AppLog.SUPPORT_EMAIL} and the app's log attached " +
            "(recent game events, engine problems and crashes, kept only on this phone). " +
            "Nothing is sent until you send the email.",
    )
}

/** The "clear" actions, offered in Settings and in the Match Library. */
private enum class ClearKind(val title: String, val button: String, val question: String, val text: String) {
    GAME_RECORDS(
        "Clear game records (keep statistics)", "Clear game records", "Clear game records?",
        "The move-by-move records of finished matches are deleted: those matches leave the Match Library and can no longer " +
            "be reviewed or exported. Their results, your statistics and rating, and your blunder list are kept. " +
            "This cannot be undone.",
    ),
    STATISTICS(
        "Clear statistics (keep games)", "Clear statistics", "Clear statistics?",
        "Statistics and rating start again from zero. Finished matches stay in the Match Library for review, marked " +
            "\"not in statistics\"; a match can be counted again from its menu there. The match in progress still counts.",
    ),
    MATCHES(
        "Delete all matches (games and statistics)", "Delete all matches", "Delete all matches?",
        "All finished matches and games will be deleted, and with them your statistics and rating. " +
            "The match in progress and your blunder list are kept. This cannot be undone.",
    ),
    BLUNDERS(
        "Clear blunder list", "Clear list", "Clear blunder list?",
        "All saved blunders will be removed. Your matches and statistics are kept. This cannot be undone.",
    );

    /** [olderThanDays]: for game records, null = all. */
    suspend fun run(app: PlyGroundApp, olderThanDays: Int?) = when (this) {
        GAME_RECORDS -> app.repository.clearGameRecords(olderThanDays)
        STATISTICS -> app.repository.clearStatistics()
        MATCHES -> app.repository.clearMatchHistory()
        BLUNDERS -> app.repository.clearBlunders()
    }
}

@Composable
private fun ClearDialog(kind: ClearKind, onDismiss: () -> Unit, onConfirm: (olderThanDays: Int?) -> Unit) {
    var days by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(kind.question) },
        text = {
            Column {
                Text(kind.text)
                if (kind == ClearKind.GAME_RECORDS) {
                    Spacer(Modifier.height(8.dp))
                    listOf(null to "All finished matches", 30 to "Matches older than 30 days", 90 to "Matches older than 90 days")
                        .forEach { (d, label) -> Radio(label, days == d) { days = d } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(days) }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Header(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))

@Composable
private fun Radio(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Text(label, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onSpeedTest: () -> Unit = {}, onWelcome: () -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            // The company logo, in the version made for the theme's background (brand kit), with clear space around it.
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(
                    if (androidx.compose.foundation.isSystemInDarkTheme()) com.boldblot.backgammonplyground.R.drawable.bold_blot_logo_dark
                    else com.boldblot.backgammonplyground.R.drawable.bold_blot_logo_light,
                ),
                contentDescription = "Bold Blot Software",
                modifier = Modifier.padding(vertical = 16.dp).width(240.dp),
            )
            Text("Backgammon PlyGround", style = MaterialTheme.typography.titleLarge)
            Text(
                "Version ${com.boldblot.backgammonplyground.BuildConfig.VERSION_NAME}, build ${com.boldblot.backgammonplyground.BuildConfig.BUILD_LABEL}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onWelcome) { Text("Welcome screen") }
            Spacer(Modifier.height(8.dp))
            Text(
                "Three backgammon engines play and analyze, each with its own neural networks and cube handling:",
            )
            Text(
                "• PureTD by Alexander Strehl: its neural networks and move generator, with Bold Blot's own search, " +
                    "bear-off database, cube handling and match equity table.\n" +
                    "• GNU Backgammon by the GNU Backgammon authors: its neural networks, bear-off database, cubeful " +
                    "evaluation and the Kazaross XG2 match equity table.\n" +
                    "• Open Sage by Mark Higgins: its production networks (stage 11), bear-off database, cubeful " +
                    "search and match equity table.\n" +
                    "Licences below.",
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Performance Rating (PR) and the error/blunder categories follow the definitions published in the " +
                    "eXtreme Gammon 2 documentation. eXtreme Gammon is a trademark of GameSite 2000 Ltd.; this app is " +
                    "not affiliated with it.",
            )
            Spacer(Modifier.height(8.dp))
            Text("Dice are generated with Java's SecureRandom.")
            Spacer(Modifier.height(16.dp))
            Text("PureTD", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "PureTD backgammon engine by Alexander Strehl (github.com/alexstrehl/backgammon-ai-engine). " +
                    "Copyright © 2026 Alex Strehl, MIT licence: permission is hereby " +
                    "granted, free of charge, to any person obtaining a copy of this software and associated " +
                    "documentation files (the \"Software\"), to deal in the Software without restriction, including " +
                    "without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, " +
                    "and/or sell copies of the Software, and to permit persons to whom the Software is furnished to " +
                    "do so, subject to the following conditions: the above copyright notice and this permission " +
                    "notice shall be included in all copies or substantial portions of the Software. THE SOFTWARE IS " +
                    "PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED " +
                    "TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO " +
                    "EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER " +
                    "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN " +
                    "CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(16.dp))
            Text("GNU Backgammon", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "GNU Backgammon (gnu.org/software/gnubg), copyright © 1998-2003 Gary Wong and © 2000-2022 the GNU " +
                    "Backgammon authors: Joseph Heled, Oystein Johansen, Jonathan Kinsey, David Montgomery, Jim " +
                    "Segrave, Joern Thyssen, Gary Wong, Christian Anthon, Michael Petch, Philippe Michel and many " +
                    "contributors. Free software under the GNU General Public License, version 3 or later. This app " +
                    "uses its evaluation code, neural network weights and one-sided bear-off database, and the " +
                    "Kazaross XG2 match equity table distributed with it.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(16.dp))
            Text("Open Sage", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Open Sage backgammon engine by Mark Higgins (github.com/markbgsage/bgsage), copyright © 2026 Mark " +
                    "Higgins, Mozilla Public License 2.0 (mozilla.org/MPL/2.0), covering its code, trained networks " +
                    "and bear-off database. The source of the version used, unmodified, is in this app's source code " +
                    "(github.com/BoldBlot/backgammon-plyground, folder engine/sage).",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSpeedTest) { Text("Engine speed test") }
            Spacer(Modifier.height(16.dp))
            Text(
                "Backgammon PlyGround is made by Bold Blot (boldblot.com, support@boldblot.com), free software under " +
                    "the GNU General Public License, version 3 or later; source code at " +
                    "github.com/BoldBlot/backgammon-plyground. Titles use the Archivo " +
                    "typeface, copyright The Archivo Project Authors, under the SIL Open Font License 1.1 (included " +
                    "in the app as licenses/archivo-OFL.txt).",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
