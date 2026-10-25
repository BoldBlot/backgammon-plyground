package com.boldblot.backgammonplyground.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Player indices. Player 0 is always the human, player 1 the computer. */
object Player {
    const val HUMAN = 0
    const val CPU = 1
    fun other(p: Int) = 1 - p
}

const val CHECKERS_PER_SIDE = 15
const val BAR = 24
const val OFF = -1

/**
 * Immutable backgammon position.
 *
 * Each player's checkers are stored in that player's own orientation, exactly like
 * GNU Backgammon: index 0..23 is the player's 1..24 point and index 24 is the bar.
 * Borne-off checkers are implicit (15 minus the checkers on the board).
 * A player's point i is the opponent's point 23 - i.
 */
@Serializable(with = BoardSerializer::class)
class Board private constructor(private val cells: IntArray) {

    fun count(player: Int, index: Int): Int = cells[player * 25 + index]

    fun bar(player: Int): Int = count(player, BAR)

    fun onBoard(player: Int): Int {
        var n = 0
        for (i in 0..24) n += count(player, i)
        return n
    }

    fun off(player: Int): Int = CHECKERS_PER_SIDE - onBoard(player)

    fun pipCount(player: Int): Int {
        var pips = 0
        for (i in 0..24) pips += count(player, i) * (i + 1)
        return pips
    }

    /** True if all of the player's checkers are in the home board (points 1..6). */
    fun allHome(player: Int): Boolean {
        for (i in 6..24) if (count(player, i) > 0) return false
        return true
    }

    /** Highest occupied index for the player (bar = 24), or -1 if all are off. */
    fun highestPoint(player: Int): Int {
        for (i in 24 downTo 0) if (count(player, i) > 0) return i
        return -1
    }

    /**
     * True if [player] has checkers on the bar and the opponent's home board is closed (all six
     * points made): the player cannot move whatever the roll.
     */
    fun closedOut(player: Int): Boolean {
        if (bar(player) == 0) return false
        val opp = Player.other(player)
        return (0..5).all { count(opp, it) >= 2 }
    }

    /** True if the two sides can no longer hit each other (a pure race). */
    fun isRace(): Boolean {
        val myBack = highestPoint(0)
        val oppBack = highestPoint(1)
        if (myBack < 0 || oppBack < 0) return true
        // My rearmost checker at my index a is at opponent index 23 - a.
        return 23 - myBack > oppBack
    }

    /** Board in the 50-int layout used by the native engine, with [onRoll] as the player on roll. */
    fun toEngine(onRoll: Int): IntArray {
        val out = IntArray(50)
        val opp = Player.other(onRoll)
        for (i in 0..24) {
            out[i] = count(opp, i)
            out[25 + i] = count(onRoll, i)
        }
        return out
    }

    /** Applies one checker step for [player] (from/to in the player's orientation). */
    fun applyStep(player: Int, step: Step): Board {
        val c = cells.copyOf()
        val base = player * 25
        val oppBase = Player.other(player) * 25
        require(c[base + step.from] > 0) { "no checker on ${step.from} for player $player" }
        c[base + step.from]--
        if (step.to != OFF) {
            val oppIndex = 23 - step.to
            if (c[oppBase + oppIndex] == 1) {
                c[oppBase + oppIndex] = 0
                c[oppBase + BAR]++
            }
            c[base + step.to]++
        }
        return Board(c)
    }

    fun applyMove(player: Int, move: Move): Board {
        var b = this
        for (s in move.steps) b = b.applyStep(player, s)
        return b
    }

    /** True if [step] would hit an opponent blot. */
    fun isHit(player: Int, step: Step): Boolean =
        step.to != OFF && count(Player.other(player), 23 - step.to) == 1

    fun toList(): List<Int> = cells.toList()

    override fun equals(other: Any?): Boolean = other is Board && cells.contentEquals(other.cells)

    override fun hashCode(): Int = cells.contentHashCode()

    override fun toString(): String =
        "Board(human=${cells.copyOfRange(0, 25).toList()}, cpu=${cells.copyOfRange(25, 50).toList()})"

    companion object {
        /** Standard starting position. */
        val INITIAL: Board = run {
            val side = IntArray(25)
            side[23] = 2; side[12] = 5; side[7] = 3; side[5] = 5
            fromSides(side, side)
        }

        fun fromSides(human: IntArray, cpu: IntArray): Board {
            require(human.size == 25 && cpu.size == 25)
            return Board(human + cpu)
        }

        fun fromList(cells: List<Int>): Board {
            require(cells.size == 50)
            return Board(cells.toIntArray())
        }

        /** Inverse of [toEngine]. */
        fun fromEngine(engine: IntArray, onRoll: Int): Board {
            val onRollSide = engine.copyOfRange(25, 50)
            val oppSide = engine.copyOfRange(0, 25)
            return if (onRoll == Player.HUMAN) fromSides(onRollSide, oppSide) else fromSides(oppSide, onRollSide)
        }
    }
}

object BoardSerializer : KSerializer<Board> {
    private val delegate = ListSerializer(Int.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: Board) = encoder.encodeSerializableValue(delegate, value.toList())
    override fun deserialize(decoder: Decoder): Board = Board.fromList(decoder.decodeSerializableValue(delegate))
}
