package com.boldblot.backgammonplyground.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boldblot.backgammonplyground.core.Labels

/** A small yellow horseshoe (open end down), for a lucky roll. */
val HorseshoeIcon: ImageVector = ImageVector.Builder("Horseshoe", 16.dp, 16.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color(0xFFF2B705)), strokeLineWidth = 4.2f, strokeLineCap = StrokeCap.Round) {
        moveTo(6f, 21f); lineTo(5f, 12f)
        arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 19f, y1 = 12f)
        lineTo(18f, 21f)
    }
    path(fill = SolidColor(Color(0xFF7A5B00))) {
        for ((x, y) in listOf(5.3f to 15f, 18.7f to 15f, 7.2f to 8f, 16.8f to 8f)) {
            moveTo(x - 0.8f, y); arcTo(0.8f, 0.8f, 0f, true, true, x + 0.8f, y); arcTo(0.8f, 0.8f, 0f, true, true, x - 0.8f, y); close()
        }
    }
}.build()

/**
 * The luck of a roll on a review row: one horseshoe for a lucky roll (at least [Labels.LUCKY] better than an average roll),
 * two for a very lucky one ([Labels.VERY_LUCKY]); one or two 💩 for unlucky ones. Nothing for an ordinary roll.
 */
@Composable
fun LuckMarker(luck: Float?, modifier: Modifier = Modifier) {
    val l = luck ?: return
    val n = when {
        l >= Labels.VERY_LUCKY || l <= -Labels.VERY_LUCKY -> 2
        l >= Labels.LUCKY || l <= -Labels.LUCKY -> 1
        else -> return
    }
    val lucky = l > 0
    val what = (if (n == 2) "Very " else "") + if (lucky) "lucky roll" else "unlucky roll"
    Row(modifier.semantics { contentDescription = what }, verticalAlignment = Alignment.CenterVertically) {
        repeat(n) {
            if (lucky) Image(HorseshoeIcon, null, Modifier.size(14.dp))
            else Text("💩", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 0.dp))
        }
    }
}
