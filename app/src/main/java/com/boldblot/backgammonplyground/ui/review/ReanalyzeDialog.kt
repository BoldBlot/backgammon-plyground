package com.boldblot.backgammonplyground.ui.review

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.EngineKind
import com.boldblot.backgammonplyground.data.Settings
import com.boldblot.backgammonplyground.ui.components.AnalysisLevelPicker

/** What "Analyze again" was asked to do. */
data class ReanalyzeRequest(
    val engine: EngineKind,
    val checker: Depth,
    val cubePlies: Int,
    /** The whole match rather than the game shown. */
    val wholeMatch: Boolean = false,
    /** The computer's decisions too. */
    val cpu: Boolean = true,
)

/**
 * Engine and depths for analyzing again, starting from the analysis settings. [gameNumber]
 * (game review only) offers this game or the whole match, and the computer's decisions.
 */
@Composable
fun ReanalyzeDialog(settings: Settings, gameNumber: Int?, onDismiss: () -> Unit, onStart: (ReanalyzeRequest) -> Unit) {
    var r by remember { mutableStateOf(ReanalyzeRequest(settings.analysisEngine, settings.checkerDepth, settings.cubePlies, cpu = settings.cpuAnalysis)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        // Wider than the default, so that four depth buttons fit across.
        modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 520.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Analyze again") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AnalysisLevelPicker(
                    engine = r.engine, onEngine = { r = r.copy(engine = it) },
                    checker = r.checker, cubePlies = r.cubePlies, onChange = { c, cube -> r = r.copy(checker = c, cubePlies = cube) },
                )
                if (gameNumber != null) {
                    Spacer(Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(false to "Game $gameNumber", true to "Whole match").forEachIndexed { i, (whole, label) ->
                            SegmentedButton(
                                selected = r.wholeMatch == whole, onClick = { r = r.copy(wholeMatch = whole) },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("The computer's decisions too", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(r.cpu, { r = r.copy(cpu = it) })
                    }
                    Text(
                        "The new analysis replaces the old one, in the review and in your statistics.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onStart(r) }) { Text("Analyze") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
