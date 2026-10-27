package com.boldblot.backgammonplyground.engine

import android.content.Context
import java.io.File

/**
 * JNI binding to the Open Sage engine (engine/sage, MPL 2.0, by Mark Higgins). Positions are
 * int[26] from the point of view of the player concerned ([1..24] its points, negative = the
 * opponent's checkers, [25] its bar, [0] the opponent's bar); cube states int[7] {value, owner (0
 * centred, 1 the player on roll, 2 the opponent), away, opponent away, Crawford, Jacoby, beaver}.
 * Depths are XG plies (1 = the network alone).
 */
object SageNative {
    @JvmStatic external fun nativeOpen(paths: Array<String>, hidden: IntArray, bearoff: String, threads: Int): Long
    @JvmStatic external fun nativeClose(handle: Long)
    /** Positions after the mover's play from [pre] (int[26] each): {equity, probs[5]} per position. */
    @JvmStatic external fun nativeScore(handle: Long, pre: IntArray, posts: IntArray, cube: IntArray, plies: Int): FloatArray?
    /** {nd, dt, dp, optimal, shouldDouble, shouldTake, beaver, cubeAvailable, probs[5]}. */
    @JvmStatic external fun nativeCube(handle: Long, board: IntArray, cube: IntArray, plies: Int): FloatArray?

    /** Open Sage's production model, stage 11: 24 networks (Stage 9's 17 game-plan pairs, some shared, and 7 back-game networks). */
    val NETWORKS: List<String> = listOf(
        "purerace", "race_race", "race_att", "race_prim", "race_anch", "att_race", "att_att", "att_prim", "att_anch",
        "prim_race", "prim_att", "prim_anch", "prim_anch", "anch_race", "anch_att", "prim_anch", "prim_anch",
    ).map { "sl_s9_$it.weights.best" } +
        listOf("deep", "middle", "double", "p3", "containment", "snake", "massive").map { "sl_s11_bg_$it.weights.best" }

    /** Hidden layer sizes: 100 for the race network, 400 for the others. */
    val HIDDEN: IntArray = IntArray(NETWORKS.size) { if (it == 0) 100 else 400 }

    const val BEAROFF = "bearoff_1sided.db"

    /** Network files unpacked from the assets (copied once per app version). */
    fun files(context: Context): File = EngineFiles.unpack(context, "sage")
}
