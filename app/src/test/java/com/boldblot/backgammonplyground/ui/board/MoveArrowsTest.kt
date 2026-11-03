package com.boldblot.backgammonplyground.ui.board

import androidx.compose.ui.graphics.Color
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.Move
import com.boldblot.backgammonplyground.core.Player
import com.boldblot.backgammonplyground.core.Step
import org.junit.Assert.assertEquals
import org.junit.Test

class MoveArrowsTest {

    /** 24/18/13: one checker, two steps. */
    private val move = Move(listOf(Step(23, 17), Step(17, 12)))

    private fun board(blotOn18: Boolean): Board {
        val human = IntArray(25).also { it[23] = 2; it[12] = 5; it[7] = 3; it[5] = 5 }
        val cpu = IntArray(25).also { it[23] = 2; it[12] = 4; it[7] = 3; it[5] = 5 }
        if (blotOn18) cpu[23 - 17] = 1 else cpu[12] = 5
        return Board.fromSides(human, cpu)
    }

    private fun ends(b: Board) =
        splitAtHits(arrowsFor(Player.HUMAN, move, Color.Red), hitsOn(b)).map { it.from to it.to }

    @Test
    fun `a move without a hit is one arrow`() {
        assertEquals(listOf(23 to 12), ends(board(blotOn18 = false)))
    }

    @Test
    fun `a hit on the way cuts the arrow in two`() {
        assertEquals(listOf(23 to 17, 17 to 12), ends(board(blotOn18 = true)))
    }

    @Test
    fun `computer arrows are cut at the hits given`() {
        val cpu = arrowsFor(Player.CPU, move, Color.Red)
        assertEquals(listOf(23 to 17, 17 to 12), splitAtHits(cpu) { _, p -> p in setOf(17) }.map { it.from to it.to })
        assertEquals(listOf(23 to 12), splitAtHits(cpu) { _, p -> p in setOf(12) }.map { it.from to it.to })
    }
}
