package com.boldblot.backgammonplyground.engine

import android.content.Context
import com.boldblot.backgammonplyground.core.AnalysisEngine
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.CpuLevel
import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.EngineKind
import com.boldblot.backgammonplyground.core.Move
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.EnumMap
import java.util.concurrent.Executors

/**
 * Owns the engines (PureTD, GNU Backgammon, Open Sage). They are not thread-safe, so every call
 * goes through [run] (or the [analysis] / [of] views called inside it), which executes on one
 * dedicated thread (the deeper searches spread their own work over all cores). PureTD loads at
 * start; the others the first time they are used.
 */
class EngineService(private val context: Context) {

    /** Single engine thread (the C searches keep large buffers on the heap, not the stack). */
    val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(null, r, "engines", 4L * 1024 * 1024).apply { isDaemon = true }
    }.asCoroutineDispatcher()

    lateinit var engine: PureTdEngine
        private set

    private val engines = EnumMap<EngineKind, SearchEngine>(EngineKind::class.java)

    private val ready = CompletableDeferred<Unit>()

    /** The engine that analyzes (Settings → Analysis), for [analysis]. */
    @Volatile var analysisKind: EngineKind = EngineKind.PURETD

    /** [kind]'s engine, loaded on first use. Call on the engine thread. */
    fun get(kind: EngineKind): SearchEngine = engines.getOrPut(kind) {
        when (kind) {
            EngineKind.PURETD -> engine
            EngineKind.GNUBG -> GnubgEngine.open(GnubgNative.files(context).path)
            EngineKind.SAGE -> {
                val dir = SageNative.files(context)
                val handle = SageNative.nativeOpen(
                    SageNative.NETWORKS.map { File(dir, it).path }.toTypedArray(), SageNative.HIDDEN,
                    File(dir, SageNative.BEAROFF).path, Runtime.getRuntime().availableProcessors(),
                )
                check(handle != 0L) { "Open Sage engine failed to load its networks" }
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
        override fun rankMoves(board: Board, ctx: CubeContext, d1: Int, d2: Int, plies: Int, played: Move?) = e.rankMoves(board, ctx, d1, d2, plies, played)
        override fun chooseMove(board: Board, ctx: CubeContext, d1: Int, d2: Int, level: CpuLevel) = e.chooseMove(board, ctx, d1, d2, level)
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
            val dir = PureTdNative.files(context)
            // Wide networks; the bear-off database is built once (about a second) and cached.
            val handle = PureTdNative.nativeOpen(
                File(dir, "prob5_wide.ptw").path, File(dir, "cubeful_wide.ptw").path,
                File(context.filesDir, "puretd-bearoff.bin").path, Runtime.getRuntime().availableProcessors(),
            )
            check(handle != 0L) { "PureTD engine failed to load its networks" }
            engine = PureTdEngine(handle)
            ready.complete(Unit)
        }
    }

    /** Runs [block] on the engine thread with [kind]'s engine (the analysis engine by default). */
    suspend fun <T> run(kind: EngineKind? = null, block: SearchEngine.() -> T): T {
        start()
        return withContext(dispatcher) { get(kind ?: analysisKind).block() }
    }
}
