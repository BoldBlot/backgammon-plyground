package com.boldblot.backgammonplyground.ui.home

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.AutoLabel
import com.boldblot.backgammonplyground.core.Labels
import com.boldblot.backgammonplyground.core.MatImport
import com.boldblot.backgammonplyground.data.SavedBlunder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** True if [text] looks like a .mat match file (rather than a positions file). */
fun looksLikeMatch(text: String): Boolean =
    Regex("""^\s*\d+\s+point\s+match""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).containsMatchIn(text) &&
        Regex("""^\s*Game\s+\d+""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).containsMatchIn(text)

/**
 * Importing a .mat file into the Match Library: which of the two players is "you", and whose
 * mistakes go to New errors. [onImport] gets the column chosen (0 = the first player) and
 * whether the opponent's mistakes go on the list too.
 */
@Composable
fun MatchImportDialog(match: MatImport.ParsedMatch, onDismiss: () -> Unit, onImport: (you: Int, both: Boolean) -> Unit) {
    var you by remember { mutableIntStateOf(1) }
    var both by remember { mutableStateOf(false) }
    val r = match.rules
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import match") },
        text = {
            Column {
                Text(
                    (if (r.isMoney) "Money session" else "${r.length}-point match") +
                        " · ${match.games.size} game${if (match.games.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Which player are you?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                match.names.forEachIndexed { i, name ->
                    Row(Modifier.fillMaxWidth().clickable { you = i }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = you == i, onClick = { you = i })
                        Text(name)
                    }
                }
                Text("Add to New errors", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !both, onClick = { both = false }, label = { Text("My mistakes") })
                    FilterChip(selected = both, onClick = { both = true }, label = { Text("Both players' mistakes") })
                }
                Text(
                    "The match is analyzed in the background, at the depths in Settings → Analysis; its PR and mistakes " +
                        "fill in as it goes. It is left out of your statistics and rating (its menu can count it). " +
                        "The opponent's mistakes are shown from their side and labelled Opponent's.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onImport(you, both) }) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * New errors' library button: [errors] (those the filter shows) are copied to My positions with
 * their labels, split into Checker blunders and Cube blunders or all into All blunders, and leave
 * New errors. [onAdded] runs once they are gone from the list.
 */
@Composable
fun AddErrorsDialog(app: PlyGroundApp, errors: List<SavedBlunder>, onDismiss: () -> Unit, onAdded: () -> Unit) {
    val context = LocalContext.current
    val cube = errors.count { AutoLabel.CUBE.name in it.labels }
    val checker = errors.size - cube
    val n = errors.size
    fun add(split: Boolean) {
        onDismiss()
        app.appScope.launch {
            val jacoby: suspend (Long) -> Boolean = { m -> app.repository.match(m)?.jacoby ?: true }
            val text = if (split) {
                // The two folders every player has (made again if they were deleted).
                app.positions.makeBuiltInFolders()
                var added = 0
                for (kind in listOf(AutoLabel.CHECKER, AutoLabel.CUBE)) {
                    val list = errors.filter { kind.name in it.labels }
                    val id = app.positions.linkedFolder(setOf(kind.name), create = true) ?: continue
                    if (list.isNotEmpty()) added += app.positions.addBlunders(list, id, jacoby)
                }
                if (added == 0) "All of them were already there." else "Added $added to Checker blunders and Cube blunders."
            } else {
                val id = app.positions.linkedFolder(emptySet(), create = true) ?: return@launch
                val added = app.positions.addBlunders(errors, id, jacoby)
                val name = Labels.folderName(emptySet())
                if (added == 0) "All of them were already in \u201c$name\u201d." else "Added $added to \u201c$name\u201d."
            }
            app.repository.removeBlunders(errors.map { it.entity.id })
            withContext(Dispatchers.Main) {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                onAdded()
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add $n error${if (n == 1) "" else "s"} to My positions") },
        text = {
            Text(
                "Split them into Checker blunders ($checker) and Cube blunders ($cube)? Otherwise they all go to " +
                    "All blunders. Positions already in the folder are skipped; each one keeps its labels. " +
                    "Added errors leave New errors.",
            )
        },
        confirmButton = {
            Row {
                TextButton(onClick = { add(split = false) }) { Text("All blunders") }
                TextButton(onClick = { add(split = true) }) { Text("Split") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
