package com.boldblot.backgammonplyground.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.Candidate
import com.boldblot.backgammonplyground.core.CheckerAnalysis
import com.boldblot.backgammonplyground.core.CubeEval
import com.boldblot.backgammonplyground.core.DecisionMath
import com.boldblot.backgammonplyground.core.DecisionKind
import com.boldblot.backgammonplyground.core.MoveNotation
import com.boldblot.backgammonplyground.core.Severity
import com.boldblot.backgammonplyground.core.XgRules
import com.boldblot.backgammonplyground.ui.theme.Quality
import java.util.Locale

fun eq(v: Float): String = String.format(Locale.US, "%+.3f", v)
fun eq(v: Double): String = String.format(Locale.US, "%+.3f", v)
fun pct(v: Float): String = String.format(Locale.US, "%.1f%%", v * 100)
fun fmt(v: Double, digits: Int = 1): String = String.format(Locale.US, "%.${digits}f", v)

@Composable
fun severityColor(s: Severity): Color = Quality.color(s, isSystemInDarkTheme())

/** Small coloured label like "Blunder -0.124". */
@Composable
fun SeverityBadge(severity: Severity, error: Float?, modifier: Modifier = Modifier) {
    val color = severityColor(severity)
    val text = if (severity == Severity.NONE) "Best" else "${severity.label} ${error?.let { eq(-it) } ?: ""}"
    Text(
        text,
        modifier = modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = color,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

/** "64.6% (G 23.4% B 0.5%)" style odds for the mover and the opponent. */
fun odds(probs: List<Float>): Pair<String, String> {
    val mover = "${pct(probs[0])} (G ${pct(probs[1])} B ${pct(probs[2])})"
    val opp = "${pct(1f - probs[0])} (G ${pct(probs[3])} B ${pct(probs[4])})"
    return mover to opp
}

/**
 * Ranked candidate plays. [board] is the position before the move, [player] the mover.
 * [factor] is the 1-point-match error factor used to colour the rows. With [detailed],
 * each row also shows win / gammon / backgammon chances for both sides (dots in the
 * players' checker colours).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CandidateList(
    board: Board,
    player: Int,
    analysis: CheckerAnalysis,
    factor: Float,
    highlightIndex: Int?,
    maxRows: Int = 6,
    selectedIndex: Int? = null,
    detailed: Boolean = false,
    moverColor: Color = Color.Unspecified,
    opponentColor: Color = Color.Unspecified,
    onSelect: ((Int, Candidate) -> Unit)? = null,
) {
    val best = analysis.bestEquity
    Column(Modifier.fillMaxWidth()) {
        val rows = analysis.candidates.withIndex().filter { (i, _) -> i < maxRows || i == highlightIndex }
        for ((i, c) in rows) {
            val diff = c.equity - best
            val sev = XgRules.severity(-diff * factor)
            val isPlayed = i == highlightIndex
            val bg = when {
                i == selectedIndex -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                isPlayed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else -> Color.Transparent
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(bg, RoundedCornerShape(8.dp))
                    .let { if (onSelect != null) it.clickable { onSelect(i, c) } else it }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", Modifier.width(22.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isPlayed) {
                        Box(Modifier.size(8.dp).background(severityColor(sev), RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        MoveNotation.format(board, player, c.move),
                        Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isPlayed) FontWeight.Bold else FontWeight.Medium,
                    )
                    if (isPlayed) Text("you  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (i == 0) eq(c.equity) else "(${eq(diff)})",
                        fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium,
                        color = if (i == 0) Color.Unspecified else severityColor(sev),
                    )
                }
                if (detailed) {
                    val (me, them) = odds(c.probs)
                    FlowRow(Modifier.padding(start = 22.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OddsChip(moverColor, me)
                        OddsChip(opponentColor, them)
                    }
                }
            }
        }
        Text(
            "${analysis.legalMoves} legal move${if (analysis.legalMoves == 1) "" else "s"} · ${com.boldblot.backgammonplyground.core.plyLabel(analysis.plies)} · cubeful equity (EMG)",
            Modifier.padding(4.dp), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OddsChip(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
        Text(" $text", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Galaxy-style summary under the board: the played move and the best move. */
@Composable
fun PlayedBestBar(
    playedText: String,
    playedError: Float?,
    playedSeverity: Severity,
    bestText: String,
    bestEquity: Float?,
    modifier: Modifier = Modifier,
    /** Stack the two chips (for narrow panels). */
    stacked: Boolean = false,
    onPlayed: (() -> Unit)? = null,
    onBest: (() -> Unit)? = null,
) {
    val playedValue = playedError?.let { if (it > 0.0005f) "(${eq(-it)})" else "" } ?: ""
    val bestValue = bestEquity?.let { eq(it) } ?: ""
    if (stacked) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BarChip(playedText, "PLAYED", severityColor(playedSeverity), playedValue, Modifier.fillMaxWidth(), onPlayed)
            BarChip(bestText, "BEST", severityColor(Severity.NONE), bestValue, Modifier.fillMaxWidth(), onBest)
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BarChip(playedText, "PLAYED", severityColor(playedSeverity), playedValue, Modifier.weight(1f), onPlayed)
            BarChip(bestText, "BEST", severityColor(Severity.NONE), bestValue, Modifier.weight(1f), onBest)
        }
    }
}

@Composable
private fun BarChip(text: String, tag: String, dot: Color, value: String, modifier: Modifier, onClick: (() -> Unit)?) {
    Row(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).background(dot, RoundedCornerShape(5.dp)))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(6.dp))
        Text(
            tag, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.background(dot.copy(alpha = 0.25f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Cube decision table. [kind] is the action taken (if any), from the decision's point of view:
 * a response to a double shows the taker's options, a raccoon decision the doubler's answers
 * to a beaver. [beavers]: a beaver is one of the taker's options.
 */
@Composable
fun CubeTable(eval: CubeEval, kind: DecisionKind?, factor: Float, beavers: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (kind?.isRaccoonDecision == true) {
            // Doubler's view, in units of the cube before the double.
            val playOn = 2 * eval.doubleTake
            val raccoon = 4 * eval.doubleTake
            val best = maxOf(playOn, raccoon)
            CubeRow("Play on (cube 4×)", playOn, playOn - best, factor)
            CubeRow("Raccoon (cube 8×)", raccoon, raccoon - best, factor)
            Text("Best: ${if (raccoon > playOn) "Raccoon" else "Play on"}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        } else if (kind?.isResponse != true) {
            // With beavers, a take worth more than nothing to the taker is a beaver (cube 4×).
            val beaver = beavers && eval.doubleTake < 0f
            val after = DecisionMath.afterDouble(eval, beavers)
            val optimal = if (beaver) maxOf(eval.noDouble, after) else eval.optimal
            val best = when {
                !eval.cubeAvailable -> "Cube not available"
                after > eval.noDouble -> when {
                    beaver -> "Double, beaver"
                    eval.doubleTake <= eval.doublePass -> "Double, take"
                    else -> "Double, pass"
                }
                eval.doubleTake > eval.doublePass -> "Too good to double, pass"
                beaver -> "No double, beaver"
                else -> "No double, take"
            }
            CubeRow("No double", eval.noDouble, eval.noDouble - optimal, factor)
            CubeRow("Double, take", eval.doubleTake, eval.doubleTake - optimal, factor)
            CubeRow("Double, pass", eval.doublePass, eval.doublePass - optimal, factor)
            if (beaver) CubeRow("Double, beaver", 2 * eval.doubleTake, 2 * eval.doubleTake - optimal, factor)
            Text("Best: $best", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        } else {
            val (take, pass, beaver) = DecisionMath.responseEquities(eval, beavers || kind == DecisionKind.BEAVER)
            val best = maxOf(take, pass, beaver ?: take)
            CubeRow("Take", take, take - best, factor)
            CubeRow("Pass", pass, pass - best, factor)
            if (beaver != null) CubeRow("Beaver", beaver, beaver - best, factor)
            val bestName = when {
                beaver != null && beaver > take && beaver >= pass -> "Beaver"
                take >= pass -> "Take"
                else -> "Pass"
            }
            Text("Best: $bestName", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Win ${pct(eval.probs[0])} · gammon ${pct(eval.probs[1])} · lose gammon ${pct(eval.probs[3])} (cubeless)",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CubeRow(label: String, equity: Float, diff: Float, factor: Float) {
    val sev = XgRules.severity(-diff * factor)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(eq(equity), Modifier.width(70.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (diff >= -0.0005f) "" else eq(diff), Modifier.width(64.dp), fontFamily = FontFamily.Monospace,
            color = severityColor(sev), style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** One-line description of a decision, e.g. "13/7 8/7" or "Double". */
fun describeAction(kind: DecisionKind, board: Board, player: Int, move: com.boldblot.backgammonplyground.core.Move?): String = when (kind) {
    DecisionKind.CHECKER -> move?.let { MoveNotation.format(board, player, it) } ?: "?"
    DecisionKind.DOUBLE -> "Double"
    DecisionKind.NO_DOUBLE -> "No double (roll)"
    DecisionKind.TAKE -> "Take"
    DecisionKind.PASS -> "Pass"
    DecisionKind.BEAVER -> "Beaver"
    DecisionKind.RACCOON -> "Raccoon"
    DecisionKind.NO_RACCOON -> "No raccoon (play on)"
}

/** The cube as the board shows it at decision [d]: offered 2× for a response, 4× for a raccoon decision. */
fun decisionCube(d: com.boldblot.backgammonplyground.core.DecisionRecord): Pair<com.boldblot.backgammonplyground.core.CubeState, Boolean> = when {
    d.kind.isResponse -> d.cube to true
    d.kind.isRaccoonDecision -> d.cube.copy(value = d.cube.value * 2) to true
    else -> d.cube to false
}

@Composable
fun Labelled(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}
