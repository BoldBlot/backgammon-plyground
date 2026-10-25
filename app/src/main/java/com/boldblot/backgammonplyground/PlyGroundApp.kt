package com.boldblot.backgammonplyground

import android.app.Application
import com.boldblot.backgammonplyground.data.AppDatabase
import com.boldblot.backgammonplyground.data.Repository
import com.boldblot.backgammonplyground.data.SettingsStore
import com.boldblot.backgammonplyground.engine.EngineService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PlyGroundApp : Application() {

    val appScope = CoroutineScope(SupervisorJob())

    val engine: EngineService by lazy { EngineService(this) }
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val repository: Repository by lazy { Repository(database) }
    val settings: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        // Warm up the engine (data extraction + neural net loading) in the background.
        appScope.launch { engine.start() }
    }
}
