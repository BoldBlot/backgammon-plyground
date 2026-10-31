package com.boldblot.backgammonplyground.data

import com.boldblot.backgammonplyground.core.AutoLabel
import com.boldblot.backgammonplyground.core.Labels
import com.boldblot.backgammonplyground.core.MyAnswer
import com.boldblot.backgammonplyground.core.PositionAnalysis
import com.boldblot.backgammonplyground.core.StudyPosition
import kotlinx.serialization.Serializable

/** A saved position with its stored parts decoded. */
data class SavedPosition(
    val entity: PositionEntity,
    val position: StudyPosition,
    val analysis: PositionAnalysis?,
    val answer: MyAnswer?,
    /** Its labels' keys (all of them; Settings may hide some automatic ones). */
    val labels: Set<String>,
    /** What was played in the game it comes from; null = not known. */
    val played: MyAnswer? = null,
    /** The last quiz answer. */
    val lastAnswer: MyAnswer? = null,
) {
    val id: Long get() = entity.id
}

/** The file My positions exports and imports: plain JSON, readable by other programs. */
@Serializable
data class PositionsFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val categories: List<String> = emptyList(),
    val positions: List<PositionRecord> = emptyList(),
) {
    companion object {
        const val FORMAT = "plyground-positions"
    }
}

/** One position in a [PositionsFile]. */
@Serializable
data class PositionRecord(
    /** The player who decides is X; with [doubled] O has doubled them (the XGID is O's cube decision). */
    val xgid: String,
    val doubled: Boolean = false,
    val beavers: Boolean = false,
    val note: String = "",
    val source: String = "",
    val categories: List<String> = emptyList(),
    val myAnswer: MyAnswer? = null,
    val analysis: PositionAnalysis? = null,
    val labels: List<String> = emptyList(),
    /** What was played in the game it comes from. */
    val played: MyAnswer? = null,
)

/** What an imported file holds, before the player picks the categories to bring in. */
data class ImportPreview(
    val positions: List<PositionRecord>,
    /** The file's categories; positions without one go to the category the player names. */
    val categories: List<String>,
    /** Lines that are not positions (plain text files). */
    val skipped: Int = 0,
) {
    val uncategorized: Int get() = positions.count { it.categories.isEmpty() }
}

/** My positions: saved positions in categories, their analyses, the player's own answers and quiz results. */
class PositionsRepository(db: AppDatabase) {

    private val dao = db.positions()

    private fun decode(e: PositionEntity): SavedPosition? = runCatching {
        val p = StudyPosition.stored(e.xgid, e.doubled, e.beavers)
        val a = e.analysis?.let { AppJson.decodeFromString<PositionAnalysis>(it) }
        SavedPosition(
            e, p, a, e.myAnswer?.let { AppJson.decodeFromString<MyAnswer>(it) }, storedLabels(e.labels) ?: autoLabels(p, a),
            played = e.played?.let { AppJson.decodeFromString<MyAnswer>(it) } ?: playedFromAnalysis(a),
            lastAnswer = e.lastAnswer?.let { runCatching { AppJson.decodeFromString<MyAnswer>(it) }.getOrNull() },
        )
    }.getOrNull()

    /** Positions added from the blunder list before schema 4: the game's analysis knows the play made. */
    private fun playedFromAnalysis(a: PositionAnalysis?): MyAnswer? {
        val c = a?.checker?.takeIf { a.label == GAME_ANALYSIS } ?: return null
        return c.candidates.getOrNull(c.playedIndex)?.let { MyAnswer(move = it.move) }
    }

    suspend fun setPlayed(id: Long, played: MyAnswer?) {
        val e = dao.position(id) ?: return
        dao.update(e.copy(played = played?.let { AppJson.encodeToString(it) }))
    }

    /** The labels a position gets from what it is: [p] and its analysis [a] (no error size, no owner). */
    fun autoLabels(p: StudyPosition, a: PositionAnalysis?): Set<String> {
        val best = a?.checker?.candidates?.firstOrNull()
        return Labels.of(
            p.board, cubeDecision = !p.isChecker, doubled = p.doubled, matchLength = p.setup.matchLength, score = p.setup.score,
            crawford = p.setup.crawford, moves = listOfNotNull(best?.move), probs = best?.probs ?: a?.cube?.probs,
        ).mapTo(HashSet()) { it.name }
    }

    suspend fun setLabels(id: Long, labels: Set<String>) = dao.setLabels(id, encodeLabels(labels))

    /** Renames the player's own label [from] to [to] on every position. */
    suspend fun renameLabel(from: String, to: String) = editLabels { it.map { k -> if (k == Labels.myKey(from)) Labels.myKey(to) else k }.toSet() }

    /** Removes the player's own label [name] from every position. */
    suspend fun deleteLabel(name: String) = editLabels { it - Labels.myKey(name) }

    private suspend fun editLabels(change: (Set<String>) -> Set<String>) {
        for (r in dao.allLabels()) {
            val old = storedLabels(r.labels) ?: continue
            val new = change(old)
            if (new != old) dao.setLabels(r.id, encodeLabels(new))
        }
    }

    // ---- folders linked to the blunder list ------------------------------------------------------

    /** The folder linked to [filter], created (named after it) if [create] and there is none. */
    suspend fun linkedFolder(filter: Set<String>, create: Boolean): Long? {
        val key = Labels.encode(filter)
        dao.categories().firstOrNull { it.filter == key }?.let { return it.id }
        if (!create) return null
        val sort = (dao.categories().maxOfOrNull { it.sort } ?: 0) + 1
        return dao.insertCategory(CategoryEntity(name = Labels.folderName(filter), sort = sort, filter = key))
    }

    suspend fun setAutoAdd(id: Long, on: Boolean) = dao.setAutoAdd(id, on)

    /** The Checker and Cube folders new errors are sorted into ([fileErrors]): not autofilters. */
    fun isSortFolder(filter: String?): Boolean =
        filter == Labels.encode(setOf(AutoLabel.CHECKER.name)) || filter == Labels.encode(setOf(AutoLabel.CUBE.name))

    /** A new autofilter: folder [name] that takes every new error passing [filter] ([Labels.matches]). */
    suspend fun addAutofilter(name: String, filter: Set<String>): Long {
        val sort = (dao.categories().maxOfOrNull { it.sort } ?: 0) + 1
        return dao.insertCategory(CategoryEntity(name = name.trim(), sort = sort, filter = Labels.encode(filter), autoAdd = true))
    }

    /** Ends autofilter [id]: its folder stays, as an ordinary folder. */
    suspend fun removeAutofilter(id: Long) = dao.unlinkFolder(id)

    /**
     * Files [errors] in My positions: in no folder, or ([split]) checker plays in Checker and cube
     * decisions in Cube (each folder made if missing). Returns how many were added.
     */
    suspend fun fileErrors(errors: List<SavedBlunder>, split: Boolean, jacoby: suspend (Long) -> Boolean): Int {
        if (!split) return addBlunders(errors, UNCATEGORIZED, jacoby)
        var added = 0
        for (kind in listOf(AutoLabel.CHECKER, AutoLabel.CUBE)) {
            val list = errors.filter { kind.name in it.labels }
            if (list.isEmpty()) continue
            val id = linkedFolder(setOf(kind.name), create = true) ?: continue
            added += addBlunders(list, id, jacoby)
        }
        return added
    }

    /**
     * 0.7.4, for the testers' phones; remove before the production release. Renames "Checker
     * blunders" and "Cube blunders" to "Checker" and "Cube" (only names the player left as they
     * were), and ends their collecting switches. Returns whether one of them was collecting (the
     * player then gets automatic filing into them).
     */
    suspend fun migrateFolders074(): Boolean {
        var collecting = false
        for (c in dao.categories()) {
            if (!isSortFolder(c.filter)) continue
            when (c.name) {
                "Checker blunders" -> dao.renameCategory(c.id, "Checker")
                "Cube blunders" -> dao.renameCategory(c.id, "Cube")
            }
            if (c.autoAdd) {
                collecting = true
                dao.setAutoAdd(c.id, false)
            }
        }
        return collecting
    }

    /**
     * Adds [blunders] to folder [folderId] with their labels and the game's analysis; those already
     * there are left as they are. Returns how many were added to the folder.
     */
    suspend fun addBlunders(blunders: List<SavedBlunder>, folderId: Long, jacoby: suspend (Long) -> Boolean): Int {
        // [UNCATEGORIZED]: no folder; then a position already anywhere in My positions is skipped.
        val inFolder = (if (folderId == UNCATEGORIZED) dao.all() else dao.inCategory(folderId)).map { it.xgid to it.doubled }.toHashSet()
        var added = 0
        for (b in blunders) {
            val p = StudyPosition.of(b.decision, b.entity.matchLength, jacoby(b.entity.matchId)) ?: continue
            if ((p.xgid to p.doubled) in inFolder) continue
            val d = b.decision
            val analysis = if (d.checker != null || d.cubeEval != null) {
                PositionAnalysis(checker = d.checker, cube = d.cubeEval, label = GAME_ANALYSIS)
            } else null
            save(p, "Blunder list", "", listOf(folderId), analysis, labels = b.labels, played = MyAnswer.played(d))
            inFolder += p.xgid to p.doubled
            added++
        }
        return added
    }

    suspend fun categories(): List<CategoryCount> = dao.categories()

    suspend fun uncategorizedCount(): Int = dao.uncategorized()

    /** Positions of category [categoryId]; null = all, -1 = those in no category. */
    suspend fun positions(categoryId: Long?): List<SavedPosition> = when (categoryId) {
        null -> dao.all()
        UNCATEGORIZED -> dao.uncategorizedPositions()
        else -> dao.inCategory(categoryId)
    }.mapNotNull(::decode)

    /** The positions of [categoryIds] (each once; [UNCATEGORIZED] for those in none). */
    suspend fun positionsIn(categoryIds: Collection<Long>): List<SavedPosition> {
        val list = dao.inCategories(categoryIds.filter { it != UNCATEGORIZED }).toMutableList()
        if (UNCATEGORIZED in categoryIds) list += dao.uncategorizedPositions()
        return list.distinctBy { it.id }.mapNotNull(::decode)
    }

    suspend fun position(id: Long): SavedPosition? = dao.position(id)?.let(::decode)

    suspend fun categoriesOf(id: Long): List<Long> = dao.categoriesOf(id)

    suspend fun createCategory(name: String): Long {
        val sort = (dao.categories().maxOfOrNull { it.sort } ?: 0) + 1
        return dao.insertCategory(CategoryEntity(name = name.trim(), sort = sort))
    }

    suspend fun renameCategory(id: Long, name: String) = dao.renameCategory(id, name.trim())

    /** Deletes a category; its positions stay (in their other categories, or in none). */
    suspend fun deleteCategory(id: Long) {
        dao.unlinkCategory(id)
        dao.deleteCategory(id)
    }

    /** Moves a category one place up ([by] -1) or down (+1). */
    suspend fun moveCategory(id: Long, by: Int) {
        val list = dao.categories().toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + by
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        list.forEachIndexed { k, c -> if (c.sort != k) dao.setSort(c.id, k) }
    }

    /**
     * Saves [p] in [categoryIds]; a position already saved (same XGID and decision) gets the
     * categories added (and [note] if it had none) instead of a copy. Returns its id.
     */
    suspend fun save(
        p: StudyPosition, source: String, note: String, categoryIds: Collection<Long>, analysis: PositionAnalysis? = null, answer: MyAnswer? = null,
        /** Labels from the game (with the error's size and whose it was); null = from the position. */
        labels: Set<String>? = null,
        /** What was played in the game it comes from. */
        played: MyAnswer? = null,
    ): Long {
        val existing = dao.find(p.xgid, p.doubled)
        val id = if (existing != null) {
            val had = storedLabels(existing.labels)
            dao.update(
                existing.copy(
                    note = existing.note.ifBlank { note.trim() },
                    analysis = existing.analysis ?: analysis?.let { AppJson.encodeToString(it) },
                    myAnswer = existing.myAnswer ?: answer?.let { AppJson.encodeToString(it) },
                    labels = if (had == null && labels != null) encodeLabels(labels) else existing.labels,
                    played = existing.played ?: played?.let { AppJson.encodeToString(it) },
                ),
            )
            existing.id
        } else dao.insert(
            PositionEntity(
                xgid = p.xgid, doubled = p.doubled, beavers = p.beavers, note = note.trim(), source = source,
                savedAt = System.currentTimeMillis(),
                analysis = analysis?.let { AppJson.encodeToString(it) }, myAnswer = answer?.let { AppJson.encodeToString(it) },
                labels = labels?.let { encodeLabels(it) } ?: "",
                played = played?.let { AppJson.encodeToString(it) },
            ),
        )
        categoryIds.filter { it != UNCATEGORIZED }.forEach { dao.link(PositionCategoryEntity(id, it)) }
        return id
    }

    suspend fun setAnalysis(id: Long, a: PositionAnalysis) {
        val e = dao.position(id) ?: return
        dao.update(e.copy(analysis = AppJson.encodeToString(a)))
    }

    suspend fun setAnswer(id: Long, a: MyAnswer?) {
        val e = dao.position(id) ?: return
        dao.update(e.copy(myAnswer = a?.let { AppJson.encodeToString(it) }))
    }

    suspend fun setNote(id: Long, note: String) {
        val e = dao.position(id) ?: return
        dao.update(e.copy(note = note.trim()))
    }

    /** The quiz answer [answer] to position [id], [right] or not. */
    suspend fun setResult(id: Long, right: Boolean, answer: MyAnswer? = null) {
        val e = dao.position(id) ?: return
        dao.update(e.copy(lastRight = right, lastAnswer = answer?.let { AppJson.encodeToString(it) } ?: e.lastAnswer))
    }

    suspend fun setCategories(id: Long, categoryIds: Collection<Long>) {
        dao.unlinkPosition(id)
        categoryIds.filter { it != UNCATEGORIZED }.forEach { dao.link(PositionCategoryEntity(id, it)) }
    }

    /** Takes positions [ids] out of folder [folderId]; they stay in My positions. */
    suspend fun removeFromFolder(ids: Collection<Long>, folderId: Long) = ids.forEach { dao.unlink(it, folderId) }

    suspend fun remove(id: Long) {
        dao.unlinkPosition(id)
        dao.deletePosition(id)
    }

    // ---- files --------------------------------------------------------------------------------

    /** The positions of [categoryIds] (or just [positionIds]) as a [PositionsFile]. */
    suspend fun export(categoryIds: Collection<Long>, positionIds: Collection<Long> = emptyList()): String {
        val names = dao.categories().associate { it.id to it.name }
        val list = if (positionIds.isNotEmpty()) positionIds.mapNotNull { dao.position(it) } else {
            val l = dao.inCategories(categoryIds.filter { it != UNCATEGORIZED }).toMutableList()
            if (UNCATEGORIZED in categoryIds) l += dao.uncategorizedPositions()
            l.distinctBy { it.id }
        }
        val records = list.map { e ->
            val cats = dao.categoriesOf(e.id).mapNotNull { names[it] }
                .filter { positionIds.isNotEmpty() || categoryIds.any { c -> names[c] == it } }
            PositionRecord(
                xgid = e.xgid, doubled = e.doubled, beavers = e.beavers, note = e.note, source = e.source, categories = cats,
                myAnswer = e.myAnswer?.let { runCatching { AppJson.decodeFromString<MyAnswer>(it) }.getOrNull() },
                analysis = e.analysis?.let { runCatching { AppJson.decodeFromString<PositionAnalysis>(it) }.getOrNull() },
                labels = storedLabels(e.labels)?.let { Labels.sorted(it) } ?: emptyList(),
                played = e.played?.let { runCatching { AppJson.decodeFromString<MyAnswer>(it) }.getOrNull() },
            )
        }
        val file = PositionsFile(categories = records.flatMap { it.categories }.distinct(), positions = records)
        return EXPORT_JSON.encodeToString(PositionsFile.serializer(), file)
    }

    /** Reads a [PositionsFile], or plain text with one XGID or GNU ID per line. */
    fun preview(text: String): ImportPreview {
        val t = text.trim()
        if (t.startsWith("{")) {
            val f = AppJson.decodeFromString<PositionsFile>(t)
            require(f.format == PositionsFile.FORMAT) { "This is not a Backgammon PlyGround positions file." }
            val ok = f.positions.filter { runCatching { StudyPosition.stored(it.xgid, it.doubled, it.beavers) }.isSuccess }
            return ImportPreview(ok, (f.categories + ok.flatMap { it.categories }).distinct(), f.positions.size - ok.size)
        }
        var skipped = 0
        val list = t.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            runCatching { StudyPosition.parse(line) }.getOrNull()?.let { PositionRecord(xgid = it.xgid, doubled = it.doubled) }
                ?: run { skipped++; null }
        }
        return ImportPreview(list, emptyList(), skipped)
    }

    /**
     * Imports [preview]'s positions in [categories] (the file's names to bring in); positions in
     * none of the file's categories go to [otherCategory] (created if needed; null = none).
     * Returns how many positions were new.
     */
    suspend fun import(preview: ImportPreview, categories: Set<String>, otherCategory: String?): Int {
        val ids = HashMap<String, Long>()
        dao.categories().forEach { ids[it.name] = it.id }
        suspend fun categoryId(name: String): Long = ids.getOrPut(name) { createCategory(name) }
        var added = 0
        for (r in preview.positions) {
            val inFile = r.categories.filter { it in categories }
            if (r.categories.isNotEmpty() && inFile.isEmpty()) continue
            val target = if (r.categories.isEmpty()) listOfNotNull(otherCategory?.takeIf { it.isNotBlank() }) else inFile
            val p = runCatching { StudyPosition.stored(r.xgid, r.doubled, r.beavers) }.getOrNull() ?: continue
            if (dao.find(p.xgid, p.doubled) == null) added++
            save(p, r.source.ifBlank { "Imported" }, r.note, target.map { categoryId(it) }, r.analysis, r.myAnswer, r.labels.toSet().takeIf { it.isNotEmpty() }, r.played)
        }
        return added
    }

    companion object {
        /** Stands for "in no category" in lists and quizzes. */
        const val UNCATEGORIZED = -1L

        /** The analysis label of positions added from the blunder list (the game's own analysis). */
        const val GAME_ANALYSIS = "Game analysis"

        /** Stored when the player took every label off ("" = never set: the labels come from the position). */
        private const val NO_LABELS = "-"

        fun encodeLabels(labels: Set<String>): String = if (labels.isEmpty()) NO_LABELS else Labels.encode(labels)

        /** The stored labels, or null if they were never set. */
        fun storedLabels(text: String): Set<String>? = when (text) {
            "" -> null
            NO_LABELS -> emptySet()
            else -> Labels.decode(text)
        }

        private val EXPORT_JSON = kotlinx.serialization.json.Json {
            prettyPrint = true
            encodeDefaults = false
            ignoreUnknownKeys = true
        }
    }
}
