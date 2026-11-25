package com.boldblot.backgammonplyground.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

/** Deep dive's icon: an arrow plunging down, outlined in [stroke], with a red dot inside. */
fun deepDiveVector(stroke: Color): ImageVector = ImageVector.Builder("DeepDive", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(stroke), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(9.5f, 1.5f); lineTo(14.5f, 1.5f); lineTo(14.5f, 7f); lineTo(19f, 7f); lineTo(12f, 22.5f); lineTo(5f, 7f); lineTo(9.5f, 7f); close()
    }
    path(fill = SolidColor(Color(0xFFE24B4A))) {
        // A circle of radius 2.5 at (12, 11.6), as two arcs.
        moveTo(9.5f, 11.6f)
        arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 14.5f, y1 = 11.6f)
        arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 9.5f, y1 = 11.6f)
        close()
    }
}.build()

/** The Deep dive icon in the current content colour (the dot stays red). */
@Composable
fun DeepDiveIcon(contentDescription: String?, modifier: Modifier = Modifier) {
    val c = LocalContentColor.current
    val v = remember(c) { deepDiveVector(c) }
    Icon(rememberVectorPainter(v), contentDescription, modifier, tint = Color.Unspecified)
}
