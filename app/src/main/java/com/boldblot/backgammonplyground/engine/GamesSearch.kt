package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.CubeView
import com.boldblot.backgammonplyground.core.MatchCube
import com.boldblot.backgammonplyground.core.MatchEquityTable
import com.boldblot.backgammonplyground.core.SearchMonitor
import com.boldblot.backgammonplyground.core.invertProbs
import java.util.SplittableRandom
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

/**
 * Match decisions valued by short played-out games (the [com.boldblot.backgammonplyground.core.GameSearch]
 * depths), on PureTD.
 *
 * A game: both sides play the network's 1-ply best move by match-winning chance at the current cube and score,
 * and turn the cube by our match Janowski on the 1-ply probabilities. It stops after [turns] turns and is then
 * valued by the same Janowski, so the fixed cube life only acts [turns] turns away, on the positions the games
 * reach. Each roll's luck (the value after the roll thrown minus the average over the 21 rolls, each played and
 * valued as the game itself would) is taken off the game's result: a turn then costs about 22 times as much,
 * and the spread of a game falls far more. The first two rolls are spread evenly over the 36 x 36 pairs.
 *
 * The things compared (No double and Double/take; the candidate plays) are played over the same dice. The
 * dice come from the position ([seed]), so the same position always gives the same values. Against XG's
 * rollouts of 406 match cube decisions (2026-11), 44 games of 7 turns lose about half the equity 4-ply
 * loses, at 4-ply's cost.
 *
 * One search at a time (as the engine itself). The games run on [threads] worker threads, each with its own
 * engine handle and match equity table.
 */
internal class GamesSearch(private val handle: Long) {
    private var handles = LongArray(0)
    private var mets = emptyArray<MatchEquityTable>()
    private var pool: ExecutorService? = null
    private var poolSize = 0

    /** Workers 0 until [n] are ready: a handle and a table each, and a thread to run on. */
    private fun ensure(n: Int) {
        if (handles.size < n) {
            val old = handles.size
            handles = LongArray(n) { if (it < old) handles[it] else PureTdNative.nativeShare(handle, 1).also { h -> check(h != 0L) { "PureTD worker failed" } } }
            mets = Array(n) { if (it < old) mets[it] else MatchEquityTable() }
        }
        if (poolSize < n) {
            pool?.shutdown()
            pool = Executors.newFixedThreadPool(n) { r -> Thread(null, r, "games", 1L shl 20).apply { isDaemon = true } }
            poolSize = n
        }
    }

    /** What the games of one decision share: the score (the side the values are for is [a] away), the cube rule and the dice. */
    private inner class Setup(val a: Int, val b: Int, val crawford: Boolean, val turns: Int, val seed: Long, val mon: SearchMonitor?) {
        val perm1 = shuffled(seed * 31 + 1)
        val perm2 = shuffled(seed * 31 + 2)

        /** Each side's weights (ours first) of its 5 probabilities in its match-winning chance, by cube level. */
        val weights = Array(8) { k ->
            val v = 1 shl k
            val met = mets[0]
            fun side(me: Int, opp: Int): List<Float> {
                fun after(won: Int, lost: Int) = met.after(me, opp, crawford, won, lost)
                val l1 = after(0, v); val lg = after(0, 2 * v); val lb = after(0, 3 * v)
                val w1 = after(v, 0); val wg = after(2 * v, 0); val wb = after(3 * v, 0)
                return listOf(w1 - l1, wg - w1, wb - wg, lg - l1, lb - lg).map { it.toFloat() }
            }
            (side(a, b) + side(b, a)).toFloatArray()
        }

        private fun shuffled(seed: Long): IntArray {
            val r = SplittableRandom(seed)
            val p = IntArray(36) { it }
            for (i in 35 downTo 1) { val j = r.nextInt(i + 1); val t = p[i]; p[i] = p[j]; p[j] = t }
            return p
        }

        /** Roll (0-35) number [t] of game [g]: the first two spread evenly over the games, the rest random. */
        private fun roll(g: Int, t: Int, rnd: SplittableRandom): Int = when (t) {
            0 -> perm1[g % 36]
            1 -> perm2[(g / 36 + 5 * (g % 36)) % 36]
            else -> rnd.nextInt(36)
        }

        /** Cube life as [com.boldblot.backgammonplyground.core.cubeLife]: 0.68 in contact, by the pip count in a race. */
        private fun life(race: Float, pips: Float) = if (race == 0f) 0.68 else (0.55 + 0.00125 * pips).coerceIn(0.6, 0.7)

        /**
         * Our match-winning chance in game [g] from [start] (its turn field is the side to roll, whose
         * probabilities are [q0]); the cube is [owner0]'s (0 centred, 1 ours, 2 theirs) at [v0]. The side to
         * roll may double before its first roll only when [doubleFirst]. NaN when the search was stopped.
         */
        fun game(k: Int, g: Int, start: IntArray, q0: List<Float>, race0: Float, pips0: Float, owner0: Int, v0: Int, doubleFirst: Boolean): Double {
            val h = handles[k]; val met = mets[k]
            val rnd = SplittableRandom(seed * 1_000_003L + g)
            val s = start.copyOf()
            val out = FloatArray(7)
            var q = q0; var race = race0; var pips = pips0
            var owner = owner0; var v = v0
            var t = 0
            var luck = 0.0 // ours, in match-winning chance
            while (true) {
                if (mon?.stop == true) return Double.NaN
                val p = s[28]
                val me = if (p == 0) a else b
                val opp = if (p == 0) b else a
                val view = when (owner) { 0 -> CubeView.CENTRED; p + 1 -> CubeView.MINE; else -> CubeView.THEIRS }
                fun ours(mwc: Double) = (if (p == 0) mwc else 1 - mwc) - luck
                val mayDouble = !crawford && view != CubeView.THEIRS && v < me
                if (t >= turns) {
                    val mc = MatchCube(met, q, me, opp, crawford, life(race, pips))
                    if (!mayDouble) return ours(mc.cubeful(view, v))
                    val (nd, dt, dp) = mc.decision(view, v)
                    return ours(maxOf(nd, minOf(dt, dp)))
                }
                if (mayDouble && (t > 0 || doubleFirst)) {
                    val (nd, dt, dp) = MatchCube(met, q, me, opp, crawford, life(race, pips)).decision(view, v)
                    if (minOf(dt, dp) > nd) {
                        if (dp < dt) return ours(met.after(me, opp, crawford, v, 0))
                        v *= 2; owner = 2 - p
                    }
                }
                val r = roll(g, t, rnd)
                val w = weights[Integer.numberOfTrailingZeros(v).coerceAtMost(7)]
                // The luck of this roll: every roll played and valued as the game would, one level deep.
                val tree = PureTdNative.nativeCubeTree(h, s, 1, w) ?: error("PureTD tree failed")
                val oview = when (view) { CubeView.MINE -> CubeView.THEIRS; CubeView.THEIRS -> CubeView.MINE; else -> view }
                val lo = minOf(r / 6, r % 6); val hi = maxOf(r / 6, r % 6)
                var mean = 0.0; var actual = 0.0
                for (i in 0 until 21) {
                    val pts = tree[i].toInt()
                    val value = if (pts > 0) met.after(me, opp, crawford, pts * v, 0) else {
                        val o = 21 + 7 * i
                        val mc = MatchCube(met, List(5) { tree[o + it] }, opp, me, crawford, life(tree[o + 5], tree[o + 6]))
                        1 - if (mc.canDoubleNow(oview, v)) mc.decision(oview, v).let { (nd, dt, dp) -> maxOf(nd, minOf(dt, dp)) } else mc.cubeful(oview, v)
                    }
                    mean += W21[i] * value
                    if (D1[i] == lo + 1 && D2[i] == hi + 1) actual = value
                }
                luck += if (p == 0) actual - mean else mean - actual
                val pts = PureTdNative.nativePlay(h, s, r / 6 + 1, r % 6 + 1, w, out)
                check(pts >= 0) { "PureTD play failed" }
                if (pts > 0) return ours(met.after(me, opp, crawford, pts * v, 0))
                q = invertProbs(List(5) { out[it] }); race = out[5]; pips = out[6]
                t++
            }
        }
    }

    /**
     * Runs [games] games of each of [jobs] things over [threads] workers, game by game (every job on a game's
     * dice before the next game): result[job] holds the games that finished for every job, in the same order.
     */
    private fun play(jobs: Int, games: Int, threads: Int, mon: SearchMonitor?, workerInit: (() -> Unit)?, one: (k: Int, job: Int, g: Int) -> Double): Array<DoubleArray> {
        val res = Array(jobs) { DoubleArray(games) }
        val ok = BooleanArray(games)
        val next = AtomicInteger(0)
        val done = AtomicInteger(0)
        val n = threads.coerceIn(1, games)
        mon?.report(WHAT, 0, games)
        val tasks = (0 until n).map { k ->
            Callable {
                workerInit?.invoke()
                while (true) {
                    val g = next.getAndIncrement()
                    if (g >= games || mon?.stop == true) break
                    var good = true
                    for (job in 0 until jobs) {
                        val v = one(k, job, g)
                        if (v.isNaN()) { good = false; break }
                        res[job][g] = v
                    }
                    if (good) { ok[g] = true; mon?.report(WHAT, done.incrementAndGet(), games) }
                }
            }
        }
        requireNotNull(pool).invokeAll(tasks).forEach { it.get() }
        val kept = (0 until games).filter { ok[it] }
        return Array(jobs) { j -> DoubleArray(kept.size) { res[j][kept[it]] } }
    }

    private fun raceAndPips(s: IntArray): Pair<Float, Float> {
        var white = 25 * s[24]; var black = 25 * s[25]; var hiw = -1; var lob = 24
        for (i in 0 until 24) {
            val c = s[i]
            if (c > 0) { white += c * (i + 1); if (i > hiw) hiw = i } else if (c < 0) { black += -c * (24 - i); if (i < lob) lob = i }
        }
        return (if (s[24] == 0 && s[25] == 0 && hiw < lob) 1f else 0f) to (white + black) / 2f
    }

    /** 1-ply probabilities of the side to roll in [s]. */
    private fun onRollProbs(s: IntArray): List<Float> {
        val r = PureTdNative.nativePostProbs(handles[0], s, 1, null) ?: error("PureTD evaluation failed")
        return invertProbs(List(5) { r[it] })
    }

    /** The dice of a decision: from its positions, the score and the cube, so that it always plays the same games. */
    private fun seed(states: List<IntArray>, a: Int, b: Int, crawford: Boolean, owner: Int, v: Int): Long {
        var h = -3750763034362895579L
        fun mix(x: Int) { h = (h xor x.toLong()) * 1099511628211L }
        states.forEach { s -> s.forEach { mix(it) } }
        mix(a); mix(b); mix(if (crawford) 1 else 0); mix(owner); mix(v)
        return h and 0xFFFFFFFFFFFL
    }

    private fun emg(a: Int, b: Int, crawford: Boolean, mwc: Double, v: Int): Double {
        val win = mets[0].after(a, b, crawford, v, 0); val lose = mets[0].after(a, b, crawford, 0, v)
        return if (win - lose < 1e-12) 0.0 else (2 * mwc - (win + lose)) / (win - lose)
    }

    private fun se(x: DoubleArray): Double {
        if (x.size < 2) return 0.0
        val m = x.average()
        return sqrt(x.sumOf { (it - m) * (it - m) } / x.size / (x.size - 1))
    }

    /** No double and Double/take (normalized, at the current cube), the standard error of their difference, and the games played of those asked for. */
    class CubeGames(val nd: Double, val dt: Double, val spread: Double, val played: Int, val games: Int)

    /**
     * The cube decision of the side on roll in [s] (turn 0; [a] away against [b]) with the cube at [v], centred
     * or its own ([owner] 0 or 1), by [games] games of [turns] turns for each of No double and Double/take.
     * Null when it was stopped before [MIN_GAMES] games.
     */
    fun cube(s: IntArray, a: Int, b: Int, crawford: Boolean, owner: Int, v: Int, games: Int, turns: Int, threads: Int, mon: SearchMonitor?, workerInit: (() -> Unit)?): CubeGames? {
        ensure(threads.coerceIn(1, games))
        val set = Setup(a, b, crawford, turns, seed(listOf(s), a, b, crawford, owner, v), mon)
        val q = onRollProbs(s); val (race, pips) = raceAndPips(s)
        val r = play(2, games, threads, mon, workerInit) { k, job, g ->
            if (job == 0) set.game(k, g, s, q, race, pips, owner, v, false) else set.game(k, g, s, q, race, pips, 2, 2 * v, false)
        }
        val n = r[0].size
        if (n < minOf(MIN_GAMES, games)) return null
        val scale = (mets[0].after(a, b, crawford, v, 0) - mets[0].after(a, b, crawford, 0, v)) / 2
        val diff = DoubleArray(n) { r[0][it] - r[1][it] }
        return CubeGames(emg(a, b, crawford, r[0].average(), v), emg(a, b, crawford, r[1].average(), v), if (scale > 1e-12) se(diff) / scale else 0.0, n, games)
    }

    /**
     * Equity (normalized, at the current cube) for the side that just moved (turn 1 in every one of [states],
     * none of them a finished game; [a] away against [b]; the cube [owner] 0 centred, 1 its own, 2 the
     * opponent's, at [v]) of each position, by [games] games of [turns] turns on the same dice. Null when it
     * was stopped before [MIN_GAMES] games; otherwise the values and the games played.
     */
    fun posts(states: List<IntArray>, a: Int, b: Int, crawford: Boolean, owner: Int, v: Int, games: Int, turns: Int, threads: Int, mon: SearchMonitor?, workerInit: (() -> Unit)?): Pair<List<Double>, Int>? {
        ensure(threads.coerceIn(1, games))
        val set = Setup(a, b, crawford, turns, seed(states, a, b, crawford, owner, v), mon)
        val qs = states.map { onRollProbs(it) }
        val rp = states.map { raceAndPips(it) }
        val r = play(states.size, games, threads, mon, workerInit) { k, job, g -> set.game(k, g, states[job], qs[job], rp[job].first, rp[job].second, owner, v, true) }
        val n = r[0].size
        if (n < minOf(MIN_GAMES, games)) return null
        return r.map { emg(a, b, crawford, it.average(), v) } to n
    }

    companion object {
        /** A stopped search needs this many finished games to count; otherwise the stage before it stands. */
        const val MIN_GAMES = 12
        const val WHAT = "Played-out games"
        private val D1 = intArrayOf(1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 5, 5, 6)
        private val D2 = intArrayOf(1, 2, 3, 4, 5, 6, 2, 3, 4, 5, 6, 3, 4, 5, 6, 4, 5, 6, 5, 6, 6)
        private val W21 = DoubleArray(21) { if (D1[it] == D2[it]) 1.0 / 36 else 2.0 / 36 }
    }
}
