package com.boldblot.backgammonplyground.core

/**
 * Analyzes recorded decisions again, at other depths: the
 * review's "Analyze again". Autofinish decisions stay unanalyzed, as when they were played.
 */
class Reanalysis(
    private val engine: AnalysisEngine,
    /** Checker play depth and cube depth ([Depth] codes). */
    private val checkerDepth: Int,
    private val cubeDepth: Int,
) {
    private val controller = MatchController(engine, DiceSource.secure(), AnalysisSettings(plies = checkerDepth, cubePlies = cubeDepth))

    /** Whether "analyze again" redoes [d] ([cpu]: the computer's decisions too). */
    fun wanted(d: DecisionRecord, cpu: Boolean): Boolean = !d.auto && (cpu || d.player == Player.HUMAN)

    /** [d] analyzed again (its board, dice, cube and score are as recorded). */
    fun decision(d: DecisionRecord, matchLength: Int, jacoby: Boolean): DecisionRecord {
        fun ctx(onRoll: Int) = CubeContext(matchLength, d.score, d.crawford, jacoby, d.cube, onRoll)
        val counts = !d.hinted && !d.auto
        return when (d.kind) {
            DecisionKind.CHECKER -> {
                val dice = d.dice ?: return d
                val a = controller.analyzeChecker(d.board, ctx(d.player), dice, d.move ?: Move.NONE, checkerDepth)
                d.copy(checker = a, error = a.error, counted = a.isDecision && counts)
            }
            DecisionKind.DOUBLE, DecisionKind.NO_DOUBLE -> {
                val doubled = d.kind == DecisionKind.DOUBLE
                val e = engine.cubeDecision(d.board, ctx(d.player), cubeDepth)
                d.copy(
                    cubeEval = e, error = DecisionMath.doubleError(e, doubled, d.beavers),
                    counted = counts && DecisionMath.isDoubleDecision(e, doubled),
                )
            }
            DecisionKind.TAKE, DecisionKind.PASS, DecisionKind.BEAVER -> {
                // The doubler's view, as when the double was offered.
                val e = engine.cubeDecision(d.board, ctx(Player.other(d.player)), cubeDepth)
                d.copy(cubeEval = e, error = DecisionMath.takeError(e, d.kind, d.beavers), counted = counts)
            }
            DecisionKind.RACCOON, DecisionKind.NO_RACCOON -> {
                val e = engine.cubeDecision(d.board, ctx(d.player), cubeDepth)
                d.copy(cubeEval = e, error = DecisionMath.raccoonError(e, d.kind == DecisionKind.RACCOON), counted = counts)
            }
        }
    }
}
