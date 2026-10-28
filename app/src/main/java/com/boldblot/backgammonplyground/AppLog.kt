package com.boldblot.backgammonplyground

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small log kept on the phone (recent game events, engine problems, crashes). It never leaves the
 * phone by itself: the player sends it with "Send error report", which opens their email app with
 * the log attached and the support address filled in.
 */
object AppLog {
    private const val MAX_BYTES = 256 * 1024
    const val SUPPORT_EMAIL = "support@boldblot.com"

    private var dir: File? = null
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)

    private val file get() = dir?.let { File(it, "app.log") }

    /** Starts logging into the app's files and records crashes before Android ends the app. */
    fun init(context: Context) {
        dir = File(context.filesDir, "logs").also { it.mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            error("CRASH on ${thread.name}", e)
            previous?.uncaughtException(thread, e)
        }
        i("App started: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.BUILD_LABEL}, Play code ${BuildConfig.VERSION_CODE})")
    }

    fun i(message: String) = write("I", message)

    fun error(message: String, e: Throwable? = null) {
        val trace = e?.let { StringWriter().also { w -> it.printStackTrace(PrintWriter(w)) }.toString() }
        write("E", if (trace == null) message else "$message\n$trace")
    }

    @Synchronized
    private fun write(level: String, message: String) {
        val f = file ?: return
        runCatching {
            // Keep the newest half when the log is full.
            if (f.length() > MAX_BYTES) {
                val keep = f.readText().takeLast(MAX_BYTES / 2)
                f.writeText("[older entries removed]\n" + keep.substringAfter('\n'))
            }
            f.appendText("${stamp.format(Date())} $level $message\n")
        }
    }

    /** Opens the email app with the support address, the phone and app versions and the log attached. */
    fun sendReport(context: Context) {
        i("Error report opened")
        val out = File(context.cacheDir, "reports").also { it.mkdirs() }
        val name = "plyground-log-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())}.txt"
        val copy = File(out, name)
        out.listFiles()?.forEach { it.delete() }
        synchronized(this) { copy.writeText(file?.takeIf { it.exists() }?.readText() ?: "(empty log)\n") }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", copy)
        val body = """
            |What happened, and what did you do just before?
            |
            |
            |---
            |Backgammon PlyGround ${BuildConfig.VERSION_NAME} (build ${BuildConfig.BUILD_LABEL}, Play code ${BuildConfig.VERSION_CODE})
            |${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}
        """.trimMargin()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "Backgammon PlyGround error report (${BuildConfig.VERSION_NAME} build ${BuildConfig.BUILD_LABEL})")
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Send error report"))
    }
}
