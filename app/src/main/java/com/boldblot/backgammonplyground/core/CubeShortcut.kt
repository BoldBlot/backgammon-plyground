package com.boldblot.backgammonplyground.core

import kotlin.math.abs

/**
 * A cube decision a 2-ply search settles by a wide margin is not searched deeper: the background analysis and the
 * computer's own cube analysis during play. The analysis then says 2-ply.
 */
object CubeShortcut {
    /** Normalized equity between the two options of a cube decision beyond which 2-ply is taken as settled. */
    const val CLEAR_MARGIN = 0.3f

    /** The cube decision for [ctx].onRoll at [cubeDepth], or at 2-ply when that settles [kind] by more than [CLEAR_MARGIN]. */
    fun cubeDecision(engine: AnalysisEngine, board: Board, ctx: CubeContext, cubeDepth: Int, kind: DecisionKind): CubeEval {
        if (Depth.of(cubeDepth).plies > 2) {
            val e = engine.cubeDecision(board, ctx, Depth(2).code)
            val margin = when (kind) {
                DecisionKind.DOUBLE, DecisionKind.NO_DOUBLE -> if (e.cubeAvailable) abs(e.noDouble - e.afterDouble) else 0f
                DecisionKind.TAKE, DecisionKind.PASS, DecisionKind.BEAVER -> abs(e.doubleTake - e.doublePass)
                else -> 0f
            }
            if (margin > CLEAR_MARGIN) return e
        }
        return engine.cubeDecision(board, ctx, cubeDepth)
    }
}
