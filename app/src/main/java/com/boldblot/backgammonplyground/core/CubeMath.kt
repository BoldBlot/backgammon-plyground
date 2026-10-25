package com.boldblot.backgammonplyground.core

import kotlin.math.max
import kotlin.math.min

/*
 * Cube arithmetic for the PureTD engine: our own match equity table (computed from a model, not
 * copied), match play Janowski in match-winning-chance (MWC) space, and money Janowski. Ported
 * from engine/sage/boldblot/{met_model,match_cube,cube_search}.py, where they are tested.
 */

/** Cube owner as seen by the player on roll. */
enum class CubeView { CENTRED, MINE, THEIRS }

/** Cube life (Janowski x): 0.68 in contact, by the average pip count in a race. */
fun cubeLife(board: Board): Double {
    if (!board.isRace()) return 0.68
    val avg = (board.pipCount(Player.HUMAN) + board.pipCount(Player.CPU)) / 2.0
    return (0.55 + 0.00125 * avg).coerceIn(0.6, 0.7)
}

/** Probabilities (win, win gammon, win bg, lose gammon, lose bg) from the other side. */
fun invertProbs(p: List<Float>): List<Float> = listOf(1 - p[0], p[3], p[4], p[1], p[2])

/**
 * Our match equity table. Within a game the winning chance moves continuously, so a player doubles
 * exactly at the opponent's take point; MWC is linear in the winning chance between those points,
 * blended with the dead cube by the cube life [x]. Wins are gammons with share [g] and backgammons
 * with share [bg] (PureTD's estimate at the starting position). Crawford game: no cube.
 * Post-Crawford: the trailer doubles at once and the leader takes or drops (free drop).
 */
class MatchEquityTable(private val g: Double = 0.270, private val bg: Double = 0.0132, private val x: Double = 0.68) {
    private val single = 1 - g - bg
    private val memo = HashMap<Long, Double>()
    private val doublePoints = HashMap<Long, Double>()

    /** MWC of the player needing [a] points against one needing [b]; [post] marks post-Crawford. */
    fun met(a: Int, b: Int, post: Boolean): Double {
        if (a <= 0) return 1.0
        if (b <= 0) return 0.0
        val key = (a.toLong() shl 32) or (b.toLong() shl 1) or (if (post) 1L else 0L)
        memo[key]?.let { return it }
        val v = compute(a, b, post)
        memo[key] = v
        return v
    }

    /** MWC after this game for the player needing [a]: won [won] points or lost [lost]. */
    fun after(a: Int, b: Int, crawfordGame: Boolean, won: Int, lost: Int): Double {
        val a2 = a - won
        val b2 = b - lost
        if (a2 <= 0) return 1.0
        if (b2 <= 0) return 0.0
        val postNow = min(a, b) == 1 && !crawfordGame
        val postNext = crawfordGame || postNow
        return met(a2, b2, postNext && min(a2, b2) == 1)
    }

    private fun model(a: Int, b: Int, won: Int, lost: Int, post: Boolean): Double {
        val a2 = a - won
        val b2 = b - lost
        if (a2 <= 0) return 1.0
        if (b2 <= 0) return 0.0
        val nowPost = post || min(a, b) == 1
        return met(a2, b2, nowPost && min(a2, b2) == 1)
    }

    private fun win(a: Int, b: Int, v: Int, post: Boolean) =
        single * model(a, b, v, 0, post) + g * model(a, b, 2 * v, 0, post) + bg * model(a, b, 3 * v, 0, post)

    private fun loss(a: Int, b: Int, v: Int, post: Boolean) =
        single * model(a, b, 0, v, post) + g * model(a, b, 0, 2 * v, post) + bg * model(a, b, 0, 3 * v, post)

    private fun compute(a: Int, b: Int, post: Boolean): Double {
        if (a == 1 && b == 1) return 0.5
        if (min(a, b) == 1) {
            if (!post) return 0.5 * win(a, b, 1, false) + 0.5 * loss(a, b, 1, false)
            if (a == 1) return 1 - met(b, a, true)
            val take = 0.5 * win(a, b, 2, true) + 0.5 * loss(a, b, 2, true)
            val drop = model(a, b, 1, 0, true)
            return min(take, drop)
        }
        val live = centred(a, b, 1, 0.5)
        val dead = 0.5 * win(a, b, 1, false) + 0.5 * loss(a, b, 1, false)
        return x * live + (1 - x) * dead
    }

    private fun doublePoint(a: Int, b: Int, v: Int): Double? {
        if (v >= a) return null
        val key = (a.toLong() shl 40) or (b.toLong() shl 20) or v.toLong()
        if (doublePoints.containsKey(key)) return doublePoints[key]?.takeIf { it >= 0 }
        val passValue = model(a, b, v, 0, false)
        val (loP, loV) = ownedByOLow(a, b, 2 * v)
        val hiV = win(a, b, 2 * v, false)
        val result: Double? = if (hiV <= passValue) null else {
            val l = loss(a, b, 2 * v, false)
            val kLive = if (loP < 1) (hiV - loV) / (1 - loP) else 0.0
            val c0 = x * (loV - kLive * loP) + (1 - x) * l
            val c1 = x * kLive + (1 - x) * (hiV - l)
            val p = if (c1 > 0) (passValue - c0) / c1 else 1.0
            p.coerceIn(if (x == 1.0) loP else 0.0, 1.0)
        }
        doublePoints[key] = result ?: -1.0
        return result
    }

    private fun ownedByOLow(a: Int, b: Int, v: Int): Pair<Double, Double> {
        val d = doublePoint(b, a, v) ?: return 0.0 to loss(a, b, v, false)
        return (1 - d) to model(a, b, 0, v, false)
    }

    private fun centred(a: Int, b: Int, v: Int, p: Double): Double {
        val right = doublePoint(a, b, v)
        val left = doublePoint(b, a, v)
        val rp = right ?: 1.0
        val rv = if (right != null) model(a, b, v, 0, false) else win(a, b, v, false)
        val lp = if (left != null) 1 - left else 0.0
        val lv = if (left != null) model(a, b, 0, v, false) else loss(a, b, v, false)
        if (p >= rp) return rv
        if (p <= lp) return lv
        return lv + (rv - lv) * (p - lp) / (rp - lp)
    }

    companion object {
        val DEFAULT by lazy { MatchEquityTable() }
    }
}

/**
 * Match cube analysis of one position (player P on roll needs [a], opponent O needs [b]) from
 * cubeless probabilities: our own Janowski in MWC space, cash and take points worked out up the
 * cube ladder.
 */
class MatchCube(
    private val met: MatchEquityTable, probs: List<Float>,
    private val a: Int, private val b: Int, private val crawfordGame: Boolean, private val x: Double,
) {
    private val w = probs[0].toDouble()
    private val winMix: DoubleArray
    private val lossMix: DoubleArray
    private val pr = probs.map { it.toDouble() }
    private val cashPoints = HashMap<Long, Double>()

    init {
        val l = 1 - w
        winMix = if (w > 1e-9) doubleArrayOf((w - pr[1]) / w, (pr[1] - pr[2]) / w, pr[2] / w) else doubleArrayOf(1.0, 0.0, 0.0)
        lossMix = if (l > 1e-9) doubleArrayOf((l - pr[3]) / l, (pr[3] - pr[4]) / l, pr[4] / l) else doubleArrayOf(1.0, 0.0, 0.0)
    }

    private fun after(won: Int, lost: Int) = met.after(a, b, crawfordGame, won, lost)
    private fun winValue(v: Int) = (0..2).sumOf { winMix[it] * after((it + 1) * v, 0) }
    private fun lossValue(v: Int) = (0..2).sumOf { lossMix[it] * after(0, (it + 1) * v) }

    fun dead(v: Int): Double =
        (w - pr[1]) * after(v, 0) + (pr[1] - pr[2]) * after(2 * v, 0) + pr[2] * after(3 * v, 0) +
            (1 - w - pr[3]) * after(0, v) + (pr[3] - pr[4]) * after(0, 2 * v) + pr[4] * after(0, 3 * v)

    private fun canDouble(isP: Boolean, v: Int) = !crawfordGame && v < (if (isP) a else b)

    private fun cashValue(isP: Boolean, v: Int) = if (isP) after(v, 0) else 1 - after(0, v)
    private fun winValueOf(isP: Boolean, v: Int) = if (isP) winValue(v) else 1 - lossValue(v)
    private fun lossValueOf(isP: Boolean, v: Int) = if (isP) lossValue(v) else 1 - winValue(v)

    private fun takeValueForDoubler(isP: Boolean, v: Int, q: Double) = 1 - ownedLive(!isP, 2 * v, 1 - q)

    private fun cashPoint(isP: Boolean, v: Int): Double {
        val key = (v.toLong() shl 1) or (if (isP) 1L else 0L)
        cashPoints[key]?.let { return it }
        val cash = cashValue(isP, v)
        val r = when {
            takeValueForDoubler(isP, v, 1.0) < cash -> 1.0
            takeValueForDoubler(isP, v, 0.0) > cash -> 0.0
            else -> {
                var lo = 0.0
                var hi = 1.0
                repeat(40) {
                    val mid = (lo + hi) / 2
                    if (takeValueForDoubler(isP, v, mid) < cash) lo = mid else hi = mid
                }
                (lo + hi) / 2
            }
        }
        cashPoints[key] = r
        return r
    }

    private fun ownedLive(isP: Boolean, v: Int, q: Double): Double {
        val lose = lossValueOf(isP, v)
        if (!canDouble(isP, v)) return q * winValueOf(isP, v) + (1 - q) * lose
        val cp = cashPoint(isP, v)
        val cash = cashValue(isP, v)
        if (q >= cp) return cash
        return lose + (cash - lose) * (if (cp > 0) q / cp else 1.0)
    }

    private fun live(owner: CubeView, v: Int): Double = when (owner) {
        CubeView.MINE -> ownedLive(true, v, w)
        CubeView.THEIRS -> 1 - ownedLive(false, v, 1 - w)
        CubeView.CENTRED -> {
            var lp = 0.0
            var lv = lossValue(v)
            var rp = 1.0
            var rv = winValue(v)
            if (canDouble(false, v)) { lp = 1 - cashPoint(false, v); lv = after(0, v) }
            if (canDouble(true, v)) { rp = cashPoint(true, v); rv = after(v, 0) }
            when {
                w <= lp -> lv
                w >= rp -> rv
                else -> lv + (rv - lv) * (w - lp) / (rp - lp)
            }
        }
    }

    fun cubeful(owner: CubeView, v: Int): Double = if (crawfordGame) dead(v) else x * live(owner, v) + (1 - x) * dead(v)

    /** (ND, DT, DP) in MWC for P; DT = ND when P cannot double. */
    fun decision(owner: CubeView, v: Int): Triple<Double, Double, Double> {
        val nd = cubeful(owner, v)
        val dp = after(v, 0)
        if (owner == CubeView.THEIRS || !canDouble(true, v)) return Triple(nd, nd, dp)
        val dt = x * (1 - ownedLive(false, 2 * v, 1 - w)) + (1 - x) * dead(2 * v)
        return Triple(nd, dt, dp)
    }

    fun canDoubleNow(owner: CubeView, v: Int) = owner != CubeView.THEIRS && canDouble(true, v)

    /** MWC -> money-equivalent equity (EMG) at cube [v] for P. */
    fun toEmg(mwc: Double, v: Int): Double {
        val win = after(v, 0)
        val lose = after(0, v)
        return if (win - lose < 1e-12) 0.0 else (2 * mwc - (win + lose)) / (win - lose)
    }
}

/** Money cubeful equity per unit cube for the player on roll (Janowski), Jacoby while centred. */
fun moneyJanowski(p: List<Float>, x: Double, owner: CubeView, jacoby: Boolean): Double {
    val w = p[0].toDouble()
    val l = 1 - w
    val bigW = if (w > 1e-7) (w + p[1] + p[2]) / w else 1.0
    val bigL = if (l > 1e-7) (l + p[3] + p[4]) / l else 1.0
    val jac = jacoby && owner == CubeView.CENTRED
    val dead = if (jac) 2 * w - 1 else w * bigW - l * bigL
    val take = (bigL - 0.5) / (bigW + bigL + 0.5)
    val cash = (bigL + 1.0) / (bigW + bigL + 0.5)
    val low = if (jac) -1.0 else -bigL + (bigL - 1) * w / max(take, 1e-7)
    val high = if (jac) 1.0 else 1 + (bigW - 1) * (w - cash) / max(1 - cash, 1e-7)
    val live = when (owner) {
        CubeView.MINE -> if (w >= cash) 1 + (bigW - 1) * (w - cash) / max(1 - cash, 1e-7) else -bigL + (1 + bigL) * w / cash
        CubeView.THEIRS -> if (w <= take) -bigL + (bigL - 1) * w / max(take, 1e-7) else -1 + (bigW + 1) * (w - take) / (1 - take)
        CubeView.CENTRED -> when {
            w <= take -> low
            w >= cash -> high
            else -> -1 + 2 * (w - take) / (cash - take)
        }
    }
    return x * live + (1 - x) * dead
}
