package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.core.CubeView
import com.boldblot.backgammonplyground.core.MatchCube
import com.boldblot.backgammonplyground.core.MatchEquityTable

/**
 * Cube-aware match cube search, in match-winning chance (MWC), ported from
 * engine/sage/boldblot/match_cube_search.py: every cube state is followed through the tree and the
 * side on roll decides at each turn (double when min(DT, DP) beats ND). The tree ([tree], from
 * PureTdNative.nativeCubeTree) has the moves picked by the cubeless network at 1-ply; its leaves
 * are valued by our match Janowski ([MatchCube]) with its own cube decision. A state is (owner,
 * cube value) seen by the side on roll; the Crawford flag belongs to the game, the same at every node.
 *
 * [a] and [b]: points needed by the player on roll at the root and by its opponent.
 */
class MatchCubeSearch(
    private val met: MatchEquityTable, private val tree: FloatArray, private val levels: Int,
    private val a: Int, private val b: Int, private val crawford: Boolean,
) {
    /** Where each level's nodes start in [tree] (level k at offsets[k]); the leaves' data follow the last level. */
    private val offsets = LongArray(levels + 2).also { o -> for (k in 2..levels + 1) o[k] = o[k - 1] + pow21(k - 1) }
    private val leafStart = offsets[levels + 1].toInt()
    private val leaves = arrayOfNulls<MatchCube>(pow21(levels).toInt())
    private val memo = HashMap<Long, Double>()

    private fun pts(level: Int, idx: Long): Int = tree[(offsets[level] + idx).toInt()].toInt()

    /** Needs (side on roll, opponent) at a node of [level]: the root's side is on roll at even levels. */
    private fun needs(level: Int) = if (level % 2 == 0) a to b else b to a

    private fun canDouble(owner: CubeView, v: Int, need: Int) = !crawford && owner != CubeView.THEIRS && v < need

    private fun flip(o: CubeView) = when (o) { CubeView.MINE -> CubeView.THEIRS; CubeView.THEIRS -> CubeView.MINE; else -> o }

    private fun leaf(idx: Long, owner: CubeView, v: Int): Double {
        val (me, them) = needs(levels)
        val mc = leaves[idx.toInt()] ?: run {
            val o = leafStart + 7 * idx.toInt()
            val probs = List(5) { tree[o + it] }
            val pips = tree[o + 6].toDouble()
            // Cube life as in core.cubeLife: 0.68 in contact, by the average pip count in a race.
            val x = if (tree[o + 5] == 0f) 0.68 else (0.55 + 0.00125 * pips).coerceIn(0.6, 0.7)
            MatchCube(met, probs, me, them, crawford, x).also { leaves[idx.toInt()] = it }
        }
        if (!canDouble(owner, v, me)) return mc.cubeful(owner, v)
        val (nd, dt, dp) = mc.decision(owner, v)
        return if (minOf(dt, dp) > nd) minOf(dt, dp) else nd
    }

    /** MWC of the side on roll at node [idx] of [level] (0 = the root), its cube decision included. */
    private fun value(level: Int, idx: Long, owner: CubeView, v: Int): Double {
        if (level == levels) return leaf(idx, owner, v)
        val key = ((idx * levels + level) * 3 + owner.ordinal) * 64 + Integer.numberOfTrailingZeros(v)
        memo[key]?.let { return it }
        val need = needs(level).first
        val nd = noDouble(level, idx, owner, v)
        val r = if (!canDouble(owner, v, need)) nd else {
            val dt = noDouble(level, idx, CubeView.THEIRS, 2 * v)
            val dp = after(level, v, 0)
            val best = minOf(dt, dp)
            if (best > nd) best else nd
        }
        memo[key] = r
        return r
    }

    /** MWC after this game for the side on roll at [level]: won [won] points or lost [lost]. */
    private fun after(level: Int, won: Int, lost: Int): Double {
        val (me, them) = needs(level)
        return met.after(me, them, crawford, won, lost)
    }

    /** The side on roll's MWC when it rolls without doubling (cube [owner], [v]). */
    private fun noDouble(level: Int, idx: Long, owner: CubeView, v: Int): Double {
        var tot = 0.0
        for (r in 0 until 21) {
            val c = idx * 21 + r
            val p = pts(level + 1, c)
            tot += W[r] * (if (p > 0) after(level, p * v, 0) else 1 - value(level + 1, c, flip(owner), v))
        }
        return tot
    }

    /** (ND, DT, DP) in MWC for the player on roll at the root; DT = ND when it cannot double. */
    fun root(owner: CubeView, v: Int): Triple<Double, Double, Double> {
        val nd = noDouble(0, 0, owner, v)
        val dt = if (canDouble(owner, v, a)) noDouble(0, 0, CubeView.THEIRS, 2 * v) else nd
        return Triple(nd, dt, after(0, v, 0))
    }

    fun canDoubleNow(owner: CubeView, v: Int) = canDouble(owner, v, a)

    /** Cubeless probabilities for the player on roll at the root, from the same tree (as PureTD's n-ply probabilities). */
    fun probs(): List<Float> = nodeProbs(0, 0).map { it.toFloat() }

    private fun nodeProbs(level: Int, idx: Long): DoubleArray {
        if (level == levels) { val o = leafStart + 7 * idx.toInt(); return DoubleArray(5) { tree[o + it].toDouble() } }
        val acc = DoubleArray(5)
        for (r in 0 until 21) {
            val c = idx * 21 + r
            val p = pts(level + 1, c)
            // The roller's view: a win by p points, or the child's probabilities turned around.
            val q = if (p > 0) doubleArrayOf(1.0, if (p >= 2) 1.0 else 0.0, if (p >= 3) 1.0 else 0.0, 0.0, 0.0)
            else nodeProbs(level + 1, c).let { k -> doubleArrayOf(1 - k[0], k[3], k[4], k[1], k[2]) }
            for (i in 0 until 5) acc[i] += W[r] * q[i]
        }
        return acc
    }

    companion object {
        fun pow21(k: Int): Long { var n = 1L; repeat(k) { n *= 21 }; return n }

        /** Chances of the 21 rolls in PureTD's order (1-1, 1-2, ..., 6-6). */
        private val W: DoubleArray = run {
            val d1 = intArrayOf(1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 5, 5, 6)
            val d2 = intArrayOf(1, 2, 3, 4, 5, 6, 2, 3, 4, 5, 6, 3, 4, 5, 6, 4, 5, 6, 5, 6, 6)
            DoubleArray(21) { if (d1[it] == d2[it]) 1.0 / 36 else 2.0 / 36 }
        }
    }
}
