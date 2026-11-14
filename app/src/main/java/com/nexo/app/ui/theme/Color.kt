package com.nexo.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * Loop palette (Nexo-docs/branding/palettes/nexo-loop-palette.json), built from the launcher icon.
 * Structure is strictly neutral: pure white in light mode, graphite in dark. Blue marks actions and
 * emphasis only (buttons, the add button, switches, the active tab, selected states); every other
 * colour carries meaning (money direction and category identity).
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
    /** Brand blue as a fill: buttons, the add button, switches, selected marks. */
    val primary: Color,
    val primaryPressed: Color,
    /** Label and icon colour on [primary]. */
    val onPrimary: Color,
    /** Brand blue for text and icons on the page: links, the active tab. */
    val primaryText: Color,
    /** Quiet blue container: selected chips, banners. */
    val primarySoft: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val isDark: Boolean,
) {
    /** Tile tint for a category hue: the hue at low strength over the bare background. */
    fun tint(hue: Color, over: Color = background): Color = hue.copy(alpha = if (isDark) 0.18f else 0.16f).compositeOver(over)
}

/** Graphite dark base. */
val DarkNexoColors = NexoColors(
    background = Color(0xFF121212),
    surface = Color(0xFF1C1C1C),
    raised = Color(0xFF282828),
    raisedHigh = Color(0xFF333333),
    hairline = Color(0xFF333333),
    text = Color(0xFFF5F5F5),
    textSecondary = Color(0xFFA8A8A8),
    textTertiary = Color(0xFF8E8E8E),
    primary = Color(0xFF3378F8),
    primaryPressed = Color(0xFF2666ED),
    onPrimary = Color(0xFFFFFFFF),
    primaryText = Color(0xFF64AFFD),
    primarySoft = Color(0xFF21304C),
    success = Color(0xFF4CD787),
    warning = Color(0xFFFFB340),
    danger = Color(0xFFFF6961),
    isDark = true,
)

val LightNexoColors = NexoColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F5F5),
    raised = Color(0xFFEBEBEB),
    raisedHigh = Color(0xFFE0E0E0),
    hairline = Color(0xFFE2E2E2),
    text = Color(0xFF111111),
    textSecondary = Color(0xFF5C5C5C),
    textTertiary = Color(0xFF707070),
    primary = Color(0xFF2666ED),
    primaryPressed = Color(0xFF1C5AD5),
    onPrimary = Color(0xFFFFFFFF),
    primaryText = Color(0xFF2462E4),
    primarySoft = Color(0xFFE9F0FD),
    success = Color(0xFF167540),
    warning = Color(0xFF8F5200),
    danger = Color(0xFFD92D20),
    isDark = false,
)

val LocalNexoColors = staticCompositionLocalOf { DarkNexoColors }

/**
 * Material roles mapped onto the palette. `primary` is the brand blue, so stock buttons, switches,
 * FABs and text buttons pick it up; everything else stays neutral.
 */
internal fun NexoColors.toScheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primarySoft,
        onPrimaryContainer = primaryText,
        inversePrimary = LightNexoColors.primary,
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
        error = danger,
        onError = background,
        errorContainer = Color(0xFF4A1F1C),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = textTertiary,
        outlineVariant = hairline,
        scrim = Color.Black,
        surfaceBright = raisedHigh,
        surfaceDim = background,
        surfaceContainerLowest = Color(0xFF0D0D0D),
        surfaceContainerLow = Color(0xFF171717),
        surfaceContainer = surface,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = raisedHigh,
    )
} else {
    lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primarySoft,
        onPrimaryContainer = primaryText,
        inversePrimary = DarkNexoColors.primaryText,
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
        error = danger,
        onError = background,
        errorContainer = Color(0xFFFDE7E5),
        onErrorContainer = Color(0xFF5C1510),
        outline = textTertiary,
        outlineVariant = hairline,
        scrim = Color.Black,
        surfaceBright = background,
        surfaceDim = raisedHigh,
        // Floating containers (dialogs, menus, pickers) stay white over the page.
        surfaceContainerLowest = background,
        surfaceContainerLow = Color(0xFFFAFAFA),
        surfaceContainer = background,
        surfaceContainerHigh = background,
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
