package com.boldblot.backgammonplyground.core

/**
 * A position as eXtreme Gammon's XGID or GNU Backgammon's Position ID (with its Match ID)
 * describe it, in the position editor's terms: [Player.HUMAN] is the bottom player (XG's X,
 * gnubg's player 1), [Player.CPU] the top one (XG's O, gnubg's player 0).
 */
data class PositionSetup(
    val board: Board,
    val onRoll: Int,
    /** Null: no dice yet (a cube decision). */
    val dice: Dice? = null,
    val cube: CubeState = CubeState(),
    /** 0 = money game. */
    val matchLength: Int = 0,
    /** Points each player has won ([Player.HUMAN], [Player.CPU]). */
    val score: List<Int> = listOf(0, 0),
    /** This game is the Crawford game. */
    val crawford: Boolean = false,
    val jacoby: Boolean = true,
    /** A double is on offer (the ID was taken at the take / pass decision). */
    val doubled: Boolean = false,
    /** "XGID" or "GNU ID". */
    val format: String,
)

/** Reads and writes XGIDs and GNU Backgammon Position / Match IDs (layouts as in gnubg 1.08's positionid.c and matchid.c). */
object PositionIds {

    /** Reads an XGID or a GNU ID out of [text] (a pasted line may carry labels around it). */
    fun parse(text: String): PositionSetup {
        val t = text.trim()
        XGID.find(t)?.let { return parseXgid(it.groupValues[1]) }
        val pos = POSITION_ID.find(t)?.value ?: throw IllegalArgumentException(
            "No XGID or GNU Backgammon ID found. Paste an XGID (XGID=-b----E-C---eE---c-e----B-:0:0:1:00:0:0:0:0:10) " +
                "or a GNU ID (4HPwATDgc/ABMA:cAkAAAAAAAAA).",
        )
        val match = MATCH_ID.findAll(t).map { it.value }.firstOrNull { it != pos && it.length == 12 }
        return parseGnu(pos, match)
    }

    // ---- XGID ----------------------------------------------------------------------------------

    private val XGID = Regex("""(?:XGID=)?([-a-pA-P]{26}(?::-?\d+){3}:(?:\d\d|[DBR])(?::\d+){5})""")

    /**
     * XGID fields: 26 board characters (0 = O's bar, 1-24 = X's points 1-24, 25 = X's bar;
     * A-P = X's checkers, a-p = O's), then cube exponent, cube owner (1 X, -1 O, 0 centred),
     * turn (1 X, -1 O), dice ("00" to roll, "D" doubled, "B" beavered, "R" raccooned), X's score,
     * O's score, Crawford (matches) or Jacoby + 2 x beavers (money), match length, cube limit.
     */
    fun parseXgid(id: String): PositionSetup {
        val f = id.removePrefix("XGID=").split(':')
        require(f.size >= 10 && f[0].length == 26) { "This XGID is incomplete." }
        val x = IntArray(25)
        val o = IntArray(25)
        f[0].forEachIndexed { i, c ->
            val n = when (c) { in 'A'..'P' -> c - 'A' + 1; in 'a'..'p' -> c - 'a' + 1; else -> 0 }
            if (n == 0) return@forEachIndexed
            val upper = c.isUpperCase()
            when (i) {
                0 -> { require(!upper) { "Character 1 (O's bar) holds X's checkers." }; o[BAR] = n }
                25 -> { require(upper) { "Character 26 (X's bar) holds O's checkers." }; x[BAR] = n }
                else -> if (upper) x[i - 1] = n else o[24 - i] = n
            }
        }
        val cubeValue = 1 shl f[1].toInt().coerceIn(0, 12)
        val owner = when (f[2].toInt()) { 1 -> Player.HUMAN; -1 -> Player.CPU; else -> -1 }
        val turn = if (f[3].toInt() == -1) Player.CPU else Player.HUMAN
        val doubled = f[4] in setOf("D", "B", "R")
        val dice = f[4].takeIf { !doubled && it != "00" }?.let { Dice(it[0].digitToInt(), it[1].digitToInt()) }
        val length = f[8].toInt()
        val flags = f[7].toInt()
        return PositionSetup(
            board = checked(x, o), onRoll = turn, dice = dice,
            cube = if (cubeValue == 1) CubeState() else CubeState(cubeValue, owner),
            matchLength = length, score = listOf(f[5].toInt(), f[6].toInt()),
            crawford = length > 0 && flags and 1 == 1, jacoby = length == 0 && flags and 1 == 1,
            doubled = doubled, format = "XGID",
        )
    }

    /** The XGID of [s] (cube limit 10, as XG writes by default). */
    fun toXgid(s: PositionSetup): String {
        val sb = StringBuilder()
        for (i in 0..25) {
            val (n, upper) = when (i) {
                0 -> s.board.count(Player.CPU, BAR) to false
                25 -> s.board.count(Player.HUMAN, BAR) to true
                else -> {
                    val xn = s.board.count(Player.HUMAN, i - 1)
                    if (xn > 0) xn to true else s.board.count(Player.CPU, 24 - i) to false
                }
            }
            sb.append(if (n == 0) '-' else ((if (upper) 'A' else 'a') + n - 1))
        }
        val exp = Integer.numberOfTrailingZeros(s.cube.value)
        val owner = when { s.cube.value == 1 || s.cube.owner == -1 -> 0; s.cube.owner == Player.HUMAN -> 1; else -> -1 }
        val dice = when { s.doubled -> "D"; s.dice == null -> "00"; else -> "${s.dice.d1}${s.dice.d2}" }
        val flags = if (s.matchLength > 0) (if (s.crawford) 1 else 0) else (if (s.jacoby) 1 else 0)
        return "XGID=$sb:$exp:$owner:${if (s.onRoll == Player.HUMAN) 1 else -1}:$dice:${s.score[0]}:${s.score[1]}:$flags:${s.matchLength}:10"
    }

    // ---- GNU Backgammon --------------------------------------------------------------------------

    private val POSITION_ID = Regex("""(?<![A-Za-z0-9+/])[A-Za-z0-9+/]{14}(?![A-Za-z0-9+/])""")
    private val MATCH_ID = Regex("""(?<![A-Za-z0-9+/])[A-Za-z0-9+/]{12}(?![A-Za-z0-9+/])""")
    private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private fun bytes(id: String, n: Int): ByteArray {
        val v = id.map { c -> B64.indexOf(c).also { require(it >= 0) { "\"$c\" cannot appear in a GNU ID." } } }
        val out = ByteArray(n)
        var bit = 0
        for (six in v) for (k in 5 downTo 0) {
            if (bit / 8 < n && (six shr k) and 1 == 1) out[bit / 8] = (out[bit / 8].toInt() or (0x80 ushr (bit % 8))).toByte()
            bit++
        }
        return out
    }

    private fun ids(bytes: ByteArray, chars: Int): String {
        val sb = StringBuilder()
        for (c in 0 until chars) {
            var six = 0
            for (k in 0 until 6) {
                val bit = c * 6 + k
                val on = bit / 8 < bytes.size && (bytes[bit / 8].toInt() shr (7 - bit % 8)) and 1 == 1
                six = (six shl 1) or (if (on) 1 else 0)
            }
            sb.append(B64[six])
        }
        return sb.toString()
    }

    /** Bit [i] of a key, counted from the low bit of byte 0 (gnubg's order). */
    private fun bit(key: ByteArray, i: Int) = (key[i / 8].toInt() shr (i % 8)) and 1
    private fun bits(key: ByteArray, from: Int, n: Int) = (0 until n).sumOf { bit(key, from + it) shl it }

    /**
     * A Position ID (14 characters: both sides' checkers in unary, the player not on roll
     * first, each from its own side, ace point first and the bar last) and, optionally, a
     * Match ID (12 characters: cube, owner, player on roll, Crawford, dice, match length, scores,
     * Jacoby). Without a Match ID: you on roll, money, cube centred, no dice.
     */
    fun parseGnu(positionId: String, matchId: String? = null): PositionSetup {
        val key = bytes(positionId, 10)
        val sides = Array(2) { IntArray(25) }
        var side = 0
        var point = 0
        loop@ for (i in 0 until 80) {
            if (bit(key, i) == 1) {
                require(side < 2) { "This Position ID has too many checkers." }
                sides[side][point]++
            } else if (++point == 25) {
                point = 0
                if (++side == 2) break@loop
            }
        }
        if (matchId == null) {
            return PositionSetup(board = checked(sides[1], sides[0]), onRoll = Player.HUMAN, format = "GNU ID")
        }
        val m = bytes(matchId, 9)
        val cube = 1 shl bits(m, 0, 4)
        val owner = when (bits(m, 4, 2)) { 0 -> Player.CPU; 1 -> Player.HUMAN; else -> -1 } // gnubg player 0 is the top
        val move = if (bits(m, 6, 1) == 1) Player.HUMAN else Player.CPU
        val crawford = bits(m, 7, 1) == 1
        val doubled = bits(m, 12, 1) == 1
        val d1 = bits(m, 15, 3)
        val d2 = bits(m, 18, 3)
        val length = bits(m, 21, 15)
        val score0 = bits(m, 36, 15)
        val score1 = bits(m, 51, 15)
        val jacoby = bit(m, 66) == 0
        // The board is seen from the player on roll: that side is the second one.
        val (human, cpu) = if (move == Player.HUMAN) sides[1] to sides[0] else sides[0] to sides[1]
        return PositionSetup(
            board = checked(human, cpu), onRoll = move,
            dice = if (d1 in 1..6 && d2 in 1..6) Dice(d1, d2) else null,
            cube = if (cube == 1) CubeState() else CubeState(cube, owner),
            matchLength = length, score = listOf(score1, score0), crawford = crawford, jacoby = jacoby,
            doubled = doubled, format = "GNU ID",
        )
    }

    /** "PositionID:MatchID" for [s]. */
    fun toGnu(s: PositionSetup): String {
        val onRoll = s.onRoll
        val first = Player.other(onRoll)
        val key = ByteArray(10)
        var i = 0
        for (p in listOf(first, onRoll)) for (pt in 0..24) {
            repeat(s.board.count(p, pt)) { key[i / 8] = (key[i / 8].toInt() or (1 shl (i % 8))).toByte(); i++ }
            i++
        }
        val m = ByteArray(9)
        fun put(from: Int, n: Int, v: Int) = (0 until n).forEach { k ->
            if ((v shr k) and 1 == 1) m[(from + k) / 8] = (m[(from + k) / 8].toInt() or (1 shl ((from + k) % 8))).toByte()
        }
        put(0, 4, Integer.numberOfTrailingZeros(s.cube.value))
        put(4, 2, when { s.cube.value == 1 || s.cube.owner == -1 -> 3; s.cube.owner == Player.HUMAN -> 1; else -> 0 })
        put(6, 1, if (onRoll == Player.HUMAN) 1 else 0)
        put(7, 1, if (s.crawford) 1 else 0)
        put(8, 3, 1) // game playing
        put(11, 1, if (onRoll == Player.HUMAN) 1 else 0)
        put(12, 1, if (s.doubled) 1 else 0)
        put(15, 3, s.dice?.d1 ?: 0)
        put(18, 3, s.dice?.d2 ?: 0)
        put(21, 15, s.matchLength)
        put(36, 15, s.score[Player.CPU])
        put(51, 15, s.score[Player.HUMAN])
        put(66, 1, if (s.jacoby) 0 else 1)
        return gnuChars(key, 14) + ":" + gnuChars(m, 12)
    }

    /** gnubg's base64: each group of 3 bytes, written high bit first. */
    private fun gnuChars(bytes: ByteArray, chars: Int) = ids(bytes, chars)

    private fun checked(human: IntArray, cpu: IntArray): Board {
        require(human.sum() <= CHECKERS_PER_SIDE && cpu.sum() <= CHECKERS_PER_SIDE) { "One side has more than 15 checkers." }
        for (pt in 0 until 24) require(human[pt] == 0 || cpu[23 - pt] == 0) { "Both sides have checkers on point ${pt + 1}." }
        return Board.fromSides(human, cpu)
    }
}
