package com.boldblot.backgammonplyground.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shows that a vertically scrolling area has more content: a permanent thin scrollbar at the
 * right edge and a fade to [background] at the bottom while there is more below. Put it before
 * `verticalScroll(state)` in the modifier chain.
 */
fun Modifier.scrollIndicator(state: ScrollState, thumb: Color, background: Color): Modifier = drawWithContent {
    drawContent()
    val max = state.maxValue
    if (max <= 0 || max == Int.MAX_VALUE) return@drawWithContent
    val fade = 28.dp.toPx()
    if (state.value < max) {
        drawRect(
            Brush.verticalGradient(listOf(background.copy(alpha = 0f), background), startY = size.height - fade, endY = size.height),
            topLeft = Offset(0f, size.height - fade), size = Size(size.width, fade),
        )
    }
    val viewport = size.height
    val thumbH = (viewport * viewport / (viewport + max)).coerceAtLeast(24.dp.toPx())
    val y = (viewport - thumbH) * state.value / max
    val w = 3.dp.toPx()
    drawRoundRect(thumb, Offset(size.width - w, y), Size(w, thumbH), CornerRadius(w / 2))
}
