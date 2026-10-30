package com.boldblot.backgammonplyground.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Reads matches in the Jellyfish ".mat" format, as eXtreme Gammon, GNU Backgammon and
 * [MatExport] write them, and replays them into games with every decision recorded (and
 * waiting for the background analysis).
 *
 * Each game is a numbered list of rows with two columns, the first player's entries on the left
 * and the second player's on the right: "52: 13/8 24/22" (dice, then the steps from the player's
 * own side, 25 or "bar" for the bar, 0 or "off" for off, '*' for a hit, "(2)" for a step made
 * twice, "24/18/13" for one checker moving on), "Doubles => 2", "Takes", "Drops" (or "Passes"),
 * "Beavers => 4", and "Wins 2 points". An entry is in the right column when it starts past
 * the twentieth character of its line.
 */
object MatImport {

    /** An entry of a game: [column] 0 = the first player (left), 1 = the second (right). */
    sealed class Entry(val column: Int) {
        /** [steps] as written (from, to) in the mover's points: 25 = bar, 0 = off; empty when no move was written. */
        class Roll(column: Int, val dice: Dice, val steps: List<Pair<Int, Int>>) : Entry(column)
        class Double(column: Int) : Entry(column)
        class Take(column: Int) : Entry(column)
        class Drop(column: Int) : Entry(column)
        class Beaver(column: Int) : Entry(column)
        class Wins(column: Int, val points: Int) : Entry(column)
    }

    class ParsedGame(val number: Int, /** Score before the game by column. */ val score: List<Int>, val entries: List<Entry>)

    class ParsedMatch(
        val rules: MatchRules,
        /** The players' names by column. */
        val names: List<String>,
        val startedAt: Long?,
        val games: List<ParsedGame>,
    )

    /** A replayed game: the score before it by player index, and the finished game. */
    class ImportedGame(val number: Int, val score: List<Int>, val state: GameState)

    class ImportedMatch(
        val rules: MatchRules,
        /** Names by player index: [Player.HUMAN] is the side the player chose as theirs. */
        val names: List<String>,
        val startedAt: Long?,
        val games: List<ImportedGame>,
        /** Score after the last game, by player index. */
        val score: List<Int>,
        /** The match winner (matches only, once someone reached the length). */
        val winner: Int?,
    )

    class FormatException(message: String) : Exception(message)

    private val header = Regex("""^;\s*\[([A-Za-z0-9 ]+?)\s+"(.*)"\s*]""")
    private val lengthLine = Regex("""^\s*(\d+)\s+point\s+match""", RegexOption.IGNORE_CASE)
    private val gameLine = Regex("""^\s*Game\s+(\d+)\s*$""", RegexOption.IGNORE_CASE)
    // The second name may follow the first score without a space (long names in older PlyGround files).
    private val scoreLine = Regex("""^\s*(.+?)\s*:\s*(\d+)\s*(\D.*?)\s*:\s*(\d+)\s*$""")
    private val rowLine = Regex("""^\s*\d+\)(.*)$""")
    private val entryStart = Regex(
        """(\d)(\d):|Doubles\b|Takes\b|Accepts\b|Drops\b|Passes\b|Rejects\b|Beavers\b|Wins\s+(\d+)\s+points?""",
        RegexOption.IGNORE_CASE,
    )

    /** Entries starting past this column of a line are in the right column. */
    private const val RIGHT_COLUMN = 20

    fun parse(text: String): ParsedMatch {
        val tags = HashMap<String, String>()
        var length: Int? = null
        val games = mutableListOf<ParsedGame>()
        var number = 0
        var score: List<Int>? = null
        var names: List<String>? = null
        var entries = mutableListOf<Entry>()
        fun closeGame() {
            if (number > 0) games += ParsedGame(number, score ?: listOf(0, 0), entries)
            entries = mutableListOf()
            score = null
        }
        for (raw in text.lines()) {
            val line = raw.trimEnd('\r').replace('\t', ' ')
            if (line.isBlank()) continue
            header.find(line)?.let { tags[it.groupValues[1].trim().lowercase()] = it.groupValues[2]; continue }
            if (line.trimStart().startsWith(";")) continue
            if (length == null) lengthLine.find(line)?.let { length = it.groupValues[1].toInt(); continue }
            gameLine.find(line)?.let {
                closeGame()
                number = it.groupValues[1].toInt()
                continue
            }
            if (number > 0 && score == null) {
                scoreLine.find(line)?.let {
                    score = listOf(it.groupValues[2].toInt(), it.groupValues[4].toInt())
                    if (names == null) names = listOf(it.groupValues[1].trim(), it.groupValues[3].trim())
                    continue
                }
            }
            if (number == 0) continue
            // A row ("12) …"), or a line with just "Wins n points".
            val body = rowLine.find(line)?.let { line.length - it.groupValues[1].length } ?: 0
            entries += entriesOf(line, body)
        }
        closeGame()
        val len = length ?: throw FormatException("This is not a .mat match file: no \"N point match\" line.")
        if (games.isEmpty()) throw FormatException("The file has no games.")
        val money = len == 0
        val rules = MatchRules(
            length = len,
            useCube = tags["cubelimit"]?.trim() != "1",
            jacoby = money && tags["jacoby"]?.equals("Off", ignoreCase = true) != true,
            crawford = !money && tags["crawford"]?.equals("Off", ignoreCase = true) != true,
            beavers = money && games.any { g -> g.entries.any { it is Entry.Beaver } },
            raccoons = money && games.any { g -> g.entries.any { it is Entry.Beaver } },
        )
        val players = listOf(tags["player 1"], tags["player 2"])
        val finalNames = (names ?: listOf("Player 1", "Player 2")).mapIndexed { i, n -> players[i]?.takeIf { it.isNotBlank() } ?: n }
        return ParsedMatch(rules, finalNames, startedAt(tags), games)
    }

    private fun startedAt(tags: Map<String, String>): Long? = runCatching {
        val d = tags["eventdate"]?.trim()?.replace('-', '.')?.split('.') ?: return null
        val date = LocalDate.of(d[0].toInt(), d[1].toInt(), d[2].toInt())
        val t = tags["eventtime"]?.trim()?.replace(':', '.')?.split('.')
        val time = if (t != null && t.size >= 2) LocalTime.of(t[0].toInt(), t[1].toInt()) else LocalTime.NOON
        date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()

    /** The entries of one line; [from] is where its row number ends. */
    private fun entriesOf(line: String, from: Int): List<Entry> {
        val starts = entryStart.findAll(line, from).toList()
        return starts.mapIndexed { i, m ->
            val column = if (m.range.first >= RIGHT_COLUMN) 1 else 0
            val end = if (i + 1 < starts.size) starts[i + 1].range.first else line.length
            val word = m.value.lowercase()
            when {
                m.groupValues[1].isNotEmpty() -> {
                    val d1 = m.groupValues[1].toInt()
                    val d2 = m.groupValues[2].toInt()
                    if (d1 !in 1..6 || d2 !in 1..6) throw FormatException("Unreadable dice \"${m.value}\".")
                    Entry.Roll(column, Dice(d1, d2), steps(line.substring(m.range.last + 1, end)))
                }
                word.startsWith("doubles") -> Entry.Double(column)
                word.startsWith("takes") || word.startsWith("accepts") -> Entry.Take(column)
                word.startsWith("drops") || word.startsWith("passes") || word.startsWith("rejects") -> Entry.Drop(column)
                word.startsWith("beavers") -> Entry.Beaver(column)
                else -> Entry.Wins(column, m.groupValues[3].toInt())
            }
        }
    }

    /** "24/18* 13/7(2) bar/20/16" as (from, to) steps in the mover's points (bar 25, off 0). */
    private fun steps(text: String): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        for (token in text.trim().split(Regex("\\s+"))) {
            if (token.isEmpty() || token == "????") continue
            val times = Regex("""\((\d)\)$""").find(token)?.groupValues?.get(1)?.toInt() ?: 1
            val points = token.replace(Regex("""\(\d\)$"""), "").split('/').map { p ->
                when (val s = p.trim('*').lowercase()) {
                    "bar", "b" -> 25
                    "off", "o" -> 0
                    else -> s.toIntOrNull()?.takeIf { it in 0..25 } ?: throw FormatException("Unreadable move \"${text.trim()}\".")
                }
            }
            if (points.size < 2) throw FormatException("Unreadable move \"${text.trim()}\".")
            repeat(times) { for (k in 0 until points.size - 1) out += points[k] to points[k + 1] }
        }
        return out
    }

    /**
     * Replays [p] with column [you] as [Player.HUMAN] (and the other as [Player.CPU]). Every
     * decision is marked for the background analysis. Games that were not finished are left out.
     */
    fun replay(p: ParsedMatch, you: Int): ImportedMatch {
        val rules = p.rules
        fun player(column: Int) = if (column == you) Player.HUMAN else Player.CPU
        val names = listOf(p.names[you], p.names[1 - you])
        val games = mutableListOf<ImportedGame>()
        var crawfordDone = false
        var score = listOf(0, 0)
        var winner: Int? = null
        for (pg in p.games) {
            val before = listOf(pg.score[you], pg.score[1 - you])
            val crawford = !rules.isMoney && rules.crawford && !crawfordDone && before.any { it == rules.length - 1 }
            if (!rules.isMoney && before.any { it == rules.length - 1 }) crawfordDone = true
            val g = try {
                replayGame(pg, rules, before, crawford, ::player)
            } catch (e: FormatException) {
                throw FormatException("Game ${pg.number}: ${e.message}")
            } catch (e: IllegalArgumentException) {
                throw FormatException("Game ${pg.number}: ${e.message}")
            } catch (e: IllegalStateException) {
                throw FormatException("Game ${pg.number}: ${e.message}")
            } ?: continue
            games += ImportedGame(pg.number, before, g)
            val r = g.result!!
            score = before.toMutableList().also { it[r.winner] += r.points }
            if (!rules.isMoney && score[r.winner] >= rules.length) winner = r.winner
        }
        if (games.isEmpty()) throw FormatException("The file has no finished games.")
        return ImportedMatch(rules, names, p.startedAt, games, score, winner)
    }

    /** One game replayed; null if it was not finished. */
    private fun replayGame(pg: ParsedGame, rules: MatchRules, score: List<Int>, crawford: Boolean, player: (Int) -> Int): GameState? {
        var g = GameState(crawford = crawford)
        fun record(who: Int, kind: DecisionKind, dice: Dice? = null, move: Move? = null) {
            g = g.record(
                DecisionRecord(
                    player = who, kind = kind, board = g.board, cube = g.cube, score = score, crawford = crawford,
                    dice = dice, move = move, beavers = kind != DecisionKind.CHECKER && rules.beaversAllowed, pending = true,
                ),
            )
        }
        for ((i, e) in pg.entries.withIndex()) {
            val who = player(e.column)
            if (g.phase == Phase.OVER) break
            when (e) {
                is Entry.Roll -> {
                    when (g.phase) {
                        Phase.OPENING -> g = if (e.dice.isDouble) {
                            g.copy(turn = who, phase = Phase.PRE_ROLL).roll(e.dice)
                        } else {
                            val (high, low) = e.dice.highFirst
                            if (who == Player.HUMAN) g.openingRoll(high, low) else g.openingRoll(low, high)
                        }
                        Phase.BEAVERED, Phase.PRE_ROLL -> {
                            if (g.phase == Phase.BEAVERED) g = g.acceptBeaver()
                            if (g.turn != who) g = g.copy(turn = who)
                            if (g.mayDouble(rules, score)) record(who, DecisionKind.NO_DOUBLE)
                            g = g.roll(e.dice)
                        }
                        else -> throw FormatException("a roll where a cube action was expected.")
                    }
                    val legal = MoveGenerator.legalMoves(g.board, who, e.dice.d1, e.dice.d2).filter { !it.isEmpty }
                    if (e.steps.isEmpty() && legal.isNotEmpty()) {
                        // Rolled, then resigned: the game ends with the next entry ("Wins").
                        if (pg.entries.drop(i + 1).any { it !is Entry.Wins }) throw FormatException("a roll of ${e.dice} without a move.")
                        continue
                    }
                    val move = if (legal.isEmpty()) Move.NONE else {
                        val target = runCatching {
                            e.steps.fold(g.board) { b, (from, to) -> b.applyStep(who, Step(from - 1, if (to == 0) OFF else to - 1)) }
                        }.getOrNull()
                        legal.firstOrNull { g.board.applyMove(who, it) == target }
                            ?: throw FormatException("the move ${e.dice}: ${e.steps.joinToString(" ") { "${it.first}/${it.second}" }} is not legal.")
                    }
                    record(who, DecisionKind.CHECKER, e.dice, move)
                    g = g.move(move, rules)
                }
                is Entry.Double -> {
                    if (g.phase == Phase.BEAVERED) g = g.acceptBeaver()
                    if (g.phase != Phase.PRE_ROLL) throw FormatException("a double out of turn.")
                    if (g.turn != who) g = g.copy(turn = who)
                    record(who, DecisionKind.DOUBLE)
                    g = g.double()
                }
                is Entry.Take -> {
                    if (g.phase != Phase.DOUBLED) throw FormatException("a take without a double.")
                    record(g.responder, DecisionKind.TAKE)
                    g = g.take()
                }
                is Entry.Drop -> {
                    if (g.phase != Phase.DOUBLED) throw FormatException("a pass without a double.")
                    record(g.responder, DecisionKind.PASS)
                    g = g.pass()
                }
                is Entry.Beaver -> when (g.phase) {
                    Phase.DOUBLED -> {
                        record(g.responder, DecisionKind.BEAVER)
                        g = g.beaver(raccoons = true)
                    }
                    Phase.BEAVERED -> {
                        record(g.turn, DecisionKind.RACCOON)
                        g = g.raccoon()
                    }
                    else -> throw FormatException("a beaver without a double.")
                }
                is Entry.Wins -> {
                    // A resignation (the game would otherwise have ended already).
                    val type = when (e.points / g.cube.value.coerceAtLeast(1)) {
                        3 -> WinType.BACKGAMMON
                        2 -> WinType.GAMMON
                        else -> WinType.SINGLE
                    }
                    g = g.copy(phase = Phase.OVER, dice = null, resignOffer = null, result = GameResult(who, type, e.points, resigned = true))
                }
            }
        }
        return g.takeIf { it.result != null }
    }
}
