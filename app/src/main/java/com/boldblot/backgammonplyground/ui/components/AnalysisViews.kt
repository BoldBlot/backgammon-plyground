package com.boldblot.backgammonplyground.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.foundation.text.appendInlineContent
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.Candidate
import com.boldblot.backgammonplyground.core.CheckerAnalysis
import com.boldblot.backgammonplyground.core.CubeEval
import com.boldblot.backgammonplyground.core.DecisionMath
import com.boldblot.backgammonplyground.core.DecisionKind
import com.boldblot.backgammonplyground.core.MoveNotation
import com.boldblot.backgammonplyground.core.Severity
import com.boldblot.backgammonplyground.core.XgRules
import com.boldblot.backgammonplyground.data.ErrorUnit
import com.boldblot.backgammonplyground.ui.theme.Quality
import java.util.Locale

fun eq(v: Float): String = String.format(Locale.US, "%+.3f", v)
fun eq(v: Double): String = String.format(Locale.US, "%+.3f", v)
fun pct(v: Float): String = String.format(Locale.US, "%.1f%%", v * 100)
fun fmt(v: Double, digits: Int = 1): String = String.format(Locale.US, "%.${digits}f", v)

/** The unit errors are shown in (Settings → Display), provided at the top of the app. */
val LocalErrorUnit = staticCompositionLocalOf { ErrorUnit.EQ }

/** Cube decisions from played-out games show a small ± that opens their standard error (Settings → Analysis). */
val LocalCubeSpread = staticCompositionLocalOf { false }

/** Match scores as "2-away, 3-away" instead of the score and the match length (Settings → Display). */
val LocalScoreAway = staticCompositionLocalOf { false }

/** The board's checker style (Settings → Board), for the checker icons beside plays and chances. */
val LocalCheckerStyle = staticCompositionLocalOf { com.boldblot.backgammonplyground.data.CheckerStyle.RIMMED }

/** An error or equity difference [v] (signed) in [unit]: "-0.026" or "-26.0". */
fun err(v: Float, unit: ErrorUnit): String = when (unit) {
    ErrorUnit.EQ -> eq(v)
    ErrorUnit.MEQ -> String.format(Locale.US, "%+.1f", v * 1000f)
}

/** [err] in the unit of Settings → Display. */
@Composable
fun errText(v: Float): String = err(v, LocalErrorUnit.current)

/** An average error [v] (unsigned) with its unit: "0.0124 Eq" or "12.4 mEq". */
fun errWithUnit(v: Double, unit: ErrorUnit): String = when (unit) {
    ErrorUnit.EQ -> String.format(Locale.US, "%.4f Eq", v)
    ErrorUnit.MEQ -> String.format(Locale.US, "%.1f mEq", v * 1000)
}

/** " (×1.5 in PR)" when errors count [factor] times in PR (1-point matches), otherwise "". */
fun factorNote(factor: Float): String = if (factor == 1f) "" else " (×${fmt(factor.toDouble())} in PR)"

@Composable
fun severityColor(s: Severity): Color = Quality.color(s, isSystemInDarkTheme())

/**
 * Small coloured label like "Blunder -0.124". [error] is the weighted error (as for PR and the
 * severity); the label shows the plain equity lost, that is [error] / [factor], and in a 1-point
 * match notes the weighting: "Error -0.026 (×1.5 in PR)".
 */
@Composable
fun SeverityBadge(severity: Severity, error: Float?, modifier: Modifier = Modifier, factor: Float = 1f) {
    val text = if (severity == Severity.NONE) "Best" else "${severity.label} ${error?.let { errText(-it / factor) + factorNote(factor) } ?: ""}"
    ErrorPill(text, severity, modifier, style = MaterialTheme.typography.labelLarge, bold = true)
}

/** Whether the app is drawn dark (it follows the system). */
@Composable
private fun dark(): Boolean = isSystemInDarkTheme()

/** The pale fill of a severity (the dark theme's colours), for pills and tinted rows. */
fun severityFill(s: Severity): Color = Quality.color(s, dark = true)

/**
 * A number or label in a pill tinted with [severity]'s colour (the analysis lists, the move list,
 * the badge): dark text in the light theme, the severity's colour in the dark one.
 */
@Composable
fun ErrorPill(
    text: String, severity: Severity, modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium, mono: Boolean = false, bold: Boolean = false,
) {
    val dark = dark()
    // The text sits in the middle of the pill whatever room the layout gives it (landscape rows
    // stretch it): centred both ways, the font's extra line spacing trimmed so it isn't low.
    Box(
        modifier
            .background(severityFill(severity).copy(alpha = if (dark) 0.30f else 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (dark) severityColor(severity) else Color(0xFF141414),
            style = style.copy(
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
                ),
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
            ),
            maxLines = 1,
            fontFamily = if (mono) FontFamily.Monospace else null,
            fontWeight = if (bold) FontWeight.SemiBold else null,
        )
    }
}

/**
 * A small checker in a player's colours (in place of plain dots, which read as severities), always drawn
 * as the board's Rimmed style whatever style the board uses. [rim]: the board's rim colour for it.
 */
@Composable
fun CheckerIcon(color: Color, modifier: Modifier = Modifier, rim: Color = Color.Unspecified) {
    val edge = if (rim != Color.Unspecified) rim else Color(color.red * 0.62f, color.green * 0.62f, color.blue * 0.62f, color.alpha)
    val style = LocalCheckerStyle.current
    androidx.compose.foundation.Canvas(modifier.size(12.dp)) {
        with(com.boldblot.backgammonplyground.ui.board.BoardDrawing) {
            checkerIcon(center, size.minDimension / 2, color, edge, style)
        }
    }
}

/**
 * [before], the match score with a checker in each player's colours on either side ("[o] 3–5 [o]",
 * your score first, by your checker), then [after]. Without a [score] (money) only the text.
 */
@Composable
fun ScoreText(
    before: String, score: List<Int>?, after: String, colors: com.boldblot.backgammonplyground.ui.board.BoardColors,
    modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
    color: Color = Color.Unspecified, maxLines: Int = Int.MAX_VALUE,
    /** With [Settings.scoreAway] the [score] is shown as the points each player is away from [matchLength] (and [before] is not shown). */
    matchLength: Int = 0, awayBefore: String = "",
) {
    val away = LocalScoreAway.current && score != null && matchLength > 0
    val text = androidx.compose.ui.text.buildAnnotatedString {
        append(if (away) awayBefore else before)
        if (score != null) {
            appendInlineContent("you", "●")
            if (away) append(" ${matchLength - score[0]}-away, ${matchLength - score[1]}-away ") else append(" ${score[0]}–${score[1]} ")
            appendInlineContent("them", "●")
        }
        append(after)
    }
    fun icon(c: Color, rim: Color) = androidx.compose.foundation.text.InlineTextContent(
        androidx.compose.ui.text.Placeholder(1.em, 1.em, androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter),
    ) { CheckerIcon(c, rim = rim) }
    Text(
        text, modifier, color = color, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        inlineContent = mapOf("you" to icon(colors.human, colors.humanRim), "them" to icon(colors.cpu, colors.cpuRim)),
    )
}

/**
 * A player's shades on a chances bar: single wins, gammons, backgammons. Single wins are the checker
 * colour itself; gammons and backgammons step away from it: brighter for dark and mid-tone checkers,
 * darker for light ones (which cannot get brighter), so the three parts stay apart on any background.
 */
fun chanceShades(c: Color): List<Color> {
    val toward = if (c.luminance() > 0.65f) Color.Black else Color.White
    val steps = if (toward == Color.Black) listOf(0.22f, 0.45f) else listOf(0.3f, 0.58f)
    return listOf(c) + steps.map { androidx.compose.ui.graphics.lerp(c, toward, it) }
}

/**
 * Winning chances in one bar, as XG shows them: from the left [left]'s backgammons, gammons and single
 * wins, then [right]'s single wins, gammons and backgammons. [probs] are [left]'s (win, gammon,
 * backgammon, lose gammon, lose backgammon).
 */
@Composable
fun ChancesBar(probs: List<Float>, left: Color, right: Color, modifier: Modifier = Modifier) {
    val win = probs[0]
    val lose = 1 - win
    val l = chanceShades(left)
    val r = chanceShades(right)
    val mine = listOf(probs[2] to l[2], probs[1] - probs[2] to l[1], win - probs[1] to l[0])
    val theirs = listOf(lose - probs[3] to r[0], probs[3] - probs[4] to r[1], probs[4] to r[2])
    Row(modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        for ((w, c) in mine) if (w > 0.0005f) Box(Modifier.weight(w).fillMaxHeight().background(c))
        Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface))
        for ((w, c) in theirs) if (w > 0.0005f) Box(Modifier.weight(w).fillMaxHeight().background(c))
    }
}

/** Cubeless chances of both players, each behind a checker in its colour: "62.3%  G 18.1%  B 1.2%". */
@Composable
fun ChancesRows(probs: List<Float>, roller: Pair<Color, Color>, other: Pair<Color, Color>, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelMedium
    Column(modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ChancesBar(probs, roller.first, other.first)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CheckerIcon(roller.first, rim = roller.second)
            Text(" ${pct(probs[0])}  G ${pct(probs[1])}  B ${pct(probs[2])}", style = style, modifier = Modifier.weight(1f), maxLines = 1)
            CheckerIcon(other.first, rim = other.second)
            Text(" ${pct(1 - probs[0])}  G ${pct(probs[3])}  B ${pct(probs[4])}", style = style, maxLines = 1)
        }
    }
}

/** A small grey tag after a play ("you", "played", "my answer"). */
@Composable
private fun Tag(text: String) {
    Text("$text  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
    /** The board's rim colours of the mover's and the opponent's checkers, for the icons. */
    moverRim: Color = Color.Unspecified,
    opponentRim: Color = Color.Unspecified,
    onSelect: ((Int, Candidate) -> Unit)? = null,
    /** The player's own answer (My positions): a ✓ before the move. */
    checkIndex: Int? = null,
    /** The tag of [highlightIndex]'s row: "you" in a review, "played" in My positions. */
    playedLabel: String = "you",
    /** "Try again": plays tried and taken back (a yellow "tried" marker) and the play made at last (a green ✓). */
    triedIndices: Set<Int> = emptySet(),
    finalIndex: Int? = null,
) {
    val best = analysis.bestEquity
    Column(Modifier.fillMaxWidth()) {
        val rows = analysis.candidates.withIndex().filter { (i, _) -> i < maxRows || i == highlightIndex || i == checkIndex || i in triedIndices || i == finalIndex }
        for ((i, c) in rows) {
            val diff = c.equity - best
            val sev = XgRules.severity(-diff * factor)
            val isPlayed = i == highlightIndex
            // The played row is tinted with its severity's colour and outlined.
            val bg = when {
                i == selectedIndex -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                isPlayed -> severityFill(sev).copy(alpha = if (dark()) 0.22f else 0.45f)
                else -> Color.Transparent
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(bg, RoundedCornerShape(8.dp))
                    .let { if (isPlayed) it.border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp)) else it }
                    .let { if (onSelect != null) it.clickable { onSelect(i, c) } else it }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", Modifier.width(22.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        MoveNotation.format(board, player, c.move),
                        Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isPlayed) FontWeight.Bold else FontWeight.Medium,
                    )
                    if (i in triedIndices) TryMarker("tried", Severity.INACCURACY)
                    if (i == finalIndex) TryMarker("final", Severity.NONE)
                    if (i == checkIndex) Tag("my answer")
                    if (isPlayed) Tag(playedLabel)
                    if (i == 0) Text(eq(c.equity), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                    else ErrorPill("(${errText(diff)})", sev, mono = true)
                }
                if (detailed) {
                    val (me, them) = odds(c.probs)
                    FlowRow(Modifier.padding(start = 22.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OddsChip(moverColor, moverRim, me)
                        OddsChip(opponentColor, opponentRim, them)
                    }
                }
            }
        }
        Text(
            "${analysis.legalMoves} legal move${if (analysis.legalMoves == 1) "" else "s"} · ${analysis.label} · cubeful equity (EMG)",
            Modifier.padding(4.dp), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OddsChip(color: Color, rim: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CheckerIcon(color, rim = rim)
        Text(" $text", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/**
 * Galaxy-style summary under the board: the played move and the best move, each in a box with its small
 * label (PLAYED, BEST) on a line above. [playedError] is the plain equity lost (no 1-point factor).
 * The bar always has the same height: long text shrinks a little, then is cut, but never wraps.
 */
@Composable
fun PlayedBestBar(
    playedText: String,
    playedError: Float?,
    playedSeverity: Severity,
    bestText: String,
    bestEquity: Float?,
    modifier: Modifier = Modifier,
    /** Stack the two boxes (for narrow panels). */
    stacked: Boolean = false,
    onPlayed: (() -> Unit)? = null,
    onBest: (() -> Unit)? = null,
) {
    val playedValue = playedError?.let { if (it > 0.0005f) "(${errText(-it)})" else "" } ?: ""
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

/** One box of the bar: [tag] and its colour dot on a small line above, then [text] and [value] on one line. */
@Composable
private fun BarChip(text: String, tag: String, dot: Color, value: String, modifier: Modifier, onClick: (() -> Unit)?) {
    Column(modifier) {
        Row(Modifier.padding(start = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(dot, CircleShape))
            Spacer(Modifier.width(4.dp))
            Text(
                tag, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val density = androidx.compose.ui.platform.LocalDensity.current
            val typography = MaterialTheme.typography
            val valueStyle = typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            // The text gets what the value leaves; below 14sp it shrinks (down to 11sp) before it is cut.
            val size = remember(text, value, maxWidth, density, typography) {
                val room = with(density) { maxWidth.toPx() } - measurer.measure(value, valueStyle).size.width - with(density) { 8.dp.toPx() }
                listOf(14, 13, 12, 11).firstOrNull { sp ->
                    measurer.measure(text, typography.bodyMedium.copy(fontSize = sp.sp, fontWeight = FontWeight.SemiBold)).size.width <= room
                } ?: 11
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text, Modifier.weight(1f), style = typography.bodyMedium.copy(fontSize = size.sp), fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(value, fontFamily = FontFamily.Monospace, style = typography.bodySmall, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * Cube decision table. [kind] is the action taken (if any), from the decision's point of view:
 * a response to a double shows the taker's options, a raccoon decision the doubler's answers
 * to a beaver. [beavers]: a beaver is one of the taker's options.
 */
/** The best action at a cube decision of [kind] ("Double, take", "Pass", "Raccoon"); [eval] is the doubler's view. */
fun bestCubeLabel(eval: CubeEval, kind: DecisionKind?, beavers: Boolean = false): String {
    if (kind?.isRaccoonDecision == true) return if (4 * eval.doubleTake > 2 * eval.doubleTake) "Raccoon" else "Play on"
    val beaver = beavers && eval.doubleTake < 0f
    if (kind?.isResponse == true) return when {
        beaver -> "Beaver"
        eval.doubleTake <= eval.doublePass -> "Take"
        else -> "Pass"
    }
    val after = DecisionMath.afterDouble(eval, beavers)
    return when {
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
}

@Composable
fun CubeTable(
    eval: CubeEval, kind: DecisionKind?, factor: Float, beavers: Boolean = false, mine: DecisionKind? = null,
    /** What was played in the game (My positions), tagged "played". */
    played: DecisionKind? = null,
    /** Checker colours for the chances; [rollerHuman]: the player on roll (the doubler, whose view [eval] is) is the human. */
    colors: com.boldblot.backgammonplyground.ui.board.BoardColors? = null,
    rollerHuman: Boolean = true,
) {
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
            val best = bestCubeLabel(eval, kind, beavers)
            // My answer "Double" goes on the row of the opponent's best answer to it.
            val doubled = mine == DecisionKind.DOUBLE
            val answerRow = when {
                beaver -> 3
                eval.doubleTake <= eval.doublePass -> 1
                else -> 2
            }
            val playedDouble = played == DecisionKind.DOUBLE
            CubeRow("No double", eval.noDouble, eval.noDouble - optimal, factor, mine == DecisionKind.NO_DOUBLE, played == DecisionKind.NO_DOUBLE)
            CubeRow("Double, take", eval.doubleTake, eval.doubleTake - optimal, factor, doubled && answerRow == 1, playedDouble && answerRow == 1)
            CubeRow("Double, pass", eval.doublePass, eval.doublePass - optimal, factor, doubled && answerRow == 2, playedDouble && answerRow == 2)
            if (beaver) CubeRow("Double, beaver", 2 * eval.doubleTake, 2 * eval.doubleTake - optimal, factor, doubled && answerRow == 3, playedDouble && answerRow == 3)
            Text("Best: $best", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        } else {
            val (take, pass, beaver) = DecisionMath.responseEquities(eval, beavers || kind == DecisionKind.BEAVER)
            val best = maxOf(take, pass, beaver ?: take)
            CubeRow("Take", take, take - best, factor, mine == DecisionKind.TAKE, played == DecisionKind.TAKE)
            CubeRow("Pass", pass, pass - best, factor, mine == DecisionKind.PASS, played == DecisionKind.PASS)
            if (beaver != null) CubeRow("Beaver", beaver, beaver - best, factor, mine == DecisionKind.BEAVER, played == DecisionKind.BEAVER)
            val bestName = when {
                beaver != null && beaver > take && beaver >= pass -> "Beaver"
                take >= pass -> "Take"
                else -> "Pass"
            }
            Text("Best: $bestName", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        val c = colors ?: com.boldblot.backgammonplyground.ui.board.BoardColors.gray()
        val human = c.human to c.humanRim
        val cpu = c.cpu to c.cpuRim
        if (eval.probs.size >= 5) ChancesRows(eval.probs, if (rollerHuman) human else cpu, if (rollerHuman) cpu else human)
        val spread = eval.spread?.takeIf { LocalCubeSpread.current }
        val spreadShown = androidx.compose.runtime.remember(eval) { androidx.compose.runtime.mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${eval.label} · cubeless chances",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Played-out games: a small ± that opens their standard error (a setting, off by default).
            if (spread != null) Text(
                " ±", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { spreadShown.value = !spreadShown.value }.padding(horizontal = 6.dp),
            )
        }
        if (spread != null && spreadShown.value) Text(
            "No double against Double/take: ± ${"%.3f".format(java.util.Locale.US, spread)} (standard error of the played-out games). Closer than that is a toss-up.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CubeRow(label: String, equity: Float, diff: Float, factor: Float, mine: Boolean = false, played: Boolean = false) {
    val sev = XgRules.severity(-diff * factor)
    Row(
        Modifier.fillMaxWidth().let { if (mine) it.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(6.dp)) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = if (mine) FontWeight.Bold else null)
        if (mine) Tag("my answer")
        if (played) Tag("played")
        Text(eq(equity), Modifier.width(70.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.width(76.dp), contentAlignment = Alignment.CenterEnd) {
            if (diff < -0.0005f) ErrorPill(errText(diff), sev, style = MaterialTheme.typography.bodySmall, mono = true)
        }
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

/** "Try again": "tried" (yellow) on a play tried and taken back, "final" (green) on the play made at last. */
@Composable
private fun TryMarker(text: String, colour: Severity) {
    ErrorPill(text, colour, Modifier.padding(end = 6.dp), style = MaterialTheme.typography.labelSmall, bold = true)
}
