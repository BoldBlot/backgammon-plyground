package com.boldblot.backgammonplyground.engine

import android.content.Context
import java.io.File

/**
 * JNI binding to the GNU Backgammon engine (engine/gnubg, GPL v3, behind engine/src/bgengine.h).
 * Boards are int[50] from the point of view of the player on roll (the opponent's 25 cells, then
 * its own), cube states int[9] ([com.boldblot.backgammonplyground.core.CubeContext.toEngine]).
 * Depths are GNU's plies (0 = the network alone, XG's 1-ply).
 */
object GnubgNative {
    @JvmStatic external fun nativeInit(dataDir: String): Int
    @JvmStatic external fun nativeLegalMoves(board: IntArray, d0: Int, d1: Int): IntArray?
    /** Positions after the mover's play (int[50] each, the mover's point of view): {equity, probs[5]} per position. */
    @JvmStatic external fun nativeScoreBoards(boards: IntArray, cube: IntArray, plies: Int): FloatArray?
    /** {nd, dt, dp, optimal, decision, shouldDouble, shouldTake, optional, cubeAvailable, probs[5]}. */
    @JvmStatic external fun nativeCubeDecision(board: IntArray, cube: IntArray, plies: Int): FloatArray?
    @JvmStatic external fun nativeLuck(board: IntArray, d0: Int, d1: Int, cube: IntArray, firstRoll: Boolean): Float

    /** Data files unpacked from the assets (copied once per app version). */
    fun files(context: Context): File = EngineFiles.unpack(context, "gnubg")
}
