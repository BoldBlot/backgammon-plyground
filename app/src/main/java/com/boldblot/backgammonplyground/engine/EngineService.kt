package com.boldblot.backgammonplyground.engine

import android.content.Context
import com.boldblot.backgammonplyground.core.AnalysisEngine
import com.boldblot.backgammonplyground.core.Board
import com.boldblot.backgammonplyground.core.CpuLevel
import com.boldblot.backgammonplyground.core.CubeContext
import com.boldblot.backgammonplyground.core.Move
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * Owns the PureTD engine. The engine is not thread-safe, so every call goes through [run], which
 * executes on one dedicated thread (the 3-ply search spreads its own work over all cores).
 */
class EngineService(private val context: Context) {

    /** Single engine thread (the C search keeps large buffers on the heap, not the stack). */
    val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(null, r, "puretd-engine", 4L * 1024 * 1024).apply { isDaemon = true }
    }.asCoroutineDispatcher()

    lateinit var engine: PureTdEngine
        private set

    private val ready = CompletableDeferred<Unit>()

    /**
     * The engine for objects created before it has loaded (a screen restored straight into a game):
     * each call looks the engine up when it is made. Calls go through [run], which waits for it.
     */
    val analysis: AnalysisEngine = object : AnalysisEngine {
        override fun rankMoves(board: Board, ctx: CubeContext, d1: Int, d2: Int, plies: Int, played: Move?) = engine.rankMoves(board, ctx, d1, d2, plies, played)
        override fun chooseMove(board: Board, ctx: CubeContext, d1: Int, d2: Int, level: CpuLevel) = engine.chooseMove(board, ctx, d1, d2, level)
        override fun cubeDecision(board: Board, ctx: CubeContext, plies: Int) = engine.cubeDecision(board, ctx, plies)
        override fun cubeDecisionAtLevel(board: Board, ctx: CubeContext, level: CpuLevel) = engine.cubeDecisionAtLevel(board, ctx, level)
        override fun luck(board: Board, ctx: CubeContext, d1: Int, d2: Int, firstRoll: Boolean) = engine.luck(board, ctx, d1, d2, firstRoll)
        override fun positionId(board: Board, onRoll: Int) = engine.positionId(board, onRoll)
        override fun matchId(ctx: CubeContext, d1: Int, d2: Int) = engine.matchId(ctx, d1, d2)
    }

    /** Extracts data files (first launch / after an update) and initializes the engine. */
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

    suspend fun <T> run(block: PureTdEngine.() -> T): T {
        start()
        return withContext(dispatcher) { engine.block() }
    }
}
