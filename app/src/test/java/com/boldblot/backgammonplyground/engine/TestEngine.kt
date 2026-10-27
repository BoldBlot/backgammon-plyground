package com.boldblot.backgammonplyground.engine

import java.io.File

/** The real engines for JVM tests, from a host build of the JNI library (scripts/host-tests.sh). */
object TestEngine {
    private val lib: String? by lazy { System.getProperty("bg.hostlib")?.also { EngineFiles.load(it) } }

    /** The app's assets folder (bg.datadir is its puretd folder). */
    private val assets: File get() = requireNotNull(File(requireNotNull(System.getProperty("bg.datadir"))).parentFile)

    val engine: PureTdEngine? by lazy {
        lib ?: return@lazy null
        val dir = File(assets, "puretd")
        val handle = PureTdNative.nativeOpen(
            "$dir/prob5_wide.ptw", "$dir/cubeful_wide.ptw",
            File(System.getProperty("java.io.tmpdir"), "puretd-bearoff-test.bin").path, Runtime.getRuntime().availableProcessors(),
        )
        check(handle != 0L) { "could not load the PureTD networks from $dir" }
        PureTdEngine(handle)
    }

    val gnubg: GnubgEngine? by lazy {
        lib ?: return@lazy null
        GnubgEngine.open(File(assets, "gnubg").path)
    }

    val sage: SageEngine? by lazy {
        lib ?: return@lazy null
        val dir = File(assets, "sage")
        val handle = SageNative.nativeOpen(
            SageNative.NETWORKS.map { File(dir, it).path }.toTypedArray(), SageNative.HIDDEN,
            File(dir, SageNative.BEAROFF).path, Runtime.getRuntime().availableProcessors(),
        )
        check(handle != 0L) { "could not load the Open Sage networks from $dir" }
        SageEngine(handle)
    }
}
