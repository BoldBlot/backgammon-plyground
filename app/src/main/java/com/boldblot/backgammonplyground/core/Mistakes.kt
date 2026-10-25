package com.boldblot.backgammonplyground.core

import kotlinx.serialization.Serializable
import java.util.Random
import kotlin.math.roundToInt

/**
 * An opponent defined by how often it makes errors and blunders (eXtreme Gammon's sizes: more than
 * 0.020 and 0.080 equity) instead of by depth and noise. Rates are chances per decision (checker
 * play, double, take); a mistake of the drawn size is made only when the decision offers one, and a
 * no mistake bigger than [MistakeRate.maxMistake]. Its own play uses its own depths.
 */
/** A named opponent on the new-match screen. */
data class Preset(val name: String, val description: String, val rate: MistakeRate)

@Serializable
data class MistakeRate(
    val errors: Float,
    val blunders: Float,
    /** Depth of its checker play (XG plies 1-4, moves within 0.16 at 1-ply searched deeper). */
    val checkerPlies: Int = DEFAULT_PLIES,
    /** Depth of its cube decisions (XG plies 1-4). */
    val cubePlies: Int = DEFAULT_PLIES,
    /** Largest mistake it makes, equity (after the 1-point-match factor). */
    val maxMistake: Float = DEFAULT_MAX_MISTAKE,
) {

    val label: String get() = buildString {
        append("${pct(errors)} errors, ${pct(blunders)} blunders")
        if (checkerPlies != DEFAULT_PLIES || cubePlies != DEFAULT_PLIES) append(", ${checkerPlies}-ply / ${cubePlies}-ply cube")
        if (maxMistake != DEFAULT_MAX_MISTAKE) append(", max %.2f".format(java.util.Locale.ROOT, maxMistake))
    }

    /** Its settings in full, for the opponent list. */
    val settingsText: String
        get() = "Checker ${Depth(checkerPlies, MoveFilter.WIDE).label()}, cube $cubePlies-ply · " +
            "${pct(errors)} errors, ${pct(blunders)} blunders, largest ${"%.2f".format(java.util.Locale.ROOT, maxMistake)}"

    /** Engine depth codes ([Depth]) of its checker play and cube decisions. */
    val checkerDepth: Int get() = Depth(checkerPlies, MoveFilter.WIDE).code
    val cubeDepth: Int get() = Depth(cubePlies).code

    /**
     * PR as the app shows it (graded by its 3-ply analysis): the engine's own at its checker depth
     * plus the mistakes. Fitted to simulated money games on 2026-10-22 (OpponentPrCalibration).
     */
    val estimatedPr: Double
        get() = (BASE_PR[checkerPlies] ?: 0.0) +
            MEASURED_FACTOR * XgRules.PR_SCALE * (errors * AVG_ERROR + blunders * AVG_BLUNDER) * AVAILABLE

    companion object {
        const val DEFAULT_PLIES = 3
        const val DEFAULT_MAX_MISTAKE = 0.25f

        /**
         * Opponents by the PR the app shows, strongest first. Checker play at 1-ply: on its own it
         * grades at PR 0.3 in normal games (3-ply 0, 2-ply 0.15), so the PR comes from the set
         * mistakes, which are its only randomness; cube decisions at 3-ply (cheap), weakened by the
         * same rates.
         */
        val PR4 = MistakeRate(0.15f, 0.03f, checkerPlies = 1, cubePlies = 3)
        val PR6 = MistakeRate(0.21f, 0.05f, checkerPlies = 1, cubePlies = 3)
        val PR8 = MistakeRate(0.26f, 0.075f, checkerPlies = 1, cubePlies = 3)
        val PR10 = MistakeRate(0.32f, 0.10f, checkerPlies = 1, cubePlies = 3)
        val PR15 = MistakeRate(0.45f, 0.16f, checkerPlies = 1, cubePlies = 3)

        /** Named presets for the new-match screen, strongest first (anything else is "Custom"). */
        val presets = listOf(
            Preset("PR 4", "Expert", PR4),
            Preset("PR 6", "Advanced", PR6),
            Preset("PR 8", "Strong intermediate", PR8),
            Preset("PR 10", "Intermediate", PR10),
            Preset("PR 15", "Beginner", PR15),
        )

        /** The engine's own PR at each checker depth, as the app grades it (measured). */
        private val BASE_PR = mapOf(1 to 0.3, 2 to 0.15, 3 to 0.0, 4 to 0.0)

        /** Measured PR of the set mistakes relative to the simple estimate (1.35-1.44 across levels). */
        private const val MEASURED_FACTOR = 1.38

        private const val AVG_ERROR = 0.045
        private const val AVG_BLUNDER = 0.14
        private const val AVAILABLE = 0.5

        /** A rate as a percentage ("8%", "2.5%"). */
        fun pct(f: Float): String {
            val tenths = (f * 1000).roundToInt()
            return if (tenths % 10 == 0) "${tenths / 10}%" else "${tenths / 10}.${tenths % 10}%"
        }
    }
}

/** Chooses the mistake-rate opponent's decisions from the engine's rankings. */
class MistakeMaker(private val rate: MistakeRate, private val rng: Random = Random()) {

    /** The size of mistake to make on this decision: BLUNDER, ERROR, or null for the best play. */
    private fun draw(): Severity? {
        val u = rng.nextFloat()
        return when {
            u < rate.blunders -> Severity.BLUNDER
            u < rate.blunders + rate.errors -> Severity.ERROR
            else -> null
        }
    }

    /**
     * A checker play from [ranked] (best first). A drawn mistake picks one of the three smallest
     * plays of that size, no bigger than the largest allowed, so blunders look like a person's,
     * not like random moves.
     */
    fun checker(ranked: List<Candidate>, factor: Float): Move {
        val best = ranked.firstOrNull() ?: return Move.NONE
        val size = draw() ?: return best.move
        val pool = ranked.filter {
            val loss = (best.equity - it.equity) * factor
            XgRules.severity(loss) == size && loss <= rate.maxMistake
        }
            .sortedByDescending { it.equity }
            .take(3)
        return if (pool.isEmpty()) best.move else pool[rng.nextInt(pool.size)].move
    }

    /** Whether to double, given the correct analysis [eval] for the player on roll. */
    fun double(eval: CubeEval, factor: Float): Boolean {
        if (!eval.cubeAvailable) return false
        val right = eval.shouldDouble
        return if (flip(DecisionMath.doubleError(eval, !right) * factor)) !right else right
    }

    /** Whether to take, given the doubler's analysis [eval]. */
    fun take(eval: CubeEval, factor: Float): Boolean {
        val right = eval.shouldTake
        return if (flip(DecisionMath.takeError(eval, !right) * factor)) !right else right
    }

    /**
     * The wrong cube action is taken when its size is the drawn one. Far from the doubling window
     * the wrong action costs far more than a blunder's threshold, and the computer was then seen
     * doubling hopeless positions, so mistakes above [MistakeRate.maxMistake] are never made.
     */
    private fun flip(wrongError: Float): Boolean {
        val size = draw() ?: return false
        return wrongError <= rate.maxMistake && XgRules.severity(wrongError) == size
    }
}

/**
 * The opponent as stored with a match: a [CpuLevel] name, or "MISTAKES:<errors>:<blunders>" for a
 * mistake-rate opponent.
 */
object Opponent {
    fun key(level: CpuLevel, rate: MistakeRate?): String = when {
        level != CpuLevel.MISTAKES || rate == null -> level.name
        rate.checkerPlies == MistakeRate.DEFAULT_PLIES && rate.cubePlies == MistakeRate.DEFAULT_PLIES &&
            rate.maxMistake == MistakeRate.DEFAULT_MAX_MISTAKE -> "${level.name}:${rate.errors}:${rate.blunders}"
        else -> "${level.name}:${rate.errors}:${rate.blunders}:${rate.checkerPlies}:${rate.cubePlies}:${rate.maxMistake}"
    }

    fun parse(key: String): Pair<CpuLevel, MistakeRate?> {
        val parts = key.split(':')
        val level = CpuLevel.entries.firstOrNull { it.name == parts[0] } ?: CpuLevel.WORLD_CLASS
        val rate = if (parts.size == 3 || parts.size == 6) {
            val e = parts[1].toFloatOrNull()
            val b = parts[2].toFloatOrNull()
            if (e == null || b == null) null
            else if (parts.size == 3) MistakeRate(e, b)
            else MistakeRate(
                e, b, parts[3].toIntOrNull() ?: MistakeRate.DEFAULT_PLIES, parts[4].toIntOrNull() ?: MistakeRate.DEFAULT_PLIES,
                parts[5].toFloatOrNull() ?: MistakeRate.DEFAULT_MAX_MISTAKE,
            )
        } else null
        return level to (if (level == CpuLevel.MISTAKES) rate ?: MistakeRate.PR8 else null)
    }

    fun label(level: CpuLevel, rate: MistakeRate?): String = if (level == CpuLevel.MISTAKES && rate != null) rate.label else level.label

    fun label(key: String): String = parse(key).let { (l, r) -> label(l, r) }

    /**
     * The computer's name in exported files: engine and PR, "PureTD PR 4", "PureTD PR <2" (world
     * class), or a custom opponent's estimated PR, "PureTD PR 7.3".
     */
    fun exportName(key: String, engine: EngineKind = EngineKind.PURETD): String {
        val (level, rate) = parse(key)
        val pr = when {
            level == CpuLevel.MAXIMUM -> "PR <2"
            level != CpuLevel.MISTAKES || rate == null -> level.label
            else -> MistakeRate.presets.firstOrNull { it.rate == rate }?.name
                ?: "PR %.1f".format(java.util.Locale.ROOT, rate.estimatedPr)
        }
        return "${engine.label} $pr"
    }

    fun rating(key: String): Double = parse(key).let { (l, r) -> Elo.cpuRating(l, r) }
}
