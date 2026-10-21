package notesplus.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** The 16 fixed accent colors (Windows-style accents). */
val ACCENT_PALETTE = listOf(
    Color(0xFF0063B1), // Blue
    Color(0xFF0099BC), // Sky
    Color(0xFF00B7C3), // Turquoise
    Color(0xFF00897B), // Teal
    Color(0xFF107C10), // Green
    Color(0xFF6B8E23), // Olive
    Color(0xFFCA9A00), // Gold
    Color(0xFFCA5010), // Orange
    Color(0xFFD13438), // Red
    Color(0xFFC30052), // Raspberry
    Color(0xFFE3008C), // Pink
    Color(0xFF881798), // Plum
    Color(0xFF744DA9), // Violet
    Color(0xFF4A5BD0), // Indigo
    Color(0xFF8E562E), // Brown
    Color(0xFF69797E), // Graphite
)

/** Used for Auto when Windows doesn't report an accent color. */
val DEFAULT_ACCENT = ACCENT_PALETTE[0]

fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return maxOf(la, lb) / minOf(la, lb)
}

/** Black or white, whichever reads better on [bg] (always at least 4.5:1). */
fun readableOn(bg: Color): Color = if (contrast(bg, Color.White) >= contrast(bg, Color.Black)) Color.White else Color.Black

fun colorSchemeFor(accent: Color, dark: Boolean): ColorScheme {
    if (dark) {
        val surface = Color(0xFF1C1B1F)
        val primary = lerp(accent, Color.White, 0.35f)
        val container = lerp(accent, surface, 0.55f)
        return darkColorScheme(
            primary = primary,
            onPrimary = readableOn(primary),
            primaryContainer = container,
            onPrimaryContainer = readableOn(container),
            secondaryContainer = container,
            onSecondaryContainer = readableOn(container),
        )
    }
    val surface = Color(0xFFFFFBFE)
    val container = lerp(accent, surface, 0.8f)
    return lightColorScheme(
        primary = accent,
        onPrimary = readableOn(accent),
        primaryContainer = container,
        onPrimaryContainer = readableOn(container),
        secondaryContainer = container,
        onSecondaryContainer = readableOn(container),
    )
}

@Composable
fun NotesPlusTheme(accent: Color, dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorSchemeFor(accent, dark), content = content)
}
