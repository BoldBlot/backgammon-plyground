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

/** Targets for the recommendation: average seconds per decision. */
private const val CHECKER_TARGET_MS = 1000.0
private const val DOUBLES_TARGET_MS = 3000.0
private const val CUBE_TARGET_MS = 1500.0

/** Longest time spent on one measurement; it then reports the positions done so far. */
private const val BUDGET_MS = 20_000.0

/** Checker play timed with pruning off, then on. */
private val FILTERS = listOf(MoveFilter.ALL, MoveFilter.WIDE)

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

/**
 * Times the engine on this phone at every analysis depth (all cores, the network the app uses), on
 * positions from a fixed self-play game, and recommends the deepest settings that stay within
 * about 1 s per checker play (3 s on doubles) and 1.5 s per cube decision. Checker play is timed
 * up to 3-ply (4-ply is research only); each measurement stops after [BUDGET_MS], and Stop (or
 * leaving the screen) ends the test after the current decision.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(app: PlyGroundApp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<String>() }
    var running by remember { mutableStateOf(false) }
    var checker by remember { mutableStateOf<Depth?>(null) }
    var cube by remember { mutableStateOf<Int?>(null) }
    var matchChecker by remember { mutableStateOf<Depth?>(null) }
    var matchCube by remember { mutableStateOf<Depth?>(null) }
    /** The match part's running search, for Stop. */
    var monitor by remember { mutableStateOf<SearchMonitor?>(null) }
    var applied by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    val cores = Runtime.getRuntime().availableProcessors()
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (running) PureTdNative.nativeBenchCancel() }
    }
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
                "Times each analysis depth on this phone and recommends the deepest settings that stay " +
                    "within about 1 s per checker play (3 s on doubles) and 1.5 s per cube decision. " +
                    "For matches it also times the searches by played-out games, and recommends the deepest " +
                    "whose average stays within half the time an analysis may take before it offers to stop " +
                    "(Settings → Analysis). It takes several minutes (each measurement at most 20 s) and keeps " +
                    "all cores busy; the phone may get warm. Plain 4-ply checker play is not timed (research only).",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) OutlinedButton(enabled = !stopping, onClick = {
                    stopping = true
                    PureTdNative.nativeBenchCancel()
                    monitor?.requestStop()
                }) { Text(if (stopping) "Stopping…" else "Stop") }
                Button(enabled = !running, onClick = {
                    running = true
                    stopping = false
                    applied = false
                    checker = null
                    cube = null
                    matchChecker = null
                    matchCube = null
                    lines.clear()
                    lines += "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}, $cores cores"
                    scope.launch {
                        val dir = withContext(Dispatchers.IO) { PureTdNative.load(); PureTdNative.files(context) }
                        val h = withContext(Dispatchers.Default) {
                            PureTdNative.nativeOpen(
                                File(dir, "prob5_wide.ptw").path, File(dir, "cubeful_wide.ptw").path,
                                File(context.filesDir, "puretd-bearoff.bin").path, cores,
                            )
                        }
                        if (h == 0L) {
                            lines += "Could not load the network."
                            running = false
                            return@launch
                        }
                        /** Milliseconds per decision, with a note when the time limit cut it short. */
                        suspend fun time(kind: Int, plies: Int, filter: MoveFilter): Pair<Double, String> = withContext(Dispatchers.Default) {
                            val within = if (filter == MoveFilter.ALL) -1f else filter.within
                            val n = if (kind == 2) maxOf(3, samples(plies) / 2) else samples(plies)
                            val r = PureTdNative.nativeBench(h, kind, plies, within, n, cores, BUDGET_MS) ?: doubleArrayOf(0.0, 0.0)
                            val done = r[1].toInt()
                            r[0] to if (done < n && !stopping) " (time limit, $done of $n)" else ""
                        }
                        try {
                            lines += "Checker play (average / doubles)"
                            var bestChecker: Depth? = null
                            checker@ for (filter in FILTERS) {
                                // 4-ply checker play is research only (FOUR_PLY_CHECKER_NOTE): not timed.
                                for (plies in 1..3) {
                                    if (plies == 1 && filter != MoveFilter.ALL) continue
                                    val (avg, n1) = time(0, plies, filter)
                                    if (stopping) break@checker
                                    val (dbl, n2) = time(2, plies, filter)
                                    if (stopping) break@checker
                                    val d = Depth(plies, filter)
                                    lines += "  ${d.label()}: ${fmt(avg)}$n1 / ${fmt(dbl)}$n2"
                                    val fits = avg <= CHECKER_TARGET_MS && dbl <= DOUBLES_TARGET_MS
                                    if (fits && (bestChecker == null || plies > bestChecker.plies)) bestChecker = d
                                    // Much too slow already: deeper with this filter only gets slower.
                                    if (avg > 4 * CHECKER_TARGET_MS || dbl > 4 * DOUBLES_TARGET_MS) break
                                }
                            }
                            var bestCube = 1
                            if (!stopping) {
                                lines += "Cube decisions"
                                for (plies in 1..Depth.MAX_SEARCH_PLIES) {
                                    val (ms, note) = time(1, plies, MoveFilter.ALL)
                                    if (stopping) break
                                    lines += "  $plies-ply: ${fmt(ms)}$note"
                                    if (ms <= CUBE_TARGET_MS) bestCube = plies
                                    if (ms > 4 * CUBE_TARGET_MS) break
                                }
                            }
                            // Match play: the searches by played-out games, timed on the same engine. The deepest
                            // whose average stays within half the time an analysis may take before it offers to stop.
                            var bestMatchChecker: Depth = bestChecker ?: Depth(1)
                            var bestMatchCube: Depth = Depth(bestCube)
                            if (!stopping) {
                                val limitMs = app.settings.settings.first().analysisLimit * 1000.0
                                val target = limitMs / 2
                                val mon = SearchMonitor()
                                monitor = mon
                                val e = PureTdEngine(h, MatchEquityTable()).also { it.gameThreads = cores; it.monitor = mon }
                                /** Average milliseconds of [search] over the samples, stopping once [BUDGET_MS] is spent. */
                                suspend fun timeMatch(samples: List<MatchSample>, code: Int): Pair<Double, String> = withContext(Dispatchers.Default) {
                                    var total = 0.0
                                    var done = 0
                                    for (x in samples) {
                                        val t0 = System.nanoTime()
                                        if (x.d1 > 0) e.rankMoves(x.board, x.ctx, x.d1, x.d2, code, null) else e.cubeDecision(x.board, x.ctx, code)
                                        if (mon.stop) break
                                        total += (System.nanoTime() - t0) / 1e6
                                        done++
                                        if (total > BUDGET_MS) break
                                    }
                                    (if (done > 0) total / done else 0.0) to if (done < samples.size && !stopping) " (time limit, $done of ${samples.size})" else ""
                                }
                                val (plays, cubes) = withContext(Dispatchers.Default) { matchSamples(e, 6, 4) }
                                lines += "Match play, played-out games (X/YY/Z)"
                                lines += "  checker play"
                                for (g in GameSearch.checker) {
                                    val (ms, note) = timeMatch(plays, g.code)
                                    if (stopping) break
                                    lines += "    ${g.label}: ${fmt(ms)}$note"
                                    if (g.games > 0 && ms <= target) bestMatchChecker = Depth(g.plies, MoveFilter.WIDE, g)
                                    if (g.games > 0 && ms > 2 * target) break
                                }
                                if (!stopping) lines += "  close cube decisions"
                                if (!stopping) for (g in GameSearch.cubes) {
                                    val (ms, note) = timeMatch(cubes, g.code)
                                    if (stopping) break
                                    lines += "    ${g.label}: ${fmt(ms)}$note"
                                    if (ms <= target) bestMatchCube = Depth(g.plies, MoveFilter.WIDE, g)
                                    if (ms > 2 * target) break
                                }
                                e.monitor = null
                            }
                            if (stopping) {
                                lines += "Stopped."
                            } else {
                                checker = bestChecker ?: Depth(1)
                                cube = bestCube
                                matchChecker = bestMatchChecker
                                matchCube = bestMatchCube
                                val money = "checker play ${checker!!.label()}, cube decisions $bestCube-ply"
                                val match = "checker play ${bestMatchChecker.label()}, cube decisions ${bestMatchCube.label(cube = true)}"
                                lines += "Recommended for money games: $money"
                                lines += "Recommended for matches: $match"
                                // Kept, so that the welcome screen, Settings and About can say it was run.
                                val result = if (money == match) money else "$money; in matches $match"
                                app.settings.update { it.copy(speedTestAt = System.currentTimeMillis(), speedTestResult = result) }
                            }
                        } finally {
                            // Also when the screen is left (the coroutine is cancelled): free the test's engine.
                            monitor = null
                            withContext(NonCancellable + Dispatchers.Default) { PureTdNative.nativeClose(h) }
                            running = false
                            stopping = false
                        }
                    }
                }) { Text(if (running) "Running…" else "Run speed test") }
                OutlinedButton(enabled = lines.isNotEmpty() && !running, onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Speed test", lines.joinToString("\n")))
                }) { Text("Copy results") }
            }
            if (running) LinearProgressIndicator(Modifier.padding(vertical = 4.dp))
            for (l in lines) Text(l, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            val c = checker
            val k = cube
            if (c != null && k != null && !running) {
                Button(enabled = !applied, onClick = {
                    applied = true
                    scope.launch {
                        app.settings.update {
                            it.copy(
                                checkerPlies = c.plies, moveFilter = c.filter, cubePlies = k,
                                matchChecker = matchChecker?.code ?: -1, matchCube = matchCube?.code ?: -1,
                            )
                        }
                    }
                }) { Text(if (applied) "Applied" else "Use the recommendation") }
            }
        }
    }
}
