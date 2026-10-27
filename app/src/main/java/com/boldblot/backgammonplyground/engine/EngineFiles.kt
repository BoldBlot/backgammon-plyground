package com.boldblot.backgammonplyground.engine

import android.content.Context
import java.io.File

/** The engines' data files, unpacked from the assets to files/<folder> once per app version. */
object EngineFiles {
    fun unpack(context: Context, folder: String): File {
        val dir = File(context.filesDir, folder)
        val stamp = File(dir, ".version")
        val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        if (stamp.takeIf { it.exists() }?.readText() != version) {
            copy(context, folder, dir)
            stamp.writeText(version)
        }
        return dir
    }

    private fun copy(context: Context, asset: String, dir: File) {
        dir.mkdirs()
        for (name in context.assets.list(asset).orEmpty()) {
            val path = "$asset/$name"
            // A folder lists its own entries; a file lists none.
            if (context.assets.list(path).orEmpty().isNotEmpty()) copy(context, path, File(dir, name))
            else context.assets.open(path).use { input -> File(dir, name).outputStream().use { input.copyTo(it) } }
        }
    }

    /** The one native library with all three engines. */
    private var loaded = false

    @Synchronized
    fun load(libraryPath: String? = null) {
        if (loaded) return
        if (libraryPath != null) System.load(libraryPath) else System.loadLibrary("bgjni")
        loaded = true
    }
}
