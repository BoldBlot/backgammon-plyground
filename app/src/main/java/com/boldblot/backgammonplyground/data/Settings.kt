package com.boldblot.backgammonplyground.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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

/** Which of the human's mistakes go on the blunder list. */
enum class BlunderListLevel(val label: String, val minSeverity: Severity) {
    BLUNDERS("Blunders only", Severity.BLUNDER),
    ERRORS("Errors and blunders", Severity.ERROR),
}

/** How long the arrows of the computer's last move stay on the board. */
enum class CpuArrows(val label: String) { UNTIL_MOVE("Until my move"), ONE_SECOND("1 second"), OFF("Off") }

/** The winning chances meter during play. */
enum class MeterMode(val label: String) { OFF("Off"), BAR("Bar"), BAR_AND_NUMBERS("Bar and numbers") }

/** What counts as a right answer in the My positions quiz. */
enum class QuizCorrect(val label: String) { NOT_ERROR("Not an error"), BEST_ONLY("Best play only") }

/** Order of the blunder list. */
enum class BlunderSort(val label: String) { BIGGEST("Biggest first"), NEWEST("Newest first") }

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

enum class CheckerStyle(val label: String) { RIMMED("Rimmed"), FLAT("Flat"), DOUBLE_RING("Double ring"), DOMED("Domed") }

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
    val autoFinishSpeed: AutoFinishSpeed = AutoFinishSpeed.S05,
    val feedback: FeedbackLevel = FeedbackLevel.ERRORS,
    val trainingMode: TrainingMode = TrainingMode.AFTER_GAME,
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
    val quizCorrect: QuizCorrect = QuizCorrect.NOT_ERROR,
    /** The quiz brings missed positions back until each has been answered right. */
    val quizRepeat: Boolean = false,
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
    /** Landscape: the side column (players, Undo / Done, menus) right of the board instead of left. */
    val railRight: Boolean = false,
    /** Automatic labels turned off in Settings → My positions ([com.boldblot.backgammonplyground.core.AutoLabel] names): hidden everywhere. */
    val labelsOff: Set<String> = emptySet(),
    /** The player's own labels, in their order. */
    val myLabels: List<String> = emptyList(),
    /** The blunder list's filter (label keys). */
    val blunderFilter: Set<String> = emptySet(),
    /** Checker blunders and Cube blunders were made in My positions (once). */
    val builtInFolders: Boolean = false,
) {
    /** The label keys in use: the automatic ones not turned off, then the player's own. */
    val labelKeys: List<String>
        get() = com.boldblot.backgammonplyground.core.AutoLabel.entries.filter { it.name !in labelsOff }.map { it.name } +
            myLabels.map { com.boldblot.backgammonplyground.core.Labels.myKey(it) }

    /** [keys] without the labels turned off or deleted. */
    fun visible(keys: Set<String>): Set<String> = keys.intersect(labelKeys.toSet())

    val checkerDepth: Depth get() = Depth(checkerPlies, moveFilter)
    val cubeDepth: Depth get() = Depth(cubePlies)

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
        val quizCorrect = stringPreferencesKey("quiz_correct")
        val quizRepeat = booleanPreferencesKey("quiz_repeat")
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
        val railRight = booleanPreferencesKey("rail_right")
        val labelsOff = stringPreferencesKey("labels_off")
        val myLabels = stringPreferencesKey("my_labels")
        val blunderFilter = stringPreferencesKey("blunder_filter")
        val builtInFolders = booleanPreferencesKey("built_in_folders")
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
            quizCorrect = p.enum(Keys.quizCorrect, d.quizCorrect),
            quizRepeat = p[Keys.quizRepeat] ?: d.quizRepeat,
            boardTheme = p.enum(Keys.theme, d.boardTheme),
            // build 31 and earlier stored the grey board's colour, YELLOW (first) or RED (second)
            checkerSide = if (p[Keys.checker] in setOf("RED", "SECOND")) CheckerSide.SECOND else CheckerSide.FIRST,
            checkerStyle = p.enum(Keys.checkerStyle, d.checkerStyle),
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
            railRight = p[Keys.railRight] ?: d.railRight,
            labelsOff = lines(p[Keys.labelsOff]).toSet(),
            myLabels = lines(p[Keys.myLabels]),
            blunderFilter = lines(p[Keys.blunderFilter]).toSet(),
            builtInFolders = p[Keys.builtInFolders] ?: d.builtInFolders,
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
            p[Keys.quizCorrect] = s.quizCorrect.name
            p[Keys.quizRepeat] = s.quizRepeat
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
            p[Keys.railRight] = s.railRight
            p[Keys.labelsOff] = s.labelsOff.joinToString("\n")
            p[Keys.myLabels] = s.myLabels.joinToString("\n")
            p[Keys.blunderFilter] = s.blunderFilter.joinToString("\n")
            p[Keys.builtInFolders] = s.builtInFolders
        }
    }
}
