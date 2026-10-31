package com.boldblot.backgammonplyground

import android.app.Application
import com.boldblot.backgammonplyground.data.AppDatabase
import com.boldblot.backgammonplyground.data.BackgroundPriority
import com.boldblot.backgammonplyground.data.ErrorsMode
import com.boldblot.backgammonplyground.data.ErrorsSort
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
        // 0.7.4, for the testers' phones; remove before the production release (see Positions.migrateFolders074).
        appScope.launch {
            if (!settings.settings.first().folders074) {
                val collecting = positions.migrateFolders074()
                settings.update { it.copy(folders074 = true, errorsMode = if (collecting) ErrorsMode.AUTOMATIC else it.errorsMode) }
            }
        }
        // A new error: autofilters take the ones they match, and with automatic filing every error is
        // filed at once ([Settings.errorsSort]). A filed error skips New errors.
        repository.onNewBlunder = { b ->
            val s = settings.settings.first()
            val jacoby: suspend (Long) -> Boolean = { id -> repository.match(id)?.jacoby ?: true }
            var filed = false
            for (c in positions.categories()) {
                val filter = c.filter?.takeIf { c.autoAdd }?.let { com.boldblot.backgammonplyground.core.Labels.decode(it) } ?: continue
                if (com.boldblot.backgammonplyground.core.Labels.matchesAll(b.labels, s.visible(filter))) {
                    positions.addBlunders(listOf(b), c.id, jacoby)
                    filed = true
                }
            }
            if (s.errorsMode == ErrorsMode.AUTOMATIC) {
                positions.fileErrors(listOf(b), s.errorsSort == ErrorsSort.SPLIT, jacoby)
                filed = true
            }
            if (filed) repository.removeBlunder(b.entity.id)
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
