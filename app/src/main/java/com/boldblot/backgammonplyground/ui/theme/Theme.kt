package com.boldblot.backgammonplyground.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.boldblot.backgammonplyground.core.Severity

// Bold Blot brand: ink, warm white and greys; red (the checker) is the only accent and is kept for
// the main call to action (see [BrandRed]).
private val Dark = darkColorScheme(
    primary = Color(0xFFF1F0EC),
    onPrimary = Color(0xFF1D1F21),
    primaryContainer = Color(0xFF3A3E41),
    onPrimaryContainer = Color(0xFFF1F0EC),
    secondary = Color(0xFF9EA1A4),
    onSecondary = Color(0xFF1D1F21),
    secondaryContainer = Color(0xFF2E3234),
    onSecondaryContainer = Color(0xFFE2E1DD),
    tertiary = Color(0xFFE5645F),
    background = Color(0xFF121414),
    onBackground = Color(0xFFF1F0EC),
    surface = Color(0xFF121414),
    onSurface = Color(0xFFF1F0EC),
    surfaceVariant = Color(0xFF2A2D2F),
    onSurfaceVariant = Color(0xFFB4B7BA),
    surfaceContainerLowest = Color(0xFF0D0F0F),
    surfaceContainerLow = Color(0xFF181B1C),
    surfaceContainer = Color(0xFF1D1F21),
    surfaceContainerHigh = Color(0xFF25282A),
    surfaceContainerHighest = Color(0xFF2E3133),
    outline = Color(0xFF8A8D90),
    outlineVariant = Color(0xFF3B3F42),
)

private val Light = lightColorScheme(
    primary = Color(0xFF1D1F21),
    onPrimary = Color(0xFFF1F0EC),
    primaryContainer = Color(0xFFE2E1DD),
    onPrimaryContainer = Color(0xFF1D1F21),
    secondary = Color(0xFF5F6266),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E6E3),
    onSecondaryContainer = Color(0xFF1D1F21),
    tertiary = Color(0xFFD0302B),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1D1F21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1F21),
    surfaceVariant = Color(0xFFE9E8E4),
    onSurfaceVariant = Color(0xFF5F6266),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F6F3),
    surfaceContainer = Color(0xFFF1F0EC),
    surfaceContainerHigh = Color(0xFFEAE9E5),
    surfaceContainerHighest = Color(0xFFE3E2DE),
    outline = Color(0xFF8A8D90),
    outlineVariant = Color(0xFFD5D4D0),
)

/** The brand's red, for the one main action on a screen. */
val BrandRed = Color(0xFFD0302B)

/** Archivo ExtraBold, the brand's display face (titles only). */
val Archivo = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(com.boldblot.backgammonplyground.R.font.archivo_extrabold))

/** Colours for decision quality, consistent across the app (XG-like: red blunder, orange error). */
object Quality {
    fun color(s: Severity, dark: Boolean): Color = when (s) {
        Severity.BLUNDER -> if (dark) Color(0xFFFF6B5E) else Color(0xFFC62828)
        Severity.ERROR -> if (dark) Color(0xFFFFB74D) else Color(0xFFE65100)
        Severity.INACCURACY -> if (dark) Color(0xFFFFE082) else Color(0xFF8D6E00)
        Severity.NONE -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
    }
}

@Composable
fun GammonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
