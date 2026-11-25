package com.boldblot.backgammonplyground.engine

import android.content.Context
import android.os.Process
import com.boldblot.backgammonplyground.core.AnalysisEngine
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.CpuLevel
import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.EngineKind
import com.boldblot.backgammonplyground.core.MatchEquityTable
import com.boldblot.backgammonplyground.core.PureTdNet
import com.boldblot.backgammonplyground.core.Move
import com.boldblot.backgammonplyground.core.SearchMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import com.boldblot.backgammonplyground.AppLog
import java.util.EnumMap
import java.util.concurrent.Executors

/** Who waits for a search: the limits differ (Settings → Analysis). */
enum class Waiting { ANALYSIS, PLAY }

/** What a call into the engine is for, for the analysis indicator (Settings → Data and Help → Debug). */
enum class ActivityKind { PLAY, ANALYSIS, BACKGROUND }

/** One call into the engine that has not returned: running, or waiting its turn on the engine thread. */
class EngineActivity(
    val id: Long, val kind: ActivityKind,
    /** What it is, for Data and help ("Deep dive: version 5 of 12"); from the caller's [EngineLabel]. */
    val what: String,
    /** Shown in Data and help as part of a [WorkItem] (with its own Stop), not on a line of its own. */
    val owned: Boolean,
) {
    /** The engine searching for it once it runs (null while it waits its turn). */
    @Volatile var engine: SearchEngine? = null
}

/** The name of the engine calls made under it ([kotlinx.coroutines.withContext]), and whether a [WorkItem] stands for them. */
class EngineLabel(val what: String, val owned: Boolean = false) : kotlin.coroutines.AbstractCoroutineContextElement(EngineLabel) {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<EngineLabel>
}

private class ActivityElement(val a: EngineActivity) : kotlin.coroutines.AbstractCoroutineContextElement(ActivityElement) {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<ActivityElement>
}

/**
 * A search someone waits for, as the screen shows it: what it is doing, [done] of [total] steps (0 of 0: not
 * counted), when it started ([startedAt], elapsed real time) and after how long the notice comes up.
 */
data class SearchProgress(val id: Long, val waiting: Waiting, val what: String, val done: Int, val total: Int, val startedAt: Long, val limitMs: Long)

/**
 * Owns the engines (PureTD, GNU Backgammon, Open Sage). They are not thread-safe, so every call
 * goes through [run] (or the [analysis] / [of] views called inside it), which executes on one
 * dedicated thread, the game's slot (the deeper searches spread their own work over
 * [gameThreads] cores). PureTD loads at start; the others the first time they are used.
 *
 * The background analysis has a slot of its own when PureTD analyzes ([twoSlots]): a second
 * PureTD on the same networks, on its own thread ([runBackground]) with [backgroundThreads] and
 * [backgroundLow] priority, so the game never waits for it. GNU Backgammon (globals) and Open
 * Sage (one thread pool for all its copies) can't have a second copy.
 */
class EngineService(private val context: Context) {

    /** Single engine thread (the C searches keep large buffers on the heap, not the stack). */
    val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(null, r, "engines", 4L * 1024 * 1024).apply { isDaemon = true }
    }.asCoroutineDispatcher()

    /** PureTD's network size for the game and the analysis (Advanced engine settings); the other net loads on first use. */
    @Volatile var net: PureTdNet = PureTdNet.WIDE

    /** Money checker plays scored with the cubeful network ([PureTdEngine.cubefulCheckers]), for every PureTD. */
    @Volatile var cubefulCheckers: Boolean = true

    /** One PureTD (handle and engine) per network size, opened on first use on the engine thread. */
    private class Ptd(val handle: Long, val engine: PureTdEngine)
    private val ptds = EnumMap<PureTdNet, Ptd>(PureTdNet::class.java)

    /** The active network's engine. Call after [start], on the engine thread. */
    val engine: PureTdEngine get() = ptd(net).engine
    private val ptdHandle: Long get() = ptd(net).handle
    private var sageHandle = 0L

    /** The GPU check's setting the GPU games run with: the shader's multiply-add mode and tile. */
    data class GpuSetting(val fma: Boolean, val tile: String)

    /**
     * T-Roller's games on the GPU (experimental, Advanced engine settings): the setting to use, or null (off, or no passing GPU
     * check for this app version, net and Android version). Applied on the engine thread before each search.
     */
    @Volatile var gpuWanted: GpuSetting? = null
    private var gpuOpen: Triple<PureTdNet, GpuSetting, GpuGames>? = null
    /** A setting that failed (opening, or a search): not tried again until the settings change. */
    @Volatile private var gpuFailedFor: GpuSetting? = null
    /** What the GPU path is doing, for the error report ("off", "on: Adreno 830 ...", "failed: ..."). */
    @Volatile var gpuState: String = "off"
        private set

    /**
     * Advanced engine settings: during a game ([inGame]) the GPU games belong to the background analysis, and the game's own
     * searches play their games on the CPU. Outside a game the GPU goes back to the main analysis, once the background
     * position in progress is done: one engine holds the GPU at a time, never both.
     */
    @Volatile var gpuToBackground: Boolean = false
        set(v) { field = v; settleGpu() }
    /** The game screen is open with a match in play. */
    @Volatile var inGame: Boolean = false
        set(v) { if (field != v) { field = v; settleGpu() } }

    private enum class GpuSlot { MAIN, BACKGROUND }
    private val gpuLock = Any()
    private var gpuOwner = GpuSlot.MAIN
    private var mainBusy = false
    private var backBusy = false
    /** The main search ran on the CPU because the background analysis held the GPU (logged once per spell). */
    private var mainWithoutGpu = false
    @Volatile private var backEngine: PureTdEngine? = null

    /** The slot that holds the GPU now, for the top label ("GPU" while the background analysis has it). */
    val gpuWithBackground: Boolean get() = synchronized(gpuLock) { gpuOpen != null && gpuOwner == GpuSlot.BACKGROUND }

    /** Moves the GPU games to the slot that should have them, when the slot holding them is not searching. */
    private fun settleGpu() = synchronized(gpuLock) {
        val open = gpuOpen
        val main = open?.let { ptds[it.first]?.engine }
        val back = backEngine
        if (open == null || gpuFailedFor == open.second) {
            main?.gpu = null; back?.gpu = null
            return@synchronized
        }
        val want = if (gpuToBackground && inGame && twoSlots) GpuSlot.BACKGROUND else GpuSlot.MAIN
        if (gpuOwner != want && !(if (gpuOwner == GpuSlot.MAIN) mainBusy else backBusy) && (want == GpuSlot.MAIN || back != null)) {
            gpuOwner = want
            AppLog.i("GPU games moved to the ${if (want == GpuSlot.MAIN) "main analysis" else "background analysis"} (in game: $inGame, setting: $gpuToBackground)")
        }
        main?.gpu = if (gpuOwner == GpuSlot.MAIN) open.third else null
        back?.gpu = if (gpuOwner == GpuSlot.BACKGROUND) open.third else null
        if (back != null && back.onGpuFailure == null) back.onGpuFailure = main?.onGpuFailure
    }

    private fun enterSlot(main: Boolean) = synchronized(gpuLock) {
        if (main) mainBusy = true else backBusy = true
        settleGpu()
        if (main && gpuOpen != null && gpuOwner == GpuSlot.BACKGROUND) {
            if (!mainWithoutGpu) AppLog.i("GPU games held by the background analysis: the main search plays its games on the CPU until it is done")
            mainWithoutGpu = true
        } else if (main) mainWithoutGpu = false
    }

    private fun exitSlot(main: Boolean) = synchronized(gpuLock) {
        if (main) mainBusy = false else backBusy = false
        settleGpu()
    }

    /** Opens, reopens (another net or setting) or closes the GPU games as [gpuWanted] says. On the engine thread. */
    private fun applyGpu() {
        val want = gpuWanted?.takeIf { it != gpuFailedFor }
        val cur = gpuOpen
        if (cur != null && (want == null || cur.first != net || cur.second != want)) {
            synchronized(gpuLock) {
                ptds[cur.first]?.engine?.gpu = null
                backEngine?.gpu = null
                gpuOwner = GpuSlot.MAIN
            }
            runCatching { cur.third.close() }
            gpuOpen = null
            AppLog.i("GPU games closed (${if (want == null) "off" else "net or setting changed"})")
            gpuState = "off"
        }
        if (want == null || gpuOpen != null) return
        val t0 = android.os.SystemClock.elapsedRealtime()
        if (!VkGamesNative.load()) {
            gpuFailedFor = want; gpuState = "failed: library not loaded (${VkGamesNative.loadError})"; AppLog.error("GPU games: $gpuState"); return
        }
        val dir = PureTdNative.files(context)
        VkGamesNative.nativeSetTile(want.tile)
        val vk = VkGamesNative.nativeOpen(File(dir, net.cubeless).path, File(context.filesDir, "puretd-bearoff.bin").path, cores, 16384, -1, want.fma, false)
        if (vk == 0L) { gpuFailedFor = want; gpuState = "failed: the GPU could not be opened"; AppLog.error("GPU games: $gpuState (fma ${want.fma}, tile ${want.tile}, net ${net.name})"); return }
        val device = runCatching { VkGamesNative.nativeDevice(vk) }.getOrDefault("?")
        val g = GpuGames(ptdHandle, vk, cores)
        val e = engine
        e.onGpuFailure = { why -> gpuFailedFor = want; gpuState = "failed: $why" }
        gpuOpen = Triple(net, want, g)
        settleGpu()
        gpuState = "on: $device, fma ${want.fma}, tile ${want.tile}, net ${net.name}"
        AppLog.i("GPU games opened in ${android.os.SystemClock.elapsedRealtime() - t0} ms: $gpuState")
    }

    /**
     * The GPU check (Advanced engine settings): every shader setting against the CPU code, bit for bit, and its speed, with the
     * selected net. The report's last line is "BEST fma=F tile=T" or "BEST none (...)". Runs on the engine thread (up to about a
     * minute); the GPU games are closed first.
     */
    suspend fun gpuCheck(): String = withContext(dispatcher) {
        val keep = gpuWanted
        gpuWanted = null
        applyGpu()
        gpuWanted = keep
        gpuFailedFor = null
        if (!VkGamesNative.load()) return@withContext "Library not loaded: ${VkGamesNative.loadError}\nBEST none (no library)"
        val dir = PureTdNative.files(context)
        runCatching { VkGamesNative.nativeSelfTest(File(dir, net.cubeless).path, File(context.filesDir, "puretd-bearoff.bin").path, -1) }
            .getOrElse { "GPU check failed: $it\nBEST none (error)" }
    }

    /** Cores the phone has online (Android tells any app, no permission needed). */
    val cores: Int = Runtime.getRuntime().availableProcessors()

    /** Threads for the game's searches and for the background analysis (Advanced engine settings). */
    @Volatile var gameThreads: Int = cores
    private var lastThreads = 0
    @Volatile var backgroundThreads: Int = defaultBackgroundThreads(cores)
    /** The background analysis runs at low priority: Android gives the game's searches the cores first. */
    @Volatile var backgroundLow: Boolean = true

    /** After this long a watched search offers to stop (Settings → Analysis): one-position analyses, and play. */
    @Volatile var analysisLimitMs: Long = 15_000
    @Volatile var playLimitMs: Long = 5_000
    /** [SearchEngine.gamesOnErrorsOnly] for everything but the analysis of one position (Settings → Analysis). */
    @Volatile var gamesOnErrorsOnly: Boolean = true
    @Volatile var gamesGap: Float = SearchEngine.DEFAULT_GAMES_GAP

    private val _activity = MutableStateFlow<List<EngineActivity>>(emptyList())

    /** Every call into the engine now (the computer's decisions, analyses someone waits for, background work). */
    val activity: StateFlow<List<EngineActivity>> = _activity
    private var activityId = 0L

    private suspend fun <T> tracked(kind: ActivityKind, block: suspend () -> T): T {
        val label = kotlinx.coroutines.currentCoroutineContext()[EngineLabel]
        val what = label?.what ?: when (kind) { ActivityKind.PLAY -> "The computer's move"; ActivityKind.ANALYSIS -> "Analysis"; ActivityKind.BACKGROUND -> "Background search" }
        val a = synchronized(this) { EngineActivity(++activityId, kind, what, label?.owned == true) }
        _activity.update { it + a }
        try { return withContext(ActivityElement(a)) { block() } } finally { _activity.update { it - a } }
    }

    private val _progress = MutableStateFlow<SearchProgress?>(null)
    /** The watched search running now ([run] with a [Waiting]), for the progress bar and the notice. */
    val progress: StateFlow<SearchProgress?> = _progress
    @Volatile private var watched: SearchMonitor? = null
    private var searchId = 0L

    /** Stops the watched search: it returns what it has ([com.boldblot.backgammonplyground.core.Depth.incomplete]). */
    fun stopSearch() { watched?.requestStop(); stopBackground() }

    /** The engines running a search nobody watches (the background analysis of games). */
    private val unwatched = java.util.Collections.synchronizedList(mutableListOf<SearchEngine>())

    /**
     * Makes the searches nobody watches return at once with what they have (marked incomplete): the background analysis
     * of a game that is dropped, or the Stop of the dialog that says a search takes too long.
     */
    fun stopBackground() { synchronized(unwatched) { unwatched.toList() }.forEach { it.stopNow() } }

    /**
     * Runs a search nobody watches. When its caller is cancelled (left the screen, Stop on a job) the search stops at once
     * instead of running to its end unseen.
     */
    private suspend fun <T> unwatchedRun(e: SearchEngine, block: SearchEngine.() -> T): T {
        val a = kotlinx.coroutines.currentCoroutineContext()[ActivityElement]?.a
        a?.engine = e
        unwatched += e
        try {
            return coroutineScope {
                var finished = false
                val guard = launch(Dispatchers.Default) {
                    try { kotlinx.coroutines.awaitCancellation() } finally {
                        if (!finished) { AppLog.i("Search stopped: its caller went away (${a?.what ?: "background search"})"); e.stopNow() }
                    }
                }
                try { e.block() } finally { finished = true; guard.cancel() }
            }
        } finally { unwatched -= e; a?.engine = null }
    }

    /** Stop on a line of Data and help: that search returns at once with what it has (the watched one as [stopSearch]). */
    fun stopActivity(a: EngineActivity) {
        AppLog.i("Data and help: Stop on \"${a.what}\" (${a.kind})")
        if (a.kind == ActivityKind.BACKGROUND) a.engine?.stopNow() else stopSearch()
    }

    /** The background analysis has its own slot (only PureTD can have a second copy). */
    val twoSlots: Boolean get() = analysisKind == EngineKind.PURETD

    /** The background slot's thread and its PureTD (on the game PureTD's networks). */
    private val backgroundDispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(null, r, "analysis", 4L * 1024 * 1024).apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val backgrounds = EnumMap<PureTdNet, Ptd>(PureTdNet::class.java)

    private val engines = EnumMap<EngineKind, SearchEngine>(EngineKind::class.java)

    private val ready = CompletableDeferred<Unit>()

    /** The engine that analyzes (Settings → Analysis), for [analysis]. */
    @Volatile var analysisKind: EngineKind = EngineKind.PURETD

    /** [kind]'s engine, loaded on first use. Call on the engine thread. */
    fun get(kind: EngineKind): SearchEngine = if (kind == EngineKind.PURETD) engine.also { it.cubefulCheckers = cubefulCheckers } else engines.getOrPut(kind) {
        when (kind) {
            EngineKind.PURETD -> error("PureTD is opened by ptd()")
            EngineKind.GNUBG -> GnubgEngine.open(GnubgNative.files(context).path)
            EngineKind.SAGE -> {
                val dir = SageNative.files(context)
                val handle = SageNative.nativeOpen(
                    SageNative.NETWORKS.map { File(dir, it).path }.toTypedArray(), SageNative.HIDDEN,
                    File(dir, SageNative.BEAROFF).path, Runtime.getRuntime().availableProcessors(),
                )
                check(handle != 0L) { "Open Sage engine failed to load its networks" }
                sageHandle = handle
                SageEngine(handle)
            }
        }
    }

    /**
     * [kind]'s engine for objects created before it has loaded (a screen restored straight into a
     * game): each call looks the engine up when it is made. Calls go through [run], which waits for it.
     */
    fun view(kind: () -> EngineKind): AnalysisEngine = object : AnalysisEngine {
        private val e get() = get(kind())
        override val name: String get() = kind().label
        override fun rankMoves(board: Board, ctx: CubeContext, d1: Int, d2: Int, plies: Int, played: Move?) = e.rankMoves(board, ctx, d1, d2, plies, played)
        override fun chooseMove(board: Board, ctx: CubeContext, d1: Int, d2: Int, level: CpuLevel) = e.chooseMove(board, ctx, d1, d2, level)
        override fun bestPlayChances(board: Board, ctx: CubeContext, d1: Int, d2: Int) = e.bestPlayChances(board, ctx, d1, d2)
        override fun cubeDecision(board: Board, ctx: CubeContext, plies: Int) = e.cubeDecision(board, ctx, plies)
        override fun cubeDecisionAtLevel(board: Board, ctx: CubeContext, level: CpuLevel) = e.cubeDecisionAtLevel(board, ctx, level)
        override fun cachedCube(board: Board, ctx: CubeContext, plies: Int) = e.cachedCube(board, ctx, plies)
        override fun luck(board: Board, ctx: CubeContext, d1: Int, d2: Int, firstRoll: Boolean) = e.luck(board, ctx, d1, d2, firstRoll)
        override fun positionId(board: Board, onRoll: Int) = e.positionId(board, onRoll)
        override fun matchId(ctx: CubeContext, d1: Int, d2: Int) = e.matchId(ctx, d1, d2)
    }

    /** The analysis engine ([analysisKind]). */
    val analysis: AnalysisEngine = view { analysisKind }

    /** [kind]'s engine (the computer's in a match, or one chosen for an analysis). */
    fun of(kind: EngineKind): AnalysisEngine = view { kind }

    /** Extracts data files (first launch / after an update) and initializes PureTD. */
    suspend fun start() {
        if (ready.isCompleted) return
        withContext(dispatcher) {
            if (ready.isCompleted) return@withContext
            PureTdNative.load()
            ptd(net)
            ready.complete(Unit)
        }
    }

    /** [n]'s PureTD, opened on first use (the wide nets' bear-off database is built once, about a second, and cached). */
    @Synchronized private fun ptd(n: PureTdNet): Ptd = ptds.getOrPut(n) {
        val dir = PureTdNative.files(context)
        val handle = PureTdNative.nativeOpen(
            File(dir, n.cubeless).path, File(dir, n.cubeful).path,
            File(context.filesDir, "puretd-bearoff.bin").path, Runtime.getRuntime().availableProcessors(),
        )
        check(handle != 0L) { "PureTD engine failed to load its networks" }
        Ptd(handle, PureTdEngine(handle, netName = n.engineName))
    }

    /**
     * Runs [block] on the engine thread with [kind]'s engine (the analysis engine by default). With [waiting]
     * (someone waits for the result) the search is watched: it shows in [progress], can be stopped
     * ([stopSearch]) and stops when the caller is cancelled.
     */
    suspend fun <T> run(kind: EngineKind? = null, waiting: Waiting? = null, block: SearchEngine.() -> T): T =
        tracked(when (waiting) { Waiting.PLAY -> ActivityKind.PLAY; Waiting.ANALYSIS -> ActivityKind.ANALYSIS; null -> ActivityKind.BACKGROUND }) {
            runTracked(kind, waiting, block)
        }

    private suspend fun <T> runTracked(kind: EngineKind?, waiting: Waiting?, block: SearchEngine.() -> T): T {
        start()
        return withContext(dispatcher) {
            // The limit applies only while a game is in play; everything else gets every core (0.9.24).
            val threads = (if (inGame) gameThreads else cores).coerceIn(1, cores)
            if (threads != lastThreads) { lastThreads = threads; AppLog.i("Main searches: $threads threads (${if (inGame) "in a game" else "outside a game"})") }
            PureTdNative.nativeSetThreads(ptdHandle, threads)
            runCatching { applyGpu() }.onFailure { AppLog.error("GPU games could not be set up", it) }
            enterSlot(main = true)
            try { onEngineThread(kind, waiting, threads, block) } finally { exitSlot(main = true) }
        }
    }

    private suspend fun <T> onEngineThread(kind: EngineKind?, waiting: Waiting?, threads: Int, block: SearchEngine.() -> T): T {
            if (sageHandle != 0L) SageNative.nativeSetThreads(sageHandle, threads)
            val e = get(kind ?: analysisKind)
            // The analysis of one position always plays its games out.
            e.gamesOnErrorsOnly = gamesOnErrorsOnly && waiting != Waiting.ANALYSIS
            e.gamesGap = gamesGap
            (e as? PureTdEngine)?.let { it.gameThreads = threads; it.workerInit = { Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT) } }
            if (waiting == null) return unwatchedRun(e, block)
            val mon = SearchMonitor()
            val id = ++searchId
            val started = android.os.SystemClock.elapsedRealtime()
            val limit = if (waiting == Waiting.PLAY) playLimitMs else analysisLimitMs
            return coroutineScope {
                var finished = false
                val ticker = launch(Dispatchers.Default) {
                    try {
                        while (true) {
                            _progress.value = SearchProgress(id, waiting, mon.what, mon.done, mon.total, started, limit)
                            delay(150)
                        }
                    } finally {
                        // The caller went away (left the screen): nobody waits for the result any more.
                        if (!finished) mon.requestStop()
                    }
                }
                e.monitor = mon
                watched = mon
                try { e.block() } finally {
                    finished = true
                    e.monitor = null
                    watched = null
                    ticker.cancel()
                    _progress.value = null
                }
            }
    }

    /**
     * Runs [block] in the background analysis slot ([twoSlots] only): the second PureTD, on its own
     * thread. Its search threads start from that thread, so they share its priority.
     */
    suspend fun <T> runBackground(block: SearchEngine.() -> T): T = tracked(ActivityKind.BACKGROUND) { runBackgroundTracked(block) }

    private suspend fun <T> runBackgroundTracked(block: SearchEngine.() -> T): T {
        start()
        return withContext(backgroundDispatcher) {
            Process.setThreadPriority(if (backgroundLow) Process.THREAD_PRIORITY_BACKGROUND else Process.THREAD_PRIORITY_DEFAULT)
            val threads = backgroundThreads.coerceIn(1, cores)
            val n = net
            val b = backgrounds[n] ?: openBackground(n, threads)
            PureTdNative.nativeSetThreads(b.handle, threads)
            b.engine.cubefulCheckers = cubefulCheckers
            b.engine.gamesOnErrorsOnly = gamesOnErrorsOnly
            b.engine.gamesGap = gamesGap
            b.engine.gameThreads = threads
            val priority = if (backgroundLow) Process.THREAD_PRIORITY_BACKGROUND else Process.THREAD_PRIORITY_DEFAULT
            b.engine.workerInit = { Process.setThreadPriority(priority) }
            if (backEngine !== b.engine) { synchronized(gpuLock) { backEngine?.gpu = null; backEngine = b.engine } }
            enterSlot(main = false)
            try { unwatchedRun(b.engine, block) } finally { exitSlot(main = false) }
        }
    }

    private fun openBackground(n: PureTdNet, threads: Int): Ptd {
        val handle = PureTdNative.nativeShare(ptd(n).handle, threads)
        check(handle != 0L) { "PureTD background engine failed" }
        // Its own match equity table: the table fills a cache as it goes, which two threads can't share.
        return Ptd(handle, PureTdEngine(handle, MatchEquityTable(), n.engineName)).also { backgrounds[n] = it }
    }

    companion object {
        /** The background analysis' default: a quarter of the cores, at least 1. */
        fun defaultBackgroundThreads(cores: Int) = (cores / 4).coerceAtLeast(1)
    }
}
