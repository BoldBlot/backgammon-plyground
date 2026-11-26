package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Every level the speed test can time runs on every reference position, in its match and as an unlimited game. */
class ReferenceSearchTest {
    @Test fun everyLevelRunsOnEveryReference() {
        // A small stack, as on a phone running the debug build: deep recursion shows here as it would there.
        var failure: Throwable? = null
        val t = Thread(null, { try { runAll() } catch (x: Throwable) { failure = x } }, "small", 256L * 1024)
        t.start(); t.join()
        failure?.let { throw it }
    }

    private fun runAll() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        val cubeCodes = listOf(Depth(1), Depth(2), Depth(3, MoveFilter.FAST), Depth(3), Depth(4, MoveFilter.FAST)).map { it.code } + GameSearch.TC48.code
        val checkerCodes = listOf(Depth(1), Depth(2, MoveFilter.FAST), Depth(3, MoveFilter.FAST)).map { it.code }
        for (r in ReferencePositions.all) for (match in listOf(true, false)) {
            val p = r.setup
            val c = CubeContext(p.matchLength, p.score, p.crawford, p.jacoby, p.cube, p.onRoll)
            val ctx = if (match) c else c.copy(matchLength = 0, score = listOf(0, 0), crawford = false)
            for (code in if (r.cube) cubeCodes else checkerCodes) {
                println("${r.title} match=$match code=$code")
                if (r.cube) e!!.cubeDecision(p.board, ctx, code) else e!!.rankMoves(p.board, ctx, p.dice!!.d1, p.dice!!.d2, code, null)
            }
        }
    }
}

/** A 3-ply match cube decision stopped part way (Stop, the screen closed, the speed test's cap) returns, without a crash. */
class StoppedMatchCubeTest {
    @Test fun stoppedThreePlyMatchCubeReturns() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        val p = ReferencePositions.of(cube = true).first { it.difficulty == RefDifficulty.HARD }.setup
        val ctx = CubeContext(p.matchLength, p.score, p.crawford, p.jacoby, p.cube, p.onRoll)
        for (delayMs in listOf(0L, 2L, 10L, 40L)) {
            val mon = SearchMonitor()
            e!!.monitor = mon
            val stopper = Thread { Thread.sleep(delayMs); mon.requestStop() }
            stopper.start()
            var failure: Throwable? = null
            val t = Thread(null, { try { e.cubeDecision(p.board, ctx, Depth(3).code) } catch (x: Throwable) { failure = x } }, "small", 256L * 1024)
            t.start(); t.join(); stopper.join()
            e.monitor = null
            failure?.let { throw AssertionError("stopped after $delayMs ms", it) }
        }
    }
}
