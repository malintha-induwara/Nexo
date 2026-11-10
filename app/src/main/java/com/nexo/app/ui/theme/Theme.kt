package com.nexo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.nexo.app.core.model.CashFlow
import com.nexo.app.core.model.ColorSet
import com.nexo.app.core.model.MemoColor
import com.nexo.app.core.settings.ThemeColor
import com.nexo.app.core.settings.ThemeMode

/** Colours of the header bar for the selected colour theme. */
@Immutable
data class HeaderColors(val container: Color, val content: Color, val accent: Color, val lightStatusBar: Boolean)

/** Income / expense colours (CFG-014). */
@Immutable
data class MoneyColors(val income: Color, val expense: Color, val neutral: Color) {
    fun of(flow: CashFlow): Color = when (flow) {
        CashFlow.INCOME -> income
        CashFlow.EXPENSE -> expense
        CashFlow.NEUTRAL -> neutral
    }

    /** Colour for a signed figure: positive = income colour, negative = expense colour. */
    fun signed(value: Double): Color = when {
        value > 0 -> income
        value < 0 -> expense
        else -> neutral
    }
}

val LocalHeaderColors = staticCompositionLocalOf { HeaderColors(Color.White, Color.Black, Color(0xFF00897B), true) }
val LocalMoneyColors = staticCompositionLocalOf { MoneyColors(Color(0xFF1F88FF), Color(0xFFFF603B), Color.Black) }
val LocalIsDark = staticCompositionLocalOf { false }

object NexoPalette {
    val Brand = Color(0xFF00897B)
    val Red = Color(0xFFF55345)
    val Pink = Color(0xFFFC7C9A)
    val Green = Color(0xFF1DBF75)
    val Blue = Color(0xFF1999FC)
    val Black = Color(0xFF4A4A4A)

    val IncomeBlue = Color(0xFF1F88FF)
    val IncomeBlueDark = Color(0xFF52A2FF)
    val ExpenseRed = Color(0xFFFF603B)
    val ExpenseRedDark = Color(0xFFFD5F59)

    val Sunday = Color(0xFFE5533D)
    val Saturday = Color(0xFF2F7DE1)

    /** 11-colour stats palette (ST-004), red/orange → yellow → green → teal → blue → purple → pink. */
    val Stats = listOf(
        Color(0xFFF2574B), Color(0xFFF7883A), Color(0xFFF5B83C), Color(0xFFE3D44A), Color(0xFF9ACB4F), Color(0xFF4DBB6E),
        Color(0xFF34B2A6), Color(0xFF3E9BE0), Color(0xFF5B6FD8), Color(0xFF9061D1), Color(0xFFE066A8),
    )

    fun memo(color: MemoColor, dark: Boolean): Color = when (color) {
        MemoColor.WHITE -> if (dark) Color(0xFF2B2B2B) else Color(0xFFFFFFFF)
        MemoColor.RED -> if (dark) Color(0xFF5C2B2B) else Color(0xFFFFD9D6)
        MemoColor.ORANGE -> if (dark) Color(0xFF5C3B22) else Color(0xFFFFE3C7)
        MemoColor.YELLOW -> if (dark) Color(0xFF5A5222) else Color(0xFFFFF4BF)
        MemoColor.GREEN -> if (dark) Color(0xFF274A2E) else Color(0xFFD7F2D9)
        MemoColor.CYAN -> if (dark) Color(0xFF214A4C) else Color(0xFFD1F2F2)
        MemoColor.BLUE -> if (dark) Color(0xFF24395C) else Color(0xFFD6E6FF)
        MemoColor.DARKBLUE -> if (dark) Color(0xFF262C55) else Color(0xFFC9CDF2)
        MemoColor.PURPLE -> if (dark) Color(0xFF42295A) else Color(0xFFE6D6F7)
        MemoColor.PINK -> if (dark) Color(0xFF5A2945) else Color(0xFFFFD6EA)
        MemoColor.BROWN -> if (dark) Color(0xFF4A3727) else Color(0xFFE8D7C7)
        MemoColor.GRAY -> if (dark) Color(0xFF3A3A3A) else Color(0xFFE2E2E2)
    }

    fun memoDot(color: MemoColor): Color = when (color) {
        MemoColor.WHITE -> Color(0xFFFFFFFF)
        MemoColor.RED -> Color(0xFFEF5350)
        MemoColor.ORANGE -> Color(0xFFFF9800)
        MemoColor.YELLOW -> Color(0xFFFDD835)
        MemoColor.GREEN -> Color(0xFF66BB6A)
        MemoColor.CYAN -> Color(0xFF26C6DA)
        MemoColor.BLUE -> Color(0xFF42A5F5)
        MemoColor.DARKBLUE -> Color(0xFF3949AB)
        MemoColor.PURPLE -> Color(0xFFAB47BC)
        MemoColor.PINK -> Color(0xFFEC407A)
        MemoColor.BROWN -> Color(0xFF8D6E63)
        MemoColor.GRAY -> Color(0xFF9E9E9E)
    }
}

fun ThemeColor.accent(): Color = when (this) {
    ThemeColor.WHITE -> NexoPalette.Brand
    ThemeColor.RED -> NexoPalette.Red
    ThemeColor.PINK -> NexoPalette.Pink
    ThemeColor.GREEN -> NexoPalette.Green
    ThemeColor.BLUE -> NexoPalette.Blue
    ThemeColor.BLACK -> NexoPalette.Black
}

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

private fun lightScheme(accent: Color): ColorScheme = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.14f),
    onPrimaryContainer = Color(0xFF1B1B1B),
    secondary = accent,
    background = Color(0xFFF7F7F8),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F1F3),
    surfaceContainer = Color(0xFFF4F4F6),
    surfaceContainerLow = Color(0xFFF9F9FA),
    surfaceContainerHigh = Color(0xFFEDEDF0),
    surfaceContainerHighest = Color(0xFFE6E6EA),
    outlineVariant = Color(0xFFE2E2E6),
)

private fun darkScheme(accent: Color): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.28f),
    secondary = accent,
    background = Color(0xFF121212),
    surface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFF2A2A2D),
    surfaceContainer = Color(0xFF222225),
    surfaceContainerLow = Color(0xFF1A1A1C),
    surfaceContainerHigh = Color(0xFF2A2A2D),
    surfaceContainerHighest = Color(0xFF333336),
    outlineVariant = Color(0xFF3A3A3E),
)

@Composable
fun NexoTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    color: ThemeColor = ThemeColor.WHITE,
    colorSet: ColorSet = ColorSet.A,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(mode)
    val accent = if (dark) Color(0xFF26A69A) else color.accent()
    val scheme = if (dark) darkScheme(accent) else lightScheme(accent)
    val header = when {
        dark -> HeaderColors(Color(0xFF1C1C1E), Color.White, accent, false)
        color == ThemeColor.WHITE -> HeaderColors(Color.White, Color(0xFF1B1B1B), accent, true)
        else -> HeaderColors(accent, Color.White, accent, false)
    }
    val blue = if (dark) NexoPalette.IncomeBlueDark else NexoPalette.IncomeBlue
    val red = if (dark) NexoPalette.ExpenseRedDark else NexoPalette.ExpenseRed
    val money = if (colorSet == ColorSet.A) MoneyColors(blue, red, scheme.onSurface) else MoneyColors(red, blue, scheme.onSurface)
    // Status / navigation bar icons follow the header and theme brightness.
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = header.lightStatusBar
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(
        LocalHeaderColors provides header,
        LocalMoneyColors provides money,
        LocalIsDark provides dark,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
