package com.boldblot.backgammonplyground.ui.review

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.CheckerAnalysis
import com.boldblot.backgammonplyground.core.CubeEval
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.PositionAnalysis
import com.boldblot.backgammonplyground.data.ErrorUnit
import com.boldblot.backgammonplyground.ui.components.LocalErrorUnit
import com.boldblot.backgammonplyground.ui.components.err
import com.boldblot.backgammonplyground.core.DecisionKind
import com.boldblot.backgammonplyground.core.DecisionMath
import com.boldblot.backgammonplyground.core.DecisionRecord
import com.boldblot.backgammonplyground.core.MoveNotation
import com.boldblot.backgammonplyground.core.PositionIds
import com.boldblot.backgammonplyground.core.Severity
import com.boldblot.backgammonplyground.core.StudyPosition
import com.boldblot.backgammonplyground.core.XgRules
import com.boldblot.backgammonplyground.ui.components.PlayedBestBar
import com.boldblot.backgammonplyground.ui.components.eq
import com.boldblot.backgammonplyground.ui.theme.Quality
import kotlinx.coroutines.launch
import java.io.File

/** What a review board shows: the decision with arrows, the bare position, or the position after a move. */
enum class ReviewView(val label: String) { ARROWS("Arrows"), BOARD("Board"), AFTER("After") }

/**
 * The chips that pick [ReviewView] (After greyed out for a decision without a move), then a Save chip
 * that saves the position to My positions ([onSave]; none when it cannot be saved).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewViewChips(view: ReviewView, hasMove: Boolean, onPick: (ReviewView) -> Unit, onSave: (() -> Unit)?, modifier: Modifier = Modifier) {
    val shown = if (!hasMove && view == ReviewView.AFTER) ReviewView.BOARD else view
    FlowRow(
        modifier, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        ReviewView.entries.forEach { v ->
            FilterChip(
                selected = shown == v, onClick = { onPick(v) }, label = { Text(v.label) },
                enabled = hasMove || v != ReviewView.AFTER,
            )
        }
        if (onSave != null) {
            AssistChip(
                onClick = onSave, label = { Text("Save") },
                leadingIcon = { Icon(Icons.Default.BookmarkAdd, null, Modifier.size(AssistChipDefaults.IconSize)) },
            )
        }
    }
}

/**
 * Keeps the room of [content] when it is not [shown] (in landscape the board takes what is left, so
 * it would change size between decisions with and without a played/best bar).
 */
@Composable
fun KeepRoom(shown: Boolean, content: @Composable () -> Unit) {
    if (shown) content() else Box(Modifier.alpha(0f).clearAndSetSemantics {}) { content() }
}

/** The played/best bar's room, for [KeepRoom] on decisions without one. */
@Composable
fun EmptyPlayedBestBar() = PlayedBestBar("", null, Severity.NONE, "", null, Modifier.padding(top = 4.dp))

/**
 * The board of a review showing [view] of decision [d]: the position after the move chosen in the
 * list ([candidate]), or after the move played when none is chosen; otherwise the position itself.
 * Returns the board and whether it is the position after a move.
 */
fun reviewBoard(d: DecisionRecord, view: ReviewView, candidate: Int?): Pair<Board, Boolean> {
    if (view != ReviewView.AFTER) return d.board to false
    val move = candidate?.let { d.checker?.candidates?.getOrNull(it)?.move } ?: d.reviewMove ?: return d.board to false
    return d.board.applyMove(d.player, move) to true
}

/** Records what a board draws, so that it can be shared as a picture. */
class BoardCapture(val layer: GraphicsLayer) {
    /** For the board's modifier: draws it through [layer]. */
    val modifier: Modifier
        get() = Modifier.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }

    suspend fun image(): ImageBitmap = layer.toImageBitmap()
}

@Composable
fun rememberBoardCapture(): BoardCapture = BoardCapture(rememberGraphicsLayer())

/**
 * The share menu's entries for decision [d]: its XGID, the board as shown ([capture]), and the board
 * with the top five plays (or the cube table). [title] heads the picture ("5-point match · score 2-1").
 */
@Composable
fun SharePositionItems(
    d: DecisionRecord, matchLength: Int, jacoby: Boolean, title: String, capture: BoardCapture, onDone: () -> Unit,
) {
    val study = StudyPosition.of(d, matchLength, jacoby)
    SharePositionItems(
        study?.let { PositionIds.toXgid(it.setup.copy(doubled = it.doubled)) }, title,
        PositionShare.analysisLines(d, XgRules.errorFactor(matchLength)), capture,
        analysisLabel = (d.checker?.label ?: d.cubeEval?.label)?.ifBlank { null }, onDone = onDone,
    )
}

/**
 * The share menu's entries for a saved position [p] (the quiz): its XGID, the board as shown, and the
 * board with the top five plays or the cube actions of [analysis]; [answerIndex] / [answerCube] mark the
 * player's answer, tagged "you".
 */
@Composable
fun SharePositionItems(
    p: StudyPosition, analysis: PositionAnalysis?, answerIndex: Int?, answerCube: DecisionKind?, capture: BoardCapture,
    noAnalysis: (() -> Unit)? = null, onDone: () -> Unit,
) {
    val lines = analysis?.let {
        PositionShare.analysisLines(
            p.board, Player.HUMAN, it.checker, it.cube, response = p.doubled, beavers = p.beavers,
            playedIndex = answerIndex, playedKind = answerCube, factor = XgRules.errorFactor(p.setup.matchLength),
        )
    }.orEmpty()
    val label = analysis?.let { a -> a.label.ifBlank { a.checker?.label ?: a.cube?.label ?: "" } }?.ifBlank { null }
    SharePositionItems(PositionIds.toXgid(p.setup.copy(doubled = p.doubled)), PositionShare.title(p, com.boldblot.backgammonplyground.ui.components.LocalScoreAway.current), lines, capture, playedTag = "you", analysisLabel = label, noAnalysis = noAnalysis, onDone = onDone)
}

/** The share menu's entries: [xgid] as text (if any), the board picture, and the board with [lines] (if any). */
@Composable
fun SharePositionItems(
    xgid: String?, title: String, lines: List<PositionShare.Line>, capture: BoardCapture, playedTag: String = "played",
    /** "PureTD 3-ply, pruned": the engine and depth of [lines], printed under them. */
    analysisLabel: String? = null,
    /** Without [lines]: the top-5 entry still shows and calls this (analyze first, or say why not). */
    noAnalysis: (() -> Unit)? = null,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unit = LocalErrorUnit.current
    // The players' checkers beside their scores in the picture's heading, in the board's colours.
    val app = context.applicationContext as? com.boldblot.backgammonplyground.PlyGroundApp
    val settings = app?.settings?.settings?.collectAsState(initial = null)?.value
    val checkers = settings?.let { com.boldblot.backgammonplyground.ui.board.boardColorsFor(it) }?.let {
        PositionShare.ScoreCheckers(it.human.toArgb(), it.humanRim.toArgb(), it.cpu.toArgb(), it.cpuRim.toArgb())
    }
    if (xgid != null) {
        DropdownMenuItem(
            text = { Text("Share XGID…") },
            onClick = {
                onDone()
                PositionShare.text(context, xgid)
            },
        )
    }
    DropdownMenuItem(
        text = { Text("Share board picture…") },
        onClick = {
            onDone()
            scope.launch { PositionShare.image(context, PositionShare.withAnalysis(capture.image().asAndroidBitmap(), title, emptyList(), null, unit, checkers = checkers), "board") }
        },
    )
    if (lines.isNotEmpty() || noAnalysis != null) {
        DropdownMenuItem(
            text = { Text("Share board with top 5…") },
            onClick = {
                onDone()
                if (lines.isEmpty()) noAnalysis?.invoke()
                else scope.launch { PositionShare.analysisImage(context, capture, title, lines, xgid, unit, playedTag, analysisLabel, checkers) }
            },
        )
    }
}

/** Sharing positions as text or pictures (Messenger, WhatsApp, email…). */
object PositionShare {
    /** The players' checker colours (ARGB), drawn beside their scores in the heading. */
    data class ScoreCheckers(val human: Int, val humanRim: Int, val cpu: Int, val cpuRim: Int)

    /** One row of the analysis under a shared board: a play or cube action, its equity and its error. */
    data class Line(val text: String, val equity: Float, val diff: Float, val severity: Severity, val played: Boolean)

    fun text(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Share position"))
    }

    fun image(context: Context, bitmap: Bitmap, name: String) {
        val dir = File(context.cacheDir, "images").also { it.mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "PlyGround-$name.png")
        runCatching { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            .onFailure { Toast.makeText(context, "The picture could not be made.", Toast.LENGTH_SHORT).show(); return }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share position"))
    }

    /** Shares the board as [capture] shows it with [lines] (the top 5 or the cube actions) under it. */
    suspend fun analysisImage(
        context: Context, capture: BoardCapture, title: String, lines: List<Line>, xgid: String?, unit: ErrorUnit,
        playedTag: String = "played", analysisLabel: String? = null, checkers: ScoreCheckers? = null,
    ) = image(context, withAnalysis(capture.image().asAndroidBitmap(), title, lines, xgid, unit, playedTag, analysisLabel, checkers), "analysis")

    /** The analysis of decision [d] for a shared picture (see the other [analysisLines]). */
    fun analysisLines(d: DecisionRecord, factor: Float): List<Line> {
        if (d.kind.isRaccoonDecision) return emptyList()
        return analysisLines(
            d.board, d.player, d.checker, d.cubeEval, response = d.kind.isResponse, beavers = d.beavers || d.kind == DecisionKind.BEAVER,
            playedIndex = d.reviewIndex, playedKind = d.reviewKind, factor = factor,
        )
    }

    /**
     * The analysis for a shared picture: the five best plays of [checker] (and the one played, [playedIndex],
     * if it is not among them), or else the cube actions of [cube] ([response]: take / pass, otherwise the
     * doubler's). [playedKind] marks the cube action taken. Empty without an analysis.
     */
    fun analysisLines(
        board: Board, player: Int, checker: CheckerAnalysis?, cube: CubeEval?, response: Boolean, beavers: Boolean,
        playedIndex: Int?, playedKind: DecisionKind?, factor: Float,
    ): List<Line> {
        fun line(text: String, equity: Float, best: Float, played: Boolean) =
            Line(text, equity, equity - best, XgRules.severity((best - equity) * factor), played)
        val a = checker
        if (a != null && a.candidates.isNotEmpty()) {
            val shown = (a.candidates.indices.take(5) + listOfNotNull(playedIndex).filter { it >= 5 && it < a.candidates.size }).distinct()
            return shown.map { i ->
                val c = a.candidates[i]
                line("${i + 1}. ${MoveNotation.format(board, player, c.move)}", c.equity, a.bestEquity, i == playedIndex)
            }
        }
        val e = cube ?: return emptyList()
        if (response) {
            val (take, pass, beaver) = DecisionMath.responseEquities(e, beavers)
            val best = maxOf(take, pass, beaver ?: take)
            return listOfNotNull(
                line("Take", take, best, playedKind == DecisionKind.TAKE),
                line("Pass", pass, best, playedKind == DecisionKind.PASS),
                beaver?.let { line("Beaver", it, best, playedKind == DecisionKind.BEAVER) },
            )
        }
        val best = maxOf(e.noDouble, DecisionMath.afterDouble(e, beavers))
        val doubled = playedKind == DecisionKind.DOUBLE
        return listOf(
            line("No double", e.noDouble, best, playedKind == DecisionKind.NO_DOUBLE),
            line("Double, take", e.doubleTake, best, doubled && e.doubleTake <= e.doublePass),
            line("Double, pass", e.doublePass, best, doubled && e.doubleTake > e.doublePass),
        )
    }

    /** [board] under [title], with the analysis [lines] and the [xgid] below it (if any), on a white card. */
    fun withAnalysis(
        board: Bitmap, title: String, lines: List<Line>, xgid: String?, unit: ErrorUnit = ErrorUnit.EQ, playedTag: String = "played",
        analysisLabel: String? = null, checkers: ScoreCheckers? = null,
    ): Bitmap {
        val w = board.width
        val pad = w / 30f
        val text = w / 30f
        val small = text * 0.7f
        val row = text * 1.45f
        val top = pad + row
        val label = analysisLabel?.takeIf { lines.isNotEmpty() }
        val below = (if (lines.isNotEmpty()) pad + row * lines.size else 0f) + (if (label != null) small * 1.6f else 0f) +
            (if (xgid != null) small * 2 else 0f) + pad / 2
        val h = (top + board.height + below).toInt()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(android.graphics.Color.WHITE)
        val dark = android.graphics.Color.rgb(0x22, 0x22, 0x22)
        val grey = android.graphics.Color.rgb(0x66, 0x66, 0x66)
        val sans = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = text; color = dark }
        val bold = Paint(sans).apply { typeface = Typeface.DEFAULT_BOLD }
        val mono = Paint(sans).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.RIGHT }
        drawTitle(c, title, pad, pad + text, bold, checkers)
        c.drawBitmap(board.copy(Bitmap.Config.ARGB_8888, false), 0f, top, null)
        var y = top + board.height + pad + text
        for (l in lines) {
            if (l.played) {
                c.drawRect(pad / 2, y - text * 1.05f, w - pad / 2, y + text * 0.4f, Paint().apply { color = android.graphics.Color.rgb(0xEC, 0xEF, 0xF6) })
            }
            c.drawText(l.text + if (l.played) "   ($playedTag)" else "", pad, y, if (l.played) bold else sans)
            c.drawText(eq(l.equity), w - pad - text * 5.5f, y, mono)
            if (l.diff < -0.0005f) {
                c.drawText(err(l.diff, unit), w - pad, y, Paint(mono).apply { color = Quality.color(l.severity, false).toArgb() })
            }
            y += row
        }
        if (label != null) {
            y += small * 0.1f
            c.drawText("Analysis: $label", pad, y, Paint(sans).apply { textSize = small; color = grey })
            y += small * 1.6f
        }
        if (xgid != null) {
            y += small * 0.5f
            c.drawText(xgid, pad, y, Paint(sans).apply { textSize = small; color = grey; typeface = Typeface.MONOSPACE })
        }
        return out
    }

    /**
     * [title] at ([x], [y]); with [checkers], a checker of each player's colour before their score
     * ("5-point match · ● 2–1 ●", your score by your checker), as on the screens.
     */
    private fun drawTitle(c: Canvas, title: String, x: Float, y: Float, paint: Paint, checkers: ScoreCheckers?) {
        val m = SCORE.find(title)
        if (checkers == null || m == null) {
            c.drawText(title, x, y, paint)
            return
        }
        val size = paint.textSize
        val r = size * 0.36f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = size * 0.07f }
        var at = x
        fun text(t: String) { c.drawText(t, at, y, paint); at += paint.measureText(t) }
        fun checker(colour: Int, rimColour: Int) {
            val cx = at + r
            val cy = y - size * 0.35f
            c.drawCircle(cx, cy, r, fill.apply { color = colour })
            c.drawCircle(cx, cy, r, rim.apply { color = rimColour })
            at += 2 * r + size * 0.2f
        }
        text(title.substring(0, m.range.first))
        checker(checkers.human, checkers.humanRim)
        text(if (m.groupValues[1].isNotEmpty()) "${m.groupValues[1]}\u2013${m.groupValues[2]}" else "${m.groupValues[3]}-away, ${m.groupValues[4]}-away")
        at += size * 0.2f
        checker(checkers.cpu, checkers.cpuRim)
        text(title.substring(m.range.last + 1).trimStart().let { if (it.isEmpty()) it else " $it" })
    }

    /** "5-point match · 2-1", or with [away] "3-away, 4-away" (Settings → Display). */
    private fun scoreHeading(matchLength: Int, score: List<Int>, away: Boolean): String = when {
        matchLength == 0 -> "Money game"
        away -> "${matchLength - score[0]}-away, ${matchLength - score[1]}-away"
        else -> "$matchLength-point match · ${score[0]}-${score[1]}"
    }

    /** The score in a heading: "2-1" after "N-point match · ". */
    private val SCORE = Regex("""(?<=match · )(\d+)-(\d+)|(\d+)-away, (\d+)-away""")

    /** The heading of a shared picture of saved position [p], as for a decision. */
    fun title(p: StudyPosition, away: Boolean = false): String = buildString {
        val s = p.setup
        append(scoreHeading(s.matchLength, s.score, away))
        if (s.crawford) append(" Crawford")
        if (s.cube.value > 1) append(" · Cube ${s.cube.value}")
        p.dice?.let { append(" · Roll $it") }
        if (p.doubled) append(" · Doubled")
    }

    /** "5-point match · 2-1 · Cube 2 · Roll 64" (or "Money game"), the heading of a shared picture. */
    fun title(d: DecisionRecord, matchLength: Int, away: Boolean = false): String = buildString {
        append(scoreHeading(matchLength, d.score, away))
        if (d.crawford) append(" Crawford")
        if (d.cube.value > 1) append(" · Cube ${d.cube.value}")
        d.dice?.let { append(" · Roll $it") }
    }
}
