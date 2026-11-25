package com.boldblot.backgammonplyground.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boldblot.backgammonplyground.AppLog
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.engine.ActivityKind
import com.boldblot.backgammonplyground.engine.BackgroundWork
import com.boldblot.backgammonplyground.engine.WorkItem
import com.boldblot.backgammonplyground.engine.WorkKind
import kotlinx.coroutines.delay

/** "Your match: 23 decisions left of 40, about 1 min". */
fun workLine(w: WorkItem): String {
    val unit = when (w.kind) { WorkKind.MATCH -> "decisions"; WorkKind.SET, WorkKind.QUIZ -> "positions" }
    val of = if (w.total > w.left) " of ${w.total}" else ""
    return "${w.label}: ${w.left} $unit left$of" + (w.etaMs?.let { ", ${BackgroundWork.eta(it)}" } ?: "")
}

/**
 * Data and help, at the bottom: what is being analyzed now, each job with its spinner and Stop, and the search someone waits
 * for; "Nothing being analyzed" when the engine is idle. Stop all stops every job and the search.
 */
@Composable
fun AnalysisNowSection(app: PlyGroundApp) {
    val items by app.work.items.collectAsState()
    val activity by app.engine.activity.collectAsState()
    val progress by app.engine.progress.collectAsState()
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { now = android.os.SystemClock.elapsedRealtime(); delay(500) } }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Being analyzed now", style = MaterialTheme.typography.titleSmall)
        val jobs = items.values.sortedBy { it.kind.ordinal }
        val p = progress
        val other = activity.count { it.kind != ActivityKind.BACKGROUND }
        if (jobs.isEmpty() && p == null && activity.isEmpty()) {
            Text("Nothing being analyzed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        if (p != null) WorkRow(searchLine(p, now - p.startedAt)) { AppLog.i("Data and help: Stop on the search \"${p.what}\""); app.engine.stopSearch() }
        else if (other > 0) WorkRow(if (other == 1) "The engine is searching (play or analysis)" else "$other searches", null)
        jobs.forEach { w ->
            WorkRow(workLine(w) + if (w.kind == WorkKind.MATCH && app.engine.gpuWithBackground) " · GPU" else "") {
                AppLog.i("Data and help: Stop on \"${w.label}\" (${w.left} left)")
                w.stop()
            }
        }
        if (jobs.isEmpty() && p == null && other == 0) WorkRow("Background analysis", null)
        if (jobs.size + (if (p != null) 1 else 0) > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { app.work.stopAll(); app.engine.stopSearch() }) { Text("Stop all") }
            }
        }
    }
}

@Composable
private fun WorkRow(text: String, onStop: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 10.dp))
        if (onStop != null) TextButton(onClick = onStop) { Text("Stop") }
    }
}

/**
 * Over every screen but the game, at the top: a match's background analysis still running after the player left the game,
 * with the decisions and time left, and "GPU" while it holds the GPU (the analyses opened meanwhile use the processor).
 * Tap: Data and help, where it can be stopped.
 */
@Composable
fun BackgroundMatchLabel(app: PlyGroundApp, onGame: Boolean, onOpen: () -> Unit) {
    val items by app.work.items.collectAsState()
    val match = items.values.filter { it.kind == WorkKind.MATCH }
    val shown = !onGame && match.isNotEmpty()
    // Logged when the label comes and goes, with what it showed.
    LaunchedEffect(shown) {
        if (shown) AppLog.i("Top label shown: ${match.joinToString { workLine(it) }}, GPU ${app.engine.gpuWithBackground}")
        else AppLog.i("Top label hidden")
    }
    if (!shown) return
    val left = match.sumOf { it.left }
    val eta = match.mapNotNull { it.etaMs }.takeIf { it.size == match.size }?.sum()
    val text = (if (match.size == 1 && match[0].label == "Your match") "Analyzing your match" else "Analyzing ${match.size} matches".takeIf { match.size > 1 } ?: "Analyzing a match") +
        ": $left decisions left" + (eta?.let { ", ${BackgroundWork.eta(it)}" } ?: "") + if (app.engine.gpuWithBackground) " · GPU" else ""
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 2.dp), contentAlignment = Alignment.TopCenter) {
        Surface(
            shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 2.dp,
            modifier = Modifier.widthIn(max = 520.dp).clickable(onClick = onOpen),
        ) {
            Text(
                text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}
