package com.boldblot.backgammonplyground.engine

import com.boldblot.backgammonplyground.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What a background job is doing now, for Data and help and the top label. */
enum class WorkKind { MATCH, SET, QUIZ }

/**
 * One background job: "Match against Club Champion: 23 decisions left", with the time left ([etaMs], null until it can be
 * estimated) and [stop], which ends it (what it has not analyzed stays unanalyzed).
 */
data class WorkItem(
    val key: String, val kind: WorkKind, val label: String, val left: Int, val total: Int,
    val etaMs: Long? = null, val stop: () -> Unit,
)

/** The background jobs running now (the game's analysis, a set's, the quiz's look-ahead), each reported by its owner. */
class BackgroundWork {
    private val _items = MutableStateFlow<Map<String, WorkItem>>(emptyMap())
    val items: StateFlow<Map<String, WorkItem>> = _items.asStateFlow()

    fun put(item: WorkItem) = _items.update { it + (item.key to item) }

    fun remove(key: String) = _items.update { it - key }

    /** Stops every job (Data and help → Stop). */
    fun stopAll() {
        val all = _items.value.values.toList()
        if (all.isEmpty()) return
        AppLog.i("Background work: Stop all (${all.joinToString { "${it.label}, ${it.left} left" }})")
        all.forEach { runCatching { it.stop() }.onFailure { e -> AppLog.error("Stopping ${it.label} failed", e) } }
    }

    companion object {
        /** "about 2 min", "about 40 s". */
        fun eta(ms: Long): String = if (ms >= 90_000) "about ${(ms + 30_000) / 60_000} min" else "about ${((ms + 5_000) / 10_000).coerceAtLeast(1) * 10} s"
    }
}
