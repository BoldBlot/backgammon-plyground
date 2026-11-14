package com.boldblot.backgammonplyground.data

import com.boldblot.backgammonplyground.core.GameSearch
import com.boldblot.backgammonplyground.core.PureTdNet
import org.junit.Assert.assertEquals
import org.junit.Test

class NewInstallTest {
    @Test fun aNewInstallationStartsWithThe4LNetAndPlayedOutGamesForMatches() {
        assertEquals(PureTdNet.COMPACT, NEW_INSTALL.puretdNet)
        assertEquals(GameSearch.G36.code, NEW_INSTALL.matchChecker)
        assertEquals(GameSearch.C44.code, NEW_INSTALL.matchCube)
        assertEquals("3/36/7", NEW_INSTALL.checkerDepth(true).label())
        // Money games keep their depths.
        assertEquals(Settings().checkerDepth(false), NEW_INSTALL.checkerDepth(false))
        assertEquals(Settings().cubeDepth(false), NEW_INSTALL.cubeDepth(false))
        // Existing installations keep the old defaults for keys they lack.
        assertEquals(PureTdNet.WIDE, Settings().puretdNet)
        assertEquals(-1, Settings().matchChecker)
        assertEquals(-1, Settings().matchCube)
    }
}
