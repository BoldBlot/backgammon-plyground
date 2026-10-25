package com.boldblot.backgammonplyground.core

import java.security.SecureRandom

/** Source of dice rolls. */
fun interface DiceSource {
    fun die(): Int

    fun roll(): Dice = Dice(die(), die())

    companion object {
        /** Cryptographically strong, unbiased dice. */
        fun secure(): DiceSource {
            val rnd = SecureRandom()
            return DiceSource { rnd.nextInt(6) + 1 }
        }
    }
}

data class AnalysisSettings(
    /** Depth used to analyze checker plays (a [Depth] code, see [plyLabel]). */
    val plies: Int = MAX_PLIES,
    /** Depth used to analyze cube decisions, and for winning chances (a [Depth] code). */
    val cubePlies: Int = MAX_PLIES,
    /** Compute luck for every roll (0-ply, cheap). */
    val luck: Boolean = true,
    /** Depth for the computer's checker plays (for its PR and for review); null = the computer is not analyzed. */
    val cpuPlies: Int? = MAX_PLIES,
) {
    val analyzeCpu: Boolean get() = cpuPlies != null
}

/** Shown to the human after a decision (practice mode). */
data class Feedback(
    val decision: DecisionRecord,
    /** Error after the 1-point-match factor, as used for classification. */
    val weightedError: Float,
    val severity: Severity,
)

/**
 * Drives a match: applies actions, asks the engine for the computer's decisions
 * and analyzes every decision. All methods are synchronous and must be called
 * from the engine thread.
 */
class MatchController(
    private val engine: AnalysisEngine,
    private val dice: DiceSource,
    var settings: AnalysisSettings = AnalysisSettings(),
) {
    private val factorCache = HashMap<Int, Float>()

    private fun factor(m: MatchState) = factorCache.getOrPut(m.rules.length) { XgRules.errorFactor(m.rules.length) }

    private var maker: Pair<MistakeRate, MistakeMaker>? = null

    /** The mistake-rate opponent's decision maker, or null for the other levels. */
    private fun mistakeMaker(m: MatchState): MistakeMaker? {
        val rate = m.mistakes?.takeIf { m.cpuLevel == CpuLevel.MISTAKES } ?: return null
        maker?.let { (r, mm) -> if (r == rate) return mm }
        return MistakeMaker(rate).also { maker = rate to it }
    }

    // ---- game start ---------------------------------------------------------

    /** Rolls the opening dice (re-rolling ties) and returns the game ready for the first move. */
    fun openingRoll(m: MatchState): MatchState {
        require(m.game.phase == Phase.OPENING)
        var h: Int
        var c: Int
        do {
            h = dice.die()
            c = dice.die()
        } while (h == c)
        val starter = if (h > c) Player.HUMAN else Player.CPU
        val luck = if (settings.luck) engine.luck(m.game.board, m.ctx(starter), h, c, firstRoll = true) else null
        return m.copy(game = m.game.openingRoll(h, c, luck))
    }

    // ---- shared helpers -----------------------------------------------------

    private fun rollDice(m: MatchState): MatchState {
        val d = dice.roll()
        val luck = if (settings.luck) engine.luck(m.game.board, m.ctx(), d.d1, d.d2, firstRoll = false) else null
        return m.copy(game = m.game.roll(d, luck))
    }

    private fun baseRecord(m: MatchState, player: Int, kind: DecisionKind) = DecisionRecord(
        player = player, kind = kind, board = m.game.board, cube = m.game.cube,
        score = m.score, crawford = m.game.crawford,
        beavers = kind != DecisionKind.CHECKER && m.rules.beaversAllowed,
    )

    private fun feedbackFor(m: MatchState, d: DecisionRecord): Feedback? {
        val err = d.error ?: return null
        val w = err * factor(m)
        return Feedback(d, w, XgRules.severity(w))
    }

    /** Analyzes a checker play at [plies]; [played] must be legal for the current dice. */
    fun analyzeChecker(board: Board, ctx: CubeContext, d: Dice, played: Move?, plies: Int = settings.plies): CheckerAnalysis {
        val r = engine.rankMoves(board, ctx, d.d1, d.d2, plies, played)
        val all = r.candidates
        if (all.isEmpty()) {
            return CheckerAnalysis(emptyList(), -1, 0f, 0f, 0f, legalMoves = 0, plies = plies)
        }
        val keep = all.take(MAX_CANDIDATES).toMutableList()
        var playedIndex = r.playedIndex
        if (playedIndex >= MAX_CANDIDATES) {
            keep += all[playedIndex]
            playedIndex = keep.lastIndex
        }
        val playedEq = if (r.playedIndex >= 0) all[r.playedIndex].equity else all[0].equity
        return CheckerAnalysis(
            candidates = keep,
            playedIndex = playedIndex,
            playedEquity = playedEq,
            bestEquity = all[0].equity,
            worstEquity = all.minOf { it.equity },
            legalMoves = all.size,
            plies = plies,
        )
    }

    /** Records a checker play, analyzed at [plies] (null = not analyzed). */
    private fun recordChecker(m: MatchState, move: Move, hinted: Boolean, plies: Int?, auto: Boolean = false): Pair<MatchState, DecisionRecord> {
        val g = m.game
        val d = requireNotNull(g.dice)
        val player = g.turn
        val analysis = plies?.let { analyzeChecker(g.board, m.ctx(), d, move, it) }
        val rec = baseRecord(m, player, DecisionKind.CHECKER).copy(
            dice = d, move = move, checker = analysis,
            error = analysis?.error,
            counted = analysis != null && analysis.isDecision && !hinted && !auto,
            hinted = hinted, auto = auto,
        )
        val next = m.copy(game = g.record(rec).move(move, m.rules))
        return next to rec
    }

    private fun recordDoubleDecision(m: MatchState, doubled: Boolean, eval: CubeEval?, hinted: Boolean, auto: Boolean = false): DecisionRecord {
        val kind = if (doubled) DecisionKind.DOUBLE else DecisionKind.NO_DOUBLE
        return baseRecord(m, m.game.turn, kind).copy(
            cubeEval = eval,
            error = eval?.let { DecisionMath.doubleError(it, doubled, m.rules.beaversAllowed) },
            counted = eval != null && !hinted && !auto && DecisionMath.isDoubleDecision(eval, doubled),
            hinted = hinted, auto = auto,
        )
    }

    /** The doubled player's answer: [kind] is TAKE, PASS or BEAVER. */
    private fun recordTakeDecision(m: MatchState, kind: DecisionKind, eval: CubeEval?, hinted: Boolean, auto: Boolean = false): DecisionRecord {
        return baseRecord(m, m.game.responder, kind).copy(
            cubeEval = eval,
            error = eval?.let { DecisionMath.takeError(it, kind, m.rules.beaversAllowed) },
            counted = eval != null && !hinted && !auto,
            hinted = hinted, auto = auto,
        )
    }

    /** The doubler's answer to a beaver ([m] in [Phase.BEAVERED] or still DOUBLED; [eval] is the doubler's view). */
    private fun recordRaccoonDecision(m: MatchState, raccooned: Boolean, eval: CubeEval?, hinted: Boolean, auto: Boolean = false): DecisionRecord {
        val kind = if (raccooned) DecisionKind.RACCOON else DecisionKind.NO_RACCOON
        return baseRecord(m, m.game.turn, kind).copy(
            cubeEval = eval,
            error = eval?.let { DecisionMath.raccoonError(it, raccooned) },
            counted = eval != null && !hinted && !auto,
            hinted = hinted, auto = auto,
        )
    }

    /** The response kind for a take ([took]) that is a beaver when allowed and [beaver]. */
    private fun responseKind(m: MatchState, took: Boolean, beaver: Boolean): DecisionKind = when {
        !took -> DecisionKind.PASS
        beaver && m.rules.beaversAllowed -> DecisionKind.BEAVER
        else -> DecisionKind.TAKE
    }

    /** Applies a response to the double in [g]. */
    private fun respond(g: GameState, kind: DecisionKind, rules: MatchRules): GameState = when (kind) {
        DecisionKind.PASS -> g.pass()
        DecisionKind.BEAVER -> g.beaver(rules.raccoonsAllowed)
        else -> g.take()
    }

    /** Cube analysis from the point of view of the player on roll (the potential doubler), at [plies]. */
    fun analyzeCube(m: MatchState, plies: Int = settings.cubePlies): CubeEval = engine.cubeDecision(m.game.board, m.ctx(), plies)

    /** The computer's cube analysis (null when the computer is not analyzed), reusing [eval] when there is one. */
    private fun cpuCubeAnalysis(m: MatchState, eval: CubeEval?): CubeEval? {
        if (!settings.analyzeCpu) return null
        return eval ?: analyzeCube(m)
    }

    // ---- human actions ------------------------------------------------------

    /**
     * The human rolls. If the human could have doubled, the implicit "no double"
     * decision is analyzed and recorded first.
     */
    fun humanRoll(m: MatchState, hinted: Boolean = false, auto: Boolean = false): Pair<MatchState, Feedback?> {
        require(m.game.phase == Phase.PRE_ROLL && m.game.turn == Player.HUMAN)
        var state = m
        var feedback: Feedback? = null
        if (!auto && m.game.mayDouble(m.rules, m.score)) {
            val eval = analyzeCube(m)
            if (eval.cubeAvailable) {
                val rec = recordDoubleDecision(m, doubled = false, eval = eval, hinted = hinted)
                state = state.copy(game = state.game.record(rec))
                feedback = feedbackFor(m, rec)
            }
        }
        return rollDice(state) to feedback
    }

    /**
     * The human doubles; the computer takes, passes, or in money games with beavers beavers when
     * taking is worth more than nothing to it. With raccoons the human then answers the beaver
     * ([Phase.BEAVERED], [humanAnswerBeaver]). [auto]: played by autofinish (not analyzed or counted).
     */
    fun humanDouble(m: MatchState, hinted: Boolean = false, auto: Boolean = false): Pair<MatchState, Feedback?> {
        require(m.game.mayDouble(m.rules, m.score) && m.game.turn == Player.HUMAN)
        val eval = if (auto) null else analyzeCube(m)
        val rec = recordDoubleDecision(m, doubled = true, eval = eval, hinted = hinted, auto = auto)
        val feedback = feedbackFor(m, rec)
        var state = m.copy(game = m.game.record(rec).double())

        // Computer's response at its playing strength.
        val cpuView = cpuCubeView(m)
        val take = mistakeMaker(m)?.take(cpuView, factor(m)) ?: cpuView.shouldTake
        val kind = responseKind(m, take, beaver = cpuView.doubleTake < 0f)
        val cpuRec = recordTakeDecision(state, kind, cpuCubeAnalysis(m, eval), hinted = false)
        state = state.copy(game = respond(state.game.record(cpuRec), kind, m.rules))
        return state to feedback
    }

    /**
     * The human responds to the computer's double: takes, passes, or beavers ([beaver] with [take],
     * money games with beavers only). After a beaver the computer may raccoon.
     */
    fun humanRespond(m: MatchState, take: Boolean, hinted: Boolean = false, auto: Boolean = false, beaver: Boolean = false): Pair<MatchState, Feedback?> {
        require(m.game.phase == Phase.DOUBLED && m.game.responder == Player.HUMAN)
        val eval = if (auto) null else analyzeCube(m)
        val kind = responseKind(m, take, beaver)
        val rec = recordTakeDecision(m, kind, eval, hinted, auto)
        var state = m.copy(game = respond(m.game.record(rec), kind, m.rules))
        if (state.game.phase == Phase.BEAVERED) {
            // The computer answers the beaver: a raccoon pays when the double was right for it.
            val raccoon = cpuCubeView(m).doubleTake > 0f
            val cpuRec = recordRaccoonDecision(state, raccoon, cpuCubeAnalysis(m, eval), hinted = false)
            state = state.copy(game = state.game.record(cpuRec).let { if (raccoon) it.raccoon() else it.acceptBeaver() })
        }
        return state to feedbackFor(m, rec)
    }

    /** The human answers the computer's beaver: plays on at 4× or raccoons to 8×. */
    fun humanAnswerBeaver(m: MatchState, raccoon: Boolean, hinted: Boolean = false, auto: Boolean = false): Pair<MatchState, Feedback?> {
        require(m.game.phase == Phase.BEAVERED && m.game.turn == Player.HUMAN)
        val eval = if (auto) null else analyzeCube(m)
        val rec = recordRaccoonDecision(m, raccoon, eval, hinted, auto)
        val g = m.game.record(rec).let { if (raccoon) it.raccoon() else it.acceptBeaver() }
        return m.copy(game = g) to feedbackFor(m, rec)
    }

    /** The human plays [move] (must be legal for the current dice). [auto]: played by autofinish (not analyzed or counted). */
    fun humanMove(m: MatchState, move: Move, hinted: Boolean = false, auto: Boolean = false): Pair<MatchState, Feedback?> {
        require(m.game.phase == Phase.MOVING && m.game.turn == Player.HUMAN)
        val (next, rec) = recordChecker(m, move, hinted, plies = if (auto) null else settings.plies, auto = auto)
        return next to feedbackFor(m, rec)
    }

    /**
     * The human offers to resign [type] (before or after rolling, before moving). The computer
     * accepts or rejects it by the rules in [Resignation]; returns the new state and whether
     * it was accepted.
     */
    fun humanResign(m: MatchState, type: WinType): Pair<MatchState, Boolean> {
        val g = m.game
        require(g.turn == Player.HUMAN && (g.phase == Phase.PRE_ROLL || g.phase == Phase.MOVING))
        val highest = Resignation.highestCounted(m.rules, g.cube, m.score, Player.CPU)
        val accepted = type >= highest || run {
            val (winGammon, winBackgammon) = cpuWinningChances(m)
            Resignation.accepts(type, winGammon, winBackgammon, highest)
        }
        val offered = g.offerResign(type)
        val next = if (accepted) offered.acceptResign(m.rules) else offered.rejectResign()
        return m.copy(game = next) to accepted
    }

    /** The computer's gammon and backgammon chances while the human is to act. */
    private fun cpuWinningChances(m: MatchState): Pair<Float, Float> {
        val g = m.game
        val d = g.dice
        if (g.phase == Phase.MOVING && d != null) {
            // After the roll: the human's best play decides the chances.
            val best = engine.rankMoves(g.board, m.ctx(), d.d1, d.d2, settings.plies, null).candidates.firstOrNull()
            if (best != null) return best.probs[3] to best.probs[4]
            // No legal move: the computer is next on roll in the same position.
            val p = engine.cubeDecision(g.board, m.ctx(Player.CPU), settings.cubePlies).probs
            return p[1] to p[2]
        }
        val p = engine.cubeDecision(g.board, m.ctx(Player.HUMAN), settings.cubePlies).probs
        return p[3] to p[4]
    }

    /** The human answers the computer's resignation offer. */
    fun humanAnswerResign(m: MatchState, accept: Boolean): MatchState {
        val g = m.game
        require(g.turn == Player.CPU && g.resignOffer != null)
        return m.copy(game = if (accept) g.acceptResign(m.rules) else g.rejectResign())
    }

    /** The resignation the computer offers before its roll, or null to play on. */
    private fun cpuResignation(m: MatchState): WinType? {
        val g = m.game
        val highest = Resignation.highestCounted(m.rules, g.cube, m.score, Player.HUMAN)
        // A cheap 0-ply look first; the analysis depth only confirms hopeless positions.
        val quick = engine.cubeDecision(g.board, m.ctx(Player.CPU), 0).probs
        if (quick[0] > PRE_RESIGN_WIN) return null
        val probs = if (Depth.of(settings.cubePlies).plies > 1) engine.cubeDecision(g.board, m.ctx(Player.CPU), settings.cubePlies).probs else quick
        val type = Resignation.offer(probs, highest) ?: return null
        val rejected = g.resignRejected[Player.CPU]
        return if (rejected != null && type <= rejected) null else type
    }

    /** The computer's cube view at its playing strength (the mistake-rate opponent at its own cube depth). */
    private fun cpuCubeView(m: MatchState): CubeEval {
        val rate = m.mistakes?.takeIf { m.cpuLevel == CpuLevel.MISTAKES }
        return if (rate != null) engine.cubeDecision(m.game.board, m.ctx(), rate.cubeDepth)
        else engine.cubeDecisionAtLevel(m.game.board, m.ctx(), m.cpuLevel)
    }

    /** Autofinish: the human's checker play, chosen by the engine at [AUTOFINISH_LEVEL]. */
    fun autoMove(m: MatchState): Move {
        val d = requireNotNull(m.game.dice)
        return engine.chooseMove(m.game.board, m.ctx(), d.d1, d.d2, AUTOFINISH_LEVEL)
    }

    /** Autofinish: the engine's cube view for the human (as doubler before the roll, or as taker). */
    fun autoCube(m: MatchState): CubeEval = engine.cubeDecisionAtLevel(m.game.board, m.ctx(), AUTOFINISH_LEVEL)

    /** Ranked candidate plays for the human's current roll (the hint). */
    fun hintMoves(m: MatchState): CheckerAnalysis {
        val d = requireNotNull(m.game.dice)
        return analyzeChecker(m.game.board, m.ctx(), d, null)
    }

    // ---- computer -------------------------------------------------------------

    sealed interface CpuAction {
        data object Doubled : CpuAction
        data object Rolled : CpuAction
        data class Moved(val move: Move) : CpuAction
        data class OffersResign(val type: WinType) : CpuAction
    }

    /** Performs the computer's next action (cube decision, roll, or move). */
    fun cpuStep(m: MatchState): Pair<MatchState, CpuAction> {
        val g = m.game
        require(g.turn == Player.CPU)
        return when (g.phase) {
            Phase.PRE_ROLL -> {
                require(g.resignOffer == null) { "waiting for the answer to the computer's resignation" }
                cpuResignation(m)?.let { return m.copy(game = g.offerResign(it)) to CpuAction.OffersResign(it) }
                if (g.mayDouble(m.rules, m.score)) {
                    val view = cpuCubeView(m)
                    val analysis = if (settings.analyzeCpu) analyzeCube(m) else null
                    val doubles = mistakeMaker(m)?.double(view, factor(m)) ?: view.shouldDouble
                    if (view.cubeAvailable && doubles) {
                        val rec = recordDoubleDecision(m, doubled = true, eval = analysis, hinted = false)
                        return m.copy(game = g.record(rec).double()) to CpuAction.Doubled
                    }
                    if (view.cubeAvailable && analysis != null && analysis.cubeAvailable) {
                        val rec = recordDoubleDecision(m, doubled = false, eval = analysis, hinted = false)
                        return rollDice(m.copy(game = g.record(rec))) to CpuAction.Rolled
                    }
                }
                rollDice(m) to CpuAction.Rolled
            }
            Phase.MOVING -> {
                val d = requireNotNull(g.dice)
                val maker = mistakeMaker(m)
                val move = if (maker != null) {
                    maker.checker(engine.rankMoves(g.board, m.ctx(), d.d1, d.d2, requireNotNull(m.mistakes).checkerDepth, null).candidates, factor(m))
                } else engine.chooseMove(g.board, m.ctx(), d.d1, d.d2, m.cpuLevel)
                val (next, _) = recordChecker(m, move, hinted = false, plies = settings.cpuPlies)
                next to CpuAction.Moved(move)
            }
            else -> error("computer cannot act in phase ${g.phase}")
        }
    }

    companion object {
        /** Candidates kept per checker decision (plus the played move if outside). */
        const val MAX_CANDIDATES = 10


        /** Strength that plays the human's side in autofinish (2-ply, no noise). */
        val AUTOFINISH_LEVEL = CpuLevel.WORLD_CLASS

        /** 0-ply winning chance above which the computer does not look into resigning. */
        const val PRE_RESIGN_WIN = 0.01f
    }
}
