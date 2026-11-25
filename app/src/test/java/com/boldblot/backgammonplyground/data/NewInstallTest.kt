package com.boldblot.backgammonplyground.data

import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.GameSearch
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.PureTdNet
import org.junit.Assert.assertEquals
import org.junit.Test

class NewInstallTest {
    @Test fun aNewInstallationStartsWithThe4LNetAndFastDepths() {
        assertEquals(PureTdNet.COMPACT, NEW_INSTALL.puretdNet)
        // Fast is on by default: 3-ply fast everywhere until the welcome screen's speed test recommends otherwise.
        assertEquals(Depth(3, MoveFilter.FAST), NEW_INSTALL.checkerDepth(true))
        assertEquals(Depth(3, MoveFilter.FAST), NEW_INSTALL.cubeDepth(true))
        assertEquals(Depth(3, MoveFilter.FAST), NEW_INSTALL.checkerDepth(false))
        assertEquals(MoveFilter.FAST, NEW_INSTALL.cubeDepth(false).filter)
        // Existing installations keep the old defaults for keys they lack.
        assertEquals(PureTdNet.WIDE, Settings().puretdNet)
        assertEquals(-1, Settings().matchChecker)
        assertEquals(-1, Settings().matchCube)
        assertEquals(false, Settings().cubeFast)
    }

    @Test fun olderGamesSearchesInTheSettingsBecomeThreePlyFast() {
        val fast3 = Depth(3, MoveFilter.FAST).code
        for (g in listOf(GameSearch.G36, GameSearch.G144, GameSearch.C44, GameSearch.C288)) assertEquals(fast3, successorCode(g.code))
        assertEquals(GameSearch.T144.code, successorCode(GameSearch.T144.code))
        assertEquals(34, successorCode(34))
        assertEquals(-1, successorCode(-1))
        assertEquals(44, Depth.of(successorCode(50)).code)
    }
}
