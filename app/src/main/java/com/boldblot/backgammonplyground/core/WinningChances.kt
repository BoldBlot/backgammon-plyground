package com.boldblot.backgammonplyground.core

/** The chances the play screen's bar shows, always from the human's side. */
object WinningChances {
    /**
     * Win, win gammon, win backgammon, lose gammon, lose backgammon, for the human in [m]: whoever is
     * on roll is evaluated as on roll, and once the dice are down the best play of that roll counts.
     */
    fun forHuman(engine: AnalysisEngine, m: MatchState): List<Float> {
        val g = m.game
        val d = g.dice
        if (g.phase == Phase.MOVING && d != null) {
            engine.bestPlayChances(g.board, m.ctx(g.turn), d.d1, d.d2)?.let { return ofHuman(g.turn, it) }
            // No legal move: the other player is next on roll in the same position.
            val next = Player.other(g.turn)
            return ofHuman(next, engine.cubeDecision(g.board, m.ctx(next), 0).probs)
        }
        return ofHuman(g.turn, engine.cubeDecision(g.board, m.ctx(g.turn), 0).probs)
    }

    /** [probs] are [player]'s; the same chances from the human's side. */
    private fun ofHuman(player: Int, probs: List<Float>): List<Float> =
        if (player == Player.HUMAN) probs else listOf(1 - probs[0], probs[3], probs[4], probs[1], probs[2])
}
