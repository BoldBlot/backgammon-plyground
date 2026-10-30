package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.BAR
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.MoveNotation
import com.boldblot.backgammonplyground.core.OFF
import com.boldblot.backgammonplyground.core.PositionIds
import com.boldblot.backgammonplyground.core.Step
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Checker-play benchmark against FlashBack cards' eXtreme Gammon figures, run by
 * scripts/fb-bench.sh (skipped otherwise). Input lines: `C <tab> id <tab> set|deck <tab> XGID <tab>
 * play=equity;play=equity...`. Each engine's best play costs what its card says (a play the card
 * does not list costs as much as its worst listed play). Settings by environment: FB_INPUT,
 * FB_OUT, FB_ENGINES (ptd,gnu,sage), FB_SET (part of the set|deck name), FB_LIMIT, FB_PLIES,
 * FB_FILTER (a MoveFilter: WIDE = pruned, as in the app; ALL = every play at full depth).
 */
class FlashbackBench {

    /** The position after XG notation [play] by [player] from [board]; null if it does not apply. */
    private fun apply(board: Board, player: Int, play: String): Board? = runCatching {
        var b = board
        for (raw in play.trim().lowercase().split(' ').filter { it.isNotBlank() }) {
            val rep = Regex("\\((\\d)\\)$").find(raw)?.groupValues?.get(1)?.toInt() ?: 1
            val pts = raw.replace(Regex("\\(\\d\\)$"), "").split('/').map { it.removeSuffix("*") }
            fun idx(p: String) = when (p) { "bar" -> BAR; "off" -> OFF; else -> p.toInt() - 1 }
            repeat(rep) { for (i in 0 until pts.size - 1) b = b.applyStep(player, Step(idx(pts[i]), idx(pts[i + 1]))) }
        }
        b
    }.getOrNull()

    @Test
    fun checkerPlay() {
        val input = System.getenv("FB_INPUT")
        assumeTrue(input != null && TestEngine.engine != null)
        val wanted = (System.getenv("FB_ENGINES") ?: "ptd").split(',').map { it.trim() }
        val engines = wanted.map { name ->
            name to when (name) {
                "ptd" -> TestEngine.engine!!
                "gnu" -> requireNotNull(TestEngine.gnubg) { "GNU Backgammon did not load" }
                "sage" -> requireNotNull(TestEngine.sage) { "Open Sage did not load" }
                else -> error("unknown engine $name (ptd, gnu, sage)")
            }
        }
        val set = System.getenv("FB_SET")?.takeIf { it.isNotBlank() }
        val limit = System.getenv("FB_LIMIT")?.toIntOrNull() ?: Int.MAX_VALUE
        val depth = Depth((System.getenv("FB_PLIES") ?: "3").toInt(), MoveFilter.valueOf(System.getenv("FB_FILTER") ?: "WIDE"))
        val out = File(requireNotNull(System.getenv("FB_OUT"))).printWriter()
        out.println((listOf("kind", "id", "group") + wanted.flatMap { listOf("$it.err", "$it.listed", "$it.sec", "$it.play") }).joinToString("\t"))
        var n = 0
        for (line in File(input!!).readLines()) {
            val (kind, id, group, xgid, xg) = line.split('\t')
            if (kind != "C" || (set != null && !group.contains(set))) continue
            if (n++ >= limit) break
            val s = PositionIds.parseXgid(xgid)
            val dice = s.dice ?: continue
            val ctx = CubeContext(s.matchLength, s.score, s.crawford, s.jacoby, s.cube, s.onRoll)
            val plays = xg.split(';').mapNotNull { p -> apply(s.board, s.onRoll, p.substringBefore('='))?.let { it to p.substringAfter('=').toFloat() } }
            if (plays.isEmpty()) continue
            val best = plays.maxOf { it.second }
            val worst = plays.minOf { it.second }
            val cols = mutableListOf(kind, id, group)
            for ((_, e) in engines) {
                val t = System.nanoTime()
                val r = e.rankMoves(s.board, ctx, dice.d1, dice.d2, depth.code, null)
                val move = r.candidates[0].move
                val eq = plays.firstOrNull { it.first == s.board.applyMove(s.onRoll, move) }?.second
                cols += "%.4f".format(best - (eq ?: worst))
                cols += if (eq == null) "out" else "in"
                cols += "%.2f".format((System.nanoTime() - t) / 1e9)
                cols += MoveNotation.format(s.board, s.onRoll, move)
            }
            out.println(cols.joinToString("\t"))
            out.flush()
        }
        out.close()
    }
}
