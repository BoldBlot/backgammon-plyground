package com.boldblot.backgammonplyground

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.boldblot.backgammonplyground.ui.game.GameScreen
import com.boldblot.backgammonplyground.ui.game.GameViewModel
import com.boldblot.backgammonplyground.ui.home.SpeedTestScreen
import com.boldblot.backgammonplyground.ui.home.WelcomeScreen
import com.boldblot.backgammonplyground.ui.home.AboutScreen
import com.boldblot.backgammonplyground.ui.home.HistoryScreen
import com.boldblot.backgammonplyground.ui.home.NewErrorsScreen
import com.boldblot.backgammonplyground.ui.home.HomeScreen
import com.boldblot.backgammonplyground.ui.home.NewMatchScreen
import com.boldblot.backgammonplyground.ui.home.SettingsGroup
import com.boldblot.backgammonplyground.ui.home.SettingsScreen
import com.boldblot.backgammonplyground.ui.position.PositionScreen
import com.boldblot.backgammonplyground.ui.positions.CategoryPositionsScreen
import com.boldblot.backgammonplyground.ui.positions.MyPositionsScreen
import com.boldblot.backgammonplyground.ui.positions.PositionDetailScreen
import com.boldblot.backgammonplyground.ui.positions.QuizScreen
import com.boldblot.backgammonplyground.ui.positions.QuizViewModel
import com.boldblot.backgammonplyground.ui.positions.QuizMenuScreen
import com.boldblot.backgammonplyground.ui.positions.QuizSpec
import com.boldblot.backgammonplyground.ui.position.PositionViewModel
import com.boldblot.backgammonplyground.ui.review.BlunderScreen
import com.boldblot.backgammonplyground.ui.review.BlunderViewModel
import com.boldblot.backgammonplyground.ui.review.ReviewScreen
import com.boldblot.backgammonplyground.ui.review.ReviewViewModel
import com.boldblot.backgammonplyground.ui.stats.StatsScreen
import com.boldblot.backgammonplyground.ui.stats.StatsViewModel
import com.boldblot.backgammonplyground.ui.theme.GammonTheme
import kotlinx.coroutines.flow.first

/** Route id of "All positions" (category ids are positive, "no category" is -1). */
private const val ALL_POSITIONS = 0L

class MainActivity : ComponentActivity() {

    private val app: PlyGroundApp get() = application as PlyGroundApp

    private inline fun <reified T : ViewModel> factory(crossinline create: () -> T) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
    }

    private val gameVm: GameViewModel by viewModels { factory { GameViewModel(app) } }
    private val positionVm: PositionViewModel by viewModels { factory { PositionViewModel(app) } }

    /** A positions file opened with PlyGround from another app (Files, Bluetooth, email, Quick Share). */
    private fun readImport(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            else -> null
        }
        val text = uri?.let { runCatching { contentResolver.openInputStream(it)?.bufferedReader()?.readText() }.getOrNull() }
            ?: intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)
        if (text != null) {
            if (com.boldblot.backgammonplyground.ui.home.looksLikeMatch(text)) app.pendingMatch.value = text else app.pendingImport.value = text
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readImport(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readImport(intent)
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
        // A positions file to import opens My positions, which shows the import dialog.
        val pendingImport by app.pendingImport.collectAsState()
        LaunchedEffect(pendingImport != null) {
            if (pendingImport != null && nav.currentDestination?.route != "positions") nav.navigate("positions")
        }
        // A match file opens the Match Library, which shows the import dialog.
        val pendingMatch by app.pendingMatch.collectAsState()
        LaunchedEffect(pendingMatch != null) {
            if (pendingMatch != null && nav.currentDestination?.route != "history") nav.navigate("history")
        }

        // The welcome screen: on a new install, after an update from a build without it, and again
        // when the version name changes.
        val newInstall = remember {
            val info = packageManager.getPackageInfo(packageName, 0)
            info.firstInstallTime == info.lastUpdateTime
        }
        LaunchedEffect(Unit) {
            val seen = app.settings.settings.first().welcomeSeen
            when {
                seen == BuildConfig.VERSION_NAME -> {}
                else -> nav.navigate("welcome")
            }
        }

        NavHost(nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    hasMatch = hasMatch,
                    onContinue = { nav.navigate("game") },
                    onNewMatch = { nav.navigate("new") },
                    onPosition = { nav.navigate("position") },
                    onMyPositions = { nav.navigate("positions") },
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
                    onSettings = { nav.navigate("settings") },
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
                    onBack = { nav.popBackStack() },
                    onImported = gameVm::queueAnalysis,
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
            composable("positions") {
                MyPositionsScreen(
                    app, onBack = { nav.popBackStack() },
                    onCategory = { id -> nav.navigate("positions/category/${id ?: ALL_POSITIONS}") },
                    onQuiz = { ids -> nav.navigate("quizmenu?ids=${ids.joinToString(",")}") },
                    onNewErrors = { nav.navigate("errors") },
                )
            }
            composable("errors") {
                NewErrorsScreen(app, onOpen = { id -> nav.navigate("blunder/$id") }, onBack = { nav.popBackStack() })
            }
            composable("positions/category/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id")?.takeIf { it != ALL_POSITIONS }
                CategoryPositionsScreen(
                    app, id, onBack = { nav.popBackStack() },
                    onOpen = { p -> nav.navigate("positions/item/$p") },
                    onQuiz = { ids -> nav.navigate("quizmenu?ids=${ids.joinToString(",")}") },
                )
            }
            composable("positions/item/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                PositionDetailScreen(app, entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
            // The quiz menu (folders, labels, how many, order), opened with [ids] ticked.
            composable("quizmenu?ids={ids}", arguments = listOf(navArgument("ids") { type = NavType.StringType; defaultValue = "" })) { entry ->
                val ids = entry.arguments?.getString("ids").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
                QuizMenuScreen(app, ids, onBack = { nav.popBackStack() }) { spec -> nav.navigate("positions/quiz/${spec.encode()}") }
            }
            composable("positions/quiz/{spec}") { entry ->
                val key = entry.arguments?.getString("spec").orEmpty()
                val vm: QuizViewModel = viewModel(key = "quiz-$key", factory = factory { QuizViewModel(app, QuizSpec.decode(key)) })
                QuizScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(
                    app, onBack = { nav.popBackStack() }, onSpeedTest = { nav.navigate("speedtest") },
                    onGroup = { g -> nav.navigate("settings/${g.name}") },
                )
            }
            composable("settings/{group}") { entry ->
                val group = SettingsGroup.entries.firstOrNull { it.name == entry.arguments?.getString("group") }
                SettingsScreen(app, onBack = { nav.popBackStack() }, onSpeedTest = { nav.navigate("speedtest") }, group = group)
            }
            composable("about") {
                AboutScreen(
                    onBack = { nav.popBackStack() },
                    onSpeedTest = { nav.navigate("speedtest") },
                    onWelcome = { nav.navigate("welcome-again") },
                )
            }
            composable("speedtest") { SpeedTestScreen(app, onBack = { nav.popBackStack() }) }
            composable("welcome") {
                WelcomeScreen(
                    app, newInstall,
                    onSpeedTest = { nav.navigate("speedtest") { popUpTo("welcome") { inclusive = true } } },
                    onDone = { nav.popBackStack() },
                )
            }
            // Opened again from About: never a new install, so Skip keeps the player's settings.
            composable("welcome-again") {
                WelcomeScreen(
                    app, newInstall = false,
                    onSpeedTest = { nav.navigate("speedtest") { popUpTo("welcome-again") { inclusive = true } } },
                    onDone = { nav.popBackStack() },
                )
            }
        }
        // Back from a screen opened over the game (review, settings) goes straight back, like the
        // screen's arrow, without the back gesture's preview: the game's end-of-game summary is a
        // dialog window, and drawing it in the preview of a cancelled gesture could lose it.
        val entry by nav.currentBackStackEntryAsState()
        BackHandler(enabled = entry != null && nav.previousBackStackEntry?.destination?.route == "game") { nav.popBackStack() }
    }
}
