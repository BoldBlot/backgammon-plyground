package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DepthTest {

    @Test
    fun codesRoundTrip() {
        for (p in 1..Depth.MAX_SEARCH_PLIES) for (f in MoveFilter.entries) {
            val d = Depth(p, f)
            assertEquals(d, Depth.of(d.code))
        }
    }

    @Test
    fun codesBeforeBuild16KeepTheirMeaning() {
        assertEquals(Depth(1), Depth.of(0))
        assertEquals(Depth(3, MoveFilter.NARROW), Depth.of(1))
        assertEquals(Depth(3, MoveFilter.NORMAL), Depth.of(2))
        assertEquals(Depth(3, MoveFilter.WIDE), Depth.of(3))
        assertEquals(Depth(3, MoveFilter.ALL), Depth.of(MAX_PLIES))
    }

    @Test
    fun labels() {
        assertEquals("1-ply", plyLabel(0))
        assertEquals("3-ply ±0.16", plyLabel(3))
        assertEquals("3-ply, all moves", plyLabel(MAX_PLIES))
        assertEquals("4-ply ±0.08", plyLabel(Depth(4, MoveFilter.NORMAL).code))
        assertEquals("2-ply", Depth(2, MoveFilter.WIDE).label(cube = true))
    }
}
