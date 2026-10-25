package com.boldblot.backgammonplyground.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Chart colours from the dataviz reference palette (categorical slots 1 and 2,
 * validated for light and dark surfaces). Text never uses series colours.
 */
object ChartColors {
    @Composable
    fun series(i: Int): Color {
        val dark = isSystemInDarkTheme()
        return when (i) {
            0 -> if (dark) Color(0xFF3987E5) else Color(0xFF2A78D6)
            else -> if (dark) Color(0xFFD95926) else Color(0xFFEB6834)
        }
    }
}

private fun niceStep(range: Double, ticks: Int): Double {
    val raw = range / ticks
    val mag = 10.0.pow(floor(log10(raw)))
    val norm = raw / mag
    val nice = when {
        norm <= 1 -> 1.0
        norm <= 2 -> 2.0
        norm <= 5 -> 5.0
        else -> 10.0
    }
    return nice * mag
}

/**
 * Single-series line chart with recessive grid, a marker on the last point and a
 * tap-to-inspect tooltip (crosshair on the nearest point).
 * [labels] describe each point for the tooltip (e.g. a date).
 */
@Composable
fun LineChart(
    values: List<Double>,
    labels: List<String>,
    valueFormat: (Double) -> String,
    modifier: Modifier = Modifier,
    reference: Double? = null,
    referenceLabel: String? = null,
) {
    if (values.isEmpty()) return
    val color = ChartColors.series(0)
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val measurer = rememberTextMeasurer()
    var selected by remember(values) { mutableStateOf<Int?>(null) }

    val lo0 = minOf(values.min(), reference ?: values.min())
    val hi0 = maxOf(values.max(), reference ?: values.max())
    val span = (hi0 - lo0).takeIf { it > 1e-9 } ?: (abs(hi0) * 0.2 + 1.0)
    val step = niceStep(span, 4)
    val lo = floor((lo0 - span * 0.05) / step) * step
    val hi = ceil((hi0 + span * 0.05) / step) * step

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(values) {
                    detectTapGestures { pos ->
                        val left = 44.dp.toPx()
                        val w = size.width - left - 8.dp.toPx()
                        val i = if (values.size == 1) 0 else (((pos.x - left) / w) * (values.size - 1)).toInt()
                            .coerceIn(0, values.size - 1)
                        selected = if (selected == i) null else i
                    }
                },
        ) {
            val left = 44.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 18.dp.toPx()
            fun x(i: Int) = if (values.size == 1) (left + right) / 2 else left + (right - left) * i / (values.size - 1)
            fun y(v: Double) = (bottom - (bottom - top) * ((v - lo) / (hi - lo))).toFloat()

            // recessive grid + y labels
            var t = lo
            while (t <= hi + step * 0.01) {
                val yy = y(t)
                drawLine(grid, Offset(left, yy), Offset(right, yy), strokeWidth = 1f)
                val lbl = measurer.measure(valueFormat(t), TextStyle(color = muted, fontSize = 10.sp))
                drawText(lbl, topLeft = Offset(left - lbl.size.width - 6f, yy - lbl.size.height / 2))
                t += step
            }
            reference?.let { r ->
                drawLine(
                    muted, Offset(left, y(r)), Offset(right, y(r)), strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                )
                referenceLabel?.let {
                    val lbl = measurer.measure(it, TextStyle(color = muted, fontSize = 10.sp))
                    drawText(lbl, topLeft = Offset(right - lbl.size.width, y(r) - lbl.size.height - 2f))
                }
            }
            // series line (2dp)
            val path = Path()
            values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
            if (values.size <= 30) {
                values.forEachIndexed { i, v -> drawCircle(color, 2.5.dp.toPx(), Offset(x(i), y(v))) }
            }
            // last point marker with surface ring + direct label
            val li = values.lastIndex
            drawCircle(surface, 6.dp.toPx(), Offset(x(li), y(values[li])))
            drawCircle(color, 4.dp.toPx(), Offset(x(li), y(values[li])))
            // x-axis end labels
            val first = measurer.measure(labels.first(), TextStyle(color = muted, fontSize = 10.sp))
            drawText(first, topLeft = Offset(left, bottom + 4f))
            if (labels.size > 1) {
                val last = measurer.measure(labels.last(), TextStyle(color = muted, fontSize = 10.sp))
                drawText(last, topLeft = Offset(right - last.size.width, bottom + 4f))
            }
            // tooltip
            selected?.let { i ->
                val px = x(i)
                drawLine(muted, Offset(px, top), Offset(px, bottom), strokeWidth = 1f)
                drawCircle(surface, 7.dp.toPx(), Offset(px, y(values[i])))
                drawCircle(color, 5.dp.toPx(), Offset(px, y(values[i])))
                val tip = measurer.measure(
                    "${labels[i]}: ${valueFormat(values[i])}",
                    TextStyle(color = ink, fontSize = 12.sp),
                )
                val tx = (px - tip.size.width / 2).coerceIn(left, right - tip.size.width)
                val ty = top
                drawRoundRect(surface, Offset(tx - 6f, ty - 2f), Size(tip.size.width + 12f, tip.size.height + 4f), CornerRadius(8f))
                drawRoundRect(grid, Offset(tx - 6f, ty - 2f), Size(tip.size.width + 12f, tip.size.height + 4f), CornerRadius(8f), style = Stroke(1f))
                drawText(tip, topLeft = Offset(tx, ty))
            }
        }
    }
}

/**
 * Grouped bar chart with two series (legend always shown, direct labels on tap),
 * 2px gap between adjacent bars, rounded data ends anchored to the baseline.
 */
@Composable
fun GroupedBarChart(
    groups: List<String>,
    series: List<Pair<String, List<Double>>>,
    valueFormat: (Double) -> String,
    modifier: Modifier = Modifier,
    reference: Double? = null,
    referenceLabel: String? = null,
) {
    val colors = series.indices.map { ChartColors.series(it) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val measurer = rememberTextMeasurer()
    var selected by remember(series) { mutableStateOf<Pair<Int, Int>?>(null) }
    val maxV = maxOf(series.maxOf { it.second.maxOrNull() ?: 0.0 }, reference ?: 0.0).takeIf { it > 0 } ?: 1.0
    val step = niceStep(maxV, 4)
    val hi = ceil(maxV * 1.08 / step) * step

    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            series.forEachIndexed { i, (name, _) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(colors[i], RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(5.dp))
                    Text(name, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .padding(top = 6.dp)
                .pointerInput(series) {
                    detectTapGestures { pos ->
                        val left = 40.dp.toPx()
                        val gw = (size.width - left) / groups.size
                        val gi = ((pos.x - left) / gw).toInt()
                        if (gi !in groups.indices) return@detectTapGestures
                        val within = (pos.x - left - gi * gw) / gw
                        val si = (((within - 0.15f) / 0.7f) * series.size).toInt().coerceIn(0, series.size - 1)
                        selected = if (selected == gi to si) null else gi to si
                    }
                },
        ) {
            val left = 40.dp.toPx()
            val top = 4.dp.toPx()
            val bottom = size.height - 16.dp.toPx()
            fun y(v: Double) = (bottom - (bottom - top) * (v / hi)).toFloat()
            var t = 0.0
            while (t <= hi + step * 0.01) {
                drawLine(grid, Offset(left, y(t)), Offset(size.width, y(t)), strokeWidth = 1f)
                val lbl = measurer.measure(valueFormat(t), TextStyle(color = muted, fontSize = 10.sp))
                drawText(lbl, topLeft = Offset(left - lbl.size.width - 6f, y(t) - lbl.size.height / 2))
                t += step
            }
            val gw = (size.width - left) / groups.size
            val gap = 2.dp.toPx()
            val barW = (gw * 0.7f - gap * (series.size - 1)) / series.size
            groups.forEachIndexed { gi, g ->
                series.forEachIndexed { si, (_, vals) ->
                    val v = vals[gi]
                    val x = left + gi * gw + gw * 0.15f + si * (barW + gap)
                    val yTop = y(v)
                    val r = 4.dp.toPx().coerceAtMost(barW / 2)
                    // rounded top, square base anchored on the baseline
                    val path = Path().apply {
                        moveTo(x, bottom)
                        lineTo(x, yTop + r)
                        quadraticTo(x, yTop, x + r, yTop)
                        lineTo(x + barW - r, yTop)
                        quadraticTo(x + barW, yTop, x + barW, yTop + r)
                        lineTo(x + barW, bottom)
                        close()
                    }
                    drawPath(path, colors[si])
                }
                val lbl = measurer.measure(g, TextStyle(color = muted, fontSize = 11.sp))
                drawText(lbl, topLeft = Offset(left + gi * gw + (gw - lbl.size.width) / 2, bottom + 2f))
            }
            reference?.let { r ->
                drawLine(muted, Offset(left, y(r)), Offset(size.width, y(r)), strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                referenceLabel?.let {
                    val lbl = measurer.measure(it, TextStyle(color = muted, fontSize = 10.sp))
                    drawText(lbl, topLeft = Offset(size.width - lbl.size.width, y(r) - lbl.size.height - 2f))
                }
            }
            selected?.let { (gi, si) ->
                val v = series[si].second[gi]
                val tip = measurer.measure("${series[si].first}, ${groups[gi]}: ${valueFormat(v)}", TextStyle(color = ink, fontSize = 12.sp))
                val cx = left + gi * gw + gw * 0.15f + si * (barW + gap) + barW / 2
                val tx = (cx - tip.size.width / 2).coerceIn(left, size.width - tip.size.width)
                drawRoundRect(surface, Offset(tx - 6f, top), Size(tip.size.width + 12f, tip.size.height + 4f), CornerRadius(8f))
                drawRoundRect(grid, Offset(tx - 6f, top), Size(tip.size.width + 12f, tip.size.height + 4f), CornerRadius(8f), style = Stroke(1f))
                drawText(tip, topLeft = Offset(tx, top + 2f))
            }
        }
    }
}
