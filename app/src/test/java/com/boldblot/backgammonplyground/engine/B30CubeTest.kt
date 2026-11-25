package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.PositionIds
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 30B (the 3-ply match cube, plain and fast): double / no double by the leaf cube lives, take / pass by the plain values.
 * Expected values (normalized equity, 5L) from the reference implementation: ND and DT with the lives at every leaf, and the
 * plain DT. Races keep the pip rule, so their values do not change.
 */
class B30CubeTest {
    private val cases = listOf(
        // XGID, ND (30B), DT (30B), DT (plain)
        listOf("XGID=-a-a-BE-C---bC-a-cbe---AA-:0:0:1:00:6:16:0:17:10", "0.54407", "0.55824", "0.55824"),
        listOf("XGID=--b-BBD-B----C-b-bbbBab-b-:0:0:1:00:3:0:0:7:10", "0.81486", "0.83042", "0.83525"),
        listOf("XGID=-A--BCDABAA-a-aa---gcaa---:0:0:1:00:8:7:0:11:10", "0.76594", "0.85540", "0.85540"),
        listOf("XGID=---a--EaD--AdCA--d-e----A-:0:0:1:00:0:4:0:7:10", "0.54448", "0.54498", "0.54784"),
        listOf("XGID=----cCD-C--AbC---b-eab--A-:0:0:1:00:4:10:0:13:10", "0.65789", "0.67383", "0.67367"),
        listOf("XGID=a-ABaBBbC---cD---c-d-A--a-:0:0:1:00:17:9:0:21:10", "0.95572", "1.13040", "1.17707"),
        listOf("XGID=-aa---E-C---dE---d-e----B-:0:0:1:00:8:10:0:11:10", "0.29052", "0.29152", "0.29152"),
    )

    @Test
    fun `3-ply match cube uses the leaf lives for double or not, the plain values for the take`() {
        val e = TestEngine.engine
        assumeTrue(e != null)
        e!!
        val code = Depth(3).code
        for (c in cases) {
            val s = PositionIds.parseXgid(c[0])
            val ctx = CubeContext(s.matchLength, s.score, s.crawford, s.jacoby, s.cube, s.onRoll)
            val r = e.cubeDecision(s.board, ctx, code)
            val (nd, dt, plainDt) = Triple(c[1].toDouble(), c[2].toDouble(), c[3].toDouble())
            assertEquals("${c[0]} DT", plainDt, r.doubleTake.toDouble(), 2e-5)
            assertEquals("${c[0]} D/ND margin", minOf(dt, 1.0) - nd, minOf(r.doubleTake, r.doublePass) - r.noDouble.toDouble(), 2e-5)
        }
    }
}
