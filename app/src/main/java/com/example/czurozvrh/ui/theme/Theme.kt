package com.example.czurozvrh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EEDF),
    onPrimaryContainer = Color(0xFF0B3A24),
    secondary = Color(0xFF4F5F56),
    secondaryContainer = LightSurfaceHigh,
    onSecondaryContainer = LightInk,
    background = LightBg,
    onBackground = LightInk,
    surface = LightBg,
    onSurface = LightInk,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightMuted,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = Color(0xFFE3E6DF),
    outline = Color(0xFFC5CBC3),
    outlineVariant = LightLine,
    error = NowRed,
)

private val DarkColors = darkColorScheme(
    primary = ForestLight,
    onPrimary = Color(0xFF06291A),
    primaryContainer = Color(0xFF1D3D2C),
    onPrimaryContainer = Color(0xFFC6EED6),
    secondary = Color(0xFFB4C2B9),
    secondaryContainer = DarkSurfaceHigh,
    onSecondaryContainer = DarkInk,
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkBg,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkMuted,
    surfaceContainerLowest = DarkBg,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = Color(0xFF2B322D),
    outline = Color(0xFF414943),
    outlineVariant = DarkLine,
    error = NowRed,
)

/** Barvy jednoho předmětu: podklad bloku, výrazný proužek a text. */
@Immutable
data class SubjectColors(val container: Color, val accent: Color, val onContainer: Color, val muted: Color)

@Immutable
class SubjectPalette(
    private val dark: Boolean,
    private val surface: Color,
    private val ink: Color,
    private val order: Map<String, Int> = emptyMap(),
) {
    /** Paleta, kde každý z daných předmětů dostane jinou barvu (dokud jich není víc než barev). */
    fun ordered(keys: List<String>) = SubjectPalette(dark, surface, ink, keys.distinct().withIndex().associate { it.value to it.index })

    fun of(key: String): SubjectColors {
        val idx = order[key] ?: Math.floorMod(key.hashCode(), SubjectAccents.size)
        val accent = SubjectAccents[idx % SubjectAccents.size]
        val container = lerp(surface, accent, if (dark) 0.26f else 0.16f)
        val text = lerp(ink, accent, if (dark) 0.10f else 0.35f)
        return SubjectColors(container, if (dark) lerp(accent, Color.White, 0.15f) else accent, text, text.copy(alpha = 0.72f))
    }
}

val LocalSubjectPalette = staticCompositionLocalOf { SubjectPalette(false, LightSurface, LightInk) }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun CZURozvrhTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val palette = if (darkTheme) SubjectPalette(true, DarkSurface, DarkInk) else SubjectPalette(false, LightSurface, LightInk)
    androidx.compose.runtime.CompositionLocalProvider(LocalSubjectPalette provides palette) {
        MaterialTheme(colorScheme = colors, typography = Typography, shapes = AppShapes, content = content)
    }
}
