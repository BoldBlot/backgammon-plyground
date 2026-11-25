package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DepthTest {

    @Test
    fun codesRoundTrip() {
        for (p in 1..Depth.MAX_SEARCH_PLIES) for (f in MoveFilter.entries) {
            if (p == 1 && f == MoveFilter.FAST) continue   // 1-ply has no fast version: 14 reads as 1-ply
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
        assertEquals("3-ply, pruned", plyLabel(3))
        assertEquals("3-ply, all moves", plyLabel(MAX_PLIES))
        assertEquals("4-ply ±0.08", plyLabel(Depth(4, MoveFilter.NORMAL).code))
        assertEquals("2-ply", Depth(2, MoveFilter.WIDE).label(cube = true))
    }

    @Test
    fun atLeastOrdersSearchesByWhatTheyDo() {
        val three = Depth(3, MoveFilter.WIDE)
        assertEquals(true, Depth(4, MoveFilter.WIDE).atLeast(three))
        assertEquals(false, Depth(2, MoveFilter.ALL).atLeast(three))
        assertEquals(true, Depth(3, MoveFilter.ALL).atLeast(three))
        assertEquals(false, Depth(3, MoveFilter.NARROW).atLeast(three))
        // Played-out games beat plain plies, and more games beat fewer.
        assertEquals(true, Depth.of(GameSearch.G36.code).atLeast(Depth(4, MoveFilter.ALL)))
        assertEquals(false, three.atLeast(Depth.of(GameSearch.G36.code)))
        assertEquals(true, Depth.of(GameSearch.G288.code).atLeast(Depth.of(GameSearch.G36.code)))
        assertEquals(false, Depth.of(GameSearch.G36.code).atLeast(Depth.of(GameSearch.G288.code)))
        // A search stopped early is below the same one that finished.
        assertEquals(false, three.copy(incomplete = true).atLeast(three))
        assertEquals(true, three.atLeast(three))
    }
}

