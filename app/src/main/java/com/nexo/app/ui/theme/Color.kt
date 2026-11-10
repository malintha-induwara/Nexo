package com.nexo.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * Soft Tally palette. The interface is neutral; colour only carries meaning
 * (money direction and category identity).
 */
@Immutable
data class NexoColors(
    val background: Color,
    /** Sheets, the bottom bar, panels. */
    val surface: Color,
    /** Controls sitting on a surface: keypad keys, segmented tracks, chips. */
    val raised: Color,
    /** One step above [raised]: pressed keys, the segmented thumb. */
    val raisedHigh: Color,
    val hairline: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val isDark: Boolean,
) {
    /** Tile tint for a category hue: the hue at low strength over the bare background. */
    fun tint(hue: Color, over: Color = background): Color = hue.copy(alpha = if (isDark) 0.18f else 0.16f).compositeOver(over)
}

val DarkNexoColors = NexoColors(
    background = Color(0xFF121110),
    surface = Color(0xFF1C1B19),
    raised = Color(0xFF272522),
    raisedHigh = Color(0xFF312E2A),
    hairline = Color(0xFF33302C),
    text = Color(0xFFF4F1EA),
    textSecondary = Color(0xFFA39E94),
    textTertiary = Color(0xFF857F75),
    isDark = true,
)

val LightNexoColors = NexoColors(
    background = Color(0xFFF6F4EF),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFEEEBE4),
    raisedHigh = Color(0xFFE4E0D7),
    hairline = Color(0xFFE2DED6),
    text = Color(0xFF1A1917),
    textSecondary = Color(0xFF6B675F),
    textTertiary = Color(0xFF7F7A71),
    isDark = false,
)

val LocalNexoColors = staticCompositionLocalOf { DarkNexoColors }

/**
 * Material roles mapped onto the neutral palette. `primary` is the text colour and
 * `onPrimary` the background, so every stock button, switch and FAB renders inverted
 * rather than in an accent.
 */
internal fun NexoColors.toScheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = text,
        onPrimary = background,
        primaryContainer = raised,
        onPrimaryContainer = text,
        inversePrimary = textSecondary,
        secondary = textSecondary,
        onSecondary = background,
        secondaryContainer = raised,
        onSecondaryContainer = text,
        tertiary = textSecondary,
        onTertiary = background,
        tertiaryContainer = raised,
        onTertiaryContainer = text,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = raised,
        onSurfaceVariant = textSecondary,
        surfaceTint = Color.Transparent,
        inverseSurface = text,
        inverseOnSurface = background,
        error = Color(0xFFFF7B6B),
        onError = background,
        errorContainer = Color(0xFF4A211B),
        onErrorContainer = Color(0xFFFFDAD3),
        outline = textTertiary,
        outlineVariant = hairline,
        scrim = Color.Black,
        surfaceBright = raisedHigh,
        surfaceDim = background,
        surfaceContainerLowest = Color(0xFF0D0C0B),
        surfaceContainerLow = Color(0xFF171614),
        surfaceContainer = surface,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = raisedHigh,
    )
} else {
    lightColorScheme(
        primary = text,
        onPrimary = background,
        primaryContainer = raised,
        onPrimaryContainer = text,
        inversePrimary = textTertiary,
        secondary = textSecondary,
        onSecondary = surface,
        secondaryContainer = raised,
        onSecondaryContainer = text,
        tertiary = textSecondary,
        onTertiary = surface,
        tertiaryContainer = raised,
        onTertiaryContainer = text,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = raised,
        onSurfaceVariant = textSecondary,
        surfaceTint = Color.Transparent,
        inverseSurface = text,
        inverseOnSurface = background,
        error = Color(0xFFC9372C),
        onError = surface,
        errorContainer = Color(0xFFFCE3DE),
        onErrorContainer = Color(0xFF5C1710),
        outline = textTertiary,
        outlineVariant = hairline,
        scrim = Color.Black,
        surfaceBright = surface,
        surfaceDim = raisedHigh,
        surfaceContainerLowest = surface,
        surfaceContainerLow = Color(0xFFFBFAF7),
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = raised,
    )
}

/**
 * Ten category hues. Index order is part of the stored data (`categories.colorIndex`),
 * so append only. Dark values are lifted for a dark ground; light values are deepened
 * for contrast on white.
 */
object CategoryHues {
    private val dark = listOf(
        Color(0xFFFF7A5C), // tomato
        Color(0xFFFFA24C), // tangerine
        Color(0xFFF2C94C), // honey
        Color(0xFF9BCB5A), // moss
        Color(0xFF4FD1A5), // mint
        Color(0xFF5BB8F5), // sky
        Color(0xFF8C93F8), // indigo
        Color(0xFFC98BF0), // orchid
        Color(0xFFF582B4), // rose
        Color(0xFFB5AFA4), // stone
    )
    private val light = listOf(
        Color(0xFFE8573A),
        Color(0xFFE8851F),
        Color(0xFFC9961A),
        Color(0xFF6FA332),
        Color(0xFF23A87A),
        Color(0xFF2A8FD6),
        Color(0xFF5B63E0),
        Color(0xFFA45BD6),
        Color(0xFFDB4F8E),
        Color(0xFF8F887C),
    )
    val count: Int get() = dark.size

    fun of(index: Int, isDark: Boolean): Color {
        val list = if (isDark) dark else light
        return list[Math.floorMod(index, list.size)]
    }
}
