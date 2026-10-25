package com.boldblot.backgammonplyground.ui.review

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.boldblot.backgammonplyground.core.GameState
import com.boldblot.backgammonplyground.core.MatExport
import com.boldblot.backgammonplyground.core.MatchRules
import com.boldblot.backgammonplyground.core.Opponent
import com.boldblot.backgammonplyground.data.GameEntity
import com.boldblot.backgammonplyground.data.MatchEntity
import java.io.File

/** Sharing and saving exported .mat files. */
object MatFiles {
    /** Not text/plain: document providers would append ".txt" to the file name. */
    const val MIME = "application/octet-stream"

    /** Opens the share sheet for [content] as a file called [name]. */
    fun share(context: Context, name: String, content: String) {
        val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, name).also { it.writeText(content) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Export $name"))
    }

    /**
     * A match, or the games of it in [only], in the .mat format read by eXtreme Gammon and GNU
     * Backgammon: file name and content.
     */
    fun matchFile(match: MatchEntity, games: List<Pair<GameEntity, GameState>>, only: Pair<GameEntity, GameState>? = null): Pair<String, String> {
        val selected = if (only != null) listOf(only) else games
        val info = MatExport.Info(
            rules = MatchRules(match.length, match.useCube, match.jacoby, match.crawford),
            names = listOf("Player", "Backgammon PlyGround ${Opponent.label(match.cpuLevel)}"), // by player index: human, computer
            startedAt = match.startedAt,
        )
        val out = selected.map { (e, g) -> MatExport.Game(e.number, listOf(e.scoreBeforeHuman, e.scoreBeforeCpu), g) }
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.ROOT).format(java.util.Date(match.startedAt))
        val name = "PlyGround-$stamp-" + if (only == null) "match.mat" else "game${out.first().number}.mat"
        return name to MatExport.write(info, out)
    }

    /** Several files, named uniquely (two matches started in the same minute get the match id added). */
    fun unique(files: List<Pair<String, String>>): List<Pair<String, String>> {
        val seen = HashSet<String>()
        return files.mapIndexed { i, (name, text) ->
            val n = if (seen.add(name)) name else name.removeSuffix(".mat") + "-${i + 1}.mat".also { seen.add(it) }
            n to text
        }
    }

    /** Opens the share sheet for several files at once. */
    fun shareMany(context: Context, files: List<Pair<String, String>>) {
        if (files.size == 1) return share(context, files[0].first, files[0].second)
        val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val uris = ArrayList<Uri>()
        for ((name, content) in files) {
            val file = File(dir, name).also { it.writeText(content) }
            uris += FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = MIME
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "Backgammon PlyGround: ${files.size} matches")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Export ${files.size} matches"))
    }

    /** Writes the files into a folder the user picked with the system folder picker. Returns how many were written. */
    fun saveToFolder(context: Context, tree: Uri, files: List<Pair<String, String>>): Int {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        var n = 0
        for ((name, content) in files) {
            val doc = runCatching { DocumentsContract.createDocument(context.contentResolver, parent, MIME, name) }.getOrNull() ?: continue
            context.contentResolver.openOutputStream(doc, "wt")?.use { it.write(content.toByteArray()); n++ }
        }
        return n
    }

    /** Writes [content] to a document the user created with the system file picker. */
    fun save(context: Context, uri: Uri, content: String) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray()) }
    }
}
