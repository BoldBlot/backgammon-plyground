package com.boldblot.backgammonplyground.core

import kotlinx.serialization.Serializable

/** Computer strengths (depth and noise: see engine/PureTdEngine). */
enum class CpuLevel(val engineLevel: Int, val label: String, val description: String) {
    BEGINNER(0, "Beginner", "1-ply, heavy noise"),
    CASUAL(1, "Casual", "1-ply, noise"),
    INTERMEDIATE(2, "Intermediate", "1-ply, light noise"),
    ADVANCED(3, "Advanced", "1-ply, slight noise"),
    EXPERT(4, "Expert", "1-ply, no noise"),
    WORLD_CLASS(5, "World Class", "3-ply, moves within 0.04"),
    SUPREMO(6, "Supremo", "3-ply, moves within 0.08"),
    GRANDMASTER(7, "Grandmaster", "3-ply, moves within 0.16"),
    /** PR <2: 2-ply on every legal move, cube 2-ply (faster than [MAXIMUM]). */
    STRONG(10, "Strong", "2-ply, every legal move"),
    /** PR <1: 3-ply on every legal move, cube 3-ply. */
    MAXIMUM(8, "Maximum", "3-ply, every legal move"),

    /**
     * Match play with PureTD: checker play [GameSearch.G36], cube [GameSearch.C44] (short played-out games).
     * In money games, and with the other engines, it plays as [MAXIMUM].
     */
    NEW_BEST(11, "New Best (experimental)", "Matches: 3-ply, then short played-out games"),

    /** Plays at Grandmaster depth but makes errors and blunders at a set rate ([MistakeRate]). */
    MISTAKES(9, "Mistake rate", "Errors and blunders at a set rate");

    companion object {
        /** Levels defined by depth and noise. */
        val classic: List<CpuLevel> get() = entries.filter { it != MISTAKES }
    }
}

/** One ranked candidate play. Equity is cubeful and normalized (EMG) for the mover. */
@Serializable
data class Candidate(
    val move: Move,
    val equity: Float,
    /** Cubeless: win, win gammon, win backgammon, lose gammon, lose backgammon. */
    val probs: List<Float>,
    val plies: Int,
)

data class RankResult(val candidates: List<Candidate>, val playedIndex: Int)

/** Cube decision equities, normalized, from the point of view of the player on roll. */
@Serializable
data class CubeEval(
    val noDouble: Float,
    val doubleTake: Float,
    val doublePass: Float,
    val optimal: Float,
    val shouldDouble: Boolean,
    val shouldTake: Boolean,
    val optional: Boolean,
    val cubeAvailable: Boolean,
    val probs: List<Float>,
    /** The engine that evaluated it ("PureTD"); null in analyses saved before 0.7.11. */
    val engine: String? = null,
    /** Its depth ([Depth] code); null in analyses saved before 0.7.11. */
    val plies: Int? = null,
    /** Standard error of No double minus Double/take, when the decision came from played-out games ([GameSearch]). */
    val spread: Float? = null,
) {
    /** "PureTD 4-ply"; "Engine unknown, 4-ply" or "Engine unknown" for what older analyses recorded. */
    val label: String get() {
        val depth = plies?.let { Depth.of(it).label(cube = true) }
        return if (engine != null) listOfNotNull(engine, depth).joinToString(" ") else listOfNotNull(UNKNOWN_ENGINE, depth).joinToString(", ")
    }

    /** Equity after doubling, given the opponent's best response. */
    val afterDouble: Float get() = minOf(doubleTake, doublePass)
}

/** Short depth code for 3-ply on every legal move ("Maximum"); see [Depth]. */
const val MAX_PLIES = 4

/** Moves always searched deeper by a pruned checker search, whatever their 1-ply gap to the best. */
const val MIN_SEARCHED = 5

/**
 * Most moves a pruned checker search takes deeper (the best at 1-ply, plus the move played). With
 * many plays inside the window (big doubles in an endgame) every one of them at 3- or 4-ply took
 * minutes on a phone; past the 16th best at 1-ply the right play is practically never found.
 */
const val MAX_SEARCHED = 16

/**
 * A game the mover can no longer win, or no longer lose, with no gammon or backgammon still at
 * stake between the plays (1-ply chances within this of each other): every play is worth the same,
 * so the search stops at 1-ply.
 */
const val DECIDED_CHANCE = 0.0005f

/**
 * Root filter of the checker search: the [MIN_SEARCHED] best moves at 1-ply and every move within
 * [within] of the best are searched deeper, at most [MAX_SEARCHED] of them ([ALL]: every move).
 * Settings offer pruning on ([WIDE]) or off ([ALL]);
 * [NARROW] and [NORMAL] remain for the computer's levels and for analyses stored with them.
 */
enum class MoveFilter(val within: Float, val label: String) {
    NARROW(0.04f, "±0.04"),
    NORMAL(0.08f, "±0.08"),
    WIDE(0.16f, "pruned"),
    ALL(Float.POSITIVE_INFINITY, "all moves"),
}

/**
 * Searches that end in short played-out games (match play, PureTD only; elsewhere they fall back to [plies]
 * with pruning). Named X/YY/Z: X plies first, then YY games of Z turns each. Checker play: every play at
 * 1-ply, [plies] for the 16 best within 0.16, then the games for the [plays] best within [within], all on the
 * same dice. Cube decisions: [plies] first, then the games for No double and Double/take when [plies] leaves
 * the decision closer than [CUBE_CLEAR]. [FAST4] has no games: 2-ply, 3-ply (8 plays within 0.08), then 4-ply
 * for the 3 best within 0.04. [code] is what analyses store (see [Depth]); never change one.
 */
enum class GameSearch(val code: Int, val label: String, val cube: Boolean, val plies: Int, val games: Int, val turns: Int, val plays: Int = 0, val within: Float = 0f) {
    FAST4(50, "4-ply, fast", false, 4, 0, 0, 3, 0.04f),
    G36(51, "3/36/7", false, 3, 36, 7, 3, 0.04f),
    G108(52, "3/108/7", false, 3, 108, 7, 4, 0.06f),
    G144(53, "3/144/7", false, 3, 144, 7, 4, 0.06f),
    G288(54, "3/288/7", false, 3, 288, 7, 5, 0.08f),
    C44(61, "3/44/7", true, 3, 44, 7),
    C48(62, "3/48/11", true, 3, 48, 11),
    C288(63, "3/288/11", true, 3, 288, 11);

    companion object {
        /** A cube decision [plies] settles by this much (normalized equity) is not played out. */
        const val CUBE_CLEAR = 0.1f
        val checker: List<GameSearch> get() = entries.filter { !it.cube }
        val cubes: List<GameSearch> get() = entries.filter { it.cube }
        fun of(code: Int): GameSearch? = entries.firstOrNull { it.code == code }
    }
}

/**
 * An analysis depth: XG plies (1 = network only, up to [MAX_SEARCH_PLIES]) and, for checker play,
 * the root [filter]; or a [search] by played-out games (then [plies] is its first stage).
 * Passed to the engine and stored with analyses as [code]: 10 × plies + filter (11-43), a
 * [GameSearch.code] (50-69), or the short codes 0 = 1-ply, 1-3 = 3-ply within 0.04 / 0.08 / 0.16 and
 * 4 = 3-ply for every legal move. 100 more marks a search the user stopped early ([incomplete]): its
 * values are those of the last stage that finished, or of the games played so far.
 *
 * Settings hand the engines one number for both kinds of game ([dual]): the engine takes the half
 * that fits the position ([resolve]) and records that.
 */
data class Depth(val plies: Int, val filter: MoveFilter = MoveFilter.ALL, val search: GameSearch? = null, val incomplete: Boolean = false) {
    val code: Int get() = (search?.code ?: (10 * plies + filter.ordinal)) + (if (incomplete) INCOMPLETE else 0)

    /** "1-ply", "3-ply, pruned", "4-ply, all moves", "3-ply ±0.04", "3/144/7"; [cube] leaves the filter out. */
    fun label(cube: Boolean = false): String = when {
        search != null -> search.label
        plies <= 1 || cube -> "$plies-ply"
        filter == MoveFilter.ALL || filter == MoveFilter.WIDE -> "$plies-ply, ${filter.label}"
        else -> "$plies-ply ${filter.label}"
    } + (if (incomplete) ", incomplete" else "")

    /** The depth without its played-out games: what a money game or another engine searches instead. */
    val plain: Depth get() = if (search == null) this else Depth(search.plies, MoveFilter.WIDE, incomplete = incomplete)

    companion object {
        const val MAX_SEARCH_PLIES = 4
        /** Added to a code when the search was stopped early. */
        const val INCOMPLETE = 100
        private const val DUAL = 1000

        fun of(code: Int): Depth = when {
            code >= DUAL -> of(code % DUAL)
            code >= INCOMPLETE -> of(code - INCOMPLETE).copy(incomplete = true)
            code >= 50 -> GameSearch.of(code)?.let { Depth(it.plies, MoveFilter.WIDE, it) } ?: Depth(MAX_SEARCH_PLIES, MoveFilter.WIDE)
            code >= 10 -> Depth((code / 10).coerceIn(1, MAX_SEARCH_PLIES), MoveFilter.entries[(code % 10).coerceAtMost(MoveFilter.ALL.ordinal)])
            code <= 0 -> Depth(1)
            code >= MAX_PLIES -> Depth(3, MoveFilter.ALL)
            else -> Depth(3, MoveFilter.entries[code - 1])
        }

        /** One number for [money] games and [match] play (both plain codes). */
        fun dual(money: Int, match: Int): Int = if (money == match) money else money + DUAL * (match + 1)

        /** The plain code of [code] for a match ([match]) or a money game; plain codes come back as they are. */
        fun resolve(code: Int, match: Boolean): Int = if (code < DUAL) code else if (match) code / DUAL - 1 else code % DUAL
    }
}

/**
 * What a running search tells the screen, and how the screen stops it. The engine reports from its own
 * thread; [requestStop] may come from any thread. A stopped search returns what it has ([Depth.incomplete]).
 */
class SearchMonitor {
    @Volatile var stop: Boolean = false
        private set
    /** What is being worked on ("3-ply on 16 plays", "Games"), with [done] of [total] steps (0 of 0: not counted). */
    @Volatile var what: String = ""
        private set
    @Volatile var done: Int = 0
        private set
    @Volatile var total: Int = 0
        private set
    /** Set by the engine: makes its native search return at once. */
    @Volatile var onStop: (() -> Unit)? = null

    fun report(what: String, done: Int, total: Int) { this.what = what; this.done = done; this.total = total }
    fun requestStop() { stop = true; onStop?.invoke() }
}

/** What an analysis label says when the engine was not recorded (analyses made before 0.7.11). */
const val UNKNOWN_ENGINE = "Engine unknown"

/** Label of a stored analysis depth code (see [Depth]). */
fun plyLabel(plies: Int): String = Depth.of(plies).label()

/** Engines the app can play and analyze with. */
enum class EngineKind(val label: String) { PURETD("PureTD"), GNUBG("GNU BG"), SAGE("Open Sage") }

/**
 * PureTD's two network sizes (Advanced engine settings). [engineName] is what an analysis records:
 * the wide net keeps the plain "PureTD" every earlier analysis carries.
 */
enum class PureTdNet(val label: String, val cubeless: String, val cubeful: String, val engineName: String, val note: String) {
    WIDE("Wide 5L", "prob5_wide.ptw", "cubeful_wide.ptw", "PureTD", "Five hidden layers (768, 768, 256, 256, 256). The strongest."),
    COMPACT("Compact 4L", "prob5_compact.ptw", "cubeful_compact.ptw", "PureTD 4L", "Four hidden layers (512, 512, 256, 256): about 1.9 times faster, but weaker in full games (6 to 13 mEq per game in tests)."),
}

/** An evaluation engine (PureTD, GNU Backgammon, Open Sage). All methods are blocking. */
interface AnalysisEngine {
    /** Progress and stop requests of the searches run while it is set (one-position analyses the user waits for). */
    var monitor: SearchMonitor?
        get() = null
        set(_) {}
    /** "PureTD", "GNU BG", "Open Sage"; recorded with each analysis. */
    val name: String get() = ""
    fun rankMoves(board: Board, ctx: CubeContext, d1: Int, d2: Int, plies: Int, played: Move?): RankResult
    fun chooseMove(board: Board, ctx: CubeContext, d1: Int, d2: Int, level: CpuLevel): Move
    /** The best play of this roll at 1-ply: its mover's cubeless chances, or null without a legal move. */
    fun bestPlayChances(board: Board, ctx: CubeContext, d1: Int, d2: Int): List<Float>? =
        rankMoves(board, ctx, d1, d2, 0, null).candidates.firstOrNull()?.probs
    fun cubeDecision(board: Board, ctx: CubeContext, plies: Int): CubeEval
    fun cubeDecisionAtLevel(board: Board, ctx: CubeContext, level: CpuLevel): CubeEval
    /** [cubeDecision] if it was the engine's last cube evaluation (free), otherwise null. */
    fun cachedCube(board: Board, ctx: CubeContext, plies: Int): CubeEval? = null
    fun luck(board: Board, ctx: CubeContext, d1: Int, d2: Int, firstRoll: Boolean): Float
    fun positionId(board: Board, onRoll: Int): String
    fun matchId(ctx: CubeContext, d1: Int, d2: Int): String
}
