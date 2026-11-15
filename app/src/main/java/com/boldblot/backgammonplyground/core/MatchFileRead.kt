package com.boldblot.backgammonplyground.core

/** A match file read for import into the Match Library: its [match], and the analysis an .xg file carries ([xg]). */
class MatchFileRead(val name: String, val match: MatImport.ParsedMatch, val xg: XgImport.Parsed?) {
    companion object {
        /** Reads [bytes]: an eXtreme Gammon .xg file, or the text of a .mat file. */
        fun read(name: String, bytes: ByteArray): Result<MatchFileRead> = runCatching {
            if (XgImport.isXg(bytes)) XgImport.parse(bytes).let { MatchFileRead(name, it.match, it) }
            else MatchFileRead(name, MatImport.parse(String(bytes)), null)
        }

        /** The player names found in every one of [files] (in the first file's order): who "you" can be in all of them. */
        fun commonNames(files: List<MatchFileRead>): List<String> {
            if (files.isEmpty()) return emptyList()
            val all = files.drop(1).fold(files.first().match.names.toSet()) { acc, f -> acc intersect f.match.names.toSet() }
            return files.first().match.names.filter { it in all }.distinct()
        }

        /** The message after importing several files: what was imported, games left out, and files that failed. */
        fun summary(done: List<Pair<MatchFileRead, MatImport.ImportedMatch>>, failed: List<String>): String = buildString {
            val games = done.sumOf { it.second.games.size }
            val total = done.size + failed.size
            append("Imported ${if (failed.isEmpty()) "" else "${done.size} of "}$total match${if (total == 1) "" else "es"}")
            append(", $games game${if (games == 1) "" else "s"}.")
            val xg = done.count { it.first.xg != null }
            if (done.isNotEmpty()) append(
                when {
                    xg == 0 -> " They are analyzed in the background."
                    xg == done.size -> " They come with eXtreme Gammon's analysis; decisions without one are analyzed in the background."
                    else -> " Those from .xg files come with eXtreme Gammon's analysis; the rest, and decisions without one, are analyzed in the background."
                },
            )
            for ((f, im) in done) for ((n, why) in im.skipped) append("\n${f.name}: game $n was left out: $why")
            if (failed.isNotEmpty()) append("\n\nNot imported:\n" + failed.joinToString("\n"))
        }
    }
}
