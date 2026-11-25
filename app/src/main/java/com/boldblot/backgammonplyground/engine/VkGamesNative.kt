package com.boldblot.backgammonplyground.engine

/**
 * JNI of engine/vkgames (libvkgames.so): one turn of many played-out games with PureTD's network on the GPU (Vulkan), and the
 * GPU check. Experimental (0.9.21); loaded only when the GPU path is used ([load]).
 */
internal object VkGamesNative {
    @Volatile private var loaded: Boolean? = null
    /** Why the library could not be loaded (for the log and the error report), or null. */
    @Volatile var loadError: String? = null
        private set

    /** Loads libvkgames.so once; false when this phone has no such library or no Vulkan. */
    @Synchronized
    fun load(): Boolean {
        loaded?.let { return it }
        val ok = runCatching { System.loadLibrary("vkgames") }.onFailure { loadError = it.toString() }.isSuccess
        loaded = ok
        return ok
    }

    /** Host tests: the library from a file. */
    @Synchronized
    fun loadFrom(path: String): Boolean {
        val ok = runCatching { System.load(path) }.onFailure { loadError = it.toString() }.isSuccess
        loaded = ok
        return ok
    }

    /** device -1 = the first suitable GPU; [cpu] = the network on the CPU threads (same arithmetic). 0 on failure. */
    @JvmStatic external fun nativeOpen(net: String, bearoff: String, threads: Int, maxRows: Int, device: Int, fma: Boolean, cpu: Boolean): Long
    @JvmStatic external fun nativeDevice(h: Long): String
    /** states int[29 n], weights float[10 n], dice int[2 n] -> float[205 n] (layout in vkgames.c), null on a GPU error. */
    @JvmStatic external fun nativeStep(h: Long, states: IntArray, weights: FloatArray, dice: IntArray, n: Int): FloatArray?
    /** ms in moves, network, pick; rows. */
    @JvmStatic external fun nativeStats(h: Long): DoubleArray
    @JvmStatic external fun nativeClose(h: Long)
    /** The GPU check: every shader setting against the CPU code, bit for bit, and its speed. Report; last line "BEST fma=F tile=T" or "BEST none ...". */
    @JvmStatic external fun nativeSelfTest(net: String, bearoff: String, device: Int): String
    /** The tile ("88", "48", "84", "44") the next [nativeOpen] uses: the GPU check's choice. */
    @JvmStatic external fun nativeSetTile(tile: String)
}
