package com.boldblot.backgammonplyground.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Importing several match files at once: reading each, the players they share, and the message after. */
class MatchFileReadTest {
    private fun bytes(name: String) = requireNotNull(javaClass.getResourceAsStream("/xg/$name")) { name }.readBytes()

    @Test
    fun `reads xg and mat files and finds the players they share`() {
        val xg = MatchFileRead.read("a.xg", bytes("match-11pt.xg")).getOrThrow()
        val mat = MatchFileRead.read("a.mat", bytes("match-11pt.mat")).getOrThrow()
        assertNotNull(xg.xg)
        assertNull(mat.xg)
        assertEquals(xg.match.names, MatchFileRead.commonNames(listOf(xg, mat)))
        assertTrue(MatchFileRead.read("x.txt", "not a match".toByteArray()).isFailure)
    }

    @Test
    fun `files with no player in common share no name`() {
        val a = MatchFileRead.read("a.mat", bytes("match-11pt.mat")).getOrThrow()
        val text = String(bytes("match-11pt.mat"))
        val renamed = a.match.names.fold(text) { t, n -> t.replace(n, n.reversed() + "Z") }
        val b = MatchFileRead.read("b.mat", renamed.toByteArray()).getOrThrow()
        assertEquals(emptyList<String>(), MatchFileRead.commonNames(listOf(a, b)))
    }

    @Test
    fun `the summary counts matches and games and lists the failures`() {
        val xg = MatchFileRead.read("a.xg", bytes("match-11pt.xg")).getOrThrow()
        val im = XgImport.withAnalysis(MatImport.replay(xg.match, 0, skipBad = true), xg.xg!!, 0)
        val text = MatchFileRead.summary(listOf(xg to im, xg to im), listOf("c.xg: The file could not be read."))
        val games = 2 * im.games.size
        assertTrue(text, text.startsWith("Imported 2 of 3 matches, $games games."))
        assertTrue(text, "eXtreme Gammon's analysis" in text)
        assertTrue(text, text.endsWith("Not imported:\nc.xg: The file could not be read."))
    }
}
