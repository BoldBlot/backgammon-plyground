package com.boldblot.backgammonplyground.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.boldblot.backgammonplyground.core.CpuLevel
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.EngineKind
import com.boldblot.backgammonplyground.core.MistakeRate
import com.boldblot.backgammonplyground.core.MoveFilter
import com.boldblot.backgammonplyground.core.Severity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/** When to show the practice-mode prompt after one of the human's decisions. */
enum class FeedbackLevel(val label: String, val minSeverity: Severity?) {
    OFF("Off", null),
    BLUNDERS("Blunders only", Severity.BLUNDER),
    ERRORS("Errors and blunders", Severity.ERROR),
    ALL("Any non-best move", Severity.INACCURACY),
}

/** When and how the player learns about a mistake. */
enum class TrainingMode(val label: String, val description: String) {
    IMMEDIATE("Show mistakes immediately", "Intuition training: the right play and how much yours cost, straight after each mistake."),
    /** Mistakes are held back until the match ends (a money session: when it is ended). */
    AFTER_GAME("Show mistakes after the match", "Play the whole match undisturbed, then review your mistakes. In a money session: when you end it."),
    TRY_AGAIN("Try again", "You are told only that it was an error or a blunder. Try the decision again, or show the answer. Your first try counts in PR."),
}

/**
 * How the player's decisions are analyzed when nothing is shown during play ("after the match"
 * training, or immediate training with the practice prompt off).
 */
enum class AnalysisTiming(val label: String, val description: String) {
    BACKGROUND("In the background", "PR and mistakes are ready when the match ends; play never waits for the analysis."),
    NONE("None", "Play at full speed and analyze later: Analyze now at the end of the match, or Analyze again in the review."),
}

/** The background analysis' thread priority (Advanced engine settings). */
enum class BackgroundPriority(val label: String) { LOW("Low"), NORMAL("Normal") }

/** The error thresholds (core's XgRules), for the defaults here. */
private object XgRulesDefaults {
    const val ERROR = com.boldblot.backgammonplyground.core.XgRules.ERROR_THRESHOLD
    const val BLUNDER = com.boldblot.backgammonplyground.core.XgRules.BLUNDER_THRESHOLD
}

/** Which of the human's mistakes went on the blunder list (before [Settings.errorMin], 0.7.11). */
enum class BlunderListLevel(val label: String, val minSeverity: Severity) {
    BLUNDERS("Blunders only", Severity.BLUNDER),
    ERRORS("Errors and blunders", Severity.ERROR),
}

/** How long the arrows of the computer's last move stay on the board. */
enum class CpuArrows(val label: String) { UNTIL_MOVE("Until my move"), ONE_SECOND("1 second"), OFF("Off") }

/** The winning chances meter during play. */
enum class MeterMode(val label: String) { OFF("Off"), BAR("Bar"), BAR_AND_NUMBERS("Bar and numbers") }

/** What counts as a right answer in the My positions quiz. */
/**
 * Training during play with deep cube analysis: a quick check at this depth stands in for the deep one
 * when it finds doubling a blunder (the player rolls), so obvious turns such as endgames don't wait.
 */
enum class QuickCubeCheck(val label: String, val plies: Int) { OFF("Off", 0), ONE("1-ply", 1), TWO("2-ply", 2) }

/** How errors (equity lost) are shown everywhere: equity (-0.026) or thousandths of it (-26.0 mEq). */
enum class ErrorUnit(val label: String) { EQ("Eq (0.026)"), MEQ("mEq (26.0)") }

enum class QuizCorrect(val label: String) { NOT_ERROR("Not an error"), BEST_ONLY("Best play only"), FIXED("Fixed level"), ADAPTIVE("Adaptive") }

/**
 * Sets the level of errors kept for My positions and the level the quiz accepts, both to [level].
 * [CUSTOM] leaves them to their own settings.
 */
enum class TrainingPlan(val label: String, val level: Float?) {
    ERROR("Error", XgRulesDefaults.ERROR),
    BLUNDER("Blunder", XgRulesDefaults.BLUNDER),
    BIG_BLUNDER("Big blunder", 0.12f),
    CUSTOM("Custom", null),
}

/** The quiz's answers to the player's own cube decisions. */
enum class QuizMyCube(val label: String) {
    TWO("No double / Double"), THREE("No double / Double / Too good"), FULL("Full decision"),
}

/** The quiz's questions when the opponent has doubled. */
enum class QuizTheirCube(val label: String) {
    TAKE_PASS("Take or pass"), THEN_DOUBLE("Take or pass, then: was the double right?"), FLIP("Turn the board: the doubler's full decision"),
}

/**
 * How new errors reach My positions: through New errors ([MANUAL]), or filed at once ([ErrorsSort]) when
 * they pass the minimum ([AUTOMATIC]), the adaptive threshold ([ADAPTIVE]) or are among a match's
 * biggest ([TOP_X], at its end). AUTOMATIC keeps its released name.
 */
enum class ErrorsMode(val label: String) {
    MANUAL("Manual"), AUTOMATIC("Fixed threshold"), ADAPTIVE("Adaptive (experimental)"), TOP_X("Top X per match"),
}

/** Where new errors are filed: in no folder, or checker plays in Checker and cube decisions in Cube. */
enum class ErrorsSort(val label: String) { NO_FOLDER("No folder"), SPLIT("Checker and Cube folders") }

/** Order of the blunder list. */
enum class BlunderSort(val label: String) { BIGGEST("Biggest first"), NEWEST("Newest first") }

/** The analysis depth while mistakes are shown immediately (Intuition training); [plies] 0 = as in Settings → Analysis. */
enum class IntuitionDepth(val label: String, val plies: Int) { SAME("Same as Analysis", 0), ONE("1-ply", 1), TWO("2-ply", 2), THREE("3-ply", 3) }

/** How deep autofinish looks when it plays the human's side; never noise. */
enum class AutoFinishDepth(val label: String, val plies: Int) { ONE("1-ply", 1), TWO("2-ply", 2), THREE("3-ply", 3) }

/** Pause after each move while autofinish plays the game out. */
enum class AutoFinishSpeed(val label: String, val pauseMillis: Long) {
    S01("0.1 s", 100), S03("0.3 s", 300), S05("0.5 s", 500), S10("1 s", 1000), S20("2 s", 2000)
}

enum class AnimationSpeed(val label: String, val stepMillis: Long) {
    SLOW("Slow", 650), NORMAL("Normal", 380), FAST("Fast", 180), INSTANT("Instant", 0)
}

/** The ready-made boards (see ui/board/BoardThemes.kt), and [CUSTOM]: one of the player's own designs. */
enum class BoardTheme(val label: String) {
    GRAY("Matt's"), CLASSIC("Classic"), CLAUDE("Claude's"), WALNUT("Walnut & ivory"), NAVY("Navy tournament"),
    LEATHER("Red leather"), OLIVE("Olive & cream"), CONTRAST("High contrast"),
    /** The design in [Settings.customSlot]. */
    CUSTOM("My design"),
}

/** Which of the board's two checker colours is the human's (the computer gets the other). */
enum class CheckerSide { FIRST, SECOND }

/**
 * Checker looks. [CATALIN] (veined) and [SWIRL] (swirled resin) are flat with a pattern per checker
 * ([com.boldblot.backgammonplyground.ui.board.CheckerPatterns]). Catalin replaced Marble (0.7.15, internal)
 * and Domed: a stored "MARBLE" or "DOMED" reads as [CATALIN].
 */
enum class CheckerStyle(val label: String) { RIMMED("Rimmed"), FLAT("Flat"), DOUBLE_RING("Double ring"), CATALIN("Catalin"), SWIRL("Swirl") }

/** A board made in the board designer; colours are ARGB. */
@Serializable
data class CustomBoard(
    val name: String,
    val frame: Long,
    val surface: Long,
    val pointLight: Long,
    val pointDark: Long,
    val mine: Long,
    val theirs: Long,
    val numbers: Long,
    val highlight: Long,
)

const val CUSTOM_SLOTS = 4

data class Settings(
    val cpuLevel: CpuLevel = CpuLevel.MISTAKES,
    val matchLength: Int = 5,
    val useCube: Boolean = true,
    val jacoby: Boolean = true,
    val crawford: Boolean = true,
    /** Money games: beavers allowed, and with them raccoons. */
    val beavers: Boolean = false,
    val raccoons: Boolean = false,
    /** Opponent's error and blunder rates for [CpuLevel.MISTAKES]. */
    val mistakes: MistakeRate = MistakeRate.PR8,
    /** The engine the computer plays with in a new match. */
    val opponentEngine: EngineKind = EngineKind.PURETD,
    /** The engine that analyzes games, hints and positions. */
    val analysisEngine: EngineKind = EngineKind.PURETD,
    /** Analyze the computer's decisions too (its PR and review), at the same depths as the human's. Off by default: it doubles the work per move. */
    val cpuAnalysis: Boolean = false,
    /** The player's name in exported .mat files. */
    val playerName: String = "Player",
    /** Checker-play analysis depth (XG plies 1-4) and which moves are searched to that depth. */
    val checkerPlies: Int = 3,
    val moveFilter: MoveFilter = MoveFilter.WIDE,
    /** Cube analysis depth (XG plies 1-4); 4-ply halves the cube error of 3-ply against XG. */
    val cubePlies: Int = 4,
    val blunderSort: BlunderSort = BlunderSort.BIGGEST,
    val autoFinishSpeed: AutoFinishSpeed = AutoFinishSpeed.S03,
    val autoFinishDepth: AutoFinishDepth = AutoFinishDepth.ONE,
    /** Every roll (both sides' and the opening roll) is entered by the player instead of rolled. */
    val manualDice: Boolean = false,
    /** Matches with entered dice still count in statistics and rating (otherwise they are left out). */
    val manualDiceStats: Boolean = false,
    val feedback: FeedbackLevel = FeedbackLevel.ERRORS,
    val trainingMode: TrainingMode = TrainingMode.AFTER_GAME,
    val intuitionDepth: IntuitionDepth = IntuitionDepth.SAME,
    /** Wait for the player to dismiss the prompt before the computer continues. */
    val pauseOnMistake: Boolean = true,
    /** The mistake prompt draws the best move's arrows too (otherwise only the played move's). */
    val bestMoveArrows: Boolean = false,
    /** The summary after each game of a match or session (the end of a match always shows one). */
    val gameSummary: Boolean = true,
    /** Roll automatically when no cube decision is possible. */
    val autoRoll: Boolean = true,
    /** Submit the move as soon as it is complete (otherwise a Done button is shown). */
    val autoConfirm: Boolean = false,
    /** Play the human's move automatically when the roll can only be played one way. */
    val autoForced: Boolean = true,
    val animation: AnimationSpeed = AnimationSpeed.NORMAL,
    val showPips: Boolean = true,
    /** Win/gammon chances meter during play (off by default: it is a hint). */
    val meter: MeterMode = MeterMode.OFF,
    /** Analysis while nothing is shown during play ([analyzesInBackground]). */
    val analysisTiming: AnalysisTiming = AnalysisTiming.BACKGROUND,
    /** Play the human's bear-off for them once contact is broken (the analysis engine's best play). */
    val autoBearOff: Boolean = false,
    val cpuArrows: CpuArrows = CpuArrows.UNTIL_MOVE,
    /** Double and Pass ask for confirmation (a bar across the board). */
    val confirmDouble: Boolean = true,
    val confirmPass: Boolean = true,
    val blunderList: BlunderListLevel = BlunderListLevel.BLUNDERS,
    val errorsMode: ErrorsMode = ErrorsMode.MANUAL,
    /**
     * New errors' minimum (weighted, as PR counts it): what goes to New errors (Manual), or to My positions
     * (Fixed threshold); the floor of Adaptive and Top X. Replaces [blunderList] (0.7.11), which set its start.
     */
    val errorMin: Float = XgRulesDefaults.BLUNDER,
    /** Adaptive: errors above this share of My positions' average error go in. */
    val adaptiveRatio: Float = 0.9f,
    /** Top X per match: how many errors of a match (or money session) go in. */
    val topX: Int = 3,
    /** The automatic methods: New errors keeps this many of the errors left out, newest first. */
    val excludedKeep: Int = 50,
    /** Top X per match: errors filed so far, per match ("id:count"; the newest matches only). */
    val topXFiled: Set<String> = emptySet(),
    val errorsSort: ErrorsSort = ErrorsSort.SPLIT,
    val quizCorrect: QuizCorrect = QuizCorrect.NOT_ERROR,
    /** [QuizCorrect.FIXED]: an answer costing up to this is right. */
    val quizFixedError: Float = 0.04f,
    /** [QuizCorrect.ADAPTIVE]: an answer costing up to this share of My positions' average error is right (never below the error threshold). */
    val quizAdaptiveRatio: Float = 0.9f,
    val trainingPlan: TrainingPlan = TrainingPlan.CUSTOM,
    /** The quiz brings missed positions back until each has been answered right. */
    val quizRepeat: Boolean = false,
    /** Quiz answers to the player's own cube decisions, and to the opponent's doubles. */
    val quizMyCube: QuizMyCube = QuizMyCube.FULL,
    val quizTheirCube: QuizTheirCube = QuizTheirCube.TAKE_PASS,
    /**
     * "Move to Learned": a position whose quiz balance (right minus missed) reaches [quizDeleteAt] leaves
     * My positions: into Learned ([quizLearned]), or deleted at once when Learned is off.
     */
    val quizAutoDelete: Boolean = false,
    val quizDeleteAt: Int = 3,
    /** Learned positions are kept (out of the quiz and the folders) until [learnedKeep] newer ones push them out. */
    val quizLearned: Boolean = true,
    val learnedKeep: Int = 50,
    /** The saved quick quiz ([com.boldblot.backgammonplyground.ui.positions.QuizSpec.encode]); blank = the default. */
    val quickQuiz: String = "",
    /** The Quick quiz button on the main screen: to the right of My positions (or the left). */
    val quickQuizRight: Boolean = true,
    val boardTheme: BoardTheme = BoardTheme.GRAY,
    val checkerSide: CheckerSide = CheckerSide.FIRST,
    val checkerStyle: CheckerStyle = CheckerStyle.RIMMED,
    /** The board designer's save slots ([CUSTOM_SLOTS]; null = empty) and the one in use with [BoardTheme.CUSTOM]. */
    val customBoards: List<CustomBoard?> = List(CUSTOM_SLOTS) { null },
    val customSlot: Int = 0,
    /** Bear off to the left: your home board on the left (checkers move clockwise) instead of the right. */
    val mirrorBoard: Boolean = false,
    /** The version name whose welcome screen was last seen ("" = none yet). */
    val welcomeSeen: String = "",
    /** Advanced engine settings: threads for the game's searches and the background analysis (0 = default). */
    val gameThreads: Int = 0,
    val backgroundThreads: Int = 0,
    /** The background analysis runs during play (its own slot, PureTD only); otherwise between games. */
    val analyzeDuringPlay: Boolean = true,
    val backgroundPriority: BackgroundPriority = BackgroundPriority.LOW,
    /** In the Crawford game a "CRAWFORD" sign stands on the bar instead of the cube (off: the bar is empty). */
    val crawfordSign: Boolean = true,
    /** Errors in equity or in thousandths of it (mEq), everywhere. */
    val errorUnit: ErrorUnit = ErrorUnit.EQ,
    /** The quick cube check before the deep one during play ([QuickCubeCheck]). */
    val quickCubeCheck: QuickCubeCheck = QuickCubeCheck.OFF,
    /** The quiz shows the position's labels under the board. */
    val quizLabels: Boolean = true,
    /** The last finished speed test: when (0 = never) and what it recommended ("checker play 3-ply, pruned, cube decisions 3-ply"). */
    val speedTestAt: Long = 0L,
    val speedTestResult: String = "",
    /** Landscape: the side column (players, Undo / Done, menus) right of the board instead of left. */
    val railRight: Boolean = false,
    /** Landscape: Undo on the board and Done by the dice, instead of as chips in the side column. */
    val onBoardMoveButtons: Boolean = false,
    /** Automatic labels turned off in Settings → My positions ([com.boldblot.backgammonplyground.core.AutoLabel] names): hidden everywhere. */
    val labelsOff: Set<String> = emptySet(),
    /** The player's own labels, in their order. */
    val myLabels: List<String> = emptyList(),
    /** The blunder list's filter (label keys). */
    val blunderFilter: Set<String> = emptySet(),
    /** Checker blunders and Cube blunders were made in My positions (once; no longer made). */
    val builtInFolders: Boolean = false,
    /** The 0.7.4 folder changes were made ([com.boldblot.backgammonplyground.data.Positions.migrateFolders074]). */
    val folders074: Boolean = false,
) {
    /** The label keys in use: the automatic ones not turned off, then the player's own. */
    val labelKeys: List<String>
        get() = com.boldblot.backgammonplyground.core.AutoLabel.entries.filter { it.name !in labelsOff }.map { it.name } +
            myLabels.map { com.boldblot.backgammonplyground.core.Labels.myKey(it) }

    /** [keys] without the labels turned off or deleted. */
    fun visible(keys: Set<String>): Set<String> = keys.intersect(labelKeys.toSet())

    /**
     * These settings under [plan]. A plan's level becomes the minimum error and the quiz's fixed level; Adaptive
     * and Top X would treat it as a mere floor, so they give way to the fixed threshold. Custom changes nothing else.
     */
    fun withPlan(plan: TrainingPlan): Settings {
        val level = plan.level ?: return copy(trainingPlan = plan)
        return copy(
            trainingPlan = plan, errorMin = level, quizCorrect = QuizCorrect.FIXED, quizFixedError = level,
            errorsMode = if (errorsMode == ErrorsMode.MANUAL) ErrorsMode.MANUAL else ErrorsMode.AUTOMATIC,
        )
    }

    /** Whether an answer costing [error] (after the 1-point factor) is right; [average] is My positions' average error (Adaptive). */
    fun quizRight(error: Float, average: Float?): Boolean = when (quizCorrect) {
        QuizCorrect.NOT_ERROR -> !com.boldblot.backgammonplyground.core.XgRules.severity(error).atLeast(com.boldblot.backgammonplyground.core.Severity.ERROR)
        QuizCorrect.BEST_ONLY -> error < 0.0005f
        QuizCorrect.FIXED -> error <= quizFixedError
        QuizCorrect.ADAPTIVE -> error <= com.boldblot.backgammonplyground.core.NewErrors.adaptiveThreshold(
            com.boldblot.backgammonplyground.core.NewErrors.MIN_ERROR, quizAdaptiveRatio, average,
        )
    }

    /** What a new error must cost to enter New errors: the minimum with Manual; any error with the automatic methods (kept on a rolling list). */
    val newErrorsMin: Float get() = if (errorsMode == ErrorsMode.MANUAL) errorMin else XgRulesDefaults.ERROR

    /** Top X per match: errors of match [id] filed so far. */
    fun topXFiledOf(id: Long): Int = topXFiled.firstNotNullOfOrNull { e -> e.split(':').takeIf { it.size == 2 && it[0] == id.toString() }?.get(1)?.toIntOrNull() } ?: 0

    /** [topXFiled] with match [id] at [count], keeping the 30 newest matches. */
    fun withTopXFiled(id: Long, count: Int): Set<String> =
        (topXFiled.filterNot { it.startsWith("$id:") } + "$id:$count").sortedByDescending { it.substringBefore(':').toLongOrNull() ?: 0L }.take(30).toSet()

    val checkerDepth: Depth get() = Depth(checkerPlies, moveFilter)
    val cubeDepth: Depth get() = Depth(cubePlies)

    /** The depths decisions are analyzed at during play: in Intuition training [intuitionDepth] when set. */
    val playCheckerDepth: Depth
        get() = intuitionPlies?.let { Depth(it, moveFilter) } ?: checkerDepth
    val playCubeDepth: Depth
        get() = intuitionPlies?.let { Depth(it) } ?: cubeDepth
    private val intuitionPlies: Int?
        get() = intuitionDepth.plies.takeIf { it > 0 && trainingMode == TrainingMode.IMMEDIATE }

    /**
     * Nothing about the player's decisions is shown during play, so they need not be analyzed on the
     * spot: "after the match" training, or immediate training with the practice prompt off.
     */
    val quietPlay: Boolean get() = trainingMode == TrainingMode.AFTER_GAME || (trainingMode == TrainingMode.IMMEDIATE && feedback == FeedbackLevel.OFF)
}

private val Context.dataStore by preferencesDataStore("settings")


class SettingsStore(private val context: Context) {

    private object Keys {
        val level = stringPreferencesKey("cpu_level")
        val length = intPreferencesKey("match_length")
        val cube = booleanPreferencesKey("use_cube")
        val jacoby = booleanPreferencesKey("jacoby")
        val crawford = booleanPreferencesKey("crawford")
        val beavers = booleanPreferencesKey("beavers")
        val raccoons = booleanPreferencesKey("raccoons")
        val cpuAnalysis = booleanPreferencesKey("analyze_cpu")
        val opponentEngine = stringPreferencesKey("opponent_engine")
        val analysisEngine = stringPreferencesKey("analysis_engine")
        val playerName = stringPreferencesKey("player_name")
        val checkerPlies = intPreferencesKey("checker_plies")
        val moveFilter = stringPreferencesKey("move_filter")
        val welcomeSeen = stringPreferencesKey("welcome_shown_version")
        val cubePlies = intPreferencesKey("cube_plies")
        val errorRate = floatPreferencesKey("mistake_errors")
        val blunderRate = floatPreferencesKey("mistake_blunders")
        val mistakeCheckerPlies = intPreferencesKey("mistake_checker_plies")
        val mistakeCubePlies = intPreferencesKey("mistake_cube_plies")
        val maxMistake = floatPreferencesKey("mistake_max")
        val blunderSort = stringPreferencesKey("blunder_sort")
        val autoFinish = stringPreferencesKey("autofinish_speed")
        val autoFinishDepth = stringPreferencesKey("autofinish_depth")
        val intuitionDepth = stringPreferencesKey("intuition_depth")
        val manualDice = booleanPreferencesKey("manual_dice")
        val manualDiceStats = booleanPreferencesKey("manual_dice_stats")
        val feedback = stringPreferencesKey("feedback")
        val trainingMode = stringPreferencesKey("training_mode")
        val pause = booleanPreferencesKey("pause_on_mistake")
        val bestArrows = booleanPreferencesKey("best_move_arrows")
        val gameSummary = booleanPreferencesKey("game_summary")
        val autoRoll = booleanPreferencesKey("auto_roll")
        val autoConfirm = booleanPreferencesKey("auto_confirm")
        val autoForced = booleanPreferencesKey("auto_forced")
        val animation = stringPreferencesKey("animation")
        val pips = booleanPreferencesKey("show_pips")
        /** Build 37 and earlier: the meter on or off (on showed the numbers). */
        val oldMeter = booleanPreferencesKey("show_meter")
        val meter = stringPreferencesKey("meter_mode")
        val analysisTiming = stringPreferencesKey("analysis_timing")
        val autoBearOff = booleanPreferencesKey("auto_bear_off")
        val cpuArrows = stringPreferencesKey("cpu_arrows")
        val confirmDouble = booleanPreferencesKey("confirm_double")
        val confirmPass = booleanPreferencesKey("confirm_pass")
        val blunderList = stringPreferencesKey("blunder_list_level")
        val errorsMode = stringPreferencesKey("errors_mode")
        val errorMin = floatPreferencesKey("error_min")
        val adaptiveRatio = floatPreferencesKey("adaptive_ratio")
        val topX = intPreferencesKey("top_x")
        val excludedKeep = intPreferencesKey("excluded_keep")
        val topXFiled = androidx.datastore.preferences.core.stringSetPreferencesKey("top_x_filed")
        val errorsSort = stringPreferencesKey("errors_sort")
        val quizCorrect = stringPreferencesKey("quiz_correct")
        val quizFixedError = floatPreferencesKey("quiz_fixed_error")
        val quizAdaptiveRatio = floatPreferencesKey("quiz_adaptive_ratio")
        val trainingPlan = stringPreferencesKey("training_plan")
        val quizRepeat = booleanPreferencesKey("quiz_repeat")
        val quizMyCube = stringPreferencesKey("quiz_my_cube")
        val quizTheirCube = stringPreferencesKey("quiz_their_cube")
        val quizAutoDelete = booleanPreferencesKey("quiz_auto_delete")
        val quizDeleteAt = intPreferencesKey("quiz_delete_at")
        val quizLearned = booleanPreferencesKey("quiz_learned")
        val learnedKeep = intPreferencesKey("learned_keep")
        val quickQuiz = stringPreferencesKey("quick_quiz")
        val quickQuizRight = booleanPreferencesKey("quick_quiz_right")
        val theme = stringPreferencesKey("board_theme")
        val checker = stringPreferencesKey("checker_color")
        val checkerStyle = stringPreferencesKey("checker_style")
        val customBoards = stringPreferencesKey("custom_boards")
        val customSlot = intPreferencesKey("custom_slot")
        val mirror = booleanPreferencesKey("mirror_board")
        val gameThreads = intPreferencesKey("game_threads")
        val backgroundThreads = intPreferencesKey("background_threads")
        val analyzeDuringPlay = booleanPreferencesKey("analyze_during_play")
        val backgroundPriority = stringPreferencesKey("background_priority")
        val crawfordSign = booleanPreferencesKey("crawford_sign")
        val errorUnit = stringPreferencesKey("error_unit")
        val quickCubeCheck = stringPreferencesKey("quick_cube_check")
        val quizLabels = booleanPreferencesKey("quiz_labels")
        val speedTestAt = longPreferencesKey("speed_test_at")
        val speedTestResult = stringPreferencesKey("speed_test_result")
        val railRight = booleanPreferencesKey("rail_right")
        // "side_move_buttons" (0.7.11–0.7.12, side column instead of on the board) is retired: don't reuse it.
        val onBoardMoveButtons = booleanPreferencesKey("board_move_buttons")
        val labelsOff = stringPreferencesKey("labels_off")
        val myLabels = stringPreferencesKey("my_labels")
        val blunderFilter = stringPreferencesKey("blunder_filter")
        val builtInFolders = booleanPreferencesKey("built_in_folders")
        val folders074 = booleanPreferencesKey("folders_074")
    }

    private fun lines(text: String?): List<String> = text?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
        this[key]?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    private fun read(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            cpuLevel = p.enum(Keys.level, d.cpuLevel),
            matchLength = p[Keys.length] ?: d.matchLength,
            useCube = p[Keys.cube] ?: d.useCube,
            jacoby = p[Keys.jacoby] ?: d.jacoby,
            crawford = p[Keys.crawford] ?: d.crawford,
            beavers = p[Keys.beavers] ?: d.beavers,
            raccoons = p[Keys.raccoons] ?: d.raccoons,
            mistakes = MistakeRate(
                p[Keys.errorRate] ?: d.mistakes.errors, p[Keys.blunderRate] ?: d.mistakes.blunders,
                (p[Keys.mistakeCheckerPlies] ?: d.mistakes.checkerPlies).coerceIn(1, Depth.MAX_SEARCH_PLIES),
                (p[Keys.mistakeCubePlies] ?: d.mistakes.cubePlies).coerceIn(1, Depth.MAX_SEARCH_PLIES),
                p[Keys.maxMistake] ?: d.mistakes.maxMistake,
            ),
            cpuAnalysis = p[Keys.cpuAnalysis] ?: d.cpuAnalysis,
            opponentEngine = p.enum(Keys.opponentEngine, d.opponentEngine),
            analysisEngine = p.enum(Keys.analysisEngine, d.analysisEngine),
            playerName = p[Keys.playerName] ?: d.playerName,
            checkerPlies = (p[Keys.checkerPlies] ?: d.checkerPlies).coerceIn(1, Depth.MAX_SEARCH_PLIES),
            // Pruning is on or off now; the older ±0.04 and ±0.08 settings become on.
            moveFilter = p.enum(Keys.moveFilter, d.moveFilter).let { if (it == MoveFilter.ALL) it else MoveFilter.WIDE },
            cubePlies = (p[Keys.cubePlies] ?: d.cubePlies).coerceIn(1, Depth.MAX_SEARCH_PLIES),
            welcomeSeen = p[Keys.welcomeSeen] ?: d.welcomeSeen,
            blunderSort = p.enum(Keys.blunderSort, d.blunderSort),
            autoFinishSpeed = p.enum(Keys.autoFinish, d.autoFinishSpeed),
            autoFinishDepth = p.enum(Keys.autoFinishDepth, d.autoFinishDepth),
            intuitionDepth = p.enum(Keys.intuitionDepth, d.intuitionDepth),
            manualDice = p[Keys.manualDice] ?: d.manualDice,
            manualDiceStats = p[Keys.manualDiceStats] ?: d.manualDiceStats,
            feedback = p.enum(Keys.feedback, d.feedback),
            trainingMode = p.enum(Keys.trainingMode, d.trainingMode),
            pauseOnMistake = p[Keys.pause] ?: d.pauseOnMistake,
            bestMoveArrows = p[Keys.bestArrows] ?: d.bestMoveArrows,
            gameSummary = p[Keys.gameSummary] ?: d.gameSummary,
            autoRoll = p[Keys.autoRoll] ?: d.autoRoll,
            autoConfirm = p[Keys.autoConfirm] ?: d.autoConfirm,
            autoForced = p[Keys.autoForced] ?: d.autoForced,
            animation = p.enum(Keys.animation, d.animation),
            showPips = p[Keys.pips] ?: d.showPips,
            meter = p[Keys.meter]?.let { v -> MeterMode.entries.firstOrNull { it.name == v } }
                ?: if (p[Keys.oldMeter] == true) MeterMode.BAR_AND_NUMBERS else d.meter,
            analysisTiming = p.enum(Keys.analysisTiming, d.analysisTiming),
            autoBearOff = p[Keys.autoBearOff] ?: d.autoBearOff,
            cpuArrows = p.enum(Keys.cpuArrows, d.cpuArrows),
            confirmDouble = p[Keys.confirmDouble] ?: d.confirmDouble,
            confirmPass = p[Keys.confirmPass] ?: d.confirmPass,
            blunderList = p.enum(Keys.blunderList, d.blunderList),
            errorsMode = p.enum(Keys.errorsMode, d.errorsMode),
            // Until the slider is moved, "Errors and blunders" or "Blunders only" set it.
            errorMin = p[Keys.errorMin] ?: if (p.enum(Keys.blunderList, d.blunderList) == BlunderListLevel.ERRORS) XgRulesDefaults.ERROR else XgRulesDefaults.BLUNDER,
            adaptiveRatio = p[Keys.adaptiveRatio] ?: d.adaptiveRatio,
            topX = p[Keys.topX] ?: d.topX,
            excludedKeep = p[Keys.excludedKeep] ?: d.excludedKeep,
            topXFiled = p[Keys.topXFiled] ?: d.topXFiled,
            errorsSort = p.enum(Keys.errorsSort, d.errorsSort),
            quizCorrect = p.enum(Keys.quizCorrect, d.quizCorrect),
            quizFixedError = p[Keys.quizFixedError] ?: d.quizFixedError,
            quizAdaptiveRatio = p[Keys.quizAdaptiveRatio] ?: d.quizAdaptiveRatio,
            trainingPlan = p.enum(Keys.trainingPlan, d.trainingPlan),
            quizRepeat = p[Keys.quizRepeat] ?: d.quizRepeat,
            quizMyCube = p.enum(Keys.quizMyCube, d.quizMyCube),
            quizTheirCube = p.enum(Keys.quizTheirCube, d.quizTheirCube),
            quizAutoDelete = p[Keys.quizAutoDelete] ?: d.quizAutoDelete,
            quizDeleteAt = p[Keys.quizDeleteAt] ?: d.quizDeleteAt,
            quizLearned = p[Keys.quizLearned] ?: d.quizLearned,
            learnedKeep = p[Keys.learnedKeep] ?: d.learnedKeep,
            quickQuiz = p[Keys.quickQuiz] ?: d.quickQuiz,
            quickQuizRight = p[Keys.quickQuizRight] ?: d.quickQuizRight,
            boardTheme = p.enum(Keys.theme, d.boardTheme),
            // build 31 and earlier stored the grey board's colour, YELLOW (first) or RED (second)
            checkerSide = if (p[Keys.checker] in setOf("RED", "SECOND")) CheckerSide.SECOND else CheckerSide.FIRST,
            checkerStyle = if (p[Keys.checkerStyle] in setOf("DOMED", "MARBLE")) CheckerStyle.CATALIN else p.enum(Keys.checkerStyle, d.checkerStyle),
            customBoards = p[Keys.customBoards]
                ?.let { runCatching { AppJson.decodeFromString<List<CustomBoard?>>(it) }.getOrNull() }
                ?.let { l -> List(CUSTOM_SLOTS) { l.getOrNull(it) } } ?: d.customBoards,
            customSlot = (p[Keys.customSlot] ?: d.customSlot).coerceIn(0, CUSTOM_SLOTS - 1),
            mirrorBoard = p[Keys.mirror] ?: d.mirrorBoard,
            gameThreads = p[Keys.gameThreads] ?: d.gameThreads,
            backgroundThreads = p[Keys.backgroundThreads] ?: d.backgroundThreads,
            analyzeDuringPlay = p[Keys.analyzeDuringPlay] ?: d.analyzeDuringPlay,
            backgroundPriority = p.enum(Keys.backgroundPriority, d.backgroundPriority),
            crawfordSign = p[Keys.crawfordSign] ?: d.crawfordSign,
            errorUnit = p.enum(Keys.errorUnit, d.errorUnit),
            quickCubeCheck = p.enum(Keys.quickCubeCheck, d.quickCubeCheck),
            quizLabels = p[Keys.quizLabels] ?: d.quizLabels,
            speedTestAt = p[Keys.speedTestAt] ?: d.speedTestAt,
            speedTestResult = p[Keys.speedTestResult] ?: d.speedTestResult,
            railRight = p[Keys.railRight] ?: d.railRight,
            onBoardMoveButtons = p[Keys.onBoardMoveButtons] ?: d.onBoardMoveButtons,
            labelsOff = lines(p[Keys.labelsOff]).toSet(),
            myLabels = lines(p[Keys.myLabels]),
            blunderFilter = lines(p[Keys.blunderFilter]).toSet(),
            builtInFolders = p[Keys.builtInFolders] ?: d.builtInFolders,
            folders074 = p[Keys.folders074] ?: d.folders074,
        )
    }

    val settings: Flow<Settings> = context.dataStore.data.map { read(it) }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val s = transform(read(p))
            p[Keys.level] = s.cpuLevel.name
            p[Keys.length] = s.matchLength
            p[Keys.cube] = s.useCube
            p[Keys.jacoby] = s.jacoby
            p[Keys.crawford] = s.crawford
            p[Keys.beavers] = s.beavers
            p[Keys.raccoons] = s.raccoons
            p[Keys.errorRate] = s.mistakes.errors
            p[Keys.blunderRate] = s.mistakes.blunders
            p[Keys.mistakeCheckerPlies] = s.mistakes.checkerPlies
            p[Keys.mistakeCubePlies] = s.mistakes.cubePlies
            p[Keys.maxMistake] = s.mistakes.maxMistake
            p[Keys.cpuAnalysis] = s.cpuAnalysis
            p[Keys.opponentEngine] = s.opponentEngine.name
            p[Keys.analysisEngine] = s.analysisEngine.name
            p[Keys.playerName] = s.playerName
            p[Keys.checkerPlies] = s.checkerPlies
            p[Keys.moveFilter] = s.moveFilter.name
            p[Keys.welcomeSeen] = s.welcomeSeen
            p[Keys.cubePlies] = s.cubePlies
            p[Keys.blunderSort] = s.blunderSort.name
            p[Keys.autoFinish] = s.autoFinishSpeed.name
            p[Keys.autoFinishDepth] = s.autoFinishDepth.name
            p[Keys.intuitionDepth] = s.intuitionDepth.name
            p[Keys.manualDice] = s.manualDice
            p[Keys.manualDiceStats] = s.manualDiceStats
            p[Keys.feedback] = s.feedback.name
            p[Keys.trainingMode] = s.trainingMode.name
            p[Keys.pause] = s.pauseOnMistake
            p[Keys.bestArrows] = s.bestMoveArrows
            p[Keys.gameSummary] = s.gameSummary
            p[Keys.autoRoll] = s.autoRoll
            p[Keys.autoConfirm] = s.autoConfirm
            p[Keys.autoForced] = s.autoForced
            p[Keys.animation] = s.animation.name
            p[Keys.pips] = s.showPips
            p[Keys.meter] = s.meter.name
            p[Keys.analysisTiming] = s.analysisTiming.name
            p[Keys.autoBearOff] = s.autoBearOff
            p[Keys.cpuArrows] = s.cpuArrows.name
            p[Keys.confirmDouble] = s.confirmDouble
            p[Keys.confirmPass] = s.confirmPass
            p[Keys.blunderList] = s.blunderList.name
            p[Keys.errorsMode] = s.errorsMode.name
            p[Keys.errorMin] = s.errorMin
            p[Keys.adaptiveRatio] = s.adaptiveRatio
            p[Keys.topX] = s.topX
            p[Keys.excludedKeep] = s.excludedKeep
            p[Keys.topXFiled] = s.topXFiled
            p[Keys.errorsSort] = s.errorsSort.name
            p[Keys.quizCorrect] = s.quizCorrect.name
            p[Keys.quizFixedError] = s.quizFixedError
            p[Keys.quizAdaptiveRatio] = s.quizAdaptiveRatio
            p[Keys.trainingPlan] = s.trainingPlan.name
            p[Keys.quizRepeat] = s.quizRepeat
            p[Keys.quizMyCube] = s.quizMyCube.name
            p[Keys.quizTheirCube] = s.quizTheirCube.name
            p[Keys.quizAutoDelete] = s.quizAutoDelete
            p[Keys.quizDeleteAt] = s.quizDeleteAt
            p[Keys.quizLearned] = s.quizLearned
            p[Keys.learnedKeep] = s.learnedKeep
            p[Keys.quickQuiz] = s.quickQuiz
            p[Keys.quickQuizRight] = s.quickQuizRight
            p[Keys.theme] = s.boardTheme.name
            p[Keys.checker] = s.checkerSide.name
            p[Keys.checkerStyle] = s.checkerStyle.name
            p[Keys.customBoards] = AppJson.encodeToString(s.customBoards)
            p[Keys.customSlot] = s.customSlot
            p[Keys.mirror] = s.mirrorBoard
            p[Keys.gameThreads] = s.gameThreads
            p[Keys.backgroundThreads] = s.backgroundThreads
            p[Keys.analyzeDuringPlay] = s.analyzeDuringPlay
            p[Keys.backgroundPriority] = s.backgroundPriority.name
            p[Keys.crawfordSign] = s.crawfordSign
            p[Keys.errorUnit] = s.errorUnit.name
            p[Keys.quickCubeCheck] = s.quickCubeCheck.name
            p[Keys.quizLabels] = s.quizLabels
            p[Keys.speedTestAt] = s.speedTestAt
            p[Keys.speedTestResult] = s.speedTestResult
            p[Keys.railRight] = s.railRight
            p[Keys.onBoardMoveButtons] = s.onBoardMoveButtons
            p[Keys.labelsOff] = s.labelsOff.joinToString("\n")
            p[Keys.myLabels] = s.myLabels.joinToString("\n")
            p[Keys.blunderFilter] = s.blunderFilter.joinToString("\n")
            p[Keys.builtInFolders] = s.builtInFolders
            p[Keys.folders074] = s.folders074
        }
    }
}
