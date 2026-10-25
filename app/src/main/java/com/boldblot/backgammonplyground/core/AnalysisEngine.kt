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
    MAXIMUM(8, "Maximum", "3-ply, every legal move"),

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
) {
    /** Equity after doubling, given the opponent's best response. */
    val afterDouble: Float get() = minOf(doubleTake, doublePass)
}

/** Short depth code for 3-ply on every legal move ("Maximum"); see [Depth]. */
const val MAX_PLIES = 4

/** Root filter of the checker search: moves within [within] 1-ply equity of the best are searched deeper. */
enum class MoveFilter(val within: Float, val label: String) {
    NARROW(0.04f, "±0.04"),
    NORMAL(0.08f, "±0.08"),
    WIDE(0.16f, "±0.16"),
    ALL(Float.POSITIVE_INFINITY, "all moves"),
}

/**
 * An analysis depth: XG plies (1 = network only, up to [MAX_SEARCH_PLIES]) and, for checker play,
 * the root [filter]. Passed to the engine and stored with analyses as [code]: 10 × plies + filter
 * (11-43), or the short codes 0 = 1-ply, 1-3 = 3-ply within 0.04 / 0.08 / 0.16 and 4 = 3-ply for
 * every legal move.
 */
data class Depth(val plies: Int, val filter: MoveFilter = MoveFilter.ALL) {
    val code: Int get() = 10 * plies + filter.ordinal

    /** "1-ply", "3-ply ±0.16", "4-ply, all moves"; [cube] leaves the filter out. */
    fun label(cube: Boolean = false): String = when {
        plies <= 1 || cube -> "$plies-ply"
        filter == MoveFilter.ALL -> "$plies-ply, all moves"
        else -> "$plies-ply ${filter.label}"
    }

    companion object {
        const val MAX_SEARCH_PLIES = 4

        fun of(code: Int): Depth = when {
            code >= 10 -> Depth((code / 10).coerceIn(1, MAX_SEARCH_PLIES), MoveFilter.entries[(code % 10).coerceAtMost(MoveFilter.ALL.ordinal)])
            code <= 0 -> Depth(1)
            code >= MAX_PLIES -> Depth(3, MoveFilter.ALL)
            else -> Depth(3, MoveFilter.entries[code - 1])
        }
    }
}

/** Label of a stored analysis depth code (see [Depth]). */
fun plyLabel(plies: Int): String = Depth.of(plies).label()

/** Engines the app can play and analyze with. */
enum class EngineKind(val label: String) { PURETD("PureTD") }

/** The evaluation engine (PureTD). All methods are blocking. */
interface AnalysisEngine {
    fun rankMoves(board: Board, ctx: CubeContext, d1: Int, d2: Int, plies: Int, played: Move?): RankResult
    fun chooseMove(board: Board, ctx: CubeContext, d1: Int, d2: Int, level: CpuLevel): Move
    fun cubeDecision(board: Board, ctx: CubeContext, plies: Int): CubeEval
    fun cubeDecisionAtLevel(board: Board, ctx: CubeContext, level: CpuLevel): CubeEval
    fun luck(board: Board, ctx: CubeContext, d1: Int, d2: Int, firstRoll: Boolean): Float
    fun positionId(board: Board, onRoll: Int): String
    fun matchId(ctx: CubeContext, d1: Int, d2: Int): String
}
