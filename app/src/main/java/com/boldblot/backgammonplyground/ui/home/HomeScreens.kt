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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.CpuLevel
import com.boldblot.backgammonplyground.core.EngineKind
import com.boldblot.backgammonplyground.core.MatchRules
import com.boldblot.backgammonplyground.core.MistakeRate
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.Opponent
import com.boldblot.backgammonplyground.core.Elo
import com.boldblot.backgammonplyground.core.PerformanceTotals
import com.boldblot.backgammonplyground.data.AnimationSpeed
import com.boldblot.backgammonplyground.data.AppJson
import com.boldblot.backgammonplyground.data.AutoFinishSpeed
import com.boldblot.backgammonplyground.data.BlunderSort
import com.boldblot.backgammonplyground.data.FeedbackLevel
import com.boldblot.backgammonplyground.data.MatchEntity
import com.boldblot.backgammonplyground.data.MatchStatus
import com.boldblot.backgammonplyground.data.SavedBlunder
import com.boldblot.backgammonplyground.data.Settings
import com.boldblot.backgammonplyground.data.TrainingMode
import com.boldblot.backgammonplyground.ui.components.EngineChips
import com.boldblot.backgammonplyground.ui.components.FOUR_PLY_CHECKER_NOTE
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
                    "Version ${com.boldblot.backgammonplyground.BuildConfig.VERSION_NAME} (build ${com.boldblot.backgammonplyground.BuildConfig.VERSION_CODE}) · by Bold Blot · boldblot.com",
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
                OutlinedButton(onClick = onStats, modifier = w) { Text("Statistics") }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onHistory, modifier = w) { Text("History & review") }
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
fun NewMatchScreen(app: PlyGroundApp, hasMatch: Boolean, onStart: (MatchRules, CpuLevel, MistakeRate?) -> Unit, onBack: () -> Unit) {
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
    var engine by remember { mutableStateOf(EngineKind.PURETD) }
    var confirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun start() {
        scope.launch {
            app.settings.update {
                it.copy(
                    matchLength = length, cpuLevel = level, useCube = cube, jacoby = jacoby, crawford = crawford,
                    beavers = beavers, raccoons = raccoons, mistakes = mistakes,
                )
            }
            onStart(MatchRules(length = length, useCube = cube, jacoby = jacoby, crawford = crawford, beavers = beavers, raccoons = raccoons), level, mistakes)
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
fun HistoryScreen(app: PlyGroundApp, onReview: (Long) -> Unit, onBlunder: (Long) -> Unit, onBack: () -> Unit) {
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
    val scope = rememberCoroutineScope()
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
                title = { Text("History") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (tab == 0 && !matches.isNullOrEmpty()) {
                        IconButton(onClick = { selecting = true }) { Icon(Icons.Default.Share, "Export matches") }
                    }
                    // Clears what the open tab shows.
                    val empty = if (tab == 0) matches.isNullOrEmpty() else blunders.isNullOrEmpty()
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { if (tab == 0) menu = true else clear = ClearKind.BLUNDERS }, enabled = !empty) {
                            Icon(Icons.Default.DeleteSweep, null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (tab == 0) "Clear…" else ClearKind.BLUNDERS.button)
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
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("My blunders" + (blunders?.let { " (${it.size})" } ?: "")) })
            }
            val list = matches
            when {
                tab == 1 -> blunders?.let {
                    Column {
                        if (it.isNotEmpty()) {
                            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                BlunderSort.entries.forEach { o ->
                                    FilterChip(
                                        selected = sort == o, label = { Text(o.label) },
                                        onClick = { scope.launch { app.settings.update { s -> s.copy(blunderSort = o) } } },
                                    )
                                }
                            }
                        }
                        BlunderList(it, onBlunder)
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
                        if (m.inStats) "" else " · not in statistics",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: PlyGroundApp, onBack: () -> Unit, onSpeedTest: () -> Unit = {}) {
    val settings by app.settings.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var clear by remember { mutableStateOf<ClearKind?>(null) }
    fun update(f: (Settings) -> Settings) = scope.launch { app.settings.update(f) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
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
                Text(
                    "Otherwise the board shows only your move; pick the best move in the list to see it.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!during) {
                Text(
                    "After the match, the review lists your errors and blunders.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Errors and blunders use eXtreme Gammon's thresholds: more than 0.020 and 0.080 equity (×1.5 in 1-point matches).",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Header("After each game")
            Toggle("Show the summary after each game", s.gameSummary) { v -> update { it.copy(gameSummary = v) } }
            Text(
                "PR, rating, luck and errors for both players. The end of a match always shows it, and the game's ⋮ menu has the last game's summary.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Header("Exported files")
            var name by remember { mutableStateOf(s.playerName) }
            OutlinedTextField(
                value = name,
                onValueChange = { v -> name = v.take(30); update { it.copy(playerName = name) } },
                label = { Text("Your name in exported files") },
                supportingText = { Text("Shown in .mat files for eXtreme Gammon and GNU Backgammon; the computer is named after its engine and PR, e.g. \"PureTD PR 4\".") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Header("Analysis")
            Text("Checker play depth", style = MaterialTheme.typography.bodyMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items((1..4).toList()) { n ->
                    FilterChip(selected = s.checkerPlies == n, onClick = { update { it.copy(checkerPlies = n) } }, label = { Text("$n-ply") })
                }
            }
            if (s.checkerPlies == 4) {
                Text(
                    FOUR_PLY_CHECKER_NOTE,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                )
            }
            if (s.checkerPlies > 1) {
                Text("Moves searched to that depth", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MoveFilter.entries) { f ->
                        FilterChip(
                            selected = s.moveFilter == f, onClick = { update { it.copy(moveFilter = f) } },
                            label = { Text(if (f == MoveFilter.ALL) "All" else f.label) },
                        )
                    }
                }
            }
            Text("Cube decision depth", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items((1..4).toList()) { n ->
                    FilterChip(selected = s.cubePlies == n, onClick = { update { it.copy(cubePlies = n) } }, label = { Text("$n-ply") })
                }
            }
            Text(
                "XG's ply numbering: 1-ply is the network alone, each further ply looks one roll deeper. " +
                    "±0.04 / ±0.08 / ±0.16: only moves within that equity of the best at 1-ply are searched " +
                    "deeper (much faster on doubles, rarely a different answer). Deeper is stronger but " +
                    "slower; the speed test times each depth on this phone and recommends one.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onSpeedTest, modifier = Modifier.padding(top = 4.dp)) { Text("Speed test and recommendation") }
            Toggle("Analyze the computer's decisions too", s.cpuAnalysis) { v -> update { it.copy(cpuAnalysis = v) } }
            Text(
                "Used for the computer's PR and its moves in review. Off (the default) makes the computer move " +
                    "sooner and the phone stay cooler; it then has no PR. Analyze again in the review can still " +
                    "analyze its moves later.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Header("Play")
            Toggle("Roll automatically when I can't double, or I'm on the bar against a closed board", s.autoRoll) { v -> update { it.copy(autoRoll = v) } }
            Toggle("Submit my move as soon as it is complete", s.autoConfirm) { v -> update { it.copy(autoConfirm = v) } }
            Toggle("Play forced moves automatically", s.autoForced) { v -> update { it.copy(autoForced = v) } }
            Toggle("Show pip counts", s.showPips) { v -> update { it.copy(showPips = v) } }
            Toggle("Show winning chances meter (a hint!)", s.showMeter) { v -> update { it.copy(showMeter = v) } }
            Text("Computer move animation", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(AnimationSpeed.entries) { a ->
                    FilterChip(selected = s.animation == a, onClick = { update { it.copy(animation = a) } }, label = { Text(a.label) })
                }
            }
            Text("Autofinish: pause after each move", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(AutoFinishSpeed.entries) { a ->
                    FilterChip(selected = s.autoFinishSpeed == a, onClick = { update { it.copy(autoFinishSpeed = a) } }, label = { Text(a.label) })
                }
            }
            Text(
                "Autofinish (in the game) lets the engine at 3-ply play your side to the end of the game; " +
                    "tap the screen to stop. Its moves are not counted in your statistics.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Header("Board")
            BoardSettings(s, ::update)
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Header("Data")
            ClearKind.entries.forEach { k ->
                OutlinedButton(onClick = { clear = k }, modifier = Modifier.padding(vertical = 2.dp)) {
                    Icon(Icons.Default.DeleteSweep, null)
                    Spacer(Modifier.width(6.dp))
                    Text(k.title)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Header("Problems")
            val context = LocalContext.current
            OutlinedButton(onClick = { AppLog.sendReport(context) }) { Text("Send error report") }
            Text(
                "Opens your email app with a message to ${AppLog.SUPPORT_EMAIL} and the app's log attached " +
                    "(recent game events, engine problems and crashes, kept only on this phone). " +
                    "Nothing is sent until you send the email.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    clear?.let { kind ->
        ClearDialog(kind, onDismiss = { clear = null }) { days ->
            clear = null
            scope.launch { kind.run(app, days) }
        }
    }
}

/** The "clear" actions, offered in Settings and in History. */
private enum class ClearKind(val title: String, val button: String, val question: String, val text: String) {
    GAME_RECORDS(
        "Clear game records (keep statistics)", "Clear game records", "Clear game records?",
        "The move-by-move records of finished matches are deleted: those matches leave History and can no longer " +
            "be reviewed or exported. Their results, your statistics and rating, and your blunder list are kept. " +
            "This cannot be undone.",
    ),
    STATISTICS(
        "Clear statistics (keep games)", "Clear statistics", "Clear statistics?",
        "Statistics and rating start again from zero. Finished matches stay in History for review, marked " +
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
fun AboutScreen(onBack: () -> Unit, onSpeedTest: () -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Backgammon PlyGround", style = MaterialTheme.typography.titleLarge)
            Text(
                "Version ${com.boldblot.backgammonplyground.BuildConfig.VERSION_NAME}, build ${com.boldblot.backgammonplyground.BuildConfig.VERSION_CODE}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "The computer opponent and all analysis use the PureTD backgammon engine by Alexander Strehl " +
                    "(its neural networks and move generator; licence below), with Bold Blot's own search, bear-off " +
                    "database, cube handling and match equity table.",
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
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSpeedTest) { Text("Engine speed test") }
            Spacer(Modifier.height(16.dp))
            Text(
                "Backgammon PlyGround is made by Bold Blot (boldblot.com, support@boldblot.com). Titles use the Archivo " +
                    "typeface, copyright The Archivo Project Authors, under the SIL Open Font License 1.1 (included " +
                    "in the app as licenses/archivo-OFL.txt).",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
