package com.boldblot.backgammonplyground.data

import com.boldblot.backgammonplyground.core.MyAnswer
import com.boldblot.backgammonplyground.core.PositionAnalysis
import com.boldblot.backgammonplyground.core.PositionSeries
import com.boldblot.backgammonplyground.core.StudyPosition

/**
 * The sets of the Match Library (series of positions imported together). A set's positions are not in My
 * positions; for lists and the quiz they are [SavedPosition]s with ids below zero (minus their own id).
 */
class PositionSetsRepository(db: AppDatabase) {

    private val dao = db.sets()

    suspend fun sets(): List<SetCount> = dao.sets()

    suspend fun set(id: Long): PositionSetEntity? = dao.set(id)

    /** Creates a set of [items] in their order; returns its id (the positions' ids: [positionIds]). */
    suspend fun create(name: String, items: List<PositionSeries.Item>, fromMatch: Boolean, bottomName: String, topName: String, matchLength: Int): Long =
        dao.create(
            PositionSetEntity(
                name = name.trim().ifBlank { "Set" }, createdAt = System.currentTimeMillis(), fromMatch = fromMatch,
                bottomName = bottomName.trim().takeIf { fromMatch }.orEmpty(), topName = topName.trim().takeIf { fromMatch }.orEmpty(),
                matchLength = if (fromMatch) matchLength else 0,
            ),
            items.mapIndexed { i, it ->
                SetPositionEntity(
                    setId = 0, number = i + 1, xgid = it.position.xgid, doubled = it.position.doubled, beavers = it.position.beavers,
                    bottomDecides = it.bottomDecides,
                    played = it.played?.let { m -> AppJson.encodeToString(MyAnswer(move = m)) },
                )
            },
        )

    suspend fun update(s: PositionSetEntity) = dao.updateSet(s)

    suspend fun position(id: Long): SetPositionEntity? = dao.position(id)

    /** The ids of the set's positions, in order; [unanalyzed]: only those without an analysis. */
    suspend fun positionIds(setId: Long, unanalyzed: Boolean = false): List<Long> =
        dao.positions(setId).filter { !unanalyzed || it.analysis == null }.map { it.id }

    suspend fun delete(id: Long) = dao.deleteSet(id)

    /** The set's positions in order, as list rows and quiz cards (the line with the number in [PositionEntity.note]). */
    suspend fun cards(setId: Long): List<SavedPosition> {
        val set = dao.set(setId) ?: return emptyList()
        return dao.positions(setId).mapNotNull { card(set, it) }
    }

    private fun card(set: PositionSetEntity, e: SetPositionEntity): SavedPosition? = runCatching {
        val p = StudyPosition.stored(e.xgid, e.doubled, e.beavers)
        SavedPosition(
            entity = PositionEntity(
                id = cardId(e.id), xgid = e.xgid, doubled = e.doubled, beavers = e.beavers, savedAt = 0L, source = set.name,
                note = PositionSeries.line(e.number, p, e.bottomDecides, set.fromMatch, set.bottomName, set.topName),
                lastRight = e.lastRight, lastNeutral = e.lastNeutral,
            ),
            position = p,
            analysis = e.analysis?.let { runCatching { AppJson.decodeFromString<PositionAnalysis>(it) }.getOrNull() },
            answer = null, labels = emptySet(),
            lastAnswer = e.lastAnswer?.let { runCatching { AppJson.decodeFromString<MyAnswer>(it) }.getOrNull() },
            played = e.played?.let { runCatching { AppJson.decodeFromString<MyAnswer>(it) }.getOrNull() },
        )
    }.getOrNull()

    /** Keeps the analysis of the card [cardId] (a set quiz analyzed it). */
    suspend fun setAnalysis(cardId: Long, a: PositionAnalysis) = dao.setAnalysis(positionId(cardId), AppJson.encodeToString(a))

    /** Keeps the last set quiz answer to the card [cardId]. */
    suspend fun setLast(cardId: Long, verdict: QuizVerdict, answer: MyAnswer) =
        dao.setLast(positionId(cardId), verdict == QuizVerdict.RIGHT, verdict == QuizVerdict.NEUTRAL, AppJson.encodeToString(answer))

    /** Marks every kept analysis obsolete (the searches changed); returns how many changed. */
    suspend fun markAllObsolete(): Int {
        var n = 0
        for (e in dao.analyzed()) {
            val a = e.analysis?.let { runCatching { AppJson.decodeFromString<PositionAnalysis>(it) }.getOrNull() } ?: continue
            val marked = a.markedObsolete() ?: continue
            dao.updatePosition(e.copy(analysis = AppJson.encodeToString(marked)))
            n++
        }
        return n
    }

    companion object {
        fun cardId(positionId: Long): Long = -positionId
        fun positionId(cardId: Long): Long = -cardId
    }
}
