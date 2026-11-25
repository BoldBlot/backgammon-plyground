package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.GameSearch
import com.boldblot.backgammonplyground.core.PositionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * T-Roller on the GPU (libvkgames.so beside the host JNI library, any Vulkan device, llvmpipe included) gives the CPU's values
 * bit for bit: cube decisions and checker play, D included. Skipped when the library or a Vulkan device is missing.
 */
class GpuParityTest {
    private fun open(): Pair<PureTdEngine, GpuGames>? {
        val e = TestEngine.engine ?: return null
        // Only when asked (scripts/host-tests.sh asks when the GPU code, the C engine or the games search changed).
        if (System.getProperty("bg.gputests") != "true") return null
        val lib = System.getProperty("bg.hostlib")?.let { File(File(it).parentFile, "libvkgames.so") }?.takeIf { it.isFile } ?: return null
        if (!VkGamesNative.loadFrom(lib.path)) return null
        val dir = File(File(System.getProperty("bg.datadir")).parentFile, "puretd")
        val bo = File(System.getProperty("java.io.tmpdir"), "puretd-bearoff-test.bin").path
        val threads = Runtime.getRuntime().availableProcessors()
        val vk = VkGamesNative.nativeOpen("$dir/prob5_wide.ptw", bo, threads, 16384, -1, false, false)
        if (vk == 0L) return null
        return e to GpuGames(e.nativeHandle, vk, threads)
    }

    private val cubes = listOf(
        "XGID=-a-a-BE-C---bC-a-cbe---AA-:0:0:1:00:6:16:0:17:10",
        "XGID=---a--EaD--AdCA--d-e----A-:0:0:1:00:0:4:0:7:10",
        "XGID=----cCD-C--AbC---b-eab--A-:0:0:1:00:4:10:0:13:10",
    )

    @Test
    fun `T-Roller cube and checker values are the same on the GPU`() {
        val (e, gpu) = open() ?: run { assumeTrue("no GPU library or device", false); return }
        try {
            val code = GameSearch.TC48.code
            var played = 0
            for (x in cubes) {
                val s = PositionIds.parseXgid(x)
                val ctx = CubeContext(s.matchLength, s.score, s.crawford, s.jacoby, s.cube, s.onRoll)
                e.gpu = null
                e.cubeDecision(s.board, ctx, Depth(1).code)   // evict the cube cache
                val cpu = e.cubeDecision(s.board, ctx, code)
                e.gpu = gpu
                e.cubeDecision(s.board, ctx, Depth(1).code)
                val g = e.cubeDecision(s.board, ctx, code)
                assertEquals(x, cpu.noDouble, g.noDouble, 0f)
                assertEquals(x, cpu.doubleTake, g.doubleTake, 0f)
                assertEquals(x, cpu.spread, g.spread)
                if (cpu.plies == code) played++
            }
            assertTrue("no decision played games out", played > 0)
            // Checker play: T-Roller 48 on a contact position (games on the close plays).
            val s = PositionIds.parseXgid("XGID=-b----E-C---eE---c-e----B-:0:0:1:52:0:0:0:7:10")
            val ctx = CubeContext(s.matchLength, s.score, s.crawford, s.jacoby, s.cube, s.onRoll)
            val d = s.dice!!
            e.gpu = null
            e.rankMoves(s.board, ctx, 1, 2, 0, null)
            val cpu = e.rankMoves(s.board, ctx, d.d1, d.d2, GameSearch.T48.code, null)
            e.gpu = gpu
            e.rankMoves(s.board, ctx, 1, 2, 0, null)
            val g = e.rankMoves(s.board, ctx, d.d1, d.d2, GameSearch.T48.code, null)
            assertEquals(cpu.candidates.map { it.move }, g.candidates.map { it.move })
            assertEquals(cpu.candidates.map { it.equity }, g.candidates.map { it.equity })
        } finally {
            e.gpu = null
            gpu.close()
        }
    }

    @Test
    fun `the GPU check reports a verdict`() {
        val (_, gpu) = open() ?: run { assumeTrue("no GPU library or device", false); return }
        gpu.close()
        val dir = File(File(System.getProperty("bg.datadir")).parentFile, "puretd")
        val bo = File(System.getProperty("java.io.tmpdir"), "puretd-bearoff-test.bin").path
        val report = VkGamesNative.nativeSelfTest("$dir/prob5_compact.ptw", bo, -1)
        assertTrue(report, report.trim().lines().last().startsWith("BEST "))
    }

}
