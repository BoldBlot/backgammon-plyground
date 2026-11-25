package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.CubeView
import com.boldblot.backgammonplyground.core.MatchCube
import com.boldblot.backgammonplyground.core.MatchEquityTable
import com.boldblot.backgammonplyground.core.SearchMonitor
import com.boldblot.backgammonplyground.core.invertProbs
import com.boldblot.backgammonplyground.engine.GamesSearch.Companion.D1
import com.boldblot.backgammonplyground.engine.GamesSearch.Companion.D2
import com.boldblot.backgammonplyground.engine.GamesSearch.Companion.W21
import java.util.SplittableRandom
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.sqrt

/**
 * T-Roller's games on the GPU (experimental, 0.9.21): [GamesSearch.cube] and [GamesSearch.posts] with every game run in lockstep,
 * one turn of all of them per [VkGamesNative.nativeStep] (moves on CPU threads, the network rows on the GPU). Same dice and the
 * same order of arithmetic, so the values equal GamesSearch's bit for bit (with PureTD's C code built -ffp-contract=off). The
 * first moves of D come from the CPU ([GamesSearch.cubeFirsts] / [GamesSearch.postFirsts]: identical to the GPU's own D).
 *
 * The games are split over [lanes] lanes, each on its own thread with its own workers, so that one lane's CPU work (cube, luck,
 * moves) runs while another's rows are on the GPU.
 */
internal class GpuGames(ptdHandle: Long, private val vk: Long, private val threads: Int, private val lanes: Int = 2) {
    private val h0 = PureTdNative.nativeShare(ptdHandle, 1).also { check(it != 0L) { "PureTD handle for the GPU games failed" } }
    private val met0 = MatchEquityTable()
    private val laneSet = Array(lanes) { Lane(maxOf(1, threads / lanes)) }
    private val lanePool = Executors.newFixedThreadPool(lanes) { r -> Thread(null, r, "gpu-lane", 1L shl 20).apply { isDaemon = true } }

    /** Time on the GPU step and in the Kotlin work around it, and game turns played, since the last [takeStats]. */
    @Volatile private var msStep = 0.0
    @Volatile private var msKotlin = 0.0
    @Volatile private var turns = 0L

    /** "GPU 1234 ms, CPU 567 ms, 89012 turns" since the last call (for the log). */
    @Synchronized
    fun takeStats(): String = "GPU step %.0f ms, CPU %.0f ms, %d game turns".format(java.util.Locale.ROOT, msStep, msKotlin, turns).also {
        msStep = 0.0; msKotlin = 0.0; turns = 0
    }

    private class G(
        val g: Int, val job: Int, val s: IntArray, var q: List<Float>, var race: Float, var pips: Float, var owner: Int, var v: Int,
        val rnd: SplittableRandom, val first: Map<Int, GamesSearch.Ov>? = null, val doubleFirst: Boolean = false,
    ) {
        var t = 0; var luck = 0.0; var result = Double.NaN; var done = false
        var p = 0; var me = 0; var opp = 0; var view = CubeView.CENTRED; var r = 0
    }

    private fun shuffled(seed: Long): IntArray {
        val r = SplittableRandom(seed)
        val p = IntArray(36) { it }
        for (i in 35 downTo 1) { val j = r.nextInt(i + 1); val t = p[i]; p[i] = p[j]; p[j] = t }
        return p
    }

    private fun life(race: Float, pips: Float) = if (race == 0f) 0.68 else (0.55 + 0.00125 * pips).coerceIn(0.6, 0.7)

    private fun raceAndPips(s: IntArray): Pair<Float, Float> {
        var white = 25 * s[24]; var black = 25 * s[25]; var hiw = -1; var lob = 24
        for (i in 0 until 24) {
            val c = s[i]
            if (c > 0) { white += c * (i + 1); if (i > hiw) hiw = i } else if (c < 0) { black += -c * (24 - i); if (i < lob) lob = i }
        }
        return (if (s[24] == 0 && s[25] == 0 && hiw < lob) 1f else 0f) to (white + black) / 2f
    }

    private fun seed(states: List<IntArray>, a: Int, b: Int, crawford: Boolean, owner: Int, v: Int): Long {
        var h = -3750763034362895579L
        fun mix(x: Int) { h = (h xor x.toLong()) * 1099511628211L }
        states.forEach { s -> s.forEach { mix(it) } }
        mix(a); mix(b); mix(if (crawford) 1 else 0); mix(owner); mix(v)
        return h and 0xFFFFFFFFFFFL
    }

    private fun emg(a: Int, b: Int, crawford: Boolean, mwc: Double, v: Int): Double {
        val win = met0.after(a, b, crawford, v, 0); val lose = met0.after(a, b, crawford, 0, v)
        return if (win - lose < 1e-12) 0.0 else (2 * mwc - (win + lose)) / (win - lose)
    }

    private fun se(x: DoubleArray): Double {
        if (x.size < 2) return 0.0
        val m = x.average()
        return sqrt(x.sumOf { (it - m) * (it - m) } / x.size / (x.size - 1))
    }

    private inner class Lane(private val threads: Int) {
        private val mets = Array(threads) { MatchEquityTable() }
        private val pool = Executors.newFixedThreadPool(threads) { r -> Thread(null, r, "gpu-games", 1L shl 20).apply { isDaemon = true } }

        /** Runs [body] for items 0 until [n] over the threads, worker k using mets[k]. */
        private fun parallel(n: Int, body: (k: Int, i: Int) -> Unit) {
            val m = threads.coerceAtMost(maxOf(1, n))
            pool.invokeAll((0 until m).map { k -> Callable { var i = k; while (i < n) { body(k, i); i += m } } }).forEach { it.get() }
        }

        /** Plays [list] to the end in lockstep; false on a GPU error or when [mon] asks to stop. */
        fun run(list: List<G>, a: Int, b: Int, crawford: Boolean, turns: Int, weights: Array<FloatArray>, roll: (G) -> Int, mon: SearchMonitor?): Boolean {
            var active: List<G> = list
            while (active.isNotEmpty()) {
                if (mon?.stop == true) return false
                val k0 = System.nanoTime()
                // Before the roll: the end of the games, the cube.
                parallel(active.size) { k, i ->
                    val x = active[i]; val met = mets[k]
                    fun ours(mwc: Double) = (if (x.p == 0) mwc else 1 - mwc) - x.luck
                    x.p = x.s[28]; x.me = if (x.p == 0) a else b; x.opp = if (x.p == 0) b else a
                    x.view = when (x.owner) { 0 -> CubeView.CENTRED; x.p + 1 -> CubeView.MINE; else -> CubeView.THEIRS }
                    val mayDouble = !crawford && x.view != CubeView.THEIRS && x.v < x.me
                    if (x.t >= turns) {
                        val mc = MatchCube(met, x.q, x.me, x.opp, crawford, life(x.race, x.pips))
                        x.result = if (!mayDouble) ours(mc.cubeful(x.view, x.v)) else { val (nd, dt, dp) = mc.decision(x.view, x.v); ours(maxOf(nd, minOf(dt, dp))) }
                        x.done = true; return@parallel
                    }
                    if (mayDouble && (x.t > 0 || x.doubleFirst)) {
                        val (nd, dt, dp) = MatchCube(met, x.q, x.me, x.opp, crawford, life(x.race, x.pips)).decision(x.view, x.v)
                        if (minOf(dt, dp) > nd) {
                            if (dp < dt) { x.result = ours(met.after(x.me, x.opp, crawford, x.v, 0)); x.done = true; return@parallel }
                            x.v *= 2; x.owner = 2 - x.p
                        }
                    }
                    x.r = roll(x)
                }
                active = active.filter { !it.done }
                if (active.isEmpty()) break
                val n = active.size
                val st = IntArray(29 * n); val wt = FloatArray(10 * n); val dice = IntArray(2 * n)
                for ((i, x) in active.withIndex()) {
                    x.s.copyInto(st, 29 * i)
                    weights[Integer.numberOfTrailingZeros(x.v).coerceAtMost(7)].copyInto(wt, 10 * i)
                    dice[2 * i] = x.r / 6 + 1; dice[2 * i + 1] = x.r % 6 + 1
                }
                val k1 = System.nanoTime()
                val out = VkGamesNative.nativeStep(vk, st, wt, dice, n) ?: return false
                val k2 = System.nanoTime()
                // After the roll: its luck, the move.
                parallel(n) { k, i ->
                    val x = active[i]; val met = mets[k]; val o0 = 205 * i
                    fun ours(mwc: Double) = (if (x.p == 0) mwc else 1 - mwc) - x.luck
                    val oview = when (x.view) { CubeView.MINE -> CubeView.THEIRS; CubeView.THEIRS -> CubeView.MINE; else -> x.view }
                    val lo = minOf(x.r / 6, x.r % 6); val hi = maxOf(x.r / 6, x.r % 6)
                    var mean = 0.0; var actual = 0.0
                    for (j in 0 until 21) {
                        val pts = out[o0 + j].toInt()
                        val value = if (pts > 0) met.after(x.me, x.opp, crawford, pts * x.v, 0) else {
                            val o = o0 + 21 + 7 * j
                            val mc = MatchCube(met, List(5) { out[o + it] }, x.opp, x.me, crawford, life(out[o + 5], out[o + 6]))
                            1 - if (mc.canDoubleNow(oview, x.v)) mc.decision(oview, x.v).let { (nd, dt, dp) -> maxOf(nd, minOf(dt, dp)) } else mc.cubeful(oview, x.v)
                        }
                        mean += W21[j] * value
                        if (D1[j] == lo + 1 && D2[j] == hi + 1) actual = value
                    }
                    x.luck += if (x.p == 0) actual - mean else mean - actual
                    // D: the first move chosen at 2-ply instead of the 1-ply pick (luck as before).
                    val ov = if (x.t == 0) x.first?.get(lo * 6 + hi) else null
                    if (ov != null) {
                        if (ov.pts > 0) { x.result = ours(met.after(x.me, x.opp, crawford, ov.pts * x.v, 0)); x.done = true; return@parallel }
                        x.q = ov.q; x.race = ov.race; x.pips = ov.pips; ov.s.copyInto(x.s); x.t++
                        return@parallel
                    }
                    val pts = out[o0 + 168].toInt()
                    if (pts > 0) { x.result = ours(met.after(x.me, x.opp, crawford, pts * x.v, 0)); x.done = true; return@parallel }
                    x.q = invertProbs(List(5) { out[o0 + 169 + it] }); x.race = out[o0 + 174]; x.pips = out[o0 + 175]
                    for (q in 0 until 29) x.s[q] = out[o0 + 176 + q].toInt()
                    x.t++
                }
                active = active.filter { !it.done }
                val k3 = System.nanoTime()
                synchronized(this@GpuGames) { msStep += (k2 - k1) / 1e6; msKotlin += ((k1 - k0) + (k3 - k2)) / 1e6; this@GpuGames.turns += n }
            }
            return true
        }
    }

    private fun roller(seed: Long): (G) -> Int {
        val perm1 = shuffled(seed * 31 + 1); val perm2 = shuffled(seed * 31 + 2)
        return { x ->
            when (x.t) {
                0 -> perm1[x.g % 36]
                1 -> perm2[(x.g / 36 + 5 * (x.g % 36)) % 36]
                else -> x.rnd.nextInt(36)
            }
        }
    }

    /**
     * [GamesSearch.posts] on the GPU: equity (normalized, current cube) for the side that just moved of each of [states] (turn 1),
     * by [games] games of [turns] turns on the same dice, the opponent free to double before its first roll; [firsts] = D's first
     * moves per state (or null). Null on a GPU error or a stop.
     */
    fun posts(
        states: List<IntArray>, a: Int, b: Int, crawford: Boolean, owner: Int, v: Int, games: Int, turns: Int,
        firsts: List<Map<Int, GamesSearch.Ov>>?, mon: SearchMonitor?,
    ): Pair<List<Double>, Int>? {
        val seed = seed(states, a, b, crawford, owner, v)
        val weights = gameWeights(met0, a, b, crawford)
        val all = ArrayList<G>(states.size * games)
        for ((j, st) in states.withIndex()) {
            val q0 = PureTdNative.nativePostProbs(h0, st, 1, null)?.let { r -> invertProbs(List(5) { r[it] }) } ?: return null
            val (race0, pips0) = raceAndPips(st)
            for (g in 0 until games) all += G(g, j, st.copyOf(), q0, race0, pips0, owner, v, SplittableRandom(seed * 1_000_003L + g), firsts?.get(j), true)
        }
        val roll = roller(seed)
        val per = (all.size + lanes - 1) / lanes
        val ok = lanePool.invokeAll((0 until lanes).map { l ->
            Callable { laneSet[l].run(all.subList(minOf(all.size, l * per), minOf(all.size, (l + 1) * per)), a, b, crawford, turns, weights, roll, mon) }
        }).all { it.get() }
        if (!ok) return null
        val res = List(states.size) { j -> all.filter { it.job == j }.map { it.result }.average() }
        return res.map { emg(a, b, crawford, it, v) } to games
    }

    /** [GamesSearch.cube] on the GPU; [firsts] = D's first moves of the two lines (or null). Null on a GPU error or a stop. */
    fun cube(
        s: IntArray, a: Int, b: Int, crawford: Boolean, owner: Int, v: Int, games: Int, turns: Int,
        firsts: List<Map<Int, GamesSearch.Ov>>?, mon: SearchMonitor?,
    ): GamesSearch.CubeGames? {
        val seed = seed(listOf(s), a, b, crawford, owner, v)
        val weights = gameWeights(met0, a, b, crawford)
        val q0 = PureTdNative.nativePostProbs(h0, s, 1, null)?.let { r -> invertProbs(List(5) { r[it] }) } ?: return null
        val (race0, pips0) = raceAndPips(s)
        val all = ArrayList<G>(2 * games)
        for (g in 0 until games) {
            all += G(g, 0, s.copyOf(), q0, race0, pips0, owner, v, SplittableRandom(seed * 1_000_003L + g), firsts?.get(0))
            all += G(g, 1, s.copyOf(), q0, race0, pips0, 2, 2 * v, SplittableRandom(seed * 1_000_003L + g), firsts?.get(1))
        }
        val roll = roller(seed)
        val per = (games + lanes - 1) / lanes
        val ok = lanePool.invokeAll((0 until lanes).map { l ->
            Callable { laneSet[l].run(all.subList(minOf(2 * games, 2 * l * per), minOf(2 * games, 2 * (l + 1) * per)), a, b, crawford, turns, weights, roll, mon) }
        }).all { it.get() }
        if (!ok) return null
        val r0 = DoubleArray(games) { all[2 * it].result }; val r1 = DoubleArray(games) { all[2 * it + 1].result }
        val scale = (met0.after(a, b, crawford, v, 0) - met0.after(a, b, crawford, 0, v)) / 2
        val diff = DoubleArray(games) { r0[it] - r1[it] }
        return GamesSearch.CubeGames(emg(a, b, crawford, r0.average(), v), emg(a, b, crawford, r1.average(), v), if (scale > 1e-12) se(diff) / scale else 0.0, games, games)
    }

    fun close() {
        lanePool.shutdownNow()
        VkGamesNative.nativeClose(vk)
        PureTdNative.nativeClose(h0)
    }
}
