package com.boldblot.backgammonplyground.ui.review

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.DecisionKind
import com.boldblot.backgammonplyground.core.DecisionMath
import com.boldblot.backgammonplyground.core.DecisionRecord
import com.boldblot.backgammonplyground.core.MoveNotation
import com.boldblot.backgammonplyground.core.PositionIds
import com.boldblot.backgammonplyground.core.Severity
import com.boldblot.backgammonplyground.core.StudyPosition
import com.boldblot.backgammonplyground.core.XgRules
import com.boldblot.backgammonplyground.ui.components.eq
import com.boldblot.backgammonplyground.ui.theme.Quality
import kotlinx.coroutines.launch
import java.io.File

/** What a review board shows: the decision with arrows, the bare position, or the position after a move. */
enum class ReviewView(val label: String) { ARROWS("Arrows"), BOARD("Board"), AFTER("After") }

/** The chips that pick [ReviewView]; After only for a decision with a move. */
@Composable
fun ReviewViewChips(view: ReviewView, hasMove: Boolean, onPick: (ReviewView) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ReviewView.entries.filter { hasMove || it != ReviewView.AFTER }.forEach { v ->
            FilterChip(selected = view == v, onClick = { onPick(v) }, label = { Text(v.label) })
        }
    }
}

/**
 * The board of a review showing [view] of decision [d]: the position after the move chosen in the
 * list ([candidate]), or after the move played when none is chosen; otherwise the position itself.
 * Returns the board and whether it is the position after a move.
 */
fun reviewBoard(d: DecisionRecord, view: ReviewView, candidate: Int?): Pair<Board, Boolean> {
    if (view != ReviewView.AFTER) return d.board to false
    val move = candidate?.let { d.checker?.candidates?.getOrNull(it)?.move } ?: d.move ?: return d.board to false
    return d.board.applyMove(d.player, move) to true
}

/** The labelled button that saves the position shown to My positions. */
@Composable
fun SaveButton(onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(Icons.Default.BookmarkAdd, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Save")
    }
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val study = StudyPosition.of(d, matchLength, jacoby)
    if (study != null) {
        DropdownMenuItem(
            text = { Text("Share XGID…") },
            onClick = {
                onDone()
                PositionShare.text(context, PositionIds.toXgid(study.setup.copy(doubled = study.doubled)))
            },
        )
    }
    DropdownMenuItem(
        text = { Text("Share board picture…") },
        onClick = {
            onDone()
            scope.launch { PositionShare.image(context, capture.image().asAndroidBitmap(), "board") }
        },
    )
    val lines = PositionShare.analysisLines(d, XgRules.errorFactor(matchLength))
    if (lines.isNotEmpty()) {
        DropdownMenuItem(
            text = { Text("Share board with top 5…") },
            onClick = {
                onDone()
                scope.launch {
                    val xgid = study?.let { PositionIds.toXgid(it.setup.copy(doubled = it.doubled)) }
                    PositionShare.image(context, PositionShare.withAnalysis(capture.image().asAndroidBitmap(), title, lines, xgid), "analysis")
                }
            },
        )
    }
}

/** Sharing positions as text or pictures (Messenger, WhatsApp, email…). */
object PositionShare {
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

    /**
     * The analysis of [d] for a shared picture: the five best plays (and the one played, if it is
     * not among them), or the cube actions. Empty without an analysis.
     */
    fun analysisLines(d: DecisionRecord, factor: Float): List<Line> {
        fun line(text: String, equity: Float, best: Float, played: Boolean) =
            Line(text, equity, equity - best, XgRules.severity((best - equity) * factor), played)
        val a = d.checker
        if (a != null && a.candidates.isNotEmpty()) {
            val shown = (a.candidates.indices.take(5) + listOf(a.playedIndex).filter { it >= 5 && it < a.candidates.size }).distinct()
            return shown.map { i ->
                val c = a.candidates[i]
                line("${i + 1}. ${MoveNotation.format(d.board, d.player, c.move)}", c.equity, a.bestEquity, i == a.playedIndex)
            }
        }
        val e = d.cubeEval ?: return emptyList()
        if (d.kind.isRaccoonDecision) return emptyList()
        if (d.kind.isResponse) {
            val (take, pass, beaver) = DecisionMath.responseEquities(e, d.beavers || d.kind == DecisionKind.BEAVER)
            val best = maxOf(take, pass, beaver ?: take)
            return listOfNotNull(
                line("Take", take, best, d.kind == DecisionKind.TAKE),
                line("Pass", pass, best, d.kind == DecisionKind.PASS),
                beaver?.let { line("Beaver", it, best, d.kind == DecisionKind.BEAVER) },
            )
        }
        val best = maxOf(e.noDouble, DecisionMath.afterDouble(e, d.beavers))
        val doubled = d.kind == DecisionKind.DOUBLE
        return listOf(
            line("No double", e.noDouble, best, d.kind == DecisionKind.NO_DOUBLE),
            line("Double, take", e.doubleTake, best, doubled && e.doubleTake <= e.doublePass),
            line("Double, pass", e.doublePass, best, doubled && e.doubleTake > e.doublePass),
        )
    }

    /** [board] with [title], the analysis [lines] and the [xgid] below it, on a white card. */
    fun withAnalysis(board: Bitmap, title: String, lines: List<Line>, xgid: String?): Bitmap {
        val w = board.width
        val pad = w / 30f
        val text = w / 30f
        val small = text * 0.7f
        val row = text * 1.45f
        val h = (board.height + pad * 2 + row * (lines.size + 1) + (if (xgid != null) small * 2 else 0f) + pad).toInt()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(android.graphics.Color.WHITE)
        c.drawBitmap(board.copy(Bitmap.Config.ARGB_8888, false), 0f, 0f, null)
        val dark = android.graphics.Color.rgb(0x22, 0x22, 0x22)
        val grey = android.graphics.Color.rgb(0x66, 0x66, 0x66)
        val sans = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = text; color = dark }
        val bold = Paint(sans).apply { typeface = Typeface.DEFAULT_BOLD }
        val mono = Paint(sans).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.RIGHT }
        var y = board.height + pad + text
        c.drawText(title, pad, y, bold)
        y += row
        for (l in lines) {
            if (l.played) {
                c.drawRect(pad / 2, y - text * 1.05f, w - pad / 2, y + text * 0.4f, Paint().apply { color = android.graphics.Color.rgb(0xEC, 0xEF, 0xF6) })
            }
            c.drawText(l.text + if (l.played) "   (played)" else "", pad, y, if (l.played) bold else sans)
            c.drawText(eq(l.equity), w - pad - text * 5.5f, y, mono)
            if (l.diff < -0.0005f) {
                c.drawText(eq(l.diff), w - pad, y, Paint(mono).apply { color = Quality.color(l.severity, false).toArgb() })
            }
            y += row
        }
        if (xgid != null) {
            y += small * 0.5f
            c.drawText(xgid, pad, y, Paint(sans).apply { textSize = small; color = grey; typeface = Typeface.MONOSPACE })
        }
        return out
    }

    /** "5-point match · score 2-1 · cube 2" (or "Money"), the heading of a shared picture. */
    fun title(d: DecisionRecord, matchLength: Int): String = buildString {
        append(if (matchLength == 0) "Money" else "$matchLength-point match · score ${d.score[0]}-${d.score[1]}")
        if (d.crawford) append(" Crawford")
        if (d.cube.value > 1) append(" · cube ${d.cube.value}")
        d.dice?.let { append(" · roll $it") }
    }
}
