package com.boldblot.backgammonplyground.ui.home

import kotlinx.coroutines.flow.first
import com.boldblot.backgammonplyground.engine.PureTdEngine
import com.boldblot.backgammonplyground.core.SearchMonitor
import com.boldblot.backgammonplyground.core.PositionIds
import com.boldblot.backgammonplyground.core.MatchEquityTable
import com.boldblot.backgammonplyground.core.GameSearch
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.engine.PureTdNative
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/** Longest time spent on one measurement; it then reports the positions done so far. */
private const val BUDGET_MS = 20_000.0


/** One timed search of the match part: a checker play (dice set) or a cube decision. */
private class MatchSample(val board: com.boldblot.backgammonplyground.core.Board, val ctx: com.boldblot.backgammonplyground.core.CubeContext, val d1: Int, val d2: Int)

/**
 * Positions for timing the match searches, from a fixed 1-ply self-play game of a 7-point match: every
 * third checker play, and the positions before a roll where the cube decision is close at 1-ply (so that
 * the games are played out, as they are for the decisions that matter).
 */
private fun matchSamples(e: PureTdEngine, checker: Int, cube: Int): Pair<List<MatchSample>, List<MatchSample>> {
    val start = PositionIds.parseXgid("-b----E-C---eE---c-e----B-:0:0:1:00:0:0:0:7:10")
    val rnd = java.util.Random(20261112)
    val plays = ArrayList<MatchSample>()
    val cubes = ArrayList<MatchSample>()
    var board = start.board
    var p = start.onRoll
    var n = 0
    repeat(4000) {
        if (plays.size >= checker && cubes.size >= cube) return@repeat
        val ctx = com.boldblot.backgammonplyground.core.CubeContext(start.matchLength, start.score, start.crawford, start.jacoby, start.cube, p)
        if (cubes.size < cube) {
            val c = e.cubeDecision(board, ctx, 0)
            if (c.cubeAvailable && kotlin.math.abs(c.noDouble - c.doubleTake) < 0.08f && c.doubleTake < c.doublePass) cubes += MatchSample(board, ctx, 0, 0)
        }
        val d1 = rnd.nextInt(6) + 1
        val d2 = rnd.nextInt(6) + 1
        val best = e.rankMoves(board, ctx, d1, d2, 0, null).candidates.firstOrNull()
        if (best != null) {
            if (n++ % 3 == 2 && plays.size < checker && d1 != d2) plays += MatchSample(board, ctx, d1, d2)
            board = board.applyMove(p, best.move)
        }
        if (board.off(p) == 15) board = start.board
        p = com.boldblot.backgammonplyground.core.Player.other(p)
    }
    return plays to cubes
}

/** Positions timed per depth (deeper is slower, so fewer). */
private fun samples(plies: Int) = when (plies) { 1 -> 60; 2 -> 24; else -> 10 }

/** "Speed test run on 1 Nov 2026: recommends checker play 3-ply, pruned, cube decisions 3-ply.", or null if never run. */
fun speedTestLine(s: com.boldblot.backgammonplyground.data.Settings): String? {
    if (s.speedTestAt <= 0L) return null
    val day = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(s.speedTestAt))
    return "Speed test run on $day: recommends ${s.speedTestResult}."
}

private fun fmt(ms: Double) = if (ms >= 1000) "%.2f s".format(ms / 1000) else "%.0f ms".format(ms)

/** The analysis budgets the welcome screen and the speed test offer (ms per decision on average). */
enum class AnalysisBudget(val ms: Int, val title: String, val note: String) {
    INSTANT(100, "Instant", "The fastest: about a tenth of a second."),
    VISIBLE(500, "Minimum visible", "Up to about half a second."),
    QUIET(1000, "Non-intrusive", "Up to about a second."),
    TWO(2000, "2 seconds", "Up to about two seconds: stronger, you will notice the wait."),
    ;
    companion object { fun of(ms: Int) = entries.firstOrNull { it.ms == ms } ?: QUIET }
}

/** The options timed, weakest first: checker play and cube decisions; [match] only: T-Roller. */
private fun checkerLadder(match: Boolean): List<Depth> = listOf(
    Depth(1), Depth(2, MoveFilter.FAST), Depth(3, MoveFilter.FAST), Depth(3, MoveFilter.WIDE), Depth(4, MoveFilter.FAST),
) + if (match) GameSearch.T_GAMES.map { Depth(4, MoveFilter.WIDE, GameSearch.tRoller(it, cube = false)) } else emptyList()

private fun cubeLadder(match: Boolean): List<Depth> = listOf(
    Depth(1), Depth(2), Depth(3, MoveFilter.FAST), Depth(3), Depth(4, MoveFilter.FAST), Depth(4),
) + if (match) GameSearch.T_GAMES.map { Depth(3, MoveFilter.WIDE, GameSearch.tRoller(it, cube = true)) } else emptyList()

/**
 * Times the searches on this phone (all cores, the network the app uses) on positions from a fixed self-play game, for unlimited
 * games and matches, and recommends the strongest whose average time stays within the analysis budget (welcome screen; chips
 * here). Each measurement stops after [BUDGET_MS]; Stop (or leaving the screen) ends the test after the current decision.
 * [autoStart]: run at once (from the welcome screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(app: PlyGroundApp, onBack: () -> Unit, autoStart: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<String>() }
    var running by remember { mutableStateOf(false) }
    /** The recommendation: unlimited checker and cube, match checker and cube. */
    var rec by remember { mutableStateOf<List<Depth>?>(null) }
    var monitor by remember { mutableStateOf<SearchMonitor?>(null) }
    var applied by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    val settings by app.settings.settings.collectAsState(initial = null)
    val budget = AnalysisBudget.of(settings?.analysisBudget ?: 1000)
    val cores = Runtime.getRuntime().availableProcessors()
    fun run() {
        running = true; stopping = false; applied = false; rec = null
        lines.clear()
        lines += "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}, $cores cores; budget ${budget.title} (${fmt(budget.ms.toDouble())})"
        scope.launch {
            val s = app.settings.settings.first()
            val net = s.puretdNet
            val dir = withContext(Dispatchers.IO) { PureTdNative.load(); PureTdNative.files(context) }
            val h = withContext(Dispatchers.Default) {
                PureTdNative.nativeOpen(File(dir, net.cubeless).path, File(dir, net.cubeful).path, File(context.filesDir, "puretd-bearoff.bin").path, cores)
            }
            if (h == 0L) { lines += "Could not load the network."; running = false; return@launch }
            com.boldblot.backgammonplyground.AppLog.i("Speed test started: budget ${budget.ms} ms, net ${net.name}, $cores cores")
            try {
                val mon = SearchMonitor()
                monitor = mon
                val e = PureTdEngine(h, MatchEquityTable(), netName = net.engineName).also { it.gameThreads = cores; it.monitor = mon }
                val (plays, cubes) = withContext(Dispatchers.Default) { matchSamples(e, 6, 4) }
                /** Average milliseconds of [code] over the samples (unlimited games: the same positions as money), stopping after [BUDGET_MS]. */
                suspend fun time(samples: List<MatchSample>, code: Int, match: Boolean): Pair<Double, String> = withContext(Dispatchers.Default) {
                    var total = 0.0; var done = 0
                    for (x in samples) {
                        val ctx = if (match) x.ctx else x.ctx.copy(matchLength = 0, score = listOf(0, 0), crawford = false)
                        e.rankMoves(x.board, ctx, 1, 2, 0, null)   // a different roll: no cached ranking
                        val t0 = System.nanoTime()
                        if (x.d1 > 0) e.rankMoves(x.board, ctx, x.d1, x.d2, code, null) else { e.cubeDecision(x.board, ctx, Depth(1).code); e.cubeDecision(x.board, ctx, code) }
                        if (mon.stop) break
                        total += (System.nanoTime() - t0) / 1e6
                        done++
                        if (total > BUDGET_MS) break
                    }
                    (if (done > 0) total / done else 0.0) to if (done < samples.size && !stopping) " (time limit, $done of ${samples.size})" else ""
                }
                /** The strongest of [ladder] within the budget (the first one at least). */
                suspend fun best(title: String, ladder: List<Depth>, samples: List<MatchSample>, match: Boolean, cube: Boolean): Depth? {
                    lines += title
                    var pick = ladder.first()
                    for (d in ladder) {
                        val (ms, note) = time(samples, d.code, match)
                        if (stopping) return null
                        lines += "  ${d.label(cube)}: ${fmt(ms)}$note"
                        com.boldblot.backgammonplyground.AppLog.i("Speed test: $title ${d.label(cube)} ${"%.0f".format(java.util.Locale.ROOT, ms)} ms$note")
                        if (ms <= budget.ms) pick = d
                        if (ms > 2 * budget.ms) break
                    }
                    return pick
                }
                val r = listOfNotNull(
                    best("Unlimited games, checker play", checkerLadder(false), plays, false, false),
                    best("Unlimited games, cube decisions", cubeLadder(false), cubes, false, true),
                    best("Matches, checker play", checkerLadder(true), plays, true, false),
                    best("Matches, close cube decisions", cubeLadder(true), cubes, true, true),
                )
                e.monitor = null
                if (stopping || r.size < 4) { lines += "Stopped."; return@launch }
                rec = r
                val money = "checker play ${r[0].label()}, cube decisions ${r[1].label(cube = true)}"
                val match = "checker play ${r[2].label()}, cube decisions ${r[3].label(cube = true)}"
                lines += "Recommended for unlimited games: $money"
                lines += "Recommended for matches: $match"
                com.boldblot.backgammonplyground.AppLog.i("Speed test done: unlimited $money; matches $match")
                app.settings.update { it.copy(speedTestAt = System.currentTimeMillis(), speedTestResult = "$money; in matches $match") }
            } finally {
                monitor = null
                withContext(NonCancellable + Dispatchers.Default) { PureTdNative.nativeClose(h) }
                running = false
                stopping = false
            }
        }
    }
    LaunchedEffect(autoStart, settings != null) { if (autoStart && settings != null && !running && lines.isEmpty()) run() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Engine speed test") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Times the analysis on this phone and recommends the strongest search whose average time per decision fits your " +
                    "budget, for unlimited games and for matches. It takes a few minutes (each measurement at most 20 s) and keeps " +
                    "all cores busy; the phone may get warm.",
            )
            Text("Budget", style = MaterialTheme.typography.bodySmall)
            com.boldblot.backgammonplyground.ui.components.EqualChips(AnalysisBudget.entries, budget, { it.title }, { b ->
                scope.launch { app.settings.update { it.copy(analysisBudget = b.ms) } }
            })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) OutlinedButton(enabled = !stopping, onClick = {
                    stopping = true
                    monitor?.requestStop()
                }) { Text(if (stopping) "Stopping…" else "Stop") }
                Button(enabled = !running, onClick = { run() }) { Text(if (running) "Running…" else "Run speed test") }
                OutlinedButton(enabled = lines.isNotEmpty() && !running, onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Speed test", lines.joinToString("\n")))
                }) { Text("Copy results") }
            }
            if (running) LinearProgressIndicator(Modifier.padding(vertical = 4.dp))
            for (l in lines) Text(l, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            val r = rec
            if (r != null && !running) {
                Button(enabled = !applied, onClick = {
                    applied = true
                    scope.launch {
                        app.settings.update {
                            it.copy(
                                checkerPlies = r[0].plies, moveFilter = if (r[0].plies > 1) r[0].filter else it.moveFilter,
                                cubePlies = r[1].plies, cubeFast = r[1].filter == MoveFilter.FAST,
                                matchChecker = r[2].code, matchCube = r[3].code,
                            )
                        }
                        com.boldblot.backgammonplyground.AppLog.i("Speed test recommendation applied")
                    }
                }) { Text(if (applied) "Applied" else "Use the recommendation") }
                Text(
                    "Deeper and longer analyses (standard plies, T-Roller with more games) are in Settings → Analysis.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
