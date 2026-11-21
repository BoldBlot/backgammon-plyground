package com.boldblot.backgammonplyground.ui.sets

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boldblot.backgammonplyground.PendingSet
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.PositionSeries
import com.boldblot.backgammonplyground.data.PositionSetEntity
import com.boldblot.backgammonplyground.data.SavedPosition
import com.boldblot.backgammonplyground.data.SetCount
import com.boldblot.backgammonplyground.data.Settings
import com.boldblot.backgammonplyground.ui.positions.EmptyListText
import com.boldblot.backgammonplyground.ui.positions.FolderAction
import com.boldblot.backgammonplyground.ui.positions.FolderScaffold
import com.boldblot.backgammonplyground.ui.positions.ListLoading
import com.boldblot.backgammonplyground.ui.positions.PositionRow
import com.boldblot.backgammonplyground.ui.positions.RowAction
import com.boldblot.backgammonplyground.ui.positions.SaveAllToPositionsDialog
import com.boldblot.backgammonplyground.ui.positions.SaveToPositionsDialog
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** A set quiz: the positions of set [setId], in its order or [shuffle]d; with [friend] nothing is kept. */
data class SetQuizSpec(val setId: Long, val shuffle: Boolean = false, val friend: Boolean = false) {
    fun encode(): String = "$setId;${if (shuffle) 1 else 0};${if (friend) 1 else 0}"

    companion object {
        fun decode(text: String): SetQuizSpec {
            val p = text.split(';')
            return SetQuizSpec(p.getOrNull(0)?.toLongOrNull() ?: 0L, p.getOrNull(1) == "1", p.getOrNull(2) == "1")
        }
    }
}

/** What a set import reads, for the help text and errors. */
const val SET_IMPORT_FORMATS = "A set is text with one position per line, in order: an XGID (XGID=…) or a GNU Backgammon " +
    "Position ID with its Match ID. Lines that are not positions are skipped."

/** "12 positions · 7-pt match · Anna vs Bob · 21 Nov 2026": a set's line in the list. */
internal fun setSummary(s: SetCount): String = listOfNotNull(
    "${s.count} position${if (s.count == 1) "" else "s"}",
    if (!s.fromMatch) null else if (s.matchLength > 0) "${s.matchLength}-pt match" else "money",
    if (s.fromMatch) "${s.bottomName.ifBlank { "Bottom" }} vs ${s.topName.ifBlank { "Top" }}" else null,
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(s.createdAt)),
).joinToString(" · ")

/**
 * The Match Library's Sets: series of positions imported together (pasted, from a text file, or sent by another
 * app), each opened like a folder. [importing] asks for the import menu (the library's top bar has the button).
 */
@Composable
fun SetsPane(app: PlyGroundApp, importing: Boolean, onImportingDone: () -> Unit, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var sets by remember { mutableStateOf<List<SetCount>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { sets = app.sets.sets() }
    val pending by app.pendingSet.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }.getOrNull()
            if (text == null) Toast.makeText(context, "The file could not be read.", Toast.LENGTH_SHORT).show()
            else app.pendingSet.value = PendingSet(text)
        }
    }
    if (importing) AlertDialog(
        onDismissRequest = onImportingDone,
        title = { Text("Import a set") },
        text = { Text(SET_IMPORT_FORMATS) },
        confirmButton = {
            TextButton(onClick = {
                onImportingDone()
                val text = clipboard.getText()?.text
                if (text.isNullOrBlank()) Toast.makeText(context, "The clipboard has no text.", Toast.LENGTH_SHORT).show()
                else app.pendingSet.value = PendingSet(text)
            }) { Text("Paste") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onImportingDone) { Text("Cancel") }
                TextButton(onClick = { onImportingDone(); picker.launch(arrayOf("text/*", "application/octet-stream")) }) { Text("Text file…") }
            }
        },
    )
    pending?.let { p ->
        SetImportDialog(app, p, onDismiss = { app.pendingSet.value = null }) { id ->
            app.pendingSet.value = null
            reload++
            onOpen(id)
        }
    }
    val list = sets
    when {
        list == null -> ListLoading(modifier)
        list.isEmpty() -> Text(
            "No sets yet. A set is a series of positions imported together, for example the positions of one match. " +
                "Import one with the button at the top. $SET_IMPORT_FORMATS",
            modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> LazyColumn(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(list, key = { it.id }) { s ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpen(s.id) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(setSummary(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** A new set from [pending]: its name, and whether it is from one match (then the players' names and the length). */
@Composable
fun SetImportDialog(app: PlyGroundApp, pending: PendingSet, onDismiss: () -> Unit, onCreated: (Long) -> Unit) {
    val settings by app.settings.settings.collectAsState(initial = null)
    val preview = remember(pending) { PositionSeries.read(pending.text) }
    val scope = rememberCoroutineScope()
    val length = preview.matchLength
    var name by remember(pending) { mutableStateOf(pending.name ?: "Set ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())}") }
    var fromMatch by remember(pending) { mutableStateOf(length != null) }
    var bottom by remember(pending) { mutableStateOf(pending.bottomName.orEmpty()) }
    var top by remember(pending) { mutableStateOf(pending.topName ?: "Opponent") }
    LaunchedEffect(settings == null) { if (bottom.isEmpty()) settings?.let { bottom = it.playerName } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New set") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val n = preview.items.size
                Text(
                    "$n position${if (n == 1) "" else "s"}, in the order given" +
                        (if (preview.skipped > 0) " (${preview.skipped} line${if (preview.skipped == 1) "" else "s"} could not be read)" else "") +
                        ". A set is not part of My positions, and its quiz answers are not in the statistics.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (n == 0) Text(SET_IMPORT_FORMATS, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                OutlinedTextField(name, { name = it.take(60) }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().clickable(enabled = length != null) { fromMatch = !fromMatch }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("From one match", style = MaterialTheme.typography.titleSmall)
                        Text(
                            when (length) {
                                null -> "The positions have different match lengths."
                                0 -> "Money game: names who decides in each position."
                                else -> "$length-pt match: names who decides and the score in each position."
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = fromMatch && length != null, onCheckedChange = { fromMatch = it }, enabled = length != null)
                }
                if (fromMatch && length != null) {
                    OutlinedTextField(bottom, { bottom = it.take(40) }, singleLine = true, label = { Text("Player at the bottom") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(top, { top = it.take(40) }, singleLine = true, label = { Text("Player at the top") }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(enabled = preview.items.isNotEmpty(), onClick = {
                scope.launch {
                    val match = fromMatch && length != null
                    onCreated(app.sets.create(name, preview.items, match, bottom, top, length ?: 0))
                }
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * A set, listed like a folder of My positions: its positions in order (the number, and in a set from one match who
 * decides and the score), with the quiz on top. A position opens in Analyze Position; its folder button saves it
 * to My positions (it stays in the set too).
 */
@Composable
fun SetScreen(app: PlyGroundApp, setId: Long, onBack: () -> Unit, onQuiz: (SetQuizSpec) -> Unit) {
    val settings by app.settings.settings.collectAsState(initial = Settings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var set by remember { mutableStateOf<PositionSetEntity?>(null) }
    var cards by remember { mutableStateOf<List<SavedPosition>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(setId, reload) {
        set = app.sets.set(setId)
        cards = app.sets.cards(setId)
    }
    var quizzing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var savingAll by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf<SavedPosition?>(null) }
    val list = cards
    FolderScaffold(
        title = (set?.name ?: "") + (list?.let { " (${it.size})" } ?: ""), onBack = onBack, railRight = settings.railRight,
        topActions = {
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Name and players…") }, onClick = { menu = false; editing = true })
                    DropdownMenuItem(text = { Text("Delete the set") }, onClick = { menu = false; deleting = true })
                }
            }
        },
        // The quiz on top, as asked for a set (a folder has it at the bottom).
        options = list.takeUnless { it.isNullOrEmpty() }?.let {
            {
                Button(onClick = { quizzing = true }, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.School, null)
                    Text("Quiz", Modifier.padding(start = 6.dp))
                }
                set?.let { s ->
                    Text(
                        if (s.fromMatch) "${s.bottomName.ifBlank { "Bottom" }} vs ${s.topName.ifBlank { "Top" }}" + if (s.matchLength > 0) " · ${s.matchLength}-pt match" else " · money"
                        else "Not from one match",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        actions = list.takeUnless { it.isNullOrEmpty() }?.let {
            {
                FolderAction("Save all to My positions", Icons.Default.Folder) { savingAll = true }
                FolderAction("Delete set", Icons.Default.Delete) { deleting = true }
            }
        },
    ) { area ->
        when {
            list == null -> ListLoading(area)
            list.isEmpty() -> EmptyListText("This set has no positions.", filtered = false, modifier = area)
            else -> LazyColumn(area) {
                items(list, key = { it.id }) { p ->
                    PositionRow(p, settings, actions = { RowAction(Icons.Default.Folder, "Save to My positions") { saving = p } }) {
                        // Analyze Position with the position as the set has it (a take or pass with the double on offer).
                        app.pendingAnalyze.value = PositionSeries.analyzeId(p.position)
                    }
                }
            }
        }
    }
    saving?.let { p -> SaveToPositionsDialog(app, p.position, "Set: ${p.entity.source}", analysis = p.analysis) { saving = null } }
    if (savingAll) list?.let { l ->
        SaveAllToPositionsDialog(app, l, "Set: ${set?.name.orEmpty()}") { savingAll = false }
    }
    if (quizzing) SetQuizDialog(onDismiss = { quizzing = false }) { shuffle, friend ->
        quizzing = false
        onQuiz(SetQuizSpec(setId, shuffle, friend))
    }
    if (editing) set?.let { s ->
        SetDetailsDialog(s, length = list?.mapNotNull { it.position.setup.matchLength }?.distinct()?.singleOrNull(), onDismiss = { editing = false }) { changed ->
            editing = false
            scope.launch { app.sets.update(changed); reload++ }
        }
    }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Delete the set?") },
        text = { Text("“${set?.name.orEmpty()}” and its ${list?.size ?: 0} positions go. Positions saved to My positions stay there. This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = {
                deleting = false
                scope.launch {
                    app.sets.delete(setId)
                    Toast.makeText(context, "Set deleted.", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
    )
}

/** The set quiz's choices: in the set's order or shuffled, and Quiz a friend (nothing kept). */
@Composable
private fun SetQuizDialog(onDismiss: () -> Unit, onStart: (shuffle: Boolean, friend: Boolean) -> Unit) {
    var shuffle by remember { mutableStateOf(false) }
    var friend by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quiz this set") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth().clickable { shuffle = false }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(!shuffle, { shuffle = false }); Text("In the set's order")
                }
                Row(Modifier.fillMaxWidth().clickable { shuffle = true }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(shuffle, { shuffle = true }); Text("Shuffled")
                }
                Row(Modifier.fillMaxWidth().clickable { friend = !friend }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Quiz a friend", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (friend) "Nothing is kept: the set's positions keep the answers they had."
                            else "The set keeps the last answer to each position. Set quizzes are never in the statistics.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = friend, onCheckedChange = { friend = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onStart(shuffle, friend) }) { Text("Start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A set's name, and whether it is from one match with whose names. */
@Composable
private fun SetDetailsDialog(s: PositionSetEntity, length: Int?, onDismiss: () -> Unit, onSave: (PositionSetEntity) -> Unit) {
    var name by remember { mutableStateOf(s.name) }
    var fromMatch by remember { mutableStateOf(s.fromMatch) }
    var bottom by remember { mutableStateOf(s.bottomName) }
    var top by remember { mutableStateOf(s.topName) }
    val canMatch = s.fromMatch || length != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name and players") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it.take(60) }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().clickable(enabled = canMatch) { fromMatch = !fromMatch }, verticalAlignment = Alignment.CenterVertically) {
                    Text("From one match", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Switch(checked = fromMatch, onCheckedChange = { fromMatch = it }, enabled = canMatch)
                }
                if (!canMatch) Text("The positions have different match lengths.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (fromMatch) {
                    OutlinedTextField(bottom, { bottom = it.take(40) }, singleLine = true, label = { Text("Player at the bottom") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(top, { top = it.take(40) }, singleLine = true, label = { Text("Player at the top") }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    s.copy(
                        name = name.trim().ifBlank { s.name }, fromMatch = fromMatch,
                        bottomName = if (fromMatch) bottom.trim() else "", topName = if (fromMatch) top.trim() else "",
                        matchLength = if (fromMatch) (length ?: s.matchLength) else 0,
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
