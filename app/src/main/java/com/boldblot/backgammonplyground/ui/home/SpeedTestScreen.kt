package com.boldblot.backgammonplyground.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.boldblot.backgammonplyground.AppLog
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.EngineKind
import com.boldblot.backgammonplyground.core.GameSearch
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.PositionIds
import com.boldblot.backgammonplyground.core.PositionSetup
import com.boldblot.backgammonplyground.core.RefDifficulty
import com.boldblot.backgammonplyground.core.ReferencePosition
import com.boldblot.backgammonplyground.core.ReferencePositions
import com.boldblot.backgammonplyground.core.climbLadder
import com.boldblot.backgammonplyground.core.ladderStart
import com.boldblot.backgammonplyground.engine.EngineLabel
import com.boldblot.backgammonplyground.engine.Waiting
import com.boldblot.backgammonplyground.ui.components.CheckerDepthPicker
import com.boldblot.backgammonplyground.ui.components.CubeDepthPicker
import com.boldblot.backgammonplyground.ui.components.EqualChips
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "Speed test run on 1 Nov 2026: recommends checker play 3-ply, pruned, cube decisions 3-ply.", or null if never run. */
fun speedTestLine(s: com.boldblot.backgammonplyground.data.Settings): String? {
    if (s.speedTestAt <= 0L) return null
    val day = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(s.speedTestAt))
    return "Speed test run on $day: recommends ${s.speedTestResult}."
}

private fun fmt(ms: Double) = if (ms >= 1000) "%.2f s".format(ms / 1000) else "%.0f ms".format(ms)

/** The analysis budgets the welcome screen and the speed test offer (ms per decision). */
enum class AnalysisBudget(val ms: Int, val title: String, val note: String) {
    INSTANT(100, "Instant", "The fastest: about a tenth of a second."),
    VISIBLE(500, "Minimum visible", "Up to about half a second."),
    QUIET(1000, "Non-intrusive", "Up to about a second."),
    TWO(2000, "2 seconds", "Up to about two seconds: stronger, you will notice the wait."),
    ;
    companion object { fun of(ms: Int) = entries.firstOrNull { it.ms == ms } ?: QUIET }
}

/** One level of a ladder and its rough cost against 1-ply (measured on a PC; only where the test starts depends on it). */
private class Rung(val depth: Depth, val ratio: Double)

/** Checker play, weakest first; [match] only: T-Roller (cheaper with the GPU). */
private fun checkerLadder(match: Boolean, gpu: Boolean): List<Rung> = listOf(
    Rung(Depth(1), 1.0), Rung(Depth(2, MoveFilter.FAST), 6.0), Rung(Depth(3, MoveFilter.FAST), 20.0),
    Rung(Depth(3, MoveFilter.WIDE), 50.0), Rung(Depth(4, MoveFilter.FAST), 120.0),
) + if (!match) emptyList() else GameSearch.T_GAMES.zip(listOf(350.0, 900.0, 1800.0)).map { (g, r) ->
    Rung(Depth(4, MoveFilter.WIDE, GameSearch.tRoller(g, cube = false)), if (gpu) r / 4 else r)
}

private fun cubeLadder(match: Boolean, gpu: Boolean): List<Rung> = listOf(
    Rung(Depth(1), 1.0), Rung(Depth(2), 5.0), Rung(Depth(3, MoveFilter.FAST), 15.0), Rung(Depth(3), 30.0),
    Rung(Depth(4, MoveFilter.FAST), 80.0), Rung(Depth(4), 200.0),
) + if (!match) emptyList() else GameSearch.T_GAMES.zip(listOf(300.0, 800.0, 1600.0)).map { (g, r) ->
    Rung(Depth(3, MoveFilter.WIDE, GameSearch.tRoller(g, cube = true)), if (gpu) r / 4 else r)
}

/** A measured search: its time, and whether it ran past the cap and was stopped. */
private data class Timed(val ms: Double, val capped: Boolean) {
    override fun toString() = if (capped) ">${fmt(ms)}" else fmt(ms)
}

/** [p]'s cube context, as in its match or ([match] false) for unlimited games. */
private fun ctxOf(p: PositionSetup, match: Boolean): CubeContext {
    val c = CubeContext(p.matchLength, p.score, p.crawford, p.jacoby, p.cube, p.onRoll)
    return if (match) c else c.copy(matchLength = 0, score = listOf(0, 0), crawford = false)
}

/**
 * Times one decision of [p] at [code] as an analysis runs (the main slot: its threads, the GPU for T-Roller), stopped at
 * [capMs]. Any cached result is pushed out first (a different roll, a 1-ply cube decision).
 */
private suspend fun timeOne(app: PlyGroundApp, p: PositionSetup, match: Boolean, code: Int, capMs: Long, what: String): Timed {
    val ctx = ctxOf(p, match)
    val d = p.dice
    app.engine.run(EngineKind.PURETD) {
        rankMoves(p.board, ctx, if (d?.d1 == 1) 2 else 1, 3, 0, null)
        cubeDecision(p.board, ctx, Depth(1).code)
    }
    var stopped = false
    val t0 = System.nanoTime()
    kotlinx.coroutines.coroutineScope {
        val cap = launch { delay(capMs); stopped = true; app.engine.stopSearch() }
        withContext(EngineLabel("Speed test: $what")) {
            app.engine.run(EngineKind.PURETD, Waiting.ANALYSIS) {
                if (d != null) rankMoves(p.board, ctx, d.d1, d.d2, code, null) else cubeDecision(p.board, ctx, code)
            }
        }
        cap.cancel()
    }
    val ms = (System.nanoTime() - t0) / 1e6
    return Timed(ms, stopped)
}

/** Android's thermal status (0 none .. 6 shutdown); 0 before Android 10. */
private fun thermal(context: Context): Int =
    if (Build.VERSION.SDK_INT >= 29) context.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: 0 else 0

/** Thermal status from which the test waits (Android's "moderate": the phone is slowing itself down). */
private const val WARM = 2

/**
 * Times the analysis on this phone and recommends, for unlimited games and matches, checker play and cube decisions, the
 * strongest level whose time on the hard reference position fits the budget. It starts at the level predicted to fit (from a
 * 1-ply timing) and steps one level at a time, up while it fits, down while it does not; each position is stopped at 3x the
 * budget. Each measurement shows as soon as it is done; the test pauses while the phone is warm. Below: a custom test of any
 * reference position (or XGID) at any level. [autoStart]: run at once (from the welcome screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(app: PlyGroundApp, onBack: () -> Unit, autoStart: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<String>() }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    /** The recommendation: unlimited checker and cube, match checker and cube. */
    var rec by remember { mutableStateOf<List<Depth>?>(null) }
    var applied by remember { mutableStateOf(false) }
    val settings by app.settings.settings.collectAsState(initial = null)
    val budget = AnalysisBudget.of(settings?.analysisBudget ?: 1000)
    val cores = app.engine.cores

    fun say(l: String) { lines += l; AppLog.i("Speed test: $l") }

    /** Waits while the phone is warm (up to 90 s); false when it stays warm. */
    suspend fun cool(): Boolean {
        if (thermal(context) < WARM) return true
        say("The phone is warm (thermal status ${thermal(context)}): pausing until it cools…")
        repeat(45) { delay(2000); if (thermal(context) < WARM) { say("Cooled down: going on."); return true } }
        return false
    }

    fun run() {
        running = true; applied = false; rec = null
        lines.clear()
        job = scope.launch {
            try {
                val s = settings ?: return@launch
                val gpu = app.engine.gpuState.startsWith("on") || (s.gpuGames && s.gpuCheck.isNotEmpty())
                say("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}, $cores cores; budget ${budget.title} (${fmt(budget.ms.toDouble())}), judged on the hard position; GPU games ${if (s.gpuGames) "on" else "off"}")
                if (thermal(context) >= WARM) { say("The phone is warm (thermal status ${thermal(context)}): let it cool and run the test again."); return@launch }
                val cap = 3L * budget.ms
                suspend fun category(title: String, cube: Boolean, match: Boolean): Depth? {
                    val refs = ReferencePositions.of(cube)
                    val hard = refs.first { it.difficulty == RefDifficulty.HARD }.setup
                    val ladder = if (cube) cubeLadder(match, gpu) else checkerLadder(match, gpu)
                    val base = timeOne(app, hard, match, Depth(1).code, cap, "$title, 1-ply").ms.coerceAtLeast(0.5)
                    val start = ladderStart(base, ladder.map { it.ratio }, budget.ms)
                    say("$title: 1-ply ${fmt(base)} on the hard position, starting at ${ladder[start].depth.label(cube)}")
                    val i = climbLadder(ladder.size, start) { k ->
                        if (!cool()) { say("Stopped: the phone stays warm. The results so far are above."); return@climbLadder null }
                        val d = ladder[k].depth
                        val times = refs.map { r -> r.difficulty to timeOne(app, r.setup, match, d.code, cap, "$title, ${d.label(cube)}, ${r.difficulty.label}") }
                        val h = times.first { it.first == RefDifficulty.HARD }.second
                        val fits = !h.capped && h.ms <= budget.ms
                        say("  ${d.label(cube)} · " + times.joinToString(" · ") { (k2, t) -> "${k2.label} $t" } + if (fits) " → fits" else " → over the budget")
                        delay(1500)   // a breath between measurements
                        fits
                    } ?: return null
                    return ladder[i].depth.also { say("  → $title: ${it.label(cube)}") }
                }
                val r = mutableListOf<Depth>()
                for ((title, cube, match) in listOf(
                    Triple("Unlimited games, checker play", false, false), Triple("Unlimited games, cube decisions", true, false),
                    Triple("Matches, checker play", false, true), Triple("Matches, cube decisions", true, true),
                )) { r += category(title, cube, match) ?: return@launch }
                rec = r
                val money = "checker play ${r[0].label()}, cube decisions ${r[1].label(cube = true)}"
                val match = "checker play ${r[2].label()}, cube decisions ${r[3].label(cube = true)}"
                say("Recommended for unlimited games: $money")
                say("Recommended for matches: $match")
                app.settings.update { it.copy(speedTestAt = System.currentTimeMillis(), speedTestResult = "$money; in matches $match") }
            } catch (e: kotlinx.coroutines.CancellationException) {
                say("Stopped.")
                throw e
            } catch (e: Exception) {
                AppLog.error("Speed test failed", e)
                lines += "The test failed: ${e.message}"
            } finally {
                running = false
            }
        }
    }

    // ---- custom test ----
    val custom = remember { mutableStateListOf<String>() }
    var pick by remember { mutableStateOf<ReferencePosition?>(ReferencePositions.all.first()) }
    var xgid by remember { mutableStateOf("") }
    var customMatch by remember { mutableStateOf(true) }
    var checkerDepth by remember { mutableStateOf(Depth(3, MoveFilter.FAST)) }
    var cubeDepth by remember { mutableStateOf(Depth(3, MoveFilter.FAST)) }
    var customRunning by remember { mutableStateOf(false) }
    val typed = xgid.trim().takeIf { it.isNotEmpty() }?.let { runCatching { PositionIds.parseXgid(it) }.getOrNull() }
    val target: PositionSetup? = if (xgid.isNotBlank()) typed else pick?.setup
    val targetCube = target?.dice == null

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
                "Times the analysis on this phone, as an analysis runs (all cores, the GPU for T-Roller when it is on), on an easy, " +
                    "an average and a hard reference position, and recommends the strongest level whose time on the hard one fits " +
                    "your budget. It starts near the budget and steps one level at a time, so it takes a minute or two; it pauses " +
                    "while the phone is warm.",
            )
            Text("Budget", style = MaterialTheme.typography.bodySmall)
            EqualChips(AnalysisBudget.entries, budget, { it.title }, { b ->
                scope.launch { app.settings.update { it.copy(analysisBudget = b.ms) } }
            })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) OutlinedButton(onClick = { app.engine.stopSearch(); job?.cancel() }) { Text("Stop") }
                Button(enabled = !running && !customRunning, onClick = { run() }) { Text(if (running) "Running…" else "Run speed test") }
                OutlinedButton(enabled = (lines.isNotEmpty() || custom.isNotEmpty()) && !running, onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Speed test", (lines + custom).joinToString("\n")))
                }) { Text("Copy results") }
            }
            if (running) LinearProgressIndicator(Modifier.padding(vertical = 4.dp).fillMaxWidth())
            for (l in lines) Text(l, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
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
                        AppLog.i("Speed test recommendation applied")
                    }
                }) { Text(if (applied) "Applied" else "Use the recommendation") }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Custom test", style = MaterialTheme.typography.titleSmall)
            Text("One position at one level, timed as an analysis runs. Pick a reference position or paste an XGID.", style = MaterialTheme.typography.bodySmall)
            Text("Checker play", style = MaterialTheme.typography.bodySmall)
            val chosen = pick.takeIf { xgid.isBlank() }
            EqualChips(listOf<ReferencePosition?>(null) + ReferencePositions.of(false), chosen?.takeIf { !it.cube }, { it?.difficulty?.label ?: "–" }, { if (it != null) { pick = it; xgid = "" } })
            Text("Cube decision", style = MaterialTheme.typography.bodySmall)
            EqualChips(listOf<ReferencePosition?>(null) + ReferencePositions.of(true), chosen?.takeIf { it.cube }, { it?.difficulty?.label ?: "–" }, { if (it != null) { pick = it; xgid = "" } })
            OutlinedTextField(
                value = xgid, onValueChange = { xgid = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("Or an XGID") },
                isError = xgid.isNotBlank() && typed == null,
                supportingText = { Text(if (xgid.isNotBlank() && typed == null) "Not a valid XGID" else if (xgid.isBlank()) "Chosen: ${pick?.title}" else if (targetCube) "A cube decision" else "A checker play") },
            )
            EqualChips(listOf(true, false), customMatch, { if (it) "As in its match" else "Unlimited game" }, { customMatch = it })
            if (targetCube) CubeDepthPicker(cubeDepth, games = customMatch) { cubeDepth = it }
            else CheckerDepthPicker(checkerDepth, games = customMatch) { checkerDepth = it }
            Button(enabled = target != null && !running && !customRunning, onClick = {
                val p = target ?: return@Button
                val d = if (targetCube) cubeDepth else checkerDepth
                val name = if (xgid.isNotBlank()) "XGID" else pick?.title ?: "?"
                customRunning = true
                scope.launch {
                    try {
                        val match = customMatch && p.matchLength > 0
                        val t = timeOne(app, p, match, d.code, 120_000, "custom, $name, ${d.label(targetCube)}")
                        val threads = if (app.engine.inGame) app.engine.gameThreads else cores
                        val gpu = Depth.of(d.code).search?.let { GameSearch.isTRoller(it) } == true && app.engine.gpuState.startsWith("on")
                        val line = "$name · ${d.label(targetCube)} · ${if (match) "match" else "unlimited"} · $t · $threads threads · ${if (gpu) "GPU" else "processor"}"
                        custom.add(0, line)
                        AppLog.i("Speed test, custom: $line (${PositionIds.toXgid(p)})")
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        AppLog.error("Speed test, custom failed", e)
                        custom.add(0, "$name: failed (${e.message})")
                    } finally { customRunning = false }
                }
            }) { Text(if (customRunning) "Running…" else "Run") }
            if (customRunning) LinearProgressIndicator(Modifier.fillMaxWidth())
            for (l in custom) Text(l, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}
