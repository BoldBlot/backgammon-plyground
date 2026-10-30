package com.boldblot.backgammonplyground

import android.app.Application
import com.boldblot.backgammonplyground.data.AppDatabase
import com.boldblot.backgammonplyground.data.BackgroundPriority
import com.boldblot.backgammonplyground.data.PositionsRepository
import com.boldblot.backgammonplyground.data.Repository
import com.boldblot.backgammonplyground.data.SettingsStore
import com.boldblot.backgammonplyground.engine.EngineService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlyGroundApp : Application() {

    val appScope = CoroutineScope(SupervisorJob())

    val engine: EngineService by lazy { EngineService(this) }
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val repository: Repository by lazy { Repository(database) }
    val positions: PositionsRepository by lazy { PositionsRepository(database) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    /** A positions file opened with PlyGround (or picked in My positions), waiting for its import dialog. */
    val pendingImport = MutableStateFlow<String?>(null)
    /** A .mat match file opened with PlyGround, waiting for the Match Library's import dialog. */
    val pendingMatch = MutableStateFlow<String?>(null)

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        // Warm up the engine (data extraction + neural net loading) in the background.
        appScope.launch { engine.start() }
        // My positions: the Checker blunders and Cube blunders folders (once), and folders linked to a
        // blunder list filter that take new blunders at once.
        appScope.launch {
            if (!settings.settings.first().builtInFolders) {
                positions.makeBuiltInFolders()
                settings.update { it.copy(builtInFolders = true) }
            }
        }
        repository.onNewBlunder = { b ->
            val s = settings.settings.first()
            for (c in positions.categories()) {
                val filter = c.filter?.takeIf { c.autoAdd }?.let { com.boldblot.backgammonplyground.core.Labels.decode(it) } ?: continue
                if (com.boldblot.backgammonplyground.core.Labels.matches(b.labels, s.visible(filter))) {
                    positions.addBlunders(listOf(b), c.id) { id -> repository.match(id)?.jacoby ?: true }
                }
            }
        }
        // Analysis uses the engine chosen in Settings.
        appScope.launch {
            settings.settings.collect {
                engine.analysisKind = it.analysisEngine
                // Advanced engine settings (0 = the default: all cores for the game, a quarter for the analysis).
                engine.gameThreads = it.gameThreads.takeIf { n -> n > 0 } ?: engine.cores
                engine.backgroundThreads = it.backgroundThreads.takeIf { n -> n > 0 } ?: EngineService.defaultBackgroundThreads(engine.cores)
                engine.backgroundLow = it.backgroundPriority == BackgroundPriority.LOW
            }
        }
    }
}
