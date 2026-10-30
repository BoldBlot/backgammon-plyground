package com.boldblot.backgammonplyground.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.boldblot.backgammonplyground.core.BAR
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.CubeState
import com.boldblot.backgammonplyground.core.OFF
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.Step
import com.boldblot.backgammonplyground.data.CheckerStyle
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/** Colours of the board. Dice take the colour of their owner's checkers. */
data class BoardColors(
    val frame: Color,
    val surface: Color,
    val pointLight: Color,
    val pointDark: Color,
    val human: Color,
    val humanRim: Color,
    val cpu: Color,
    val cpuRim: Color,
    val humanPip: Color,
    val cpuPip: Color,
    val highlight: Color,
    val lastMove: Color,
    val label: Color,
    val tray: Color,
    val checkerStyle: CheckerStyle = CheckerStyle.RIMMED,
) {
    companion object {
        /** Matt's board (the default). */
        fun gray() = BoardThemes.matts.colors(com.boldblot.backgammonplyground.data.CheckerSide.FIRST, CheckerStyle.RIMMED)
    }
}

/**
 * Two dice to draw in [owner]'s half of the board.
 * [dim] per die: 0 = available, 0.5 = half used (one move of a double), 1 = used.
 * [greyed] draws the opponent's last roll as a faded reminder; [error] outlines them in red.
 */
data class DiceView(
    val owner: Int,
    val values: List<Int>,
    val dim: List<Float> = values.map { 0f },
    val greyed: Boolean = false,
    val error: Boolean = false,
)

/**
 * Geometry of the board for a given canvas size. The human plays from the bottom,
 * home board bottom-right; human point index i (0..23) is drawn at:
 *   i in 0..11  -> bottom row, column 11 - i (right to left)
 *   i in 12..23 -> top row, column i - 12 (left to right)
 */
/**
 * Where everything sits on a board [width] × [height]. [mirrored]: the human's home board on the
 * left and the bear-off tray on the left (checkers move clockwise); otherwise home on the right.
 */
class BoardGeometry(val width: Float, val height: Float, val mirrored: Boolean = false) {
    val margin = width * 0.018f
    /** Strip above and below the playing surface (point numbers). */
    val vMargin = height * 0.04f
    val surfaceTop = vMargin
    val surfaceBottom = height - vMargin
    val trayW = width * 0.075f
    val barW = width * 0.065f
    val playLeft = if (mirrored) trayW + margin else margin
    val playRight = if (mirrored) width - margin else width - trayW - margin
    val pointW = (playRight - playLeft - barW) / 12f
    val pointH = (surfaceBottom - surfaceTop) * 0.42f
    /** Checkers are as wide as a point, or five of them fill 46.5% of the surface height (as in Backgammon Galaxy). */
    val radius = min(pointW * 0.5f, (surfaceBottom - surfaceTop) / 21.5f)
    val barCenterX = playLeft + 6 * pointW + barW / 2
    val trayLeft = if (mirrored) 0f else width - trayW

    fun columnLeft(col: Int): Float = playLeft + col * pointW + if (col >= 6) barW else 0f
    val cubeSize = barW * 0.86f
    /** Width of the Pass button drawn beside the offered cube (see [cubeAside]). */
    val passW = pointW * 1.85f

    /**
     * Left edges of the offered cube and of Pass when the computer doubles: spaced evenly across
     * the computer's half (edge, cube, Pass, bar; mirrored: bar, Pass, cube, edge).
     */
    fun cubeAside(): Pair<Float, Float> {
        val gap = (6 * pointW - cubeSize - passW) / 3
        return if (mirrored) {
            val left = columnLeft(6)
            (left + 2 * gap + passW) to (left + gap)
        } else {
            (playLeft + gap) to (playLeft + 2 * gap + cubeSize)
        }
    }

    fun isTop(index: Int) = index >= 12
    fun column(index: Int): Int {
        val c = if (index < 12) 11 - index else index - 12
        return if (mirrored) 11 - c else c
    }

    fun pointCenterX(index: Int) = columnLeft(column(index)) + pointW / 2

    /** Centre of the k-th checker (0 = outermost) on a point. */
    fun checkerCenter(index: Int, k: Int): Offset {
        val x = pointCenterX(index)
        val step = 2 * radius
        val kk = k % 5
        val layer = k / 5
        val offset = kk * step + layer * radius * 0.5f
        // The first checker sits flush against the base of the point.
        return if (isTop(index)) Offset(x, surfaceTop + radius + offset) else Offset(x, surfaceBottom - radius - offset)
    }

    /** Bar checkers: the human's above the centre, the computer's below. */
    fun barCenter(player: Int, k: Int): Offset {
        val step = 2 * radius
        val y0 = height / 2
        return if (player == Player.HUMAN) Offset(barCenterX, y0 - radius * 2.2f - k * step)
        else Offset(barCenterX, y0 + radius * 2.2f + k * step)
    }

    val dieSize: Float get() = min(pointW * 0.95f, height * 0.11f)

    /** Screen rectangles of two dice in [owner]'s half (human right, computer left). */
    fun diceRects(owner: Int): List<Rect> {
        val gap = dieSize * 0.25f
        val total = 2 * dieSize + gap
        val halfLeft = if ((owner == Player.HUMAN) != mirrored) columnLeft(6) else columnLeft(0)
        val x0 = halfLeft + (6 * pointW - total) / 2
        val y = height / 2 - dieSize / 2
        return List(2) { i -> Rect(Offset(x0 + i * (dieSize + gap), y), Size(dieSize, dieSize)) }
    }

    /** True if [p] is on (or right around) the human's dice. */
    fun hitsHumanDice(p: Offset): Boolean {
        val r = diceRects(Player.HUMAN)
        val pad = dieSize * 0.25f
        return Rect(r[0].left - pad, r[0].top - pad, r[1].right + pad, r[1].bottom + pad).contains(p)
    }

    /** Checker slot (0 = at the base, up to 4) of point [index] under [p]. */
    fun slotAt(index: Int, p: Offset): Int {
        val d = if (isTop(index)) p.y - surfaceTop else surfaceBottom - p.y
        return (d / (2 * radius)).toInt().coerceIn(0, 4)
    }

    /** Direction (+1 down, -1 up) in which checkers stack on point [index], or on [player]'s bar half. */
    fun stackDirection(index: Int): Float = if (isTop(index)) 1f else -1f
    fun barStackDirection(player: Int): Float = if (player == Player.HUMAN) -1f else 1f

    /** Bar slot under [p]: player whose bar half it is, and the slot index from the centre. */
    fun barSlotAt(p: Offset): Pair<Int, Int> {
        val y0 = height / 2
        val player = if (p.y < y0) Player.HUMAN else Player.CPU
        val d = kotlin.math.abs(p.y - y0) - radius * 1.2f
        return player to (d / (2 * radius)).toInt().coerceAtLeast(0)
    }

    /** Hit test: point index (0..23), [BAR], [OFF] (-1) or null. */
    fun hitTest(p: Offset): Int? {
        if (p.x >= trayLeft && p.x < trayLeft + trayW) return OFF
        if (p.x < playLeft || p.x > playRight) return null
        if (p.x >= columnLeft(6) - barW && p.x < columnLeft(6)) return BAR
        val screenCol = (0 until 12).firstOrNull { c -> p.x >= columnLeft(c) && p.x < columnLeft(c) + pointW } ?: return null
        val col = if (mirrored) 11 - screenCol else screenCol
        val top = p.y < height / 2
        return if (top) col + 12 else 11 - col
    }
}

/**
 * The backgammon board. [board] is drawn with the human at the bottom.
 * Callbacks receive human-orientation targets: 0..23, [BAR] or [OFF].
 */
@Composable
fun BoardView(
    board: Board,
    modifier: Modifier = Modifier,
    colors: BoardColors = BoardColors.gray(),
    dice: List<DiceView> = emptyList(),
    cube: CubeState = CubeState(),
    cubeOffered: Boolean = false,
    /** The offered cube to the outer side of the computer's half (Pass sits in the middle). */
    cubeAside: Boolean = false,
    /** Crawford game: no cube can be turned, so a vertical "CRAWFORD" sign stands on the bar instead ([crawfordSign]; otherwise nothing). */
    crawford: Boolean = false,
    crawfordSign: Boolean = true,
    movable: Set<Int> = emptySet(),
    selected: Int? = null,
    destinations: Set<Int> = emptySet(),
    lastCpuSteps: List<Step> = emptyList(),
    showNumbers: Boolean = true,
    /** Pip counts drawn on the bar: [human, computer]. */
    pips: List<Int>? = null,
    /** Arrows illustrating a move on the (pre-move) board. */
    arrows: List<MoveArrow> = emptyList(),
    onTap: ((Int) -> Unit)? = null,
    /** Tap on the human's dice. */
    onDiceTap: (() -> Unit)? = null,
    onDrag: ((from: Int, to: Int) -> Unit)? = null,
    /** Points (human orientation) to mark for checking (framed in orange). */
    flagged: Set<Int> = emptySet(),
    /** Home board on the left (Settings → Board); see [BoardGeometry]. */
    mirrored: Boolean = false,
    /**
     * Position editing: a tap on point [target] (human orientation) at checker [slot] (0 = base),
     * or on the bar ([target] = [BAR], [slot] counted from the centre) in [barPlayer]'s half.
     */
    onEditTap: ((target: Int, slot: Int, barPlayer: Int) -> Unit)? = null,
    /**
     * Position editing by dragging along a stack: [delta] checkers to add (+) or remove (-) on point
     * [target] (human orientation), or on [barPlayer]'s bar half when [target] = [BAR]. Dragging in
     * the direction the stack grows adds, one checker per checker height of travel.
     */
    onEditSwipe: ((target: Int, delta: Int, barPlayer: Int) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var geometry by remember { mutableStateOf<BoardGeometry?>(null) }

    var mod = modifier
    if (onEditTap != null) {
        mod = mod.pointerInput(onEditTap) {
            detectTapGestures { pos ->
                val g = geometry ?: return@detectTapGestures
                when (val t = g.hitTest(pos)) {
                    null, OFF -> {}
                    BAR -> g.barSlotAt(pos).let { (pl, k) -> onEditTap(BAR, k, pl) }
                    else -> onEditTap(t, g.slotAt(t, pos), Player.HUMAN)
                }
            }
        }
    }
    if (onEditSwipe != null) {
        val swipe by rememberUpdatedState(onEditSwipe)
        mod = mod.pointerInput(Unit) {
            var target: Int? = null
            var barPlayer = Player.HUMAN
            var dir = 1f
            var travel = 0f
            detectVerticalDragGestures(
                onDragStart = { pos ->
                    val g = geometry
                    travel = 0f
                    target = when (val t = g?.hitTest(pos)) {
                        null, OFF -> null
                        BAR -> BAR.also { barPlayer = g.barSlotAt(pos).first; dir = g.barStackDirection(barPlayer) }
                        else -> t.also { dir = g.stackDirection(t) }
                    }
                },
                onVerticalDrag = { change, dy ->
                    val t = target ?: return@detectVerticalDragGestures
                    val step = (geometry?.radius ?: return@detectVerticalDragGestures) * 1.6f
                    change.consume()
                    travel += dy * dir
                    val n = (travel / step).toInt()
                    if (n != 0) {
                        travel -= n * step
                        swipe(t, n, barPlayer)
                    }
                },
                onDragEnd = { target = null },
                onDragCancel = { target = null },
            )
        }
    }
    if (onTap != null || onDiceTap != null) {
        mod = mod.pointerInput(onTap, onDiceTap) {
            detectTapGestures { pos ->
                val g = geometry ?: return@detectTapGestures
                if (onDiceTap != null && g.hitsHumanDice(pos)) onDiceTap() else g.hitTest(pos)?.let { onTap?.invoke(it) }
            }
        }
    }
    if (onDrag != null) {
        mod = mod.pointerInput(onDrag, movable) {
            detectDragGestures(
                onDragStart = { pos ->
                    val t = geometry?.hitTest(pos)
                    if (t != null && t in movable) {
                        dragFrom = t
                        dragPos = pos
                    }
                },
                onDrag = { change, amount ->
                    if (dragFrom != null) {
                        change.consume()
                        dragPos += amount
                    }
                },
                onDragEnd = {
                    val from = dragFrom
                    val to = geometry?.hitTest(dragPos)
                    dragFrom = null
                    if (from != null && to != null && to != from) onDrag(from, to)
                },
                onDragCancel = { dragFrom = null },
            )
        }
    }

    Canvas(mod) {
        val g = BoardGeometry(size.width, size.height, mirrored)
        geometry = g
        drawBoard(g, colors, measurer, showNumbers)
        drawFlags(g, flagged)
        drawCheckers(g, colors, board, dragFrom, measurer)
        if (lastCpuSteps.isNotEmpty()) {
            drawArrows(g, board, arrowsFor(Player.CPU, com.boldblot.backgammonplyground.core.Move(lastCpuSteps), colors.lastMove), postMove = true)
        }
        drawHighlights(g, colors, board, movable, selected, destinations)
        if (crawford && !cubeOffered) {
            if (crawfordSign) drawCrawford(g, measurer)
        } else drawCube(g, colors, cube, cubeOffered, cubeAside, measurer)
        pips?.let { drawPips(g, colors, it, measurer) }
        dice.forEach { drawDice(g, colors, it) }
        drawArrows(g, board, arrows, postMove = false)
        dragFrom?.let { checker(dragPos, g.radius * 1.08f, colors.human, colors.humanRim, colors.checkerStyle) }
    }
}

private fun DrawScope.drawBoard(g: BoardGeometry, c: BoardColors, measurer: TextMeasurer, numbers: Boolean) {
    drawRect(c.frame)
    drawRect(c.surface, Offset(g.playLeft, g.surfaceTop), Size(g.playRight - g.playLeft, g.surfaceBottom - g.surfaceTop))
    // bar
    drawRect(c.frame, Offset(g.columnLeft(6) - g.barW, 0f), Size(g.barW, size.height))
    // tray
    drawRect(c.frame, Offset(g.trayLeft, 0f), Size(g.trayW, size.height))
    drawRoundRect(
        c.tray, Offset(g.trayLeft + g.trayW * 0.12f, g.vMargin),
        Size(g.trayW * 0.76f, size.height - 2 * g.vMargin), CornerRadius(6f, 6f),
    )
    for (i in 0 until 24) {
        val left = g.columnLeft(g.column(i))
        val top = g.isTop(i)
        val baseY = if (top) g.surfaceTop else g.surfaceBottom
        val tipY = if (top) baseY + g.pointH else baseY - g.pointH
        val path = Path().apply {
            moveTo(left, baseY)
            lineTo(left + g.pointW, baseY)
            lineTo(left + g.pointW / 2, tipY)
            close()
        }
        drawPath(path, if (i % 2 == 0) c.pointDark else c.pointLight)
        if (numbers) {
            val label = measurer.measure((i + 1).toString(), TextStyle(color = c.label, fontSize = 9.sp))
            val y = if (top) 0f else size.height - label.size.height
            drawText(label, topLeft = Offset(left + (g.pointW - label.size.width) / 2, y - (if (top) 1f else -1f)))
        }
    }
}

/** Orange frame around each flagged point. */
private fun DrawScope.drawFlags(g: BoardGeometry, flagged: Set<Int>) {
    val c = Color(0xFFFF9800)
    for (i in flagged) {
        if (i !in 0..23) continue
        val left = g.columnLeft(g.column(i))
        val top = g.isTop(i)
        val h = 10 * g.radius + g.radius * 0.3f
        val y = if (top) g.surfaceTop else g.surfaceBottom - h
        drawRoundRect(c.copy(alpha = 0.28f), Offset(left + 1f, y), Size(g.pointW - 2f, h), CornerRadius(g.radius * 0.4f))
        drawRoundRect(c, Offset(left + 1f, y), Size(g.pointW - 2f, h), CornerRadius(g.radius * 0.4f), style = Stroke(width = g.radius * 0.12f))
    }
}

internal fun DrawScope.checker(center: Offset, r: Float, fill: Color, rim: Color, style: CheckerStyle) {
    when (style) {
        CheckerStyle.RIMMED -> {
            drawCircle(fill, r, center)
            drawCircle(rim, r * 0.96f, center, style = Stroke(width = r * 0.08f))
            drawCircle(rim.copy(alpha = 0.35f), r * 0.6f, center, style = Stroke(width = r * 0.05f))
        }
        CheckerStyle.FLAT -> {
            drawCircle(fill, r, center)
            drawCircle(rim, r * 0.98f, center, style = Stroke(width = r * 0.04f))
        }
        CheckerStyle.DOUBLE_RING -> {
            drawCircle(fill, r, center)
            drawCircle(rim, r * 0.94f, center, style = Stroke(width = r * 0.1f))
            drawCircle(rim, r * 0.64f, center, style = Stroke(width = r * 0.09f))
        }
        CheckerStyle.DOMED -> {
            // lit from the top left: lighter centre, darker edge
            drawCircle(
                Brush.radialGradient(
                    listOf(lerp(fill, Color.White, 0.35f), fill, lerp(fill, Color.Black, 0.28f)),
                    center = center + Offset(-r * 0.35f, -r * 0.35f), radius = r * 1.45f,
                ),
                r, center,
            )
            drawCircle(rim.copy(alpha = 0.7f), r * 0.98f, center, style = Stroke(width = r * 0.04f))
        }
    }
}

private fun DrawScope.drawCheckers(g: BoardGeometry, c: BoardColors, board: Board, dragFrom: Int?, measurer: TextMeasurer) {
    for (player in 0..1) {
        val fill = if (player == Player.HUMAN) c.human else c.cpu
        val rim = if (player == Player.HUMAN) c.humanRim else c.cpuRim
        for (idx in 0 until 24) {
            var n = board.count(player, idx)
            if (player == Player.HUMAN && dragFrom == idx) n--
            if (n <= 0) continue
            val humanIdx = if (player == Player.HUMAN) idx else 23 - idx
            val shown = min(n, 5)
            for (k in 0 until shown) checker(g.checkerCenter(humanIdx, k), g.radius, fill, rim, c.checkerStyle)
            if (n > 5) {
                val center = g.checkerCenter(humanIdx, 4)
                val t = measurer.measure(
                    n.toString(),
                    TextStyle(color = if (player == Player.HUMAN) c.humanPip else c.cpuPip, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
                drawText(t, topLeft = center - Offset(t.size.width / 2f, t.size.height / 2f))
            }
        }
        var bar = board.bar(player)
        if (player == Player.HUMAN && dragFrom == BAR) bar--
        for (k in 0 until min(bar, 4)) checker(g.barCenter(player, k), g.radius, fill, rim, c.checkerStyle)
        if (bar > 4) {
            val t = measurer.measure(bar.toString(), TextStyle(color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            drawText(t, topLeft = g.barCenter(player, 3) - Offset(t.size.width / 2f, t.size.height / 2f))
        }
        // borne off: thin slabs in the tray (human bottom, computer top)
        val off = board.off(player)
        val slabH = (size.height / 2 - g.vMargin * 1.5f) / 15f
        for (k in 0 until off) {
            val y = if (player == Player.HUMAN) size.height - g.vMargin - (k + 1) * slabH else g.vMargin + k * slabH
            drawRoundRect(fill, Offset(g.trayLeft + g.trayW * 0.18f, y + 1f), Size(g.trayW * 0.64f, slabH - 2f), CornerRadius(3f, 3f))
            drawRoundRect(rim, Offset(g.trayLeft + g.trayW * 0.18f, y + 1f), Size(g.trayW * 0.64f, slabH - 2f), CornerRadius(3f, 3f), style = Stroke(1f))
        }
    }
}

private fun DrawScope.drawHighlights(
    g: BoardGeometry, c: BoardColors, board: Board, movable: Set<Int>, selected: Int?, destinations: Set<Int>,
) {
    for (idx in movable) {
        val center = if (idx == BAR) g.barCenter(Player.HUMAN, board.bar(Player.HUMAN) - 1)
        else g.checkerCenter(idx, (board.count(Player.HUMAN, idx) - 1).coerceAtLeast(0))
        val sel = idx == selected
        drawCircle(c.highlight, g.radius * 1.02f, center, style = Stroke(width = g.radius * if (sel) 0.28f else 0.13f), alpha = if (sel) 1f else 0.75f)
    }
    for (d in destinations) {
        if (d == OFF) {
            drawRoundRect(
                c.highlight.copy(alpha = 0.35f), Offset(g.trayLeft + 4f, size.height / 2), Size(g.trayW - 8f, size.height / 2 - g.vMargin),
                CornerRadius(8f, 8f),
            )
            continue
        }
        val k = board.count(Player.HUMAN, d).coerceAtMost(4)
        val opp = board.count(Player.CPU, 23 - d)
        val center = g.checkerCenter(d, if (opp == 1) 0 else k)
        drawCircle(c.highlight, g.radius * 0.95f, center, alpha = 0.45f)
        drawCircle(c.highlight, g.radius * 0.95f, center, style = Stroke(width = g.radius * 0.1f))
    }
}

private fun DrawScope.drawCube(g: BoardGeometry, c: BoardColors, cube: CubeState, offered: Boolean, aside: Boolean, measurer: TextMeasurer) {
    val s = g.cubeSize
    val value = if (offered) cube.value * 2 else cube.value
    val label = if (value == 1) "64" else value.toString()
    val y = when {
        offered -> size.height / 2 - s / 2
        cube.owner == Player.HUMAN -> size.height - g.vMargin - s
        cube.owner == Player.CPU -> g.vMargin
        else -> size.height / 2 - s / 2
    }
    // Offered: in the middle of the computer's half (the left one unless mirrored), or [aside]
    // beside Pass.
    val x = when {
        !offered -> g.barCenterX - s / 2
        aside -> g.cubeAside().first
        else -> g.columnLeft(if (g.mirrored) 9 else 3) - s / 2
    }
    drawRoundRect(Color(0xFFF7F3EA), Offset(x, y), Size(s, s), CornerRadius(s * 0.15f, s * 0.15f))
    drawRoundRect(Color(0xFF555555), Offset(x, y), Size(s, s), CornerRadius(s * 0.15f, s * 0.15f), style = Stroke(2f))
    val t = measurer.measure(label, TextStyle(color = Color(0xFF222222), fontSize = (s / density * 0.42f).sp, fontWeight = FontWeight.Bold))
    drawText(t, topLeft = Offset(x + (s - t.size.width) / 2, y + (s - t.size.height) / 2))
}

/** The Crawford sign: the word written upwards on a cube-coloured strip in the middle of the bar. */
private fun DrawScope.drawCrawford(g: BoardGeometry, measurer: TextMeasurer) {
    val w = g.cubeSize * 0.72f
    val t = measurer.measure("CRAWFORD", TextStyle(color = Color(0xFF222222), fontSize = (w / density * 0.5f).sp,
        fontWeight = FontWeight.Bold, letterSpacing = (w / density * 0.08f).sp))
    val h = t.size.width + w * 0.9f
    val cx = g.barCenterX
    val cy = size.height / 2
    rotate(-90f, Offset(cx, cy)) {
        val topLeft = Offset(cx - h / 2, cy - w / 2)
        drawRoundRect(Color(0xFFF7F3EA), topLeft, Size(h, w), CornerRadius(w * 0.25f, w * 0.25f))
        drawRoundRect(Color(0xFF555555), topLeft, Size(h, w), CornerRadius(w * 0.25f, w * 0.25f), style = Stroke(2f))
        drawText(t, topLeft = Offset(cx - t.size.width / 2f, cy - t.size.height / 2f))
    }
}

private fun DrawScope.drawDice(g: BoardGeometry, c: BoardColors, d: DiceView) {
    val rects = g.diceRects(d.owner)
    val corner = CornerRadius(g.dieSize * 0.18f)
    val face = when {
        d.greyed -> Color(0xFFB9BCC0)
        d.owner == Player.HUMAN -> c.human
        else -> c.cpu
    }
    val pip = when {
        d.greyed -> Color(0xFF55585C)
        d.owner == Player.HUMAN -> c.humanPip
        else -> c.cpuPip
    }
    val baseAlpha = if (d.greyed) 0.6f else 1f
    d.values.take(2).forEachIndexed { i, v ->
        val r = rects[i]
        val dim = d.dim.getOrElse(i) { 0f }
        val alpha = baseAlpha * if (dim >= 1f) 0.35f else 1f
        drawRoundRect(face.copy(alpha = alpha), r.topLeft, r.size, corner)
        drawPips(r, v, pip.copy(alpha = alpha))
        if (dim in 0.25f..0.75f) {
            // One of the two moves of this die is used: shade its right half.
            clipRect(r.left + r.width / 2, r.top, r.right, r.bottom) {
                drawRoundRect(c.surface.copy(alpha = 0.65f), r.topLeft, r.size, corner)
            }
        }
        if (d.error) {
            drawRoundRect(Color(0xFFE53935), r.topLeft - Offset(3f, 3f), Size(r.width + 6f, r.height + 6f),
                CornerRadius(g.dieSize * 0.22f), style = Stroke(width = 6f))
        }
    }
}

private fun DrawScope.drawPips(r: Rect, v: Int, color: Color) {
    val p = r.width * 0.23f
    val pr = r.width * 0.09f
    val cx = r.center.x
    val cy = r.center.y
    val spots = when (v) {
        1 -> listOf(0f to 0f)
        2 -> listOf(-1f to -1f, 1f to 1f)
        3 -> listOf(-1f to -1f, 0f to 0f, 1f to 1f)
        4 -> listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)
        5 -> listOf(-1f to -1f, 1f to -1f, 0f to 0f, -1f to 1f, 1f to 1f)
        else -> listOf(-1f to -1f, 1f to -1f, -1f to 0f, 1f to 0f, -1f to 1f, 1f to 1f)
    }
    for ((sx, sy) in spots) drawCircle(color, pr, Offset(cx + sx * p, cy + sy * p))
}

/** Board colours for the user's settings. */
fun boardColorsFor(s: com.boldblot.backgammonplyground.data.Settings): BoardColors =
    BoardThemes.palette(s).colors(s.checkerSide, s.checkerStyle)

/** Fixed arrow colours (readable on every board theme). */
object ArrowColors {
    val best = Color(0xFF2ECC71)
    val inaccuracy = Color(0xFFFFE066)
    val error = Color(0xFFFFA43A)
    val blunder = Color(0xFFFF4D4D)
    val neutral = Color(0xFF6EC1FF)

    fun forSeverity(s: com.boldblot.backgammonplyground.core.Severity): Color = when (s) {
        com.boldblot.backgammonplyground.core.Severity.NONE -> best
        com.boldblot.backgammonplyground.core.Severity.INACCURACY -> inaccuracy
        com.boldblot.backgammonplyground.core.Severity.ERROR -> error
        com.boldblot.backgammonplyground.core.Severity.BLUNDER -> blunder
    }
}

/** An arrow from [from] to [to], in [player]'s own orientation (0..23, [BAR] or [OFF]). */
data class MoveArrow(val player: Int, val from: Int, val to: Int, val color: Color)

/**
 * Arrows for a move: consecutive steps of the same checker are merged into one arrow
 * (as in move notation), so 24/18/13 is drawn as a single arrow 24 -> 13.
 */
fun arrowsFor(player: Int, move: com.boldblot.backgammonplyground.core.Move, color: Color): List<MoveArrow> {
    val remaining = move.steps.sortedWith(compareByDescending<Step> { it.from }.thenByDescending { it.to }).toMutableList()
    val out = mutableListOf<MoveArrow>()
    while (remaining.isNotEmpty()) {
        var cur = remaining.removeAt(0)
        val from = cur.from
        while (cur.to != OFF) {
            val i = remaining.indexOfFirst { it.from == cur.to }
            if (i < 0) break
            cur = remaining.removeAt(i)
        }
        out += MoveArrow(player, from, cur.to, color)
    }
    return out
}

private fun DrawScope.drawPips(g: BoardGeometry, c: BoardColors, pips: List<Int>, measurer: TextMeasurer) {
    val style = TextStyle(color = c.label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    for (player in 0..1) {
        val t = measurer.measure(pips[player].toString(), style)
        val y = if (player == Player.HUMAN) size.height * 0.83f - t.size.height / 2 else size.height * 0.17f - t.size.height / 2
        drawText(t, topLeft = Offset(g.barCenterX - t.size.width / 2f, y))
    }
}

/**
 * Screen position of an arrow end. Before the move the arrow starts at the top checker of
 * the source and ends on the next free slot; after the move ([postMove]) it starts on the
 * slot the checker left and ends on the top checker of the destination. [used] counts the
 * earlier arrows of the same move leaving (or reaching) this point, so that two checkers
 * moved from one point start from different checkers and two arriving land on different slots.
 */
private fun DrawScope.arrowEnd(
    g: BoardGeometry, board: Board, player: Int, index: Int, source: Boolean, postMove: Boolean, used: Int,
): Offset {
    return when (index) {
        BAR -> g.barCenter(player, used)
        OFF -> {
            val y = if (player == Player.HUMAN) size.height * 0.78f - used * g.radius else size.height * 0.22f + used * g.radius
            Offset(g.trayLeft + g.trayW / 2, y)
        }
        else -> {
            val humanIdx = if (player == Player.HUMAN) index else 23 - index
            val n = board.count(player, index)
            val k = when {
                source && !postMove -> n - 1 - used
                source -> n + used
                !postMove -> n + used
                else -> n - 1 - used
            }.coerceIn(0, 4)
            g.checkerCenter(humanIdx, k)
        }
    }
}

/** An arrow laid out on screen: a quadratic curve bending [bend] pixels to the left of its direction. */
private class ArrowLayout(val arrow: MoveArrow, val move: Int, val start: Offset, val end: Offset, var bend: Float) {
    val dir: Offset get() = (end - start).let { it / it.getDistance().coerceAtLeast(1f) }
}

/**
 * Lays out the arrows so that they do not lie on top of each other. Each colour is one move
 * (played and best are drawn in different colours). Arrows are slightly curved, towards the
 * middle of the board when they run along one side (away from the checkers); when an arrow of a
 * later move starts or ends where an arrow of an earlier move does and points the same way, or
 * repeats it exactly, it bends to the other side.
 */
private fun DrawScope.layoutArrows(g: BoardGeometry, board: Board, arrows: List<MoveArrow>, postMove: Boolean): List<ArrowLayout> {
    val out = mutableListOf<ArrowLayout>()
    val near = g.radius * 0.8f
    arrows.groupBy { it.color }.values.forEachIndexed { move, list ->
        val leaving = HashMap<Int, Int>()
        val arriving = HashMap<Int, Int>()
        for (a in list) {
            val start = arrowEnd(g, board, a.player, a.from, source = true, postMove, used = leaving.getOrDefault(a.from, 0))
            val end = arrowEnd(g, board, a.player, a.to, source = false, postMove, used = arriving.getOrDefault(a.to, 0))
            leaving.merge(a.from, 1, Int::plus)
            arriving.merge(a.to, 1, Int::plus)
            val len = (end - start).getDistance()
            if (len < 1f) continue
            val l = ArrowLayout(a, move, start, end, 0f)
            val mid = (start + end) / 2f
            val normal = Offset(-l.dir.y, l.dir.x)
            val towardMiddle = size.height / 2 - mid.y
            // Along one side of the board: bend towards the middle; across it: to the left.
            val side = if (abs(towardMiddle) > g.radius && normal.y * towardMiddle < 0) -1f else 1f
            var bend = side * min(len * 0.12f, g.radius * 1.6f)
            for (o in out) {
                if (o.move == move) continue
                val sameStart = (o.start - start).getDistance() < near
                val sameEnd = (o.end - end).getDistance() < near
                val parallel = o.dir.x * l.dir.x + o.dir.y * l.dir.y > 0.9f
                if ((sameStart && sameEnd) || ((sameStart || sameEnd) && parallel)) {
                    val magnitude = maxOf(abs(bend), abs(o.bend), g.radius * 0.7f)
                    val oSide = if (o.bend == 0f) 1f else sign(o.bend)
                    o.bend = oSide * magnitude
                    bend = -oSide * magnitude
                }
            }
            l.bend = bend
            out += l
        }
    }
    return out
}

private fun DrawScope.drawArrows(g: BoardGeometry, board: Board, arrows: List<MoveArrow>, postMove: Boolean) {
    val w = g.radius * if (postMove) 0.14f else 0.2f
    val head = g.radius * if (postMove) 0.55f else 0.75f
    for (l in layoutArrows(g, board, arrows, postMove)) {
        val start = l.start
        val end = l.end
        val normal = Offset(-l.dir.y, l.dir.x)
        // A quadratic curve's apex lies halfway to its control point.
        val control = (start + end) / 2f + normal * (l.bend * 2f)
        val tangent = (end - control).let { it / it.getDistance().coerceAtLeast(1f) }
        val tangentNormal = Offset(-tangent.y, tangent.x)
        val shaftEnd = end - tangent * (head * 0.8f)
        val shaft = Path().apply {
            moveTo(start.x, start.y)
            quadraticTo(control.x, control.y, shaftEnd.x, shaftEnd.y)
        }
        drawPath(shaft, Color.Black.copy(alpha = 0.55f), style = Stroke(width = w + 4f, cap = StrokeCap.Round))
        drawPath(shaft, l.arrow.color, style = Stroke(width = w, cap = StrokeCap.Round))
        val tip = Path().apply {
            moveTo(end.x, end.y)
            lineTo(end.x - tangent.x * head + tangentNormal.x * head * 0.55f, end.y - tangent.y * head + tangentNormal.y * head * 0.55f)
            lineTo(end.x - tangent.x * head - tangentNormal.x * head * 0.55f, end.y - tangent.y * head - tangentNormal.y * head * 0.55f)
            close()
        }
        drawPath(tip, Color.Black.copy(alpha = 0.55f), style = Stroke(width = 4f))
        drawPath(tip, l.arrow.color)
        drawCircle(l.arrow.color, w * 0.9f, start)
    }
}
