package com.boldblot.backgammonplyground

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

    companion object {
        /** The action another app sends to open Analyze Position with a position: the XGID in [Intent.EXTRA_TEXT]. */
        const val ACTION_ANALYZE_POSITION = "com.boldblot.backgammonplyground.action.ANALYZE_POSITION"
        /**
         * The action another app sends to make a set (Match Library → Sets): one XGID per line in [Intent.EXTRA_TEXT], in order;
         * optional the set's name in [Intent.EXTRA_TITLE] and the players at the bottom and the top in [EXTRA_BOTTOM_PLAYER], [EXTRA_TOP_PLAYER].
         */
        const val ACTION_IMPORT_SET = "com.boldblot.backgammonplyground.action.IMPORT_SET"
        const val EXTRA_BOTTOM_PLAYER = "com.boldblot.backgammonplyground.extra.BOTTOM_PLAYER"

        /**
         * Optional with [ACTION_IMPORT_SET]: the move played in each position, a String array (or ArrayList) parallel to
         * the ID lines (blank lines not counted): "13/7 8/7", "8/5(2) 6/5(2)", "bar/22*", "6/off", from the decider's
         * view; "" when not known. Another count, or an entry that is not a legal play there, is ignored (the position
         * comes without it).
         */
        const val EXTRA_PLAYED = "com.boldblot.backgammonplyground.extra.PLAYED"
        const val EXTRA_TOP_PLAYER = "com.boldblot.backgammonplyground.extra.TOP_PLAYER"
    }

    private val app: PlyGroundApp get() = application as PlyGroundApp

    private inline fun <reified T : ViewModel> factory(crossinline create: () -> T) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
    }

    private val gameVm: GameViewModel by viewModels { factory { GameViewModel(app) } }
    private val positionVm: PositionViewModel by viewModels { factory { PositionViewModel(app) } }

    /** A positions file opened with PlyGround from another app (Files, Bluetooth, email, Quick Share). */
    private fun readImport(intent: Intent?) {
        // Another app's position for Analyze Position (an XGID as text).
        if (intent?.action == ACTION_ANALYZE_POSITION) {
            app.pendingAnalyze.value = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            return
        }
        // Another app's series of positions for a new set.
        if (intent?.action == ACTION_IMPORT_SET) {
            app.pendingSet.value = PendingSet(
                intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(), name = intent.getStringExtra(Intent.EXTRA_TITLE)?.takeIf { it.isNotBlank() },
                bottomName = intent.getStringExtra(EXTRA_BOTTOM_PLAYER)?.takeIf { it.isNotBlank() }, topName = intent.getStringExtra(EXTRA_TOP_PLAYER)?.takeIf { it.isNotBlank() },
                played = intent.getStringArrayExtra(EXTRA_PLAYED)?.toList() ?: intent.getStringArrayListExtra(EXTRA_PLAYED),
            )
            return
        }
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            else -> null
        }
        val bytes = uri?.let { runCatching { contentResolver.openInputStream(it)?.use { s -> s.readBytes() } }.getOrNull() }
        // An eXtreme Gammon match file goes to the Match Library as it is.
        if (bytes != null && com.boldblot.backgammonplyground.core.XgImport.isXg(bytes)) {
            app.pendingMatchFile.value = bytes
            return
        }
        val text = bytes?.let { String(it) }
            ?: intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)
        if (text != null) {
            val shared = bytes == null
            when {
                com.boldblot.backgammonplyground.ui.home.looksLikeMatch(text) -> app.pendingMatch.value = text
                // Text with one position (an XGID selected on a page and shared) opens Analyze Position; several go to My positions.
                shared && com.boldblot.backgammonplyground.core.PositionIds.single(text) != null ->
                    app.pendingAnalyze.value = com.boldblot.backgammonplyground.core.PositionIds.single(text)
                else -> app.pendingImport.value = text
            }
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
            // Errors in Eq or mEq everywhere (Settings → Display).
            val errorUnit by androidx.compose.runtime.remember { app.settings.settings.map { it.errorUnit } }.collectAsState(initial = com.boldblot.backgammonplyground.data.ErrorUnit.EQ)
            val checkerStyle by androidx.compose.runtime.remember { app.settings.settings.map { it.checkerStyle } }.collectAsState(initial = com.boldblot.backgammonplyground.data.CheckerStyle.RIMMED)
            val scoreAway by androidx.compose.runtime.remember { app.settings.settings.map { it.scoreAway } }.collectAsState(initial = false)
            val cubeSpread by androidx.compose.runtime.remember { app.settings.settings.map { it.showCubeSpread } }.collectAsState(initial = false)
            val shareChances by androidx.compose.runtime.remember { app.settings.settings.map { it.shareChances } }.collectAsState(initial = true)
            GammonTheme {
                androidx.compose.runtime.CompositionLocalProvider(com.boldblot.backgammonplyground.ui.components.LocalShareChances provides shareChances, com.boldblot.backgammonplyground.ui.components.LocalScoreAway provides scoreAway, com.boldblot.backgammonplyground.ui.components.LocalCubeSpread provides cubeSpread, com.boldblot.backgammonplyground.ui.components.LocalErrorUnit provides errorUnit, com.boldblot.backgammonplyground.ui.components.LocalCheckerStyle provides checkerStyle) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize().safeDrawingPadding()) { AppNav() }
                    }
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
        val pendingMatchFile by app.pendingMatchFile.collectAsState()
        LaunchedEffect(pendingMatch != null, pendingMatchFile != null) {
            if ((pendingMatch != null || pendingMatchFile != null) && nav.currentDestination?.route != "history") nav.navigate("history")
        }

        // A position sent by another app opens Analyze Position with it set up.
        val pendingSet by app.pendingSet.collectAsState()
        LaunchedEffect(pendingSet != null) {
            if (pendingSet != null && nav.currentDestination?.route != "history") nav.navigate("history")
        }
        val pendingAnalyze by app.pendingAnalyze.collectAsState()
        LaunchedEffect(pendingAnalyze) {
            val text = pendingAnalyze ?: return@LaunchedEffect
            app.pendingAnalyze.value = null
            positionVm.openSent(text, played = app.pendingAnalyzePlayed.also { app.pendingAnalyzePlayed = null })
            if (nav.currentDestination?.route != "position") nav.navigate("position")
        }
        val pendingDeepDive by app.pendingDeepDive.collectAsState()
        LaunchedEffect(pendingDeepDive) {
            val req = pendingDeepDive ?: return@LaunchedEffect
            app.pendingDeepDive.value = null
            app.deepDive = req
            nav.navigate("deepdive")
        }

        // The welcome screen: only when it has never been shown (a new install, or the app's data
        // cleared); updates go straight to the home screen.
        val newInstall = remember {
            val info = packageManager.getPackageInfo(packageName, 0)
            info.firstInstallTime == info.lastUpdateTime
        }
        LaunchedEffect(Unit) {
            val s0 = app.settings.settings.first()
            // An update from before the analysis budget (0.9.21): the budget and the speed test, once.
            if (s0.welcomeSeen.isEmpty()) nav.navigate("welcome") else if (!s0.budgetAsked) nav.navigate("welcome-again")
        }

        val quickScope = androidx.compose.runtime.rememberCoroutineScope()
        val quickRight by androidx.compose.runtime.remember { app.settings.settings.map { it.quickQuizRight } }.collectAsState(initial = true)
        NavHost(nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    hasMatch = hasMatch,
                    onContinue = { nav.navigate("game") },
                    onNewMatch = { nav.navigate("new") },
                    onPosition = { nav.navigate("position") },
                    onMyPositions = { nav.navigate("positions") },
                    quickQuizRight = quickRight,
                    onQuickQuiz = {
                        quickScope.launch {
                            val s = app.settings.settings.first()
                            val q = com.boldblot.backgammonplyground.ui.positions.QuizSpec.quick(s)
                            // Folders deleted since the quick quiz was saved are left out.
                            val known = app.positions.categories().map { it.id }.toSet() + com.boldblot.backgammonplyground.data.PositionsRepository.UNCATEGORIZED
                            nav.navigate("positions/quiz/${q.copy(folders = q.folders.filter { it in known }).encode()}")
                        }
                    },
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
                    onReview = { id -> nav.navigate("review/$id?marks=true") },
                    onReviewMistakes = { id, game -> nav.navigate("review/$id?game=$game&mistakes=true&marks=true") },
                    onReviewMarked = { id, game -> nav.navigate("review/$id?game=$game&marks=true&markedOnly=true") },
                    onSettings = { nav.navigate("settings") },
                    onQuiz = { id -> nav.navigate("gamequizmenu?match=$id") },
                )
            }
            composable(
                "review/{id}?game={game}&mistakes={mistakes}&marks={marks}&markedOnly={markedOnly}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("game") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("mistakes") { type = NavType.BoolType; defaultValue = false },
                    navArgument("marks") { type = NavType.BoolType; defaultValue = false },
                    navArgument("markedOnly") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                val game = entry.arguments?.getInt("game")?.takeIf { it > 0 }
                val mistakes = entry.arguments?.getBoolean("mistakes") ?: false
                val marks = entry.arguments?.getBoolean("marks") ?: false
                val markedOnly = entry.arguments?.getBoolean("markedOnly") ?: false
                val vm: ReviewViewModel = viewModel(key = "review-$id-$game-$mistakes-$marks-$markedOnly", factory = factory { ReviewViewModel(app, id, game, mistakes, marks, markedOnly) })
                ReviewScreen(vm, onBack = { nav.popBackStack() }, onQuiz = { m -> nav.navigate("gamequizmenu?match=$m") })
            }
            composable("stats?quiz={quiz}", arguments = listOf(navArgument("quiz") { type = NavType.BoolType; defaultValue = false })) { entry ->
                val vm: StatsViewModel = viewModel(factory = factory { StatsViewModel(app) })
                StatsScreen(vm, onBack = { nav.popBackStack() }, quizFirst = entry.arguments?.getBoolean("quiz") ?: false, onOpenFolder = { id -> nav.navigate("positions/category/$id") },
                    onOpenLabel = { label -> nav.navigate("positions/category/$ALL_POSITIONS?filter=${android.net.Uri.encode(label)}") })
            }
            composable("history") {
                HistoryScreen(
                    app,
                    onReview = { id -> nav.navigate("review/$id") },
                    onQuiz = { id -> nav.navigate("gamequizmenu?match=$id") },
                    onBack = { nav.popBackStack() },
                    onImported = gameVm::queueAnalysis,
                    analysisProgress = gameVm.analysisProgress.collectAsState().value,
                    onSettings = { nav.navigate("settings/DATA") },
                    onOpenSet = { id -> nav.navigate("sets/$id") },
                )
            }
            // A set of the Match Library, and its quiz.
            composable("sets/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val setId = entry.arguments?.getLong("id") ?: 0L
                com.boldblot.backgammonplyground.ui.sets.SetScreen(app, setId, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("sets/$setId/item/$it") }) { spec ->
                    nav.navigate("setquiz/${spec.encode()}")
                }
            }
            // A position of a set, on the page a folder's position opens on.
            composable(
                "sets/{set}/item/{id}",
                arguments = listOf(navArgument("set") { type = NavType.LongType }, navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val setId = entry.arguments?.getLong("set") ?: 0L
                PositionDetailScreen(
                    app, entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() }, onSettings = { nav.navigate("settings/POSITIONS") },
                    onStep = { p -> nav.navigate("sets/$setId/item/$p") { popUpTo("sets/{set}/item/{id}") { inclusive = true } } },
                    setId = setId,
                )
            }
            composable("setquiz/{spec}") { entry ->
                val key = entry.arguments?.getString("spec").orEmpty()
                val vm: QuizViewModel = viewModel(
                    key = "setquiz-$key",
                    factory = factory { QuizViewModel(app, QuizSpec(), set = com.boldblot.backgammonplyground.ui.sets.SetQuizSpec.decode(key)) },
                )
                QuizScreen(vm, onBack = { nav.popBackStack() }, onSettings = { nav.navigate("settings") })
            }
            composable(
                "blunder/{id}?marked={marked}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }, navArgument("marked") { type = NavType.BoolType; defaultValue = false }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                val marked = entry.arguments?.getBoolean("marked") ?: false
                val vm: BlunderViewModel = viewModel(key = "blunder-$id-$marked", factory = factory { BlunderViewModel(app, id, marked) })
                BlunderScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("deepdive") {
                val req = app.deepDive
                if (req == null) {
                    LaunchedEffect(Unit) { nav.popBackStack() }
                } else {
                    val vm: com.boldblot.backgammonplyground.ui.deepdive.DeepDiveViewModel =
                        viewModel(key = "deepdive-${req.id}", factory = factory { com.boldblot.backgammonplyground.ui.deepdive.DeepDiveViewModel(app, req) })
                    com.boldblot.backgammonplyground.ui.deepdive.DeepDiveScreen(vm, onBack = { nav.popBackStack() })
                }
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
                    onNewMarked = { nav.navigate("errors/marked") },
                    onLearned = { nav.navigate("learned") },
                )
            }
            composable("learned") {
                com.boldblot.backgammonplyground.ui.positions.LearnedScreen(app, onOpen = { p -> nav.navigate("positions/item/$p") }, onBack = { nav.popBackStack() })
            }
            composable("errors") {
                NewErrorsScreen(app, onOpen = { id -> nav.navigate("blunder/$id") }, onBack = { nav.popBackStack() })
            }
            composable("errors/marked") {
                NewErrorsScreen(app, onOpen = { id -> nav.navigate("blunder/$id?marked=true") }, onBack = { nav.popBackStack() }, marked = true)
            }
            composable(
                "positions/category/{id}?filter={filter}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }, navArgument("filter") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                val id = entry.arguments?.getLong("id")?.takeIf { it != ALL_POSITIONS }
                CategoryPositionsScreen(
                    app, id, onBack = { nav.popBackStack() },
                    initialFilter = com.boldblot.backgammonplyground.core.Labels.decode(entry.arguments?.getString("filter").orEmpty()),
                    onOpen = { p -> nav.navigate("positions/item/$p") },
                    onQuiz = { ids, filter -> nav.navigate("quizmenu?ids=${ids.joinToString(",")}&filter=${android.net.Uri.encode(filter.joinToString("\n"))}") },
                )
            }
            composable("positions/item/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                PositionDetailScreen(
                    app, entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() }, onSettings = { nav.navigate("settings/POSITIONS") },
                    onStep = { p -> nav.navigate("positions/item/$p") { popUpTo("positions/item/{id}") { inclusive = true } } },
                )
            }
            // The quiz menu (folders, labels, how many, order), opened with [ids] ticked and the folder's [filter].
            composable(
                "quizmenu?ids={ids}&filter={filter}",
                arguments = listOf(
                    navArgument("ids") { type = NavType.StringType; defaultValue = "" },
                    navArgument("filter") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val ids = entry.arguments?.getString("ids").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
                val filter = entry.arguments?.getString("filter").orEmpty().split('\n').filter { it.isNotBlank() }.toSet()
                QuizMenuScreen(app, ids, onBack = { nav.popBackStack() }, preFilter = filter, onGameQuiz = { nav.navigate("gamequizmenu") }, onStats = { nav.navigate("stats?quiz=true") }) { spec ->
                    nav.navigate("positions/quiz/${spec.encode()}")
                }
            }
            // The game quiz: its menu (for [match], or with the library's list to pick one), then the quiz on that match's decisions.
            composable("gamequizmenu?match={match}", arguments = listOf(navArgument("match") { type = NavType.LongType; defaultValue = -1L })) { entry ->
                val match = entry.arguments?.getLong("match")?.takeIf { it > 0 }
                com.boldblot.backgammonplyground.ui.positions.GameQuizMenuScreen(app, match, onBack = { nav.popBackStack() }) { spec ->
                    nav.navigate("gamequiz/${spec.encode()}")
                }
            }
            composable("gamequiz/{spec}") { entry ->
                val key = entry.arguments?.getString("spec").orEmpty()
                val vm: QuizViewModel = viewModel(
                    key = "gamequiz-$key",
                    factory = factory { QuizViewModel(app, QuizSpec(), com.boldblot.backgammonplyground.ui.positions.GameQuizSpec.decode(key)) },
                )
                QuizScreen(vm, onBack = { nav.popBackStack() }, onSettings = { nav.navigate("settings") })
            }
            composable("positions/quiz/{spec}") { entry ->
                val key = entry.arguments?.getString("spec").orEmpty()
                val vm: QuizViewModel = viewModel(key = "quiz-$key", factory = factory { QuizViewModel(app, QuizSpec.decode(key)) })
                QuizScreen(vm, onBack = { nav.popBackStack() }, onOpen = { p -> com.boldblot.backgammonplyground.ui.positions.BrowseList.ids = emptyList(); nav.navigate("positions/item/$p") }, onSettings = { nav.navigate("settings") })
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
                val settingsNow by app.settings.settings.collectAsState(initial = null)
                AboutScreen(
                    speedTest = settingsNow?.let { com.boldblot.backgammonplyground.ui.home.speedTestLine(it) },
                    onBack = { nav.popBackStack() },
                    onSpeedTest = { nav.navigate("speedtest") },
                    onWelcome = { nav.navigate("welcome-again") },
                )
            }
            composable("speedtest?auto={auto}", arguments = listOf(navArgument("auto") { type = NavType.BoolType; defaultValue = false })) { entry ->
                SpeedTestScreen(app, onBack = { nav.popBackStack() }, autoStart = entry.arguments?.getBoolean("auto") == true)
            }
            composable("welcome") {
                WelcomeScreen(
                    app, newInstall,
                    onSpeedTest = { nav.navigate("speedtest?auto=true") { popUpTo("welcome") { inclusive = true } } },
                    onDone = { nav.popBackStack() },
                )
            }
            // Opened again from About: never a new install, so Skip keeps the player's settings.
            composable("welcome-again") {
                WelcomeScreen(
                    app, newInstall = false,
                    onSpeedTest = { nav.navigate("speedtest?auto=true") { popUpTo("welcome-again") { inclusive = true } } },
                    onDone = { nav.popBackStack() },
                )
            }
        }
        // The search someone is waiting for: what it is doing, and past its limit the offer to stop.
        com.boldblot.backgammonplyground.ui.components.SearchOverlay(app.engine, onSettings = { nav.navigate("settings/ANALYSIS") })
        // The game screen with a match in play: the GPU can go to the background analysis (Advanced engine settings).
        val route = nav.currentBackStackEntryAsState().value?.destination?.route
        val playing = route == "game" && gameState.match?.let { it.game.phase != com.boldblot.backgammonplyground.core.Phase.OVER } == true
        androidx.compose.runtime.LaunchedEffect(playing) { app.engine.inGame = playing }
        com.boldblot.backgammonplyground.ui.components.BackgroundMatchLabel(app, onGame = route == "game" || route?.startsWith("welcome") == true) {
            com.boldblot.backgammonplyground.AppLog.i("Top label tapped: opening Data and help")
            nav.navigate("settings/DATA")
        }
        val indicator by androidx.compose.runtime.remember { app.settings.settings.map { it.analysisIndicator } }.collectAsState(initial = false)
        if (indicator) {
            val queued = gameVm.analysisProgress.collectAsState().value.values.sumOf { it.left }
            com.boldblot.backgammonplyground.ui.components.EngineIndicator(app.engine, queued)
        }
        // Back from a screen opened over the game (review, settings) goes straight back, like the
        // screen's arrow, without the back gesture's preview: the game's end-of-game summary is a
        // dialog window, and drawing it in the preview of a cancelled gesture could lose it.
        val entry by nav.currentBackStackEntryAsState()
        BackHandler(enabled = entry != null && nav.previousBackStackEntry?.destination?.route == "game") { nav.popBackStack() }
    }
}
