package com.boldblot.backgammonplyground.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Writes games in the Jellyfish ".mat" match format, which both eXtreme Gammon and
 * GNU Backgammon import. The layout follows gnubg's own "export match mat":
 *
 * ```
 *  5 point match
 *
 *  Game 1
 *  gnubg World Class : 0          Player : 0
 *   1)                             31: 8/5 6/5
 *   2) 52: 13/8 24/22              63: 6/3* 11/5
 *   3)  Doubles => 2                Takes
 *   4)  Doubles => 2                Beavers => 4
 *  ...
 *                                   Wins 2 points
 * ```
 *
 * The computer is the left column (gnubg's player 0) and the human the right one
 * (gnubg's player 1, the human side in gnubg). Each player's moves are written from
 * their own side, one entry per checker step (highest point first), with 25 for the bar, 0 for off and
 * '*' after a hit. Dice are written higher first. A resignation is written as the
 * winner's "Wins n points" line before the game was finished, which is how gnubg
 * and XG record it. A beaver is written as gnubg writes it ("Beavers => 4" instead of "Takes"),
 * and so is a raccoon ("Beavers => 8", with the beaverer's column left empty).
 */
object MatExport {

    data class Info(
        val rules: MatchRules,
        /** Player names by player index ([Player.HUMAN], [Player.CPU]). */
        val names: List<String>,
        val startedAt: Long,
    )

    /** A game with the score before it (by player index). */
    data class Game(val number: Int, val score: List<Int>, val state: GameState)

    private const val NL = "\r\n"

    /** Column of a player: the computer left (0), the human right (1). */
    private fun column(player: Int) = if (player == Player.CPU) 0 else 1

    fun write(info: Info, games: List<Game>): String = buildString {
        val time = Instant.ofEpochMilli(info.startedAt).atZone(ZoneId.systemDefault())
        val left = info.names[Player.CPU]
        val right = info.names[Player.HUMAN]
        append("; [Site \"Backgammon PlyGround\"]").append(NL)
        append("; [Player 1 \"").append(left).append("\"]").append(NL)
        append("; [Player 2 \"").append(right).append("\"]").append(NL)
        append("; [EventDate \"").append(time.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))).append("\"]").append(NL)
        append("; [EventTime \"").append(time.format(DateTimeFormatter.ofPattern("HH.mm"))).append("\"]").append(NL)
        append("; [Variation \"Backgammon\"]").append(NL)
        if (!info.rules.isMoney) append("; [Crawford \"").append(onOff(info.rules.crawford)).append("\"]").append(NL)
        if (info.rules.isMoney) append("; [Jacoby \"").append(onOff(info.rules.jacoby)).append("\"]").append(NL)
        append("; [CubeLimit \"").append(if (info.rules.useCube) "1024" else "1").append("\"]").append(NL)
        append(NL)
        append(' ').append(info.rules.length).append(" point match").append(NL).append(NL)
        for (g in games) writeGame(this, g, left, right)
    }

    private fun onOff(b: Boolean) = if (b) "On" else "Off"

    private fun writeGame(out: StringBuilder, g: Game, left: String, right: String) {
        out.append(" Game ").append(g.number).append(NL)
        val l = "$left : ${g.score[Player.CPU]}"
        out.append(' ').append(l.padEnd(31)).append(right).append(" : ").append(g.score[Player.HUMAN]).append(NL)

        // Entries in playing order; the two players' entries strictly alternate.
        val entries = entries(g.state)
        var slot = if (entries.isNotEmpty()) column(entries.first().first) else 0
        var row = 1
        var lineOpen = false
        for ((player, text) in entries) {
            val col = column(player)
            if (col != slot) {
                // Never expected (players alternate); keep the columns right regardless.
                if (slot == 0) { out.append(rowStart(row, "")); lineOpen = true } else { out.append(NL); row++; lineOpen = false }
                slot = col
            }
            if (col == 0) {
                out.append(rowStart(row, text))
                lineOpen = true
            } else {
                if (!lineOpen) out.append(rowStart(row, ""))
                out.append(text).append(NL)
                lineOpen = false
                row++
            }
            slot = 1 - slot
        }

        val result = g.state.result
        if (result != null) {
            val text = "Wins ${result.points} point${if (result.points > 1) "s" else ""}"
            if (column(result.winner) == 0) {
                if (lineOpen) out.append(NL)
                out.append("      ").append(text).append(NL)
            } else {
                // The right column starts at 33 with a space ("  Takes"); "Wins" goes one further.
                out.append(if (lineOpen) " " else " ".repeat(34))
                out.append(text).append(NL)
            }
        } else if (lineOpen) {
            out.append(NL)
        }
        out.append(NL)
    }

    /** "  3) <left entry padded> " — the start of a row up to the right column. */
    private fun rowStart(row: Int, leftText: String): String = String.format(java.util.Locale.ROOT, "%3d) %-27s ", row, leftText)

    /** (player, text) for every checker play and cube action, in order. */
    fun entries(game: GameState): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        var cube = 1
        var moves = 0
        for (d in game.decisions) {
            when (d.kind) {
                DecisionKind.CHECKER -> {
                    val dice = requireNotNull(d.dice)
                    out += d.player to "$dice: ${formatMove(d.board, d.player, d.move ?: Move.NONE)}".trimEnd()
                    moves++
                }
                DecisionKind.DOUBLE -> {
                    cube *= 2
                    out += d.player to " Doubles => $cube"
                }
                DecisionKind.TAKE -> out += d.player to " Takes"
                DecisionKind.PASS -> out += d.player to " Drops"
                DecisionKind.BEAVER, DecisionKind.RACCOON -> {
                    cube *= 2
                    out += d.player to " Beavers => $cube"
                    if (d.kind == DecisionKind.RACCOON) out += Player.other(d.player) to ""
                }
                DecisionKind.NO_DOUBLE, DecisionKind.NO_RACCOON -> Unit
            }
        }
        // A roll without a move: the player resigned after rolling.
        if (game.rolls.size > moves) {
            val r = game.rolls.last()
            out += r.player to "${r.dice}:"
        }
        return out
    }

    /**
     * gnubg's plain move format: "24/18 13/11*", bar = 25, off = 0, highest starting point
     * first (as gnubg writes them). Hits are found by replaying the steps in playing order.
     */
    fun formatMove(board: Board, player: Int, move: Move): String {
        var b = board
        val hitPoints = mutableListOf<Int>()
        for (s in move.steps) {
            if (b.isHit(player, s)) hitPoints += s.to
            b = b.applyStep(player, s)
        }
        // Like gnubg, the '*' goes on the first listed step that lands on a hit point.
        return move.steps.sortedWith(compareByDescending<Step> { it.from }.thenByDescending { it.to })
            .joinToString(" ") { s -> "${s.from + 1}/${s.to + 1}" + if (hitPoints.remove(s.to)) "*" else "" }
    }
}
