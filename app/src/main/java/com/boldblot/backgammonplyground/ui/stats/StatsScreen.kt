package com.boldblot.backgammonplyground.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.DiceTotals
import com.boldblot.backgammonplyground.core.Elo
import com.boldblot.backgammonplyground.core.PerformanceTotals
import com.boldblot.backgammonplyground.core.XgRules
import com.boldblot.backgammonplyground.data.MatchTypeFilter
import com.boldblot.backgammonplyground.data.PeriodFilter
import com.boldblot.backgammonplyground.data.StatsFilter
import com.boldblot.backgammonplyground.data.StatsReport
import com.boldblot.backgammonplyground.ui.components.GroupedBarChart
import com.boldblot.backgammonplyground.ui.components.Labelled
import com.boldblot.backgammonplyground.ui.components.LineChart
import com.boldblot.backgammonplyground.ui.components.fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StatsState(val loading: Boolean = true, val filter: StatsFilter = StatsFilter(), val report: StatsReport? = null)

class StatsViewModel(private val app: PlyGroundApp) : ViewModel() {
    private val _state = MutableStateFlow(StatsState())
    val state: StateFlow<StatsState> = _state.asStateFlow()

    init { load(StatsFilter()) }

    fun load(filter: StatsFilter) {
        _state.value = _state.value.copy(loading = true, filter = filter)
        viewModelScope.launch {
            val report = app.repository.stats(filter)
            _state.value = StatsState(loading = false, filter = filter, report = report)
        }
    }
}

private val dateFmt = SimpleDateFormat("d MMM", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: StatsViewModel, onBack: () -> Unit) {
    val st by vm.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Filters(st.filter, vm::load)
            Box(Modifier.fillMaxSize()) {
                val r = st.report
                if (st.loading || r == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(Modifier.widthIn(max = 720.dp)) { Report(r) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Filters(f: StatsFilter, onChange: (StatsFilter) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(MatchTypeFilter.entries) { t ->
                FilterChip(selected = f.type == t, onClick = { onChange(f.copy(type = t)) }, label = { Text(t.label) })
            }
            items(PeriodFilter.entries) { p ->
                FilterChip(selected = f.period == p, onClick = { onChange(f.copy(period = p)) }, label = { Text(p.label) })
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun Hero(value: String, caption: String) {
    Column {
        Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun pctOf(a: Int, b: Int) = if (b == 0) "–" else "${fmt(100.0 * a / b)}%"

@Composable
private fun Report(r: StatsReport) {
    val h = r.human
    Section("Performance (XG PR)") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Hero(h.pr?.let { fmt(it, 2) } ?: "–", h.pr?.let { XgRules.ratingName(it) } ?: "No analyzed decisions yet")
        }
        Spacer(Modifier.height(8.dp))
        Labelled("Checker play PR", h.checkerPr?.let { fmt(it, 2) } ?: "–")
        Labelled("Cube PR", h.cubePr?.let { fmt(it, 2) } ?: "–")
        Labelled("Decisions (checker / cube)", "${h.checkerDecisions} / ${h.cubeDecisions}")
        Labelled("Checker errors / blunders", "${h.checkerErrors} / ${h.checkerBlunders}")
        Labelled("Double errors / blunders", "${h.doubleErrors} / ${h.doubleBlunders}")
        Labelled("Take errors / blunders", "${h.takeErrors} / ${h.takeBlunders}")
        Labelled("Error rate (NJ/GNUBG, mEMG per move)", r.njErrorRate?.let { fmt(it, 1) } ?: "–")
        h.pr?.let { Labelled("XG Elo equivalent", fmt(XgRules.eloEquivalent(it), 0)) }
        Labelled("Computer PR (same games)", r.cpu.pr?.let { fmt(it, 2) } ?: "–")
        Text(
            "PR = 500 × average equity lost per non-obvious decision (XG definition). Lower is better.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    if (r.prHistory.size >= 2) {
        Section("PR per match") {
            var kind by rememberSaveable { mutableStateOf(PrKind.TOTAL) }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                PrKind.entries.forEachIndexed { i, k ->
                    SegmentedButton(selected = kind == k, onClick = { kind = k }, shape = SegmentedButtonDefaults.itemShape(i, PrKind.entries.size)) {
                        Text(k.label)
                    }
                }
            }
            val points = r.prHistory.mapNotNull { p -> k(kind, p)?.let { p.time to it } }
            val avg = when (kind) {
                PrKind.TOTAL -> h.pr
                PrKind.CHECKER -> h.checkerPr
                PrKind.CUBE -> h.cubePr
            }
            if (points.size >= 2) {
                LineChart(
                    values = points.map { it.second },
                    labels = points.map { dateFmt.format(Date(it.first)) },
                    valueFormat = { fmt(it, 1) },
                    reference = avg,
                    referenceLabel = avg?.let { "average ${fmt(it, 1)}" },
                )
            } else {
                Text("Not enough matches with ${kind.label.lowercase()} decisions yet.", style = MaterialTheme.typography.bodyMedium)
            }
            if (kind == PrKind.CUBE) {
                Text(
                    "Matches without a cube decision are left out.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Section("Rating (FIBS formula)") {
        Hero(fmt(r.elo, 0), "${Elo.title(r.elo)} · experience ${r.experience}")
        if (r.eloHistory.size >= 2) {
            Spacer(Modifier.height(8.dp))
            LineChart(
                values = r.eloHistory.map { it.second },
                labels = r.eloHistory.map { dateFmt.format(Date(it.first)) },
                valueFormat = { fmt(it, 0) },
            )
        }
        Text(
            "Updated after every completed match against the computer (not money sessions, nor matches you left out of the statistics).",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Section("Results") {
        Labelled("Matches won", "${r.matchesWon} of ${r.matchesPlayed} (${pctOf(r.matchesWon, r.matchesPlayed)})")
        Labelled("Games won", "${r.gamesWon} of ${r.gamesPlayed} (${pctOf(r.gamesWon, r.gamesPlayed)})")
        Labelled("Gammons won / lost", "${r.gammonsWon} / ${r.gammonsLost}")
        Labelled("Backgammons won / lost", "${r.backgammonsWon} / ${r.backgammonsLost}")
        if (r.moneyGames > 0) {
            val net = r.moneyPointsWon - r.moneyPointsLost
            Labelled("Money points won / lost", "${r.moneyPointsWon} / ${r.moneyPointsLost}")
            Labelled("Money net per game", fmt(net.toDouble() / r.moneyGames, 3))
        }
    }

    Section("Luck") {
        LuckRows("You", h, r.gamesPlayed)
        LuckRows("Computer", r.cpu, r.gamesPlayed)
        Labelled("Net luck per game (you − computer)", if (r.gamesPlayed == 0) "–" else fmt((h.luck - r.cpu.luck) / r.gamesPlayed, 3))
        Text(
            "Luck = equity of the roll's best play minus the average over all rolls (0-ply), for each player's " +
                "own rolls, so both players can be lucky; the net luck shows who was luckier.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Section("Dice") { DiceSection(r.humanDice, r.cpuDice) }
}

private enum class PrKind(val label: String) { TOTAL("Total"), CHECKER("Checker"), CUBE("Cube") }

private fun k(kind: PrKind, p: com.boldblot.backgammonplyground.data.PrPoint): Double? = when (kind) {
    PrKind.TOTAL -> p.pr
    PrKind.CHECKER -> p.checkerPr
    PrKind.CUBE -> p.cubePr
}

@Composable
private fun LuckRows(who: String, p: PerformanceTotals, games: Int) {
    Labelled("$who: luck per game", if (games == 0) "–" else fmt(p.luck / games, 3))
    Labelled("$who: luck per roll", if (p.rolls == 0) "–" else fmt(p.luck / p.rolls, 4))
}

@Composable
private fun DiceSection(you: DiceTotals, cpu: DiceTotals) {
    Labelled("Rolls (you / computer)", "${you.rolls} / ${cpu.rolls}")
    Labelled("Doubles (expected 16.7%)", "${pctOf(you.doubles, you.rolls)} / ${pctOf(cpu.doubles, cpu.rolls)}")
    Labelled(
        "Average pips per roll (expected 8.17)",
        "${if (you.rolls == 0) "–" else fmt(you.pips.toDouble() / you.rolls, 2)} / ${if (cpu.rolls == 0) "–" else fmt(cpu.pips.toDouble() / cpu.rolls, 2)}",
    )
    if (you.rolls + cpu.rolls == 0) return
    Spacer(Modifier.height(8.dp))
    Text("Rolls containing each number", style = MaterialTheme.typography.titleSmall)
    fun share(d: DiceTotals) = d.faceCounts().map { if (d.rolls == 0) 0.0 else 100.0 * it / d.rolls }
    GroupedBarChart(
        groups = (1..6).map { it.toString() },
        series = listOf("You" to share(you), "Computer" to share(cpu)),
        valueFormat = { "${fmt(it, 0)}%" },
        reference = 100.0 * 11 / 36,
        referenceLabel = "expected 30.6%",
        modifier = Modifier.padding(top = 6.dp),
    )
    var table by remember { mutableStateOf(false) }
    TextButton(onClick = { table = !table }) { Text(if (table) "Hide roll table" else "Show roll table") }
    if (table) RollTable(you, cpu)
}

@Composable
private fun RollTable(you: DiceTotals, cpu: DiceTotals) {
    Row(Modifier.fillMaxWidth()) {
        Text("Roll", Modifier.width(56.dp), style = MaterialTheme.typography.labelMedium)
        Text("You", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        Text("Computer", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        Text("Expected", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
    }
    HorizontalDivider()
    for (a in 6 downTo 1) for (b in a downTo 1) {
        val expected = if (a == b) 1.0 / 36 else 2.0 / 36
        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
            Text("$a-$b", Modifier.width(56.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Text("${you.count(a, b)} (${pctOf(you.count(a, b), you.rolls)})", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text("${cpu.count(a, b)} (${pctOf(cpu.count(a, b), cpu.rolls)})", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text("${fmt(expected * 100)}%", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        }
    }
}
