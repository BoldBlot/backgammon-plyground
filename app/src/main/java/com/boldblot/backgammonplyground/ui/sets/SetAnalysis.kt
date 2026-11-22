package com.boldblot.backgammonplyground.ui.sets

import com.boldblot.backgammonplyground.AppLog
import com.boldblot.backgammonplyground.PlyGroundApp
import com.boldblot.backgammonplyground.core.StudyPosition
import com.boldblot.backgammonplyground.data.PositionSetsRepository
import com.boldblot.backgammonplyground.ui.positions.analyzeInBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A set's background analysis: [left] of the [total] positions asked for still to do. */
data class SetProgress(val left: Int, val total: Int)

/**
 * The bookkeeping of [SetAnalysis]: set position ids waiting, in the order asked, each with its set, and per set how many
 * were asked for since its queue was last empty.
 */
class SetQueue {
    private val queue = LinkedHashMap<Long, Long>()
    private val totals = HashMap<Long, Int>()

    /** Adds [positionIds] of [setId]; those already waiting keep their place. Returns how many were new. */
    @Synchronized
    fun add(setId: Long, positionIds: List<Long>): Int {
        val fresh = positionIds.distinct().filter { it !in queue }
        fresh.forEach { queue[it] = setId }
        if (fresh.isNotEmpty()) totals[setId] = (totals[setId] ?: 0) + fresh.size
        return fresh.size
    }

    /** Drops what [setId] has waiting. */
    @Synchronized
    fun stop(setId: Long) {
        queue.entries.removeAll { it.value == setId }
        totals.remove(setId)
    }

    /** The next position and its set, or null when nothing waits. */
    @Synchronized
    fun next(): Pair<Long, Long>? = queue.entries.firstOrNull()?.let { it.key to it.value }

    /** [positionId] is done (or failed): out of the queue; its set's count ends when the set has nothing left. */
    @Synchronized
    fun finished(positionId: Long) {
        val setId = queue.remove(positionId) ?: return
        if (queue.values.none { it == setId }) totals.remove(setId)
    }

    @Synchronized
    fun progress(): Map<Long, SetProgress> = totals.mapValues { (set, total) -> SetProgress(queue.values.count { it == set }, total) }
}

/**
 * The background analysis of sets' positions (on import, Analyze in a set): one position at a time, at the depths of
 * Settings → Analysis, in the background analysis slot ([analyzeInBackground]); each analysis is kept in its set.
 * The queue lives as long as the app; positions it did not reach stay unanalyzed (Analyze the rest).
 */
class SetAnalysis(private val app: PlyGroundApp) {

    private val queue = SetQueue()
    private var job: Job? = null

    private val _progress = MutableStateFlow<Map<Long, SetProgress>>(emptyMap())
    /** Per set being analyzed: how many are left. */
    val progress: StateFlow<Map<Long, SetProgress>> = _progress.asStateFlow()

    private val _done = MutableStateFlow(0)
    /** Counts the analyses finished: lists showing a set reload when it changes. */
    val done: StateFlow<Int> = _done.asStateFlow()

    /** Queues [positionIds] of set [setId] (those already waiting keep their place). */
    @Synchronized
    fun add(setId: Long, positionIds: List<Long>) {
        if (queue.add(setId, positionIds) == 0) return
        _progress.value = queue.progress()
        // The worker clears [job] under this lock when it finds nothing left, so a position added then starts a new one.
        if (job == null) job = app.appScope.launch { work() }
    }

    /** Drops what set [setId] still has waiting (the position being analyzed finishes). */
    fun stop(setId: Long) {
        queue.stop(setId)
        _progress.value = queue.progress()
    }

    private suspend fun work() {
        while (true) {
            val (id, setId) = synchronized(this) { queue.next() ?: run { job = null; null } } ?: break
            try {
                val e = app.sets.position(id)
                if (e != null) {
                    val s = app.settings.settings.first()
                    val t0 = android.os.SystemClock.elapsedRealtime()
                    // The move played (when the set has it) is searched as deeply as the best play.
                    val played = e.played?.let { runCatching { com.boldblot.backgammonplyground.data.AppJson.decodeFromString<com.boldblot.backgammonplyground.core.MyAnswer>(it) }.getOrNull()?.move }
                    val a = analyzeInBackground(app, StudyPosition.stored(e.xgid, e.doubled, e.beavers), s, listOfNotNull(played))
                    app.sets.setAnalysis(PositionSetsRepository.cardId(id), a)
                    AppLog.i("Set analysis: position $id of set $setId in ${android.os.SystemClock.elapsedRealtime() - t0} ms: ${a.label}")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLog.error("Set analysis of position $id failed", e)
            }
            queue.finished(id)
            _progress.value = queue.progress()
            _done.update { it + 1 }
        }
    }
}
