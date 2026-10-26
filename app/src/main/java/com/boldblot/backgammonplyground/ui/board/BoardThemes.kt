package com.boldblot.backgammonplyground.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.boldblot.backgammonplyground.data.BoardTheme
import com.boldblot.backgammonplyground.data.CheckerSide
import com.boldblot.backgammonplyground.data.CheckerStyle
import com.boldblot.backgammonplyground.data.CustomBoard
import com.boldblot.backgammonplyground.data.Settings

/**
 * A board's own colours: everything else (rims, pips on the dice, the tray) is derived unless given.
 * [first] and [second] are its pair of checker colours, named for the "My checkers" choice.
 */
data class BoardPalette(
    val frame: Color,
    val surface: Color,
    val pointLight: Color,
    val pointDark: Color,
    val first: Color,
    val second: Color,
    val firstName: String,
    val secondName: String,
    val numbers: Color,
    val highlight: Color,
    val lastMove: Color,
    val firstRim: Color? = null,
    val secondRim: Color? = null,
    val firstPip: Color? = null,
    val secondPip: Color? = null,
    val tray: Color? = null,
) {
    fun colors(side: CheckerSide, style: CheckerStyle): BoardColors {
        val mine = side == CheckerSide.FIRST
        val a = Triple(first, firstRim ?: rimFor(first), firstPip ?: pipFor(first))
        val b = Triple(second, secondRim ?: rimFor(second), secondPip ?: pipFor(second))
        val (h, c) = if (mine) a to b else b to a
        return BoardColors(
            frame = frame, surface = surface, pointLight = pointLight, pointDark = pointDark,
            human = h.first, humanRim = h.second, cpu = c.first, cpuRim = c.second,
            humanPip = h.third, cpuPip = c.third,
            highlight = highlight, lastMove = lastMove, label = numbers,
            tray = tray ?: lerp(frame, Color.Black, 0.3f), checkerStyle = style,
        )
    }

    companion object {
        /** A checker's rim: darker, or lighter for very dark checkers (so the rim still shows). */
        fun rimFor(c: Color): Color = if (c.luminance() < 0.06f) lerp(c, Color.White, 0.32f) else lerp(c, Color.Black, 0.4f)

        fun pipFor(c: Color): Color = if (c.luminance() > 0.4f) Color(0xFF2B1D05) else Color(0xFFFFF4E8)
    }
}

object BoardThemes {
    /** Grey felt, black and white points, warm yellow and red checkers (the original board). */
    val matts = BoardPalette(
        frame = Color(0xFF3A3D41), surface = Color(0xFF8E9297),
        pointLight = Color(0xFFF1F0EC), pointDark = Color(0xFF1E1F21),
        first = Color(0xFFF2C14E), second = Color(0xFFC8372D), firstName = "Yellow", secondName = "Red",
        firstRim = Color(0xFFA9781A), secondRim = Color(0xFF7A1C16),
        firstPip = Color(0xFF2B1D05), secondPip = Color(0xFFFFF4E8),
        numbers = Color(0xFFD8DADD), highlight = Color(0xFF19D3C5), lastMove = Color(0xFF3D7BFF), tray = Color(0xFF2A2C2F),
    )

    /** Wooden frame, green felt, cream and red points, white and black checkers. */
    val classic = BoardPalette(
        frame = Color(0xFF4A2F1B), surface = Color(0xFF285C43),
        pointLight = Color(0xFFE6D3AA), pointDark = Color(0xFFA9412C),
        first = Color(0xFFF5F0E6), second = Color(0xFF2A2A2A), firstName = "White", secondName = "Black",
        firstRim = Color(0xFF8A7F6C), secondRim = Color(0xFF6B6B6B),
        firstPip = Color(0xFF1E1E1E), secondPip = Color(0xFFF2F2F2),
        numbers = Color(0xFFE9E1CF), highlight = Color(0xFFFFC94A), lastMove = Color(0xFF7FD3FF), tray = Color(0xFF3A2414),
    )

    /** Warm clay: terracotta frame, sand surface, cream and rust points, ivory and charcoal checkers. */
    val claudes = BoardPalette(
        frame = Color(0xFF9C4F35), surface = Color(0xFFD9C3A0),
        pointLight = Color(0xFFF7F0E3), pointDark = Color(0xFFB5532F),
        first = Color(0xFFFBF7EE), second = Color(0xFF34302C), firstName = "Ivory", secondName = "Charcoal",
        firstRim = Color(0xFF9A8C74), secondRim = Color(0xFF7A726A),
        numbers = Color(0xFFF8E9DC), highlight = Color(0xFF168F86), lastMove = Color(0xFF2F6FE0), tray = Color(0xFF6E3423),
    )

    /** Dark walnut frame and surface, ivory and ebony points, ivory and mahogany checkers. */
    val walnut = BoardPalette(
        frame = Color(0xFF3B2416), surface = Color(0xFF6E4A2F),
        pointLight = Color(0xFFEDE3CF), pointDark = Color(0xFF24160D),
        first = Color(0xFFF7F1E3), second = Color(0xFF8E2A1E), firstName = "Ivory", secondName = "Mahogany",
        firstRim = Color(0xFF9C8F78),
        numbers = Color(0xFFE8D9C0), highlight = Color(0xFFFFC94A), lastMove = Color(0xFF7FD3FF),
    )

    /** Navy felt, white and steel-blue points, white and orange checkers. */
    val navy = BoardPalette(
        frame = Color(0xFF14213D), surface = Color(0xFF27406B),
        pointLight = Color(0xFFE9EDF2), pointDark = Color(0xFF7C9CC4),
        first = Color(0xFFFFFFFF), second = Color(0xFFF08A24), firstName = "White", secondName = "Orange",
        firstRim = Color(0xFF8C97A8),
        numbers = Color(0xFFD6DEEA), highlight = Color(0xFFFFE066), lastMove = Color(0xFF7FFFD4),
    )

    /** Black frame, tan leather, red and black points, white and black checkers. */
    val leather = BoardPalette(
        frame = Color(0xFF161616), surface = Color(0xFFB08A5E),
        pointLight = Color(0xFFB3242A), pointDark = Color(0xFF1C1917),
        first = Color(0xFFF5F2EA), second = Color(0xFF242120), firstName = "White", secondName = "Black",
        firstRim = Color(0xFF8E8676), secondRim = Color(0xFF8A7F74),
        numbers = Color(0xFFD9CBB5), highlight = Color(0xFF19D3C5), lastMove = Color(0xFF3D7BFF),
    )

    /** Olive felt, cream and brown points, white and burgundy checkers. */
    val olive = BoardPalette(
        frame = Color(0xFF3E3A2B), surface = Color(0xFF7D8450),
        pointLight = Color(0xFFF2EBD3), pointDark = Color(0xFF4A3B2A),
        first = Color(0xFFFBF8F0), second = Color(0xFF7B1E2B), firstName = "White", secondName = "Burgundy",
        firstRim = Color(0xFF948C78),
        numbers = Color(0xFFE5E0CC), highlight = Color(0xFFFFC94A), lastMove = Color(0xFF7FD3FF),
    )

    /** Black and dark grey, white and mid-grey points, yellow and blue checkers: easy to tell apart. */
    val contrast = BoardPalette(
        frame = Color(0xFF000000), surface = Color(0xFF2B2B2B),
        pointLight = Color(0xFFFFFFFF), pointDark = Color(0xFF6A6A6A),
        first = Color(0xFFFFD400), second = Color(0xFF2D7DFF), firstName = "Yellow", secondName = "Blue",
        firstRim = Color(0xFF8C7400), secondRim = Color(0xFF0F3F99), secondPip = Color(0xFFFFFFFF),
        numbers = Color(0xFFFFFFFF), highlight = Color(0xFFFF3FA4), lastMove = Color(0xFFB388FF),
    )

    fun palette(t: BoardTheme): BoardPalette = when (t) {
        BoardTheme.GRAY, BoardTheme.CUSTOM -> matts
        BoardTheme.CLASSIC -> classic
        BoardTheme.CLAUDE -> claudes
        BoardTheme.WALNUT -> walnut
        BoardTheme.NAVY -> navy
        BoardTheme.LEATHER -> leather
        BoardTheme.OLIVE -> olive
        BoardTheme.CONTRAST -> contrast
    }

    /** A board designer design; its checkers are named after the nearest swatches. */
    fun palette(b: CustomBoard): BoardPalette =
        BoardPalette(
            frame = argb(b.frame), surface = argb(b.surface), pointLight = argb(b.pointLight), pointDark = argb(b.pointDark),
            first = argb(b.mine), second = argb(b.theirs),
            firstName = Swatches.nearestName(b.mine), secondName = Swatches.nearestName(b.theirs),
            numbers = argb(b.numbers), highlight = argb(b.highlight), lastMove = Color(0xFF6EC1FF),
        )

    /** The board in use: [Settings.boardTheme], or the saved design (Matt's if that slot is empty). */
    fun palette(s: Settings): BoardPalette =
        if (s.boardTheme == BoardTheme.CUSTOM) s.customBoards.getOrNull(s.customSlot)?.let { palette(it) } ?: matts
        else palette(s.boardTheme)

    /** The current board as a starting point for a new design. */
    fun toCustom(p: BoardPalette, name: String) = CustomBoard(
        name = name, frame = p.frame.argbLong(), surface = p.surface.argbLong(),
        pointLight = p.pointLight.argbLong(), pointDark = p.pointDark.argbLong(),
        mine = p.first.argbLong(), theirs = p.second.argbLong(), numbers = p.numbers.argbLong(), highlight = p.highlight.argbLong(),
    )
}

fun argb(v: Long): Color = Color(v.toInt())

fun Color.argbLong(): Long {
    fun ch(x: Float) = (x * 255f + 0.5f).toInt().coerceIn(0, 255).toLong()
    return (ch(alpha) shl 24) or (ch(red) shl 16) or (ch(green) shl 8) or ch(blue)
}

/** The board designer's palette: a few named colours. */
object Swatches {
    val all: List<Pair<String, Long>> = listOf(
        "White" to 0xFFF5F2EA, "Ivory" to 0xFFEDE3CF, "Cream" to 0xFFF2EBD3, "Sand" to 0xFFD9C3A0,
        "Tan" to 0xFFB08A5E, "Caramel" to 0xFFC27C3A, "Walnut" to 0xFF6E4A2F, "Dark brown" to 0xFF3B2416,
        "Yellow" to 0xFFF2C14E, "Gold" to 0xFFD4A017, "Orange" to 0xFFF08A24, "Terracotta" to 0xFF9C4F35,
        "Rust" to 0xFFB5532F, "Red" to 0xFFC8372D, "Burgundy" to 0xFF7B1E2B, "Pink" to 0xFFE58FB0,
        "Lime" to 0xFFA4C639, "Olive" to 0xFF7D8450, "Green" to 0xFF285C43, "Forest" to 0xFF1E3B2A,
        "Teal" to 0xFF168F86, "Turquoise" to 0xFF19D3C5, "Sky" to 0xFF6FA8DC, "Blue" to 0xFF2D7DFF,
        "Navy" to 0xFF27406B, "Midnight" to 0xFF14213D, "Purple" to 0xFF7E57C2, "Lavender" to 0xFFB9A6E0,
        "Light grey" to 0xFFC9CCD0, "Grey" to 0xFF8E9297, "Slate" to 0xFF3A3D41, "Charcoal" to 0xFF34302C, "Black" to 0xFF1E1F21,
    )

    fun nameOf(v: Long): String? = all.firstOrNull { it.second == v }?.first

    /** The name of [v], or of the closest swatch (for colours a design took over from a ready-made board). */
    fun nearestName(v: Long): String = nameOf(v) ?: all.minBy { (_, w) ->
        // weighted RGB (blue 3, green 4, red 2): closer to how different colours look than plain RGB
        (0..2).sumOf { k -> val d = ((v shr (8 * k)) and 0xFF) - ((w shr (8 * k)) and 0xFF); longArrayOf(3, 4, 2)[k] * d * d }
    }.first
}

/** A small sample of a board: two points (one light, one dark) with two checkers of each colour. */
@Composable
fun BoardPreview(c: BoardColors, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val inset = size.minDimension * 0.1f
        drawRect(c.frame)
        drawRect(c.surface, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset))
        val w = (size.width - 2 * inset) / 2f
        val r = w * 0.3f
        val base = size.height - inset
        for (k in 0..1) {
            val left = inset + k * w
            val path = Path().apply {
                moveTo(left, base); lineTo(left + w, base); lineTo(left + w / 2, inset + (size.height - 2 * inset) * 0.08f); close()
            }
            drawPath(path, if (k == 0) c.pointLight else c.pointDark)
            val (fill, rim) = if (k == 0) c.human to c.humanRim else c.cpu to c.cpuRim
            for (n in 0..1) checker(Offset(left + w / 2, base - r - n * 2 * r), r, fill, rim, c.checkerStyle)
        }
    }
}
