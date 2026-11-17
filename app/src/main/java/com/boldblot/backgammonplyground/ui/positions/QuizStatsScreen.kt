package com.boldblot.backgammonplyground.ui.positions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.QuizAnswer
import com.boldblot.backgammonplyground.core.QuizStats
import com.boldblot.backgammonplyground.core.QuizStatsReport
import com.boldblot.backgammonplyground.core.QuizTally
import com.boldblot.backgammonplyground.ui.components.GroupedBarChart
import com.boldblot.backgammonplyground.ui.components.LineChart
import com.boldblot.backgammonplyground.ui.components.fmt
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

private enum class Period(val label: String, val days: Int) { WEEK("7 days", 7), MONTH("30 days", 30), ALL("All time", 0) }

private val dayFmt = SimpleDateFormat("d MMM", Locale.getDefault())

private fun pct(r: Double?) = if (r == null) "–" else "${fmt(r * 100, 0)}%"

private fun tallyText(t: QuizTally) = if (t.answered == 0) "–" else "${pct(t.rate)} of ${t.answered}"

/** The quiz's statistics: how the answers kept so far went (not game quizzes or Quiz a friend). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuizStatsBody(app: PlyGroundApp, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    var period by remember { mutableStateOf(Period.MONTH) }
    var report by remember { mutableStateOf<QuizStatsReport?>(null) }
    var titles by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var empty by remember { mutableStateOf(false) }
    LaunchedEffect(period) {
        val all = app.positions.quizAnswers().map {
            QuizAnswer(it.at, it.positionId, runCatching { QuizAnswer.Verdict.valueOf(it.verdict) }.getOrDefault(QuizAnswer.Verdict.NEUTRAL), it.error, it.cube, it.match)
        }
        empty = all.isEmpty()
        val since = if (period.days == 0) 0L else System.currentTimeMillis() - period.days * 86_400_000L
        val folders = app.positions.folderLinks().map { it.positionId to it.name }
        val r = QuizStats.report(all, folders, since)
        titles = r.missed.mapNotNull { m -> app.positions.savedPosition(m.positionId)?.let { m.positionId to (it.entity.source.ifBlank { "Position ${m.positionId}" } + " · " + (if (it.position.dice == null) "cube" else "roll ${it.position.dice}")) } }.toMap()
        report = r
    }
    run {
        val r = report
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (r == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            if (empty) {
                Text("No answers kept yet. Quiz your positions and they show up here (game quizzes and Quiz a friend are not counted).")
                return@Column
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Period.entries.forEach { p -> FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label) }) }
            }
            val t = r.total
            Text(pct(t.rate), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Text(
                "right of ${t.answered} answers · ${t.right} right · ${t.wrong} wrong · ${t.neutral} neutral",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row2("Streak", "${r.streak} day${if (r.streak == 1) "" else "s"} (best ${r.bestStreak})")
            Row2("Average equity lost per answer", r.averageError?.let { fmt(it, 3) } ?: "–")
            Row2("Checker plays", tallyText(r.checker))
            Row2("Cube decisions", tallyText(r.cube))
            Row2("Match positions", tallyText(r.match))
            Row2("Money positions", tallyText(r.money))
            val points = r.days.filter { it.tally.answered > 0 }
            if (points.size >= 2) {
                Text("Right per day", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                val shown = points.takeLast(60)
                LineChart(
                    values = shown.map { (it.tally.rate ?: 0.0) * 100 },
                    labels = shown.map { dayFmt.format(Date(LocalDate.ofEpochDay(it.day).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())) + " · ${it.tally.answered} answers" },
                    valueFormat = { fmt(it, 0) + "%" },
                )
            }
            if (points.isNotEmpty()) {
                Text("Answers per day", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                val shown = points.takeLast(14)
                GroupedBarChart(
                    groups = shown.map { dayFmt.format(Date(LocalDate.ofEpochDay(it.day).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())) },
                    series = listOf("Answers" to shown.map { it.tally.answered.toDouble() }),
                    valueFormat = { fmt(it, 0) },
                )
            }
            if (r.folders.isNotEmpty()) {
                Text("Hardest folders", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                Text("Lowest right-rate first; folders with at least ${QuizStats.MIN_FOLDER} answers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                r.folders.take(8).forEach { f -> Row2(f.name, tallyText(f.tally)) }
            }
            if (r.missed.isNotEmpty()) {
                Text("Missed most", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                r.missed.forEach { m ->
                    val name = titles[m.positionId]
                    if (name != null) Row(Modifier.fillMaxWidth().clickable { onOpen(m.positionId) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), maxLines = 1)
                        Text("missed ${m.missed} of ${m.answered}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Row2(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
