package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.ui.components.workLine
import com.boldblot.backgammonplyground.ui.home.recommendedBackgroundThreads
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundWorkTest {
    @Test fun etaText() {
        assertEquals("about 10 s", BackgroundWork.eta(2_000))
        assertEquals("about 40 s", BackgroundWork.eta(38_000))
        assertEquals("about 2 min", BackgroundWork.eta(100_000))
    }

    @Test fun lines() {
        assertEquals("Your match: 23 decisions left of 40, about 2 min", workLine(WorkItem("m", WorkKind.MATCH, "Your match", 23, 40, 120_000) {}))
        assertEquals("Quiz look-ahead: 3 positions left", workLine(WorkItem("q", WorkKind.QUIZ, "Quiz look-ahead", 3, 3) {}))
    }

    @Test fun putRemoveStop() {
        val w = BackgroundWork()
        var stopped = 0
        w.put(WorkItem("a", WorkKind.SET, "Set", 2, 5) { stopped++ })
        w.put(WorkItem("b", WorkKind.QUIZ, "Quiz", 1, 1) { stopped++ })
        w.stopAll()
        assertEquals(2, stopped)
        w.remove("a"); w.remove("b")
        assertEquals(0, w.items.value.size)
    }

    @Test fun recommendedThreads() {
        assertEquals(4, recommendedBackgroundThreads(8))
        assertEquals(2, recommendedBackgroundThreads(6))
        assertEquals(1, recommendedBackgroundThreads(1))
    }
}
