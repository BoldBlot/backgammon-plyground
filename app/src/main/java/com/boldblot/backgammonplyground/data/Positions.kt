package com.boldblot.backgammonplyground.data

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
        SavedPosition(
            e, StudyPosition.stored(e.xgid, e.doubled, e.beavers),
            e.analysis?.let { AppJson.decodeFromString<PositionAnalysis>(it) },
            e.myAnswer?.let { AppJson.decodeFromString<MyAnswer>(it) },
        )
    }.getOrNull()

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
    suspend fun save(p: StudyPosition, source: String, note: String, categoryIds: Collection<Long>, analysis: PositionAnalysis? = null, answer: MyAnswer? = null): Long {
        val existing = dao.find(p.xgid, p.doubled)
        val id = if (existing != null) {
            dao.update(
                existing.copy(
                    note = existing.note.ifBlank { note.trim() },
                    analysis = existing.analysis ?: analysis?.let { AppJson.encodeToString(it) },
                    myAnswer = existing.myAnswer ?: answer?.let { AppJson.encodeToString(it) },
                ),
            )
            existing.id
        } else dao.insert(
            PositionEntity(
                xgid = p.xgid, doubled = p.doubled, beavers = p.beavers, note = note.trim(), source = source,
                savedAt = System.currentTimeMillis(),
                analysis = analysis?.let { AppJson.encodeToString(it) }, myAnswer = answer?.let { AppJson.encodeToString(it) },
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

    suspend fun setResult(id: Long, right: Boolean) {
        val e = dao.position(id) ?: return
        dao.update(e.copy(lastRight = right))
    }

    suspend fun setCategories(id: Long, categoryIds: Collection<Long>) {
        dao.unlinkPosition(id)
        categoryIds.filter { it != UNCATEGORIZED }.forEach { dao.link(PositionCategoryEntity(id, it)) }
    }

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
            save(p, r.source.ifBlank { "Imported" }, r.note, target.map { categoryId(it) }, r.analysis, r.myAnswer)
        }
        return added
    }

    companion object {
        /** Stands for "in no category" in lists and quizzes. */
        const val UNCATEGORIZED = -1L

        private val EXPORT_JSON = kotlinx.serialization.json.Json {
            prettyPrint = true
            encodeDefaults = false
            ignoreUnknownKeys = true
        }
    }
}
