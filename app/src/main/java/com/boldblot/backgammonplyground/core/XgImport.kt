package com.boldblot.backgammonplyground.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

/**
 * Reads eXtreme Gammon's .xg match files, with the analysis they carry. The layout follows the format
 * eXtreme Gammon publishes (extremegammon.com/xgformat.aspx): a header, then an archive of zlib-compressed
 * files, of which "temp.xg" is the match as a run of 2560-byte records (match header, then per game a header,
 * the cube and checker records of each turn, and a footer).
 *
 * The moves become a [MatImport.ParsedMatch], replayed like a .mat file; [withAnalysis] then gives each
 * decision the analysis XG stored for it. Decisions XG did not analyze stay waiting for PlyGround's own.
 */
object XgImport {

    /** The engine name the analyses read from a file carry. */
    const val ENGINE = "eXtreme Gammon"

    private const val HEADER = 8232
    private const val TRAILER = 36
    private const val REGISTRY_ENTRY = 532
    private const val RECORD = 2560
    private const val MAGIC_FILE = 0x484D4752   // "RGMH"
    private const val MAGIC_MATCH = 0x494C4D44  // "DMLI"
    private const val UNLIMITED = 99999
    private const val MAX_CANDIDATES = 32

    private const val T_MATCH = 0
    private const val T_GAME = 1
    private const val T_CUBE = 2
    private const val T_MOVE = 3
    private const val T_GAME_END = 4

    /** A play XG evaluated: the position after it ([after], 26 cells in the mover's frame), XG's level and its seven values. */
    class Play(val after: IntArray, val level: Int, val eval: FloatArray)

    /** A checker record: the mover (1 = the first player, -1 = the second), the roll, the play made and XG's candidates. */
    class MoveRec(val actif: Int, val dice: IntArray, val played: List<Pair<Int, Int>>, val analyzed: Boolean, val plays: List<Play>)

    /** A cube record: XG writes one before every roll; [double] is 1 when the player doubled, [analyzed] when XG evaluated it. */
    class CubeRec(
        val actif: Int, val double: Int, val take: Int, val beaver: Int, val analyzed: Boolean, val level: Int,
        val noDouble: Float, val doubleTake: Float, val doublePass: Float, val eval: FloatArray,
    )

    /** A player's turn: the cube record and the checker record after it (none when the double was passed). */
    class Turn(val cube: CubeRec?, val move: MoveRec?)

    /** The file read: the match as a .mat file would give it, and XG's records by game number. */
    class Parsed(val match: MatImport.ParsedMatch, val turns: Map<Int, List<Turn>>)

    /** Whether [bytes] start like an .xg file. */
    fun isXg(bytes: ByteArray): Boolean = bytes.size >= 4 && le(bytes).getInt(0) == MAGIC_FILE

    private fun le(b: ByteArray): ByteBuffer = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)

    private fun fail(why: String): Nothing = throw MatImport.FormatException(why)

    /** The archive's file [name], decompressed. */
    internal fun archiveFile(bytes: ByteArray, name: String): ByteArray {
        if (!isXg(bytes) || bytes.size < HEADER + TRAILER) fail("This is not an eXtreme Gammon match file.")
        val b = le(bytes)
        val trailer = bytes.size - TRAILER
        val files = b.getInt(trailer + 4)
        val registrySize = b.getInt(trailer + 12)
        val dataSize = b.getInt(trailer + 16)
        val compressed = bytes[trailer + 20].toInt() != 0
        val registryStart = trailer - registrySize
        val dataStart = registryStart - dataSize
        if (files !in 1..64 || registrySize <= 0 || dataSize <= 0 || dataStart < HEADER) fail("The .xg file is damaged.")
        val raw = bytes.copyOfRange(registryStart, trailer)
        val registry = if (compressed) inflate(raw, files * REGISTRY_ENTRY) else raw
        if (registry.size < files * REGISTRY_ENTRY) fail("The .xg file is damaged.")
        val r = le(registry)
        for (i in 0 until files) {
            val at = i * REGISTRY_ENTRY
            val length = registry[at].toInt() and 0xFF
            if (String(registry, at + 1, length, Charsets.ISO_8859_1) != name) continue
            val size = r.getInt(at + 512)
            val stored = r.getInt(at + 516)
            val start = dataStart + r.getInt(at + 520)
            if (size < 0 || size > 64 shl 20 || stored < 0 || start < dataStart || start + stored > registryStart) fail("The .xg file is damaged.")
            val blob = bytes.copyOfRange(start, start + stored)
            return if (registry[at + 528].toInt() == 0) inflate(blob, size) else blob
        }
        fail("The .xg file has no match in it.")
    }

    private fun inflate(data: ByteArray, size: Int): ByteArray {
        val out = ByteArray(size)
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            var n = 0
            while (n < size && !inflater.finished()) {
                val k = inflater.inflate(out, n, size - n)
                if (k == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                n += k
            }
            if (n != size) fail("The .xg file is damaged.")
        } catch (e: java.util.zip.DataFormatException) {
            fail("The .xg file is damaged.")
        } finally {
            inflater.end()
        }
        return out
    }

    /** Reads an .xg file. Throws [MatImport.FormatException] with a message for the player when it can't. */
    fun parse(bytes: ByteArray): Parsed {
        val data = archiveFile(bytes, "temp.xg")
        val b = le(data)
        if (data.size < RECORD || data[8].toInt() != T_MATCH || b.getInt(556) != MAGIC_MATCH) fail("The .xg file has no match in it.")
        if (b.getInt(96) != 0) fail("Only backgammon matches can be imported (this file is another variant).")
        val length = b.getInt(92).let { if (it >= UNLIMITED || it <= 0) 0 else it }
        val rules = MatchRules(
            length = length, useCube = true, jacoby = length == 0 && data[101].toInt() != 0, crawford = length == 0 || data[100].toInt() != 0,
            beavers = length == 0 && data[102].toInt() != 0, raccoons = length == 0 && data[102].toInt() != 0,
        )
        val names = listOf(
            unicode(data, 880).ifBlank { ansi(data, 9) }.ifBlank { "Player 1" },
            unicode(data, 1138).ifBlank { ansi(data, 50) }.ifBlank { "Player 2" },
        )
        val games = mutableListOf<MatImport.ParsedGame>()
        val turns = HashMap<Int, List<Turn>>()
        var number = 0
        var score = listOf(0, 0)
        var entries = mutableListOf<MatImport.Entry>()
        var gameTurns = mutableListOf<Turn>()
        var cube: CubeRec? = null
        var open = false
        fun close() {
            if (!open) return
            cube?.let { gameTurns += Turn(it, null) }
            cube = null
            games += MatImport.ParsedGame(number, score, entries)
            turns[number] = gameTurns
            open = false
        }
        var at = RECORD
        while (at + RECORD <= data.size) {
            when (data[at + 8].toInt()) {
                T_GAME -> {
                    close()
                    number = b.getInt(at + 48).takeIf { it > 0 } ?: (number + 1)
                    score = listOf(b.getInt(at + 12), b.getInt(at + 16))
                    entries = mutableListOf()
                    gameTurns = mutableListOf()
                    open = true
                }
                T_CUBE -> if (open) {
                    val c = cubeRecord(data, b, at)
                    cube?.let { gameTurns += Turn(it, null) }
                    cube = c
                    val column = column(c.actif)
                    if (c.double == 1) {
                        entries += MatImport.Entry.Double(column)
                        when (c.take) {
                            0 -> entries += MatImport.Entry.Drop(1 - column)
                            2 -> {
                                entries += MatImport.Entry.Beaver(1 - column)
                                if (c.beaver == 2) entries += MatImport.Entry.Beaver(column)
                            }
                            else -> entries += MatImport.Entry.Take(1 - column)
                        }
                    }
                }
                T_MOVE -> if (open) {
                    val m = moveRecord(data, b, at)
                    gameTurns += Turn(cube, m)
                    cube = null
                    val dice = runCatching { Dice(m.dice[0], m.dice[1]) }.getOrElse { fail("Game $number: an unreadable roll.") }
                    // XG's points run 0..23 with the bar at 24 and off at -1; a .mat file's 1..24 with 25 and 0.
                    entries += MatImport.Entry.Roll(column(m.actif), dice, m.played.map { (from, to) -> from + 1 to to + 1 })
                }
                T_GAME_END -> if (open) {
                    val winner = b.getInt(at + 24)
                    val points = b.getInt(at + 28)
                    if (winner != 0 && points > 0) entries += MatImport.Entry.Wins(column(winner), points)
                    close()
                }
            }
            at += RECORD
        }
        close()
        if (games.isEmpty()) fail("The .xg file has no games.")
        return Parsed(MatImport.ParsedMatch(rules, names, started(b.getDouble(128)), games), turns)
    }

    private fun column(actif: Int) = if (actif == 1) 0 else 1

    /** A Delphi date (days since 30 December 1899, local time) as epoch milliseconds; null when not set. */
    private fun started(days: Double): Long? = runCatching {
        if (days.isNaN() || days < 30000 || days > 100000) return null
        java.time.LocalDateTime.of(1899, 12, 30, 0, 0).plusSeconds((days * 86400).toLong())
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()

    /** A name of up to 128 two-byte characters, ended by a zero. */
    private fun unicode(data: ByteArray, at: Int): String {
        val sb = StringBuilder()
        for (i in 0 until 128) {
            val c = (data[at + 2 * i].toInt() and 0xFF) or ((data[at + 2 * i + 1].toInt() and 0xFF) shl 8)
            if (c == 0) break
            sb.append(c.toChar())
        }
        return sb.toString().trim()
    }

    /** A Pascal short string: its length, then its characters. */
    private fun ansi(data: ByteArray, at: Int): String =
        String(data, at + 1, (data[at].toInt() and 0xFF).coerceAtMost(40), Charsets.ISO_8859_1).trim()

    private fun cells(data: ByteArray, at: Int): IntArray = IntArray(26) { data[at + it].toInt() }

    private fun floats(b: ByteBuffer, at: Int): FloatArray = FloatArray(7) { b.getFloat(at + 4 * it) }

    private fun cubeRecord(data: ByteArray, b: ByteBuffer, at: Int): CubeRec {
        val action = at + 64
        val nd = b.getFloat(action + 88)
        val dt = b.getFloat(action + 92)
        val dp = b.getFloat(action + 96)
        val double = b.getInt(at + 16)
        val analyzed = double >= 0 && b.getInt(at + 232) >= 0 && (nd != 0f || dt != 0f || dp != 0f) && listOf(nd, dt, dp).all { it.isFinite() }
        return CubeRec(
            actif = b.getInt(at + 12), double = double, take = b.getInt(at + 20), beaver = b.getInt(at + 24),
            analyzed = analyzed, level = b.getInt(action + 28), noDouble = nd, doubleTake = dt, doublePass = dp, eval = floats(b, action + 60),
        )
    }

    /**
     * The play made, from XG's eight numbers (from, to pairs; points 0..23 in the mover's frame, the bar 24, -1 after
     * the last step). A checker borne off lands "below" point 0 by what is left of the die (-1 to -6): all of it is off (-1).
     */
    internal fun playedSteps(raw: IntArray): List<Pair<Int, Int>> {
        val played = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until 4) {
            val from = raw[2 * i]
            val to = raw[2 * i + 1]
            if (from !in 0..24 || to !in -6..23) break
            played += from to to.coerceAtLeast(-1)
        }
        return played
    }

    private fun moveRecord(data: ByteArray, b: ByteBuffer, at: Int): MoveRec {
        val played = playedSteps(IntArray(8) { b.getInt(at + 68 + 4 * it) })
        val moves = at + 124
        val n = b.getInt(moves + 64).coerceIn(0, MAX_CANDIDATES)
        val plays = (0 until n).map { i ->
            Play(cells(data, moves + 68 + 26 * i), b.getShort(moves + 1156 + 4 * i).toInt(), floats(b, moves + 1284 + 28 * i))
        }
        return MoveRec(b.getInt(at + 64), intArrayOf(b.getInt(at + 100), b.getInt(at + 104)), played, b.getInt(at + 2472) >= 0, plays)
    }

    /** XG's name for an analysis level ("3-ply", "XG Roller++", "rollout"). */
    fun levelName(level: Int): String = when (level) {
        in 0..6 -> "${level + 1}-ply"
        12 -> "3-ply red"
        100 -> "rollout"
        998, 999 -> "opening book"
        1000 -> "XG Roller"
        1001 -> "XG Roller+"
        1002 -> "XG Roller++"
        else -> "level $level"
    }

    /** The nearest depth code for [level]: what [CheckerAnalysis.plies] holds beside the name. */
    private fun depthCode(level: Int): Int = Depth(if (level in 0..6) (level + 1).coerceAtMost(Depth.MAX_SEARCH_PLIES) else Depth.MAX_SEARCH_PLIES, MoveFilter.ALL).code

    /** XG's seven values (lose backgammon, lose gammon, lose, win, win gammon, win backgammon, equity) as PlyGround's five chances. */
    private fun probs(e: FloatArray): List<Float> = listOf(e[3], e[4], e[5], e[1], e[0])

    /** The position [cells] (the mover's frame: 1..24 their points, 25 their bar, 0 the opponent's bar) with [mover] to play. */
    private fun board(cells: IntArray, mover: Int): Board {
        val mine = IntArray(25)
        val theirs = IntArray(25)
        for (i in 1..24) {
            if (cells[i] > 0) mine[i - 1] = cells[i] else if (cells[i] < 0) theirs[24 - i] = -cells[i]
        }
        mine[BAR] = kotlin.math.abs(cells[25])
        theirs[BAR] = kotlin.math.abs(cells[0])
        return if (mover == Player.HUMAN) Board.fromSides(mine, theirs) else Board.fromSides(theirs, mine)
    }

    /**
     * [im] (the file's match replayed with column [you] as the player) with XG's analysis on every decision that
     * has one; those are no longer waiting for the background analysis. A game whose records do not follow the
     * replayed decisions keeps what was matched up to there.
     */
    fun withAnalysis(im: MatImport.ImportedMatch, parsed: Parsed, you: Int): MatImport.ImportedMatch {
        val games = im.games.map { g ->
            val turns = parsed.turns[g.number] ?: return@map g
            MatImport.ImportedGame(g.number, g.score, g.state.copy(decisions = analyzed(g.state.decisions, turns, you)))
        }
        return MatImport.ImportedMatch(im.rules, im.names, im.startedAt, games, im.score, im.winner)
    }

    /**
     * Whether a game that came as [original] from its file was analyzed again ([now]): some decision the file
     * had analyzed carries another analysis. Decisions the file left out do not count: PlyGround analyzes those itself.
     */
    fun reanalyzed(original: GameState, now: GameState): Boolean =
        original.decisions.size != now.decisions.size ||
            original.decisions.withIndex().any { (i, d) -> !d.pending && (d.checker != now.decisions[i].checker || d.cubeEval != now.decisions[i].cubeEval) }

    /** [original] put back over [now]: the file's analysis again, and for decisions it had none for, the analysis they have now. */
    fun restored(original: GameState, now: GameState): GameState = original.copy(
        decisions = original.decisions.mapIndexed { i, d ->
            if (!d.pending) d else now.decisions.getOrNull(i)?.takeIf { it.kind == d.kind && !it.pending && (it.checker != null || it.cubeEval != null) } ?: d
        },
        marked = now.marked,
    )

    private fun analyzed(decisions: List<DecisionRecord>, turns: List<Turn>, you: Int): List<DecisionRecord> {
        fun player(actif: Int) = if (column(actif) == you) Player.HUMAN else Player.CPU
        val out = decisions.toMutableList()
        var t = 0
        for ((i, d) in decisions.withIndex()) {
            val turn = turns.getOrNull(t) ?: break
            when (d.kind) {
                DecisionKind.CHECKER -> {
                    val m = turn.move ?: break
                    val dice = d.dice ?: break
                    if (player(m.actif) != d.player || setOf(m.dice[0], m.dice[1]) != setOf(dice.d1, dice.d2)) break
                    checker(d, m)?.let { out[i] = it }
                    t++
                }
                DecisionKind.DOUBLE, DecisionKind.NO_DOUBLE -> {
                    val c = turn.cube?.takeIf { it.analyzed && player(it.actif) == d.player && (it.double == 1) == (d.kind == DecisionKind.DOUBLE) } ?: continue
                    val e = cubeEval(c)
                    val doubled = d.kind == DecisionKind.DOUBLE
                    out[i] = d.copy(
                        cubeEval = e, error = DecisionMath.doubleError(e, doubled, d.beavers),
                        counted = DecisionMath.isDoubleDecision(e, doubled), pending = false,
                    )
                }
                DecisionKind.TAKE, DecisionKind.PASS, DecisionKind.BEAVER -> {
                    // The doubler's view, as PlyGround's own analysis of a take keeps it.
                    val c = turn.cube?.takeIf { it.analyzed && it.double == 1 && player(it.actif) != d.player } ?: continue
                    val e = cubeEval(c)
                    out[i] = d.copy(cubeEval = e, error = DecisionMath.takeError(e, d.kind, d.beavers), counted = true, pending = false)
                }
                DecisionKind.RACCOON, DecisionKind.NO_RACCOON -> Unit
            }
        }
        return out
    }

    private fun cubeEval(c: CubeRec): CubeEval {
        val after = minOf(c.doubleTake, c.doublePass)
        val double = after > c.noDouble
        return CubeEval(
            noDouble = c.noDouble, doubleTake = c.doubleTake, doublePass = c.doublePass,
            optimal = if (double) after else c.noDouble, shouldDouble = double, shouldTake = c.doubleTake <= c.doublePass,
            optional = kotlin.math.abs(c.noDouble - after) < 1e-4f, cubeAvailable = true, probs = probs(c.eval),
            engine = ENGINE, plies = null, level = levelName(c.level),
        )
    }

    /** [d] with XG's candidates; null when the record does not fit the decision (it then waits for PlyGround's analysis). */
    private fun checker(d: DecisionRecord, m: MoveRec): DecisionRecord? {
        if (!m.analyzed) return null
        val dice = d.dice ?: return null
        val legal = MoveGenerator.legalMoves(d.board, d.player, dice.d1, dice.d2).filter { !it.isEmpty }
        val level = m.plays.firstOrNull()?.level ?: 0
        if (legal.isEmpty()) {
            return d.copy(
                checker = CheckerAnalysis(emptyList(), -1, 0f, 0f, 0f, legalMoves = 0, plies = depthCode(level), engine = ENGINE, level = levelName(level)),
                error = 0f, counted = false, pending = false,
            )
        }
        if (m.plays.isEmpty()) return null
        val byBoard = legal.associateBy { d.board.applyMove(d.player, it) }
        val all = m.plays.map { p ->
            val move = byBoard[board(p.after, d.player)] ?: return null
            if (!p.eval[6].isFinite()) return null
            Candidate(move, p.eval[6], probs(p.eval), depthCode(p.level))
        }
        val target = d.board.applyMove(d.player, d.move ?: Move.NONE)
        val played = all.indexOfFirst { d.board.applyMove(d.player, it.move) == target }
        if (played < 0) return null
        val keep = all.take(MatchController.MAX_CANDIDATES).toMutableList()
        var playedIndex = played
        if (played >= MatchController.MAX_CANDIDATES) {
            keep += all[played]
            playedIndex = keep.lastIndex
        }
        val a = CheckerAnalysis(
            candidates = keep, playedIndex = playedIndex, playedEquity = all[played].equity, bestEquity = all[0].equity,
            worstEquity = all.minOf { it.equity }, legalMoves = legal.size, plies = depthCode(level), engine = ENGINE, level = levelName(level),
        )
        return d.copy(checker = a, error = a.error, counted = a.isDecision, pending = false)
    }
}
