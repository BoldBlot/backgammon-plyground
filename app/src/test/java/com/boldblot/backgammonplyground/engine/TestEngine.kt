package com.boldblot.backgammonplyground.engine

import java.io.File

/** The real PureTD engine for JVM tests, from a host build of the JNI library (scripts/host-tests.sh). */
object TestEngine {
    val engine: PureTdEngine? by lazy {
        val lib = System.getProperty("bg.hostlib") ?: return@lazy null
        PureTdNative.load(lib)
        val dir = requireNotNull(System.getProperty("bg.datadir"))
        val handle = PureTdNative.nativeOpen(
            "$dir/prob5_wide.ptw", "$dir/cubeful_wide.ptw",
            File(System.getProperty("java.io.tmpdir"), "puretd-bearoff-test.bin").path, Runtime.getRuntime().availableProcessors(),
        )
        check(handle != 0L) { "could not load the PureTD networks from $dir" }
        PureTdEngine(handle)
    }
}
