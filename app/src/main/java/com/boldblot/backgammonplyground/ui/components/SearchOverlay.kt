package com.boldblot.backgammonplyground.ui.components

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.unit.sp
import com.boldblot.backgammonplyground.engine.ActivityKind
import com.boldblot.backgammonplyground.engine.EngineService
import com.boldblot.backgammonplyground.engine.SearchProgress
import com.boldblot.backgammonplyground.engine.Waiting
import kotlinx.coroutines.delay

/** A watched search shows after this long: shorter ones would only flash by. */
private const val SHOW_AFTER_MS = 1_000L

/** "Played-out games: 96 of 144 · 12 s", "Cube decision, 4-ply · 3 s". */
fun searchLine(p: SearchProgress, elapsedMs: Long): String {
    val what = p.what.ifEmpty { "Searching" }
    val count = if (p.total > 0) ": ${p.done} of ${p.total}" else ""
    return "$what$count · ${elapsedMs / 1000} s"
}

/**
 * Over every screen, at the bottom: the search someone is waiting for ([EngineService.progress]) as a progress
 * bar that says what it is doing. Past its limit (Settings → Analysis) a notice offers to keep waiting, to stop
 * (the search returns what it has, marked incomplete) or to stop and open the analysis settings. The search
 * itself never stops on its own.
 */
@Composable
fun SearchOverlay(engine: EngineService, onSettings: () -> Unit) {
    val progress by engine.progress.collectAsState()
    val p = progress ?: return
    var now by remember(p.id) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var waiting by remember(p.id) { mutableStateOf(false) }
    LaunchedEffect(p.id) {
        while (true) { now = SystemClock.elapsedRealtime(); delay(250) }
    }
    val elapsed = now - p.startedAt
    if (elapsed < SHOW_AFTER_MS) return
    Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.BottomCenter) {
        Surface(
            shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp, shadowElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.widthIn(max = 520.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(searchLine(p, elapsed), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                if (p.total > 0) LinearProgressIndicator(progress = { p.done.toFloat() / p.total }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                if (elapsed >= p.limitMs && !waiting) {
                    Text(
                        (if (p.waiting == Waiting.PLAY) "This search has run past the ${p.limitMs / 1000} s set for play."
                        else "This analysis has run past the ${p.limitMs / 1000} s set for it.") +
                            " Stop shows what it has so far, marked incomplete.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { engine.stopSearch(); onSettings() }) { Text("Settings") }
                        TextButton(onClick = { waiting = true }) { Text("Keep waiting") }
                        TextButton(onClick = { engine.stopSearch() }) { Text("Stop") }
                    }
                }
            }
        }
    }
}

/**
 * Debug (Settings → Data and help): in the top left corner a spinning wheel for each call into the engine that has not
 * returned, the computer's decisions in the tertiary colour, analyses someone waits for in the primary, background work
 * in the secondary. The first background wheel carries the number of decisions waiting in the analysis queue.
 */
@Composable
fun EngineIndicator(engine: EngineService, queued: Int) {
    val jobs by engine.activity.collectAsState()
    if (jobs.isEmpty() && queued == 0) return
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        var numbered = false
        jobs.forEach { j ->
            val colour = when (j.kind) {
                ActivityKind.PLAY -> MaterialTheme.colorScheme.tertiary
                ActivityKind.ANALYSIS -> MaterialTheme.colorScheme.primary
                ActivityKind.BACKGROUND -> MaterialTheme.colorScheme.secondary
            }
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), color = colour, strokeWidth = 2.dp)
                if (j.kind == ActivityKind.BACKGROUND && !numbered && queued > 0) {
                    numbered = true
                    Text("$queued", fontSize = 8.sp, color = colour, maxLines = 1)
                }
            }
        }
        // Queued decisions with no engine call running right now (between two of them).
        if (!numbered && queued > 0) Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.secondary, strokeWidth = 2.dp)
            Text("$queued", fontSize = 8.sp, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
        }
    }
}
