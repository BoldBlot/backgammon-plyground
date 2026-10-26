package com.boldblot.backgammonplyground.ui.review

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.GameSummary
import com.boldblot.backgammonplyground.core.Opponent
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.WinType
import com.boldblot.backgammonplyground.data.GameEntity
import com.boldblot.backgammonplyground.data.MatchEntity
import com.boldblot.backgammonplyground.data.MatchReport
import com.boldblot.backgammonplyground.ui.components.fmt
import com.boldblot.backgammonplyground.ui.game.PerformanceRows
import com.boldblot.backgammonplyground.ui.game.SummaryRow

/**
 * The end-of-game summary for a saved match: game [gameNumber] (null: the last one) or the whole
 * match / session, with the same rows as at the end of a game (PR, rating, luck, errors).
 */
@Composable
fun SavedSummaryDialog(app: PlyGroundApp, matchId: Long, gameNumber: Int?, startWithMatch: Boolean, onDismiss: () -> Unit) {
    var match by remember { mutableStateOf<MatchEntity?>(null) }
    var games by remember { mutableStateOf<List<Pair<GameEntity, GameSummary>>>(emptyList()) }
    var report by remember { mutableStateOf<MatchReport?>(null) }
    LaunchedEffect(matchId) {
        match = app.repository.match(matchId)
        games = app.repository.gameSummaries(matchId)
        report = runCatching { app.repository.matchReport(matchId) }.getOrNull()
    }
    val m = match
    val game = games.firstOrNull { it.first.number == gameNumber } ?: games.lastOrNull()
    var showMatch by remember(games) { mutableStateOf(startWithMatch && games.size > 1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (m == null) "Summary" else if (m.length == 0) "Money session" else "${m.length}-point match") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (m == null || game == null) {
                    Text("No finished games yet.")
                    return@Column
                }
                Text(
                    "vs ${Opponent.name(m.cpuLevel)} · " +
                        (if (m.length == 0) "session" else "score") + ": you ${m.humanScore}, computer ${m.cpuScore} · ${m.games} game" +
                        if (m.games == 1) "" else "s",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val r = report
                if (games.size > 1 && r != null) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        SegmentedButton(selected = !showMatch, onClick = { showMatch = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                            Text("Game ${game.first.number}", maxLines = 1)
                        }
                        SegmentedButton(selected = showMatch, onClick = { showMatch = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                            Text("${if (m.length == 0) "Session" else "Match"} (${r.games} games)", maxLines = 1)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                if (showMatch && r != null) {
                    SummaryRow(if (m.length > 0) "${m.length}-point match" else "Money session", "You", "Computer", header = true)
                    SummaryRow("Games won", r.gamesWon[0].toString(), r.gamesWon[1].toString(), strong = true)
                    PerformanceRows(r.human, r.cpu)
                    val before = r.eloBefore
                    val after = r.eloAfter
                    if (before != null && after != null) {
                        val change = after - before
                        Text(
                            "Your FIBS rating: ${fmt(before, 1)} → ${fmt(after, 1)} (${if (change >= 0) "+" else "−"}${fmt(kotlin.math.abs(change), 1)})",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                } else {
                    val (e, s) = game
                    val res = s.result
                    val won = res.winner == Player.HUMAN
                    val kind = when (res.type) {
                        WinType.SINGLE -> if (res.byPass) "by a pass" else "a single game"
                        WinType.GAMMON -> "a gammon"
                        WinType.BACKGAMMON -> "a backgammon"
                    }
                    Text(
                        "Game ${e.number} (${e.scoreBeforeHuman}-${e.scoreBeforeCpu}): ${if (won) "you win" else "computer wins"} $kind, " +
                            "+${res.points} point${if (res.points > 1) "s" else ""}" + if (res.resigned) " (resigned)" else "",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp),
                    )
                    SummaryRow("Game ${e.number}", "You", "Computer", header = true)
                    PerformanceRows(s.performance[0], s.performance[1])
                }
                Text(
                    "Luck: each player's rolls compared with the average roll in the same position, so both " +
                        "players can be lucky; the net luck shows who was luckier.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
