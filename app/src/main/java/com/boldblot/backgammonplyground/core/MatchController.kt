package com.boldblot.backgammonplyground.core

import java.security.SecureRandom

/** Source of dice rolls. */
fun interface DiceSource {
    fun die(): Int

    fun roll(): Dice = Dice(die(), die())

    /** False while the next roll is still to be entered (manual dice): the computer then waits ([MatchController.CpuAction.NeedsDice]). */
    fun ready(): Boolean = true

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
    /**
     * Decisions are recorded [DecisionRecord.pending] instead of analyzed on the spot, for the
     * background analysis (nothing is shown during play). An evaluation the engine already has is used.
     */
    val deferred: Boolean = false,
    /** Nothing is analyzed during play (pending or not): the player analyzes later. */
    val none: Boolean = false,
    /** XG plies with which autofinish plays the human's side (no noise). */
    val autoPlies: Int = 1,
    /** Autofinish's depths as [Depth] codes, when they are more than plies (a [GameSearch] in matches); null = [autoPlies]. */
    val autoChecker: Int? = null,
    val autoCube: Int? = null,
    /**
     * The quick cube check (a [Depth] code; 0 = off): when the human rolls and this shallower analysis
     * finds doubling a blunder, it stands in for the analysis at [cubePlies].
     */
    val quickCubePlies: Int = 0,
) {
    val analyzeCpu: Boolean get() = cpuPlies != null && !none
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
    /** The analysis engine (and the human's side in autofinish). */
    private val engine: AnalysisEngine,
    private val dice: DiceSource,
    var settings: AnalysisSettings = AnalysisSettings(),
    /** The computer's engine for a match's [MatchState.engine]. */
    private val opponents: (EngineKind) -> AnalysisEngine = { engine },
) {
    /** The engine the computer plays [m] with. */
    private fun cpu(m: MatchState): AnalysisEngine = opponents(m.engine)

    private val factorCache = java.util.concurrent.ConcurrentHashMap<Int, Float>()

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
        // What the analysis records: the depth for this kind of game, or what a stopped search reached.
        @Suppress("NAME_SHADOWING")
        val plies = all.firstOrNull()?.plies?.takeIf { it >= Depth.INCOMPLETE } ?: Depth.resolve(plies, ctx.matchLength > 0)
        if (all.isEmpty()) {
            return CheckerAnalysis(emptyList(), -1, 0f, 0f, 0f, legalMoves = 0, plies = plies, engine = engine.name.ifEmpty { null })
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
            engine = engine.name.ifEmpty { null },
        )
    }

    /** Records a checker play, analyzed at [plies] (null = not analyzed; [pending]: by the background analysis). */
    private fun recordChecker(
        m: MatchState, move: Move, hinted: Boolean, plies: Int?, auto: Boolean = false, pending: Boolean = false, rated: Boolean = true,
    ): Pair<MatchState, DecisionRecord> {
        val g = m.game
        val d = requireNotNull(g.dice)
        val player = g.turn
        val analysis = plies?.let { analyzeChecker(g.board, m.ctx(), d, move, it) }
        val rec = baseRecord(m, player, DecisionKind.CHECKER).copy(
            dice = d, move = move, checker = analysis,
            error = analysis?.error,
            counted = analysis != null && analysis.isDecision && !hinted && !auto && rated,
            hinted = hinted, auto = auto, pending = pending && analysis == null,
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

    /** The analysis of the computer's own cube decision before its roll, with [CubeShortcut]. */
    private fun cpuCubeAnalysis(m: MatchState): CubeEval =
        // The computer's own view may already be the full analysis (same engine and depth): free, and kept.
        engine.cachedCube(m.game.board, m.ctx(), settings.cubePlies)
            ?: CubeShortcut.cubeDecision(engine, m.game.board, m.ctx(), settings.cubePlies, DecisionKind.NO_DOUBLE)

    /**
     * The analysis of the human's cube decision when they roll: the quick check's ([AnalysisSettings.quickCubePlies])
     * when it is shallower than the cube depth and finds doubling a blunder (or the game hopeless either way, so
     * that the cube does not matter), otherwise the full analysis.
     */
    fun rollCubeEval(m: MatchState): CubeEval {
        val q = settings.quickCubePlies
        if (q > 0 && Depth.of(q).plies < Depth.of(settings.cubePlies).plies) {
            val quick = engine.cubeDecision(m.game.board, m.ctx(), q)
            val after = DecisionMath.afterDouble(quick, m.rules.beaversAllowed)
            val doubling = DecisionMath.doubleError(quick, doubled = true, beavers = m.rules.beaversAllowed) * factor(m)
            // Hopeless either way (XG's "very negative" rule), with rolling no worse: the cube does not matter.
            val hopeless = quick.noDouble <= XgRules.VERY_NEGATIVE && after <= XgRules.VERY_NEGATIVE && after <= quick.noDouble + XgRules.OPTIONAL_EPSILON
            if (quick.cubeAvailable && (doubling > XgRules.BLUNDER_THRESHOLD || hopeless)) return quick
        }
        return analyzeCube(m)
    }

    /** Whether a decision of the human (not autofinish's) is analyzed now: null = not at all. */
    private fun humanAnalysis(auto: Boolean): Analysis = when {
        auto || settings.none -> Analysis.NONE
        settings.deferred -> Analysis.LATER
        else -> Analysis.NOW
    }

    private enum class Analysis { NOW, LATER, NONE }

    /** The cube analysis for a decision analyzed [how]: now, or later unless the engine already has it. */
    private fun cubeAnalysis(m: MatchState, how: Analysis): CubeEval? = when (how) {
        Analysis.NOW -> analyzeCube(m)
        Analysis.LATER -> engine.cachedCube(m.game.board, m.ctx(), settings.cubePlies)
        Analysis.NONE -> null
    }

    /**
     * The computer is not analyzed at the analysis depths (Settings), but its decisions still get a quick
     * 1-ply analysis (milliseconds), so that the review never shows them blank. Not with no analysis at all.
     */
    private val cpuLight: Boolean get() = !settings.analyzeCpu && !settings.none

    /** The computer's decision [this] stays out of its PR when it has only the quick analysis ([cpuLight]). */
    private fun DecisionRecord.rated(): DecisionRecord = if (settings.analyzeCpu) this else copy(counted = false)

    /** The computer's cube analysis (null when the computer is not analyzed now), reusing [eval] when there is one. */
    private fun cpuCubeAnalysis(m: MatchState, eval: CubeEval?): CubeEval? {
        if (!settings.analyzeCpu) return if (cpuLight) eval ?: analyzeCube(m, LIGHT_PLIES) else null
        return eval ?: cubeAnalysis(m, if (settings.deferred) Analysis.LATER else Analysis.NOW)
    }

    /** A record whose analysis is still to come: pending unless it has one or is not analyzed at all. */
    private fun DecisionRecord.later(how: Analysis): DecisionRecord =
        if (how == Analysis.LATER && cubeEval == null && checker == null) copy(pending = true) else this

    private val cpuAnalysis: Analysis
        get() = when {
            !settings.analyzeCpu -> Analysis.NONE
            settings.deferred -> Analysis.LATER
            else -> Analysis.NOW
        }

    // ---- human actions ------------------------------------------------------

    /**
     * The human rolls. If the human could have doubled, the implicit "no double"
     * decision is analyzed and recorded first.
     */
    fun humanRoll(m: MatchState, hinted: Boolean = false, auto: Boolean = false): Pair<MatchState, Feedback?> {
        val (state, feedback) = humanNoDouble(m, hinted, auto)
        return rollDice(state) to feedback
    }

    /**
     * The human chooses to roll: the no-double decision is recorded (when there is one to analyze),
     * but the dice are not rolled yet; [rollForHuman] rolls them. A missed double shown to the player
     * waits for Continue between the two.
     */
    fun humanNoDouble(m: MatchState, hinted: Boolean = false, auto: Boolean = false): Pair<MatchState, Feedback?> {
        require(m.game.phase == Phase.PRE_ROLL && m.game.turn == Player.HUMAN)
        val how = humanAnalysis(auto)
        if (how != Analysis.NONE && m.game.mayDouble(m.rules, m.score)) {
            val eval = if (how == Analysis.NOW) rollCubeEval(m) else cubeAnalysis(m, how)
            if (eval == null || eval.cubeAvailable) {
                val rec = recordDoubleDecision(m, doubled = false, eval = eval, hinted = hinted).later(how)
                return m.copy(game = m.game.record(rec)) to feedbackFor(m, rec)
            }
        }
        return m to null
    }

    /** Rolls the human's dice, after [humanNoDouble]. */
    fun rollForHuman(m: MatchState): MatchState {
        require(m.game.phase == Phase.PRE_ROLL && m.game.turn == Player.HUMAN)
        return rollDice(m)
    }

    /**
     * The human doubles; the computer takes, passes, or in money games with beavers beavers when
     * taking is worth more than nothing to it. With raccoons the human then answers the beaver
     * ([Phase.BEAVERED], [humanAnswerBeaver]). [auto]: played by autofinish (not analyzed or counted).
     */
    fun humanDouble(m: MatchState, hinted: Boolean = false, auto: Boolean = false): Pair<MatchState, Feedback?> {
        require(m.game.mayDouble(m.rules, m.score) && m.game.turn == Player.HUMAN)
        val how = humanAnalysis(auto)
        // The computer's view first: at the same depth and engine it is also the analysis.
        val cpuView = cpuCubeView(m)
        val eval = cubeAnalysis(m, how)
        val rec = recordDoubleDecision(m, doubled = true, eval = eval, hinted = hinted, auto = auto).later(how)
        val feedback = feedbackFor(m, rec)
        var state = m.copy(game = m.game.record(rec).double())

        // Computer's response at its playing strength.
        val take = mistakeMaker(m)?.take(cpuView, factor(m)) ?: cpuView.shouldTake
        val kind = responseKind(m, take, beaver = cpuView.doubleTake < 0f)
        val cpuRec = recordTakeDecision(state, kind, cpuCubeAnalysis(m, eval), hinted = false).rated().later(cpuAnalysis)
        state = state.copy(game = respond(state.game.record(cpuRec), kind, m.rules))
        return state to feedback
    }

    /**
     * The human responds to the computer's double: takes, passes, or beavers ([beaver] with [take],
     * money games with beavers only). After a beaver the computer may raccoon.
     */
    fun humanRespond(m: MatchState, take: Boolean, hinted: Boolean = false, auto: Boolean = false, beaver: Boolean = false): Pair<MatchState, Feedback?> {
        require(m.game.phase == Phase.DOUBLED && m.game.responder == Player.HUMAN)
        val how = humanAnalysis(auto)
        val eval = cubeAnalysis(m, how)
        val kind = responseKind(m, take, beaver)
        val rec = recordTakeDecision(m, kind, eval, hinted, auto).later(how)
        var state = m.copy(game = respond(m.game.record(rec), kind, m.rules))
        if (state.game.phase == Phase.BEAVERED) {
            // The computer answers the beaver: a raccoon pays when the double was right for it.
            val raccoon = cpuCubeView(m).doubleTake > 0f
            val cpuRec = recordRaccoonDecision(state, raccoon, cpuCubeAnalysis(m, eval), hinted = false).rated().later(cpuAnalysis)
            state = state.copy(game = state.game.record(cpuRec).let { if (raccoon) it.raccoon() else it.acceptBeaver() })
        }
        return state to feedbackFor(m, rec)
    }

    /** The human answers the computer's beaver: plays on at 4× or raccoons to 8×. */
    fun humanAnswerBeaver(m: MatchState, raccoon: Boolean, hinted: Boolean = false, auto: Boolean = false): Pair<MatchState, Feedback?> {
        require(m.game.phase == Phase.BEAVERED && m.game.turn == Player.HUMAN)
        val how = humanAnalysis(auto)
        val rec = recordRaccoonDecision(m, raccoon, cubeAnalysis(m, how), hinted, auto).later(how)
        val g = m.game.record(rec).let { if (raccoon) it.raccoon() else it.acceptBeaver() }
        return m.copy(game = g) to feedbackFor(m, rec)
    }

    /** The human plays [move] (must be legal for the current dice). [auto]: played by autofinish (not analyzed or counted). */
    fun humanMove(m: MatchState, move: Move, hinted: Boolean = false, auto: Boolean = false): Pair<MatchState, Feedback?> {
        require(m.game.phase == Phase.MOVING && m.game.turn == Player.HUMAN)
        val how = humanAnalysis(auto)
        val (next, rec) = recordChecker(m, move, hinted, plies = settings.plies.takeIf { how == Analysis.NOW }, auto = auto, pending = how == Analysis.LATER)
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

    /** The cube depth for winning chances: played-out games add nothing to them, so the plies before the games. */
    private fun chancesDepth(m: MatchState): Int = Depth.of(Depth.resolve(settings.cubePlies, m.ctx().matchLength > 0)).plain.code

    /** The computer's gammon and backgammon chances while the human is to act. */
    private fun cpuWinningChances(m: MatchState): Pair<Float, Float> {
        val g = m.game
        val d = g.dice
        if (g.phase == Phase.MOVING && d != null) {
            // After the roll: the human's best play decides the chances.
            val best = engine.rankMoves(g.board, m.ctx(), d.d1, d.d2, settings.plies, null).candidates.firstOrNull()
            if (best != null) return best.probs[3] to best.probs[4]
            // No legal move: the computer is next on roll in the same position.
            val p = engine.cubeDecision(g.board, m.ctx(Player.CPU), chancesDepth(m)).probs
            return p[1] to p[2]
        }
        val p = engine.cubeDecision(g.board, m.ctx(Player.HUMAN), chancesDepth(m)).probs
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
        val rejected = g.resignRejected[Player.CPU]
        // A cheap 1-ply look first: a deeper look only confirms a resignation the quick one would
        // already offer (and that was not rejected), so a lost game with the gammon still open, or
        // a few hundredths of a chance left, costs no search before every roll.
        val quick = cpu(m).cubeDecision(g.board, m.ctx(Player.CPU), 0).probs
        val quickType = Resignation.offer(quick, highest) ?: return null
        if (rejected != null && quickType <= rejected) return null
        val plies = minOf(Depth.of(settings.cubePlies).plies, RESIGN_CONFIRM_PLIES)
        val probs = if (plies > 1) cpu(m).cubeDecision(g.board, m.ctx(Player.CPU), Depth(plies).code).probs else quick
        val type = Resignation.offer(probs, highest) ?: return null
        return if (rejected != null && type <= rejected) null else type
    }

    /** The computer's cube view at its playing strength (the mistake-rate opponent at its own cube depth). */
    private fun cpuCubeView(m: MatchState): CubeEval {
        val rate = m.mistakes?.takeIf { m.cpuLevel == CpuLevel.MISTAKES }
        return if (rate != null) cpu(m).cubeDecision(m.game.board, m.ctx(), rate.cubeDepth)
        else cpu(m).cubeDecisionAtLevel(m.game.board, m.ctx(), m.cpuLevel)
    }

    /** Autofinish: the human's checker play, the engine's best at [AnalysisSettings.autoPlies] (deeper looks: moves within 0.04). */
    fun autoMove(m: MatchState): Move {
        val d = requireNotNull(m.game.dice)
        val depth = settings.autoChecker ?: (if (settings.autoPlies <= 1) Depth(1) else Depth(settings.autoPlies, MoveFilter.NARROW)).code
        return engine.rankMoves(m.game.board, m.ctx(), d.d1, d.d2, depth, null).candidates.firstOrNull()?.move ?: Move.NONE
    }

    /** Autofinish: the engine's cube view for the human (as doubler before the roll, or as taker). */
    fun autoCube(m: MatchState): CubeEval = engine.cubeDecision(m.game.board, m.ctx(), settings.autoCube ?: Depth(settings.autoPlies.coerceAtLeast(1)).code)

    /** Ranked candidate plays for the human's current roll (the hint). */
    /** The best of [moves] (legal for the dice) at [plies], by the resulting position; null if none is legal. */
    fun bestAmong(m: MatchState, moves: List<Move>, plies: Int): Move? {
        val d = requireNotNull(m.game.dice)
        val board = m.game.board
        val wanted = moves.map { board.applyMove(m.game.turn, it) }.toSet()
        return engine.rankMoves(board, m.ctx(), d.d1, d.d2, plies, null).candidates
            .firstOrNull { board.applyMove(m.game.turn, it.move) in wanted }?.move ?: moves.firstOrNull()
    }

    fun hintMoves(m: MatchState): CheckerAnalysis {
        val d = requireNotNull(m.game.dice)
        return analyzeChecker(m.game.board, m.ctx(), d, null)
    }

    // ---- computer -------------------------------------------------------------

    sealed interface CpuAction {
        data object Doubled : CpuAction
        data object Rolled : CpuAction
        /** The computer will roll, but the dice are still to be entered ([DiceSource.ready]): then [cpuRoll]. */
        data object NeedsDice : CpuAction
        data class Moved(val move: Move) : CpuAction
        data class OffersResign(val type: WinType) : CpuAction
    }

    private fun cpuRollOrWait(m: MatchState): Pair<MatchState, CpuAction> =
        if (dice.ready()) rollDice(m) to CpuAction.Rolled else m to CpuAction.NeedsDice

    /** The computer's roll after [CpuAction.NeedsDice] (the dice have been entered). */
    fun cpuRoll(m: MatchState): MatchState = rollDice(m)

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
                    val how = cpuAnalysis
                    // A clear decision at 2-ply is not searched deeper (as in the background analysis).
                    val analysis = (if (how == Analysis.NOW) cpuCubeAnalysis(m) else cubeAnalysis(m, how))
                        ?: if (cpuLight) analyzeCube(m, LIGHT_PLIES) else null
                    val doubles = mistakeMaker(m)?.double(view, factor(m)) ?: view.shouldDouble
                    if (view.cubeAvailable && doubles) {
                        val rec = recordDoubleDecision(m, doubled = true, eval = analysis, hinted = false).rated().later(how)
                        return m.copy(game = g.record(rec).double()) to CpuAction.Doubled
                    }
                    val recorded = if (analysis != null) analysis.cubeAvailable else how == Analysis.LATER
                    if (view.cubeAvailable && recorded) {
                        val rec = recordDoubleDecision(m, doubled = false, eval = analysis, hinted = false).rated().later(how)
                        return cpuRollOrWait(m.copy(game = g.record(rec)))
                    }
                }
                cpuRollOrWait(m)
            }
            Phase.MOVING -> {
                val d = requireNotNull(g.dice)
                val maker = mistakeMaker(m)
                val move = if (maker != null) {
                    maker.checker(cpu(m).rankMoves(g.board, m.ctx(), d.d1, d.d2, requireNotNull(m.mistakes).checkerDepth, null).candidates, factor(m))
                } else cpu(m).chooseMove(g.board, m.ctx(), d.d1, d.d2, m.cpuLevel)
                val how = cpuAnalysis
                val (next, _) = recordChecker(m, move, hinted = false, plies = settings.cpuPlies.takeIf { how == Analysis.NOW } ?: LIGHT_PLIES.takeIf { cpuLight }, pending = how == Analysis.LATER, rated = settings.analyzeCpu)
                next to CpuAction.Moved(move)
            }
            else -> error("computer cannot act in phase ${g.phase}")
        }
    }

    companion object {
        /** Candidates kept per checker decision (plus the played move if outside). */
        const val MAX_CANDIDATES = 10

        /** Depth of the computer's quick analysis when it is not analyzed at the analysis depths. */
        val LIGHT_PLIES = Depth(1).code


        /**
         * Deepest look (XG plies) that confirms the computer's resignation; only outcome chances count.
         * 3-ply reaches the opponent's next roll, so a game they end with it is scored exactly (a backgammon too).
         */
        const val RESIGN_CONFIRM_PLIES = 3
    }
}
