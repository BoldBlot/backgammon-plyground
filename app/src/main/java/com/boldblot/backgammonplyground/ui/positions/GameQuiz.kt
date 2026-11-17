package com.boldblot.backgammonplyground.ui.positions

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.GameQuiz
import com.boldblot.backgammonplyground.core.GameQuizLevel
import com.boldblot.backgammonplyground.core.GameQuizOrder
import com.boldblot.backgammonplyground.core.GameQuizPick
import com.boldblot.backgammonplyground.core.GameQuizPriority
import com.boldblot.backgammonplyground.core.GameQuizRules
import com.boldblot.backgammonplyground.core.GameQuizSide
import com.boldblot.backgammonplyground.core.GameQuizStop
import com.boldblot.backgammonplyground.core.MyAnswer
import com.boldblot.backgammonplyground.core.NewErrors
import com.boldblot.backgammonplyground.core.Opponent
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.Reanalysis
import com.boldblot.backgammonplyground.ui.review.ReanalyzeDialog
import com.boldblot.backgammonplyground.ui.review.ReanalyzeRequest
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import com.boldblot.backgammonplyground.core.PositionAnalysis
import com.boldblot.backgammonplyground.core.StudyPosition
import com.boldblot.backgammonplyground.data.LibraryFilter
import com.boldblot.backgammonplyground.data.LibraryView
import com.boldblot.backgammonplyground.data.MatchEntity
import com.boldblot.backgammonplyground.data.PositionEntity
import com.boldblot.backgammonplyground.data.PositionsRepository
import com.boldblot.backgammonplyground.data.SavedPosition
import com.boldblot.backgammonplyground.ui.components.errWithUnit
import com.boldblot.backgammonplyground.ui.components.fmt
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** How a game quiz shows the opponent's decisions. */
enum class TheirView(val label: String, val note: String) {
    COLOUR("At the bottom, in their colour", "The board is turned, and the checkers keep the colours they had in the game."),
    PLAIN("At the bottom, in my colour", "The board is turned and the colours change over: every decision looks like one of mine."),
    TOP("At the top, as in the game", "Nothing is turned: the opponent plays from the top. (Their take or pass is still shown turned.)"),
}

/** A game quiz: the decisions of saved match [matchId] that [rules] pick, the opponent's shown as [theirView]. */
data class GameQuizSpec(val matchId: Long, val rules: GameQuizRules = GameQuizRules(), val theirView: TheirView = TheirView.COLOUR) {
    /** For the quiz route, and (without the match) [com.boldblot.backgammonplyground.data.Settings.gameQuiz]. */
    fun encode(): String = with(rules) {
        listOf(
            matchId, stops.joinToString(",") { it.name }, closeLevel.name, closeCustom, wrongLevel.name, wrongCustom, side.name,
            if (cube) 1 else 0, limit, priority.name, order.name, theirView.name,
        ).joinToString(";") { Uri.encode(it.toString()) }
    }

    companion object {
        fun decode(text: String): GameQuizSpec {
            val p = text.split(';').map { Uri.decode(it) }
            val d = GameQuizRules()
            fun <T : Enum<T>> pick(i: Int, all: List<T>, or: T): T = all.firstOrNull { it.name == p.getOrNull(i) } ?: or
            return GameQuizSpec(
                matchId = p.getOrNull(0)?.toLongOrNull() ?: 0L,
                rules = GameQuizRules(
                    stops = p.getOrNull(1)?.split(',')?.mapNotNull { n -> GameQuizStop.entries.firstOrNull { it.name == n } }?.toSet() ?: d.stops,
                    closeLevel = pick(2, GameQuizLevel.entries, d.closeLevel), closeCustom = p.getOrNull(3)?.toFloatOrNull() ?: d.closeCustom,
                    wrongLevel = pick(4, GameQuizLevel.entries, d.wrongLevel), wrongCustom = p.getOrNull(5)?.toFloatOrNull() ?: d.wrongCustom,
                    side = pick(6, GameQuizSide.entries, d.side), cube = p.getOrNull(7) != "0",
                    limit = p.getOrNull(8)?.toIntOrNull() ?: d.limit,
                    priority = pick(9, GameQuizPriority.entries, d.priority), order = pick(10, GameQuizOrder.entries, d.order),
                ),
                theirView = pick(11, TheirView.entries, TheirView.COLOUR),
            )
        }
    }
}

/** The analyzed decisions of match [matchId] a game quiz can ask, in the match's order (empty when its records are gone). */
suspend fun gameQuizDecisions(app: PlyGroundApp, matchId: Long): Pair<MatchEntity, List<GameQuizPick>>? {
    val m = app.repository.match(matchId) ?: return null
    return m to GameQuiz.decisions(app.repository.games(matchId).map { it.second.decisions }, m.length)
}

/** A game quiz's cards: each picked decision as a position that is not in My positions (ids below zero), and the ids of the opponent's. */
fun gameQuizCards(m: MatchEntity, picks: List<GameQuizPick>): Pair<List<SavedPosition>, Set<Long>> {
    val theirs = mutableSetOf<Long>()
    val seen = mutableSetOf<Pair<String, Boolean>>()
    val cards = picks.mapIndexedNotNull { n, pick ->
        val d = pick.decision
        val p = StudyPosition.of(d, m.length, m.jacoby) ?: return@mapIndexedNotNull null
        // The same position twice in a match is asked once.
        if (!seen.add(p.xgid to p.doubled)) return@mapIndexedNotNull null
        val id = -(n + 1L)
        if (d.player != Player.HUMAN) theirs += id
        SavedPosition(
            entity = PositionEntity(id = id, xgid = p.xgid, doubled = p.doubled, beavers = p.beavers, source = "Game ${pick.game + 1}", savedAt = 0L),
            position = p,
            analysis = PositionAnalysis(checker = d.checker, cube = d.cubeEval, label = PositionsRepository.GAME_ANALYSIS).withRecordedEngine(),
            answer = null, labels = emptySet(), played = MyAnswer.played(d),
        )
    }
    return cards to theirs
}

internal fun matchTitle(m: MatchEntity): String =
    "${if (m.length == 0) "Money" else "${m.length}-pt match"} vs ${Opponent.name(m.cpuLevel)}"

/**
 * The game quiz's menu: a saved match ([matchId], or picked from the library's list here), which of its decisions
 * to stop at, how many, which ones stay and in what order, and how the opponent's decisions are shown.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GameQuizMenuScreen(app: PlyGroundApp, matchId: Long?, onBack: () -> Unit, onStart: (GameQuizSpec) -> Unit) {
    val settings by app.settings.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf(matchId) }
    var match by remember { mutableStateOf<MatchEntity?>(null) }
    var decisions by remember { mutableStateOf<List<GameQuizPick>?>(null) }
    var matches by remember { mutableStateOf<List<MatchEntity>?>(null) }
    var rules by remember { mutableStateOf<GameQuizRules?>(null) }
    var theirView by remember { mutableStateOf(TheirView.COLOUR) }
    // The opponent's decisions the match has no analysis for, and the review of them under way (done / total).
    var cpuTodo by remember { mutableStateOf(0) }
    var reviewed by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var reviewJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var reviewMessage by remember { mutableStateOf<String?>(null) }
    val s = settings
    // The choices of the last game quiz.
    LaunchedEffect(s != null) {
        if (s != null && rules == null) {
            val last = s.gameQuiz.takeIf { it.isNotBlank() }?.let(GameQuizSpec::decode)
            rules = last?.rules ?: GameQuizRules()
            theirView = last?.theirView ?: TheirView.COLOUR
        }
    }
    LaunchedEffect(chosen, reviewed) {
        decisions = null
        val id = chosen
        cpuTodo = if (id == null) 0 else Reanalysis.unanalyzedCpuCount(app.repository.games(id).map { it.second })
        if (id == null) {
            match = null
            matches = app.repository.history()
        } else {
            val r = gameQuizDecisions(app, id)
            match = r?.first
            decisions = r?.second ?: emptyList()
        }
    }
    var askingAnalysis by remember { mutableStateOf(false) }
    // Analyzes the match: [req] null = only the computer's decisions never analyzed (the opponent card), at the settings' depths;
    // otherwise as asked (Analyze match: every decision, the computer's too if asked).
    fun startAnalysis(req: ReanalyzeRequest?) {
        val st = settings ?: return
        val mm = match ?: return
        val id = chosen ?: return
        if (reviewJob?.isActive == true) return
        reviewMessage = null
        reviewJob = scope.launch {
            val engine = req?.engine ?: st.analysisEngine
            val re = Reanalysis(
                app.engine.of(engine), (req?.checker ?: st.checkerDepth(mm.length > 0)).code, (req?.cube ?: st.cubeDepth(mm.length > 0)).code,
            )
            fun wanted(d: com.boldblot.backgammonplyground.core.DecisionRecord) = if (req == null) Reanalysis.isUnanalyzedCpu(d) else re.wanted(d, req.cpu)
            val games = app.repository.games(id)
            val total = games.sumOf { (_, g) -> g.decisions.count(::wanted) }
            var done = 0
            progress = 0 to total
            try {
                for ((e, g) in games) {
                    if (g.decisions.none(::wanted)) continue
                    val ds2 = g.decisions.toMutableList()
                    for ((k, d) in g.decisions.withIndex()) {
                        if (!wanted(d)) continue
                        ds2[k] = app.engine.run(engine) { re.decision(d, mm.length, mm.jacoby) }
                        done++
                        progress = done to total
                    }
                    app.repository.saveReanalyzedGame(e, g.copy(decisions = ds2), mm.length)
                }
                reviewed = reviewed + 1
            } catch (c: kotlinx.coroutines.CancellationException) {
                reviewMessage = "Stopped. What was analyzed so far is kept."
                reviewed = reviewed + 1
                throw c
            } catch (t: Throwable) {
                reviewMessage = "Analysis failed: ${t.message}"
            } finally {
                progress = null
            }
        }
    }
    val r = rules
    val matching = if (r != null) decisions?.let { GameQuiz.matching(it, r).size } else null
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Title("Game quiz") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    Button(
                        onClick = {
                            val spec = GameQuizSpec(chosen ?: return@Button, r ?: return@Button, theirView)
                            scope.launch { app.settings.update { it.copy(gameQuiz = spec.copy(matchId = 0).encode()) } }
                            onStart(spec)
                        },
                        enabled = chosen != null && (matching ?: 0) > 0, modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Start") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (s == null || r == null) {
                CircularProgressIndicator()
                return@Column
            }
            val m = match
            if (chosen == null) {
                Text("Match", style = MaterialTheme.typography.titleMedium)
                val all = matches
                when {
                    all == null -> CircularProgressIndicator()
                    all.isEmpty() -> Text("No saved matches yet.")
                    else -> {
                        val filter = LibraryFilter.decode(s.libraryFilter)
                        val list = LibraryView.list(all, filter, s.librarySort)
                        Text(
                            (if (filter.active) "${list.size} of ${all.size} matches (the Match Library's filter: ${filter.parts().joinToString(" · ")})" else "${all.size} matches") +
                                " · ${s.librarySort.label.lowercase()}. Filter and order are set in the Match Library.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        list.forEach { e -> MatchPickRow(e) { chosen = e.id } }
                    }
                }
                return@Column
            }
            Text("Match", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m?.let(::matchTitle) ?: "…", style = MaterialTheme.typography.bodyLarge)
                    m?.let {
                        Text(
                            "${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it.startedAt))} · score ${it.humanScore}-${it.cpuScore}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = { chosen = null }) { Text("Change") }
            }
            if (m != null) {
                val pr = progress
                if (pr != null) {
                    Text("Analyzing the match… ${pr.first} / ${pr.second} decisions", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { if (pr.second == 0) 0f else pr.first / pr.second.toFloat() }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { reviewJob?.cancel() }) { Text("Stop") }
                } else {
                    OutlinedButton(onClick = { askingAnalysis = true }) { Text("Analyze match") }
                    reviewMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
            if (askingAnalysis && m != null) {
                ReanalyzeDialog(
                    s, gameNumber = null, onDismiss = { askingAnalysis = false }, cpuDefault = true, match = m.length > 0, wholeMatch = true,
                    onStart = { req -> askingAnalysis = false; startAnalysis(req) },
                )
            }
            val ds = decisions
            if (ds != null && ds.isEmpty()) {
                Text(
                    "This match has no analyzed decisions to ask. Analyze it first with Analyze match above.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp),
                )
                return@Column
            }
            fun set(n: GameQuizRules) { rules = n }
            Spacer(Modifier.height(12.dp))
            Text("Stop at", style = MaterialTheme.typography.titleMedium)
            Text("Any of the ticked kinds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GameQuizStop.entries.forEach { stop ->
                val on = stop in r.stops
                Row(Modifier.fillMaxWidth().clickable { set(r.copy(stops = if (on) r.stops - stop else r.stops + stop)) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                    Text(
                        when (stop) {
                            GameQuizStop.CLOSE -> "Close decisions: the two best within"
                            GameQuizStop.WRONG -> "Errors in the game: at least"
                            GameQuizStop.ANY -> "Any decision (not forced or obvious)"
                        },
                    )
                }
                if (on && stop == GameQuizStop.CLOSE) {
                    LevelChips(r.closeLevel, r.closeCustom, s.errorUnit, 0.005f..0.2f, { set(r.copy(closeLevel = it)) }) { set(r.copy(closeCustom = it)) }
                }
                if (on && stop == GameQuizStop.WRONG) {
                    LevelChips(r.wrongLevel, r.wrongCustom, s.errorUnit, NewErrors.MIN_ERROR..NewErrors.MAX_ERROR, { set(r.copy(wrongLevel = it)) }) { set(r.copy(wrongCustom = it)) }
                }
            }
            Text("Whose decisions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GameQuizSide.entries.forEach { o -> FilterChip(selected = r.side == o, onClick = { set(r.copy(side = o)) }, label = { Text(o.label) }) }
            }
            if (r.side != GameQuizSide.MINE && cpuTodo > 0 && progress == null) {
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "The opponent's moves in this match have not been reviewed ($cpuTodo decisions), so none of them can be asked.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { startAnalysis(null) }) { Text("Review the opponent's moves") }
                        Text(
                            "Uses the analysis depths of Settings → Analysis.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().clickable { set(r.copy(cube = !r.cube)) }, verticalAlignment = Alignment.CenterVertically) {
                Text("Cube decisions too", Modifier.weight(1f))
                Switch(checked = r.cube, onCheckedChange = { set(r.copy(cube = it)) })
            }
            Text("Questions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5 to "5", 10 to "10", 20 to "20", 30 to "30", 0 to "All").forEach { (n, label) ->
                    FilterChip(selected = r.limit == n, onClick = { set(r.copy(limit = n)) }, label = { Text(label) })
                }
            }
            Text("When the match has more: keep", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GameQuizPriority.entries.forEach { o -> FilterChip(selected = r.priority == o, onClick = { set(r.copy(priority = o)) }, label = { Text(o.label) }) }
            }
            Text("Order", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GameQuizOrder.entries.forEach { o -> FilterChip(selected = r.order == o, onClick = { set(r.copy(order = o)) }, label = { Text(o.label) }) }
            }
            if (r.side != GameQuizSide.MINE) {
                Text("The opponent's decisions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TheirView.entries.forEach { o -> FilterChip(selected = theirView == o, onClick = { theirView = o }, label = { Text(o.label) }) }
                }
                Text(theirView.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            val n = matching
            val asked = if (n == null) null else if (r.limit > 0) minOf(r.limit, n) else n
            Text(
                when {
                    n == null -> ""
                    r.stops.isEmpty() -> "Tick what to stop at."
                    n == 0 -> "No decisions of this match fit."
                    asked != n -> "$n decisions fit; the quiz asks $asked."
                    else -> if (n == 1) "1 decision fits." else "$n decisions fit."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "A game quiz saves nothing: no quiz counts, and the positions do not go to My positions.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Error, Blunder or Custom, with the custom value's slider. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelChips(
    level: GameQuizLevel, custom: Float, unit: com.boldblot.backgammonplyground.data.ErrorUnit, range: ClosedFloatingPointRange<Float>, onLevel: (GameQuizLevel) -> Unit, onCustom: (Float) -> Unit,
) {
    Column(Modifier.padding(start = 40.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GameQuizLevel.entries.forEach { l ->
                FilterChip(
                    selected = level == l, onClick = { onLevel(l) },
                    label = { Text(if (l == GameQuizLevel.CUSTOM) l.label else "${l.label} (${errWithUnit(l.value(custom).toDouble(), unit)})") },
                )
            }
        }
        if (level == GameQuizLevel.CUSTOM) {
            Text(errWithUnit(custom.toDouble(), unit), style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = custom.coerceIn(range), valueRange = range,
                onValueChange = { v -> onCustom((Math.round(v * 200) / 200f).coerceIn(range)) },
            )
        }
    }
}

@Composable
private fun MatchPickRow(m: MatchEntity, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text((if (m.favourite) "★ " else "") + matchTitle(m), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(m.startedAt))} · score ${m.humanScore}-${m.cpuScore}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LibraryView.pr(m.humanPerformance)?.let { Text("PR ${fmt(it)}", style = MaterialTheme.typography.titleSmall) }
        }
    }
}
