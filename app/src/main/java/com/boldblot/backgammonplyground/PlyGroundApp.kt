package com.boldblot.backgammonplyground

import android.app.Application
import com.boldblot.backgammonplyground.data.AppDatabase
import com.boldblot.backgammonplyground.data.PositionsRepository
import com.boldblot.backgammonplyground.data.Repository
import com.boldblot.backgammonplyground.data.SettingsStore
import com.boldblot.backgammonplyground.engine.EngineService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
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

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        // Warm up the engine (data extraction + neural net loading) in the background.
        appScope.launch { engine.start() }
        // Analysis uses the engine chosen in Settings.
        appScope.launch { settings.settings.collect { engine.analysisKind = it.analysisEngine } }
    }
}
