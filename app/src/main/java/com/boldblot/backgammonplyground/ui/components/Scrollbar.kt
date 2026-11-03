package com.boldblot.backgammonplyground.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/**
 * A thin scroll bar at the right edge, always shown while the content is taller than the view, so a
 * list in a dialog shows it scrolls. Put it before `verticalScroll(state)` (it draws in the view).
 */
@Composable
fun Modifier.scrollbar(state: ScrollState): Modifier {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    return drawWithContent {
        drawContent()
        val max = state.maxValue
        if (max <= 0 || max == Int.MAX_VALUE) return@drawWithContent
        val view = size.height
        val thumb = (view * view / (view + max)).coerceAtLeast(24.dp.toPx())
        val top = state.value.toFloat() / max * (view - thumb)
        val w = 3.dp.toPx()
        drawRoundRect(color, Offset(size.width - w, top), Size(w, thumb), CornerRadius(w / 2))
    }
}
