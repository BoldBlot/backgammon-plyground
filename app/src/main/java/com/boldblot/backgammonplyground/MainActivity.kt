package com.boldblot.backgammonplyground

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.boldblot.backgammonplyground.ui.game.GameScreen
import com.boldblot.backgammonplyground.ui.game.GameViewModel
import com.boldblot.backgammonplyground.ui.home.SpeedTestScreen
import com.boldblot.backgammonplyground.ui.home.AboutScreen
import com.boldblot.backgammonplyground.ui.home.HistoryScreen
import com.boldblot.backgammonplyground.ui.home.HomeScreen
import com.boldblot.backgammonplyground.ui.home.NewMatchScreen
import com.boldblot.backgammonplyground.ui.home.SettingsScreen
import com.boldblot.backgammonplyground.ui.position.PositionScreen
import com.boldblot.backgammonplyground.ui.position.PositionViewModel
import com.boldblot.backgammonplyground.ui.review.BlunderScreen
import com.boldblot.backgammonplyground.ui.review.BlunderViewModel
import com.boldblot.backgammonplyground.ui.review.ReviewScreen
import com.boldblot.backgammonplyground.ui.review.ReviewViewModel
import com.boldblot.backgammonplyground.ui.stats.StatsScreen
import com.boldblot.backgammonplyground.ui.stats.StatsViewModel
import com.boldblot.backgammonplyground.ui.theme.GammonTheme

class MainActivity : ComponentActivity() {

    private val app: PlyGroundApp get() = application as PlyGroundApp

    private inline fun <reified T : ViewModel> factory(crossinline create: () -> T) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
    }

    private val gameVm: GameViewModel by viewModels { factory { GameViewModel(app) } }
    private val positionVm: PositionViewModel by viewModels { factory { PositionViewModel(app) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Landscape is played full screen (system bars appear on a swipe), like dedicated board game apps.
            val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            LaunchedEffect(landscape) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (landscape) controller.hide(WindowInsetsCompat.Type.systemBars())
                else controller.show(WindowInsetsCompat.Type.systemBars())
            }
            GammonTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) { AppNav() }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun AppNav() {
        val nav = rememberNavController()
        val gameState by gameVm.state.collectAsState()
        val hasMatch = gameState.match != null && gameState.match?.isOver == false

        NavHost(nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    hasMatch = hasMatch,
                    onContinue = { nav.navigate("game") },
                    onNewMatch = { nav.navigate("new") },
                    onPosition = { nav.navigate("position") },
                    onStats = { nav.navigate("stats") },
                    onHistory = { nav.navigate("history") },
                    onSettings = { nav.navigate("settings") },
                    onAbout = { nav.navigate("about") },
                )
            }
            composable("new") {
                NewMatchScreen(app, hasMatch, onStart = { rules, level, mistakes, engine ->
                    gameVm.newMatch(rules, level, mistakes, engine)
                    nav.navigate("game") { popUpTo("home") }
                }, onBack = { nav.popBackStack() })
            }
            composable("game") {
                GameScreen(
                    gameVm, onBack = { nav.popBackStack("home", inclusive = false) },
                    onReview = { id -> nav.navigate("review/$id") },
                    onReviewMistakes = { id, game -> nav.navigate("review/$id?game=$game&mistakes=true") },
                )
            }
            composable(
                "review/{id}?game={game}&mistakes={mistakes}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("game") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("mistakes") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                val game = entry.arguments?.getInt("game")?.takeIf { it > 0 }
                val mistakes = entry.arguments?.getBoolean("mistakes") ?: false
                val vm: ReviewViewModel = viewModel(key = "review-$id-$game-$mistakes", factory = factory { ReviewViewModel(app, id, game, mistakes) })
                ReviewScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("stats") {
                val vm: StatsViewModel = viewModel(factory = factory { StatsViewModel(app) })
                StatsScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("history") {
                HistoryScreen(
                    app,
                    onReview = { id -> nav.navigate("review/$id") },
                    onBlunder = { id -> nav.navigate("blunder/$id") },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("blunder/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                val vm: BlunderViewModel = viewModel(key = "blunder-$id", factory = factory { BlunderViewModel(app, id) })
                BlunderScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("position") {
                PositionScreen(positionVm, onBack = { nav.popBackStack() })
            }
            composable("settings") { SettingsScreen(app, onBack = { nav.popBackStack() }, onSpeedTest = { nav.navigate("speedtest") }) }
            composable("about") { AboutScreen(onBack = { nav.popBackStack() }, onSpeedTest = { nav.navigate("speedtest") }) }
            composable("speedtest") { SpeedTestScreen(app, onBack = { nav.popBackStack() }) }
        }
    }
}
