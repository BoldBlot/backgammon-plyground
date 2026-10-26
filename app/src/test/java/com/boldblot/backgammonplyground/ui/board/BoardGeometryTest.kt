package com.boldblot.backgammonplyground.ui.board

import androidx.compose.ui.geometry.Offset
import com.boldblot.backgammonplyground.core.BAR
import com.boldblot.backgammonplyground.core.OFF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardGeometryTest {

    private val normal = BoardGeometry(1380f, 1000f)
    private val mirror = BoardGeometry(1380f, 1000f, mirrored = true)

    private fun pointAt(g: BoardGeometry, index: Int): Offset {
        val y = if (g.isTop(index)) g.surfaceTop + g.radius else g.surfaceBottom - g.radius
        return Offset(g.pointCenterX(index), y)
    }

    @Test
    fun everyPointIsHitWhereItIsDrawn() {
        for (g in listOf(normal, mirror)) for (i in 0 until 24) assertEquals("point $i", i, g.hitTest(pointAt(g, i)))
    }

    @Test
    fun mirroringSwapsLeftAndRight() {
        // The human's 1-point: bottom right normally, bottom left mirrored; the tray moves with it.
        assertTrue(normal.pointCenterX(0) > normal.barCenterX)
        assertTrue(mirror.pointCenterX(0) < mirror.barCenterX)
        assertEquals(OFF, normal.hitTest(Offset(1370f, 500f)))
        assertEquals(OFF, mirror.hitTest(Offset(10f, 500f)))
        assertEquals(BAR, mirror.hitTest(Offset(mirror.barCenterX, 500f)))
        // The human's dice stay in the human's (home) half.
        assertTrue(normal.diceRects(0)[0].left > normal.barCenterX)
        assertTrue(mirror.diceRects(0)[1].right < mirror.barCenterX)
    }
}
