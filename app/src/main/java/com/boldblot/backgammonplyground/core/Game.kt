package com.boldblot.backgammonplyground.core

import kotlinx.serialization.Serializable

enum class Phase {
    /** Each player rolls one die to decide who starts. */
    OPENING,
    /** The player to act may double (if allowed) or roll. */
    PRE_ROLL,
    /** A double was offered; the other player must take or pass (or beaver, if allowed). */
    DOUBLED,
    /** Money only: the doubled player beavered; the doubler accepts the beaver or raccoons. */
    BEAVERED,
    /** Dice are rolled; the player to act must move. */
    MOVING,
    OVER,
}

@Serializable
data class Dice(val d1: Int, val d2: Int) {
    init {
        require(d1 in 1..6 && d2 in 1..6)
    }

    val isDouble: Boolean get() = d1 == d2
    val pips: Int get() = if (isDouble) 4 * d1 else d1 + d2

    /** Both dice, higher value first (the order in which dice are always presented). */
    val highFirst: List<Int> get() = listOf(maxOf(d1, d2), minOf(d1, d2))

    override fun toString() = "${maxOf(d1, d2)}${minOf(d1, d2)}"
}

/**
 * A recorded decision with everything needed to review it later.
 * [board] is the position before the decision; [dice] is set for checker plays.
 */
@Serializable
data class DecisionRecord(
    val player: Int,
    val kind: DecisionKind,
    val board: Board,
    val cube: CubeState,
    val score: List<Int>,
    val crawford: Boolean,
    val dice: Dice? = null,
    val move: Move? = null,
    val checker: CheckerAnalysis? = null,
    val cubeEval: CubeEval? = null,
    /** Raw error in normalized equity (before the 1-point factor); null if not analyzed. */
    val error: Float? = null,
    /** True if XG would count this as a decision (non-obvious). */
    val counted: Boolean = false,
    /** True if the player asked for a hint before deciding. */
    val hinted: Boolean = false,
    /** True if autofinish played this decision for the human (not analyzed, not counted). */
    val auto: Boolean = false,
    /** Money game with beavers allowed: cube decisions are judged with the beaver as an option. */
    val beavers: Boolean = false,
    /** "Try again" training: the player's first try at this decision (what PR counts); this record is the last try. */
    val firstTry: FirstTry? = null,
    /** Waiting for the background analysis (see [AnalysisSettings.deferred]); not analyzed or counted yet. */
    val pending: Boolean = false,
) {
    /** The error PR and the mistake lists use: the first try's when the decision was tried again. */
    val scoredError: Float? get() = firstTry?.error ?: error
}

/** The first try at a decision that was then tried again ([DecisionRecord.firstTry]). */
@Serializable
data class FirstTry(
    val kind: DecisionKind,
    val move: Move? = null,
    val error: Float? = null,
    val counted: Boolean = false,
    /** Tries after the first one. */
    val retries: Int = 1,
)

@Serializable
data class RollRecord(val player: Int, val dice: Dice, val luck: Float? = null, val opening: Boolean = false)

/** A single game. Immutable: every action returns a new state. */
@Serializable
data class GameState(
    val board: Board = Board.INITIAL,
    /** Player whose turn it is (on DOUBLED and BEAVERED: the player who doubled). */
    val turn: Int = Player.HUMAN,
    val phase: Phase = Phase.OPENING,
    val dice: Dice? = null,
    val cube: CubeState = CubeState(),
    val crawford: Boolean = false,
    val decisions: List<DecisionRecord> = emptyList(),
    val rolls: List<RollRecord> = emptyList(),
    val result: GameResult? = null,
    /** True while the first move of the game (made with the opening roll) is pending. */
    val openingMove: Boolean = false,
    /** Resignation offered by the player to act ([turn]), waiting for the opponent's answer. */
    val resignOffer: WinType? = null,
    /** Largest resignation per player that the opponent rejected in this game (no repeats). */
    val resignRejected: List<WinType?> = listOf(null, null),
) {
    /** Player who must respond to a double. */
    val responder: Int get() = Player.other(turn)

    fun cubeContext(rules: MatchRules, score: List<Int>, onRoll: Int = turn) =
        CubeContext(rules.length, score, crawford, rules.jacoby, cube, onRoll)

    /** Opening roll: each player rolls one die; ties are re-rolled by the caller. */
    fun openingRoll(humanDie: Int, cpuDie: Int, luck: Float? = null): GameState {
        require(phase == Phase.OPENING)
        require(humanDie != cpuDie) { "tied opening roll must be re-rolled" }
        val starter = if (humanDie > cpuDie) Player.HUMAN else Player.CPU
        val dice = Dice(humanDie, cpuDie)
        return copy(
            turn = starter, phase = Phase.MOVING, dice = dice, openingMove = true,
            rolls = rolls + RollRecord(starter, dice, luck, opening = true),
        )
    }

    fun roll(dice: Dice, luck: Float? = null): GameState {
        require(phase == Phase.PRE_ROLL)
        return copy(phase = Phase.MOVING, dice = dice, rolls = rolls + RollRecord(turn, dice, luck))
    }

    fun double(): GameState {
        require(phase == Phase.PRE_ROLL)
        return copy(phase = Phase.DOUBLED)
    }

    fun take(): GameState {
        require(phase == Phase.DOUBLED)
        return copy(phase = Phase.PRE_ROLL, cube = CubeState(cube.value * 2, responder))
    }

    /**
     * The responder beavers: takes and at once redoubles, keeping the cube (as in gnubg). With
     * [raccoons] the doubler answers first ([Phase.BEAVERED]); otherwise the cube is now 4×, theirs.
     */
    fun beaver(raccoons: Boolean): GameState {
        require(phase == Phase.DOUBLED)
        return if (raccoons) copy(phase = Phase.BEAVERED) else copy(phase = Phase.PRE_ROLL, cube = CubeState(cube.value * 4, responder))
    }

    /** The doubler plays on after a beaver: the cube is 4×, owned by the beaverer. */
    fun acceptBeaver(): GameState {
        require(phase == Phase.BEAVERED)
        return copy(phase = Phase.PRE_ROLL, cube = CubeState(cube.value * 4, responder))
    }

    /** The doubler raccoons: the cube goes to 8×, still owned by the beaverer. */
    fun raccoon(): GameState {
        require(phase == Phase.BEAVERED)
        return copy(phase = Phase.PRE_ROLL, cube = CubeState(cube.value * 8, responder))
    }

    /** Value of the cube as offered on the board (DOUBLED: 2×, BEAVERED: 4×), or null. */
    val offeredCube: Int?
        get() = when (phase) {
            Phase.DOUBLED -> cube.value * 2
            Phase.BEAVERED -> cube.value * 4
            else -> null
        }

    /** The responder passes: the doubler wins the current cube value. */
    fun pass(): GameState {
        require(phase == Phase.DOUBLED)
        return copy(phase = Phase.OVER, result = GameResult(turn, WinType.SINGLE, cube.value, byPass = true))
    }

    fun move(move: Move, rules: MatchRules): GameState {
        require(phase == Phase.MOVING)
        val newBoard = board.applyMove(turn, move)
        if (newBoard.off(turn) == CHECKERS_PER_SIDE) {
            return copy(
                board = newBoard, phase = Phase.OVER, dice = null, openingMove = false,
                result = Scoring.result(newBoard, turn, cube, rules),
            )
        }
        return copy(board = newBoard, turn = Player.other(turn), phase = Phase.PRE_ROLL, dice = null, openingMove = false)
    }

    /** The player to act offers to resign; the opponent must accept or reject. */
    fun offerResign(type: WinType): GameState {
        require(phase == Phase.PRE_ROLL || phase == Phase.MOVING)
        return copy(resignOffer = type)
    }

    fun rejectResign(): GameState {
        val type = requireNotNull(resignOffer)
        return copy(resignOffer = null, resignRejected = resignRejected.toMutableList().also { it[turn] = type })
    }

    /** The opponent accepts the pending resignation: the game ends. */
    fun acceptResign(rules: MatchRules): GameState {
        val type = requireNotNull(resignOffer)
        return copy(
            phase = Phase.OVER, resignOffer = null,
            result = Resignation.result(Player.other(turn), type, cube, rules),
        )
    }

    fun record(decision: DecisionRecord): GameState = copy(decisions = decisions + decision)

    fun replaceLastDecision(decision: DecisionRecord): GameState =
        copy(decisions = decisions.dropLast(1) + decision)

    fun mayDouble(rules: MatchRules, score: List<Int>): Boolean =
        phase == Phase.PRE_ROLL && Scoring.mayDouble(rules, cube, turn, score, crawford)

    /** The opponent's roll just before [player]'s current turn; null if [player] is not to act. */
    fun opponentLastRoll(player: Int): Dice? {
        if (turn != player || phase == Phase.OVER || phase == Phase.OPENING) return null
        val earlier = if (phase == Phase.MOVING) rolls.dropLast(1) else rolls
        return earlier.lastOrNull()?.takeIf { it.player != player }?.dice
    }
}

/** A match (or money session) in progress. */
@Serializable
data class MatchState(
    val id: Long = 0,
    val rules: MatchRules,
    val cpuLevel: CpuLevel,
    /** Error and blunder rates when [cpuLevel] is [CpuLevel.MISTAKES]. */
    val mistakes: MistakeRate? = null,
    /** The engine the computer plays with. */
    val engine: EngineKind = EngineKind.PURETD,
    val score: List<Int> = listOf(0, 0),
    val gameNumber: Int = 1,
    /** True once the Crawford game has been played (or started). */
    val crawfordDone: Boolean = false,
    val game: GameState = GameState(),
    val winner: Int? = null,
    val startedAt: Long = System.currentTimeMillis(),
) {
    val isOver: Boolean get() = winner != null

    /** How the opponent is stored with the match ([Opponent.key]) and shown. */
    val opponentKey: String get() = Opponent.key(cpuLevel, mistakes, engine)
    val opponentLabel: String get() = Opponent.name(cpuLevel, mistakes, engine)

    fun ctx(onRoll: Int = game.turn) = game.cubeContext(rules, score, onRoll)

    /** Applies the finished game's result to the score; returns the match ready for the next game. */
    fun finishGame(): MatchState {
        val result = requireNotNull(game.result)
        val newScore = score.toMutableList().also { it[result.winner] += result.points }
        if (!rules.isMoney && newScore[result.winner] >= rules.length) {
            return copy(score = newScore, winner = result.winner)
        }
        val crawfordNext = !rules.isMoney && rules.crawford && !crawfordDone &&
            newScore[result.winner] == rules.length - 1
        return copy(
            score = newScore,
            gameNumber = gameNumber + 1,
            crawfordDone = crawfordDone || crawfordNext,
            game = GameState(crawford = crawfordNext),
        )
    }
}
