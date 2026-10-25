package com.boldblot.backgammonplyground.engine

import android.content.Context
import java.io.File

/**
 * JNI binding to the PureTD engine (engine/puretd: PureTD's networks and move generator, MIT
 * licence, with our own search, bear-off database and cube logic). Positions are int[29] each:
 * points[24] (positive = the side called WHITE), bar[WHITE], bar[BLACK], off[WHITE], off[BLACK],
 * turn (0 = WHITE, 1 = BLACK).
 */
object PureTdNative {
    @JvmStatic external fun nativeOpen(cubeless: String, cubeful: String, bearoffCache: String?, threads: Int): Long
    @JvmStatic external fun nativeClose(handle: Long)
    /** Probabilities for the side that just moved (turn = the other side), 5 per position. */
    @JvmStatic external fun nativePostProbs(handle: Long, states: IntArray, plies: Int): FloatArray?
    /** Money cube decision for the side on roll: {nd, dt, 5 cubeless probabilities}. */
    @JvmStatic external fun nativeCube(handle: Long, state: IntArray, plies: Int, owner: Int, jacoby: Boolean): FloatArray?
    /** Resulting positions (int[29] each) of every legal play with dice (d1, d2). */
    @JvmStatic external fun nativeLegalStates(state: IntArray, d1: Int, d2: Int): IntArray?
    /**
     * Speed test over up to [n] positions: {milliseconds per decision, decisions timed}. [kind] 0
     * checker play, 1 cube decision, 2 checker play with doubles; [within] is the checker root
     * filter (negative: every legal move). Stops after the decision that crosses [budgetMs].
     */
    @JvmStatic external fun nativeBench(handle: Long, kind: Int, plies: Int, within: Float, n: Int, threads: Int, budgetMs: Double): DoubleArray?
    /** Stops a running [nativeBench] after its current decision. */
    @JvmStatic external fun nativeBenchCancel()

    private var loaded = false

    /** Loads the native library (by name on Android, or from an explicit path in host tests). */
    @Synchronized
    fun load(libraryPath: String? = null) {
        if (loaded) return
        if (libraryPath != null) System.load(libraryPath) else System.loadLibrary("ptdjni")
        loaded = true
    }

    /** Network files unpacked from the assets (copied once per app version). */
    fun files(context: Context): File {
        val dir = File(context.filesDir, "puretd")
        val stamp = File(dir, ".version")
        val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        if (stamp.takeIf { it.exists() }?.readText() != version) {
            dir.mkdirs()
            for (name in context.assets.list("puretd").orEmpty()) {
                context.assets.open("puretd/$name").use { input -> File(dir, name).outputStream().use { input.copyTo(it) } }
            }
            stamp.writeText(version)
        }
        return dir
    }
}
