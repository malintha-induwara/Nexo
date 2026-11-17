package com.nexo.app.ui.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.nexo.app.core.model.CashFlow
import com.nexo.app.core.model.ColorSet
import com.nexo.app.core.model.MemoColor
import com.nexo.app.core.model.ThemeMode

@Immutable
data class HeaderColors(
    val container: Color,
    val content: Color,
    val accent: Color,
    val lightStatusBar: Boolean,
)

// [incomeFill] and [expenseFill] are for filled chart marks: on a dark ground a fill needs a
// deeper step than text does.
@Immutable
data class MoneyColors(
    val income: Color,
    val expense: Color,
    val neutral: Color,
    val incomeFill: Color = income,
    val expenseFill: Color = expense,
) {
    fun of(flow: CashFlow): Color = when (flow) {
        CashFlow.INCOME -> income
        CashFlow.EXPENSE -> expense
        CashFlow.NEUTRAL -> neutral
    }

    fun signed(value: Double): Color = when {
        value > 0 -> income
        value < 0 -> expense
        else -> neutral
    }
}

val LocalHeaderColors = staticCompositionLocalOf {
    HeaderColors(DarkNexoColors.background, DarkNexoColors.text, DarkNexoColors.text, false)
}
val LocalMoneyColors = staticCompositionLocalOf {
    MoneyColors(NexoPalette.IncomeBlueDark, NexoPalette.ExpenseRedDark, DarkNexoColors.text)
}
val LocalIsDark = staticCompositionLocalOf { true }

object NexoPalette {
    val IncomeBlue = LightNexoColors.primaryText
    val IncomeBlueDark = DarkNexoColors.primaryText
    val ExpenseRed = LightNexoColors.danger
    val ExpenseRedDark = DarkNexoColors.danger

    val ExpenseFillDark = Color(0xFFE5554D)

    val Sunday = Color(0xFFE5533D)
    val Saturday = Color(0xFF2F7DE1)

    // Red/orange → yellow → green → teal → blue → purple → pink.
    val Stats = listOf(
        Color(0xFFF2574B),
        Color(0xFFF7883A),
        Color(0xFFF5B83C), Color(0xFFE3D44A), Color(0xFF9ACB4F), Color(0xFF4DBB6E),
        Color(0xFF34B2A6),
        Color(0xFF3E9BE0), Color(0xFF5B6FD8), Color(0xFF9061D1), Color(0xFFE066A8),
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

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

val MaterialTheme.nexo: NexoColors
    @Composable get() = LocalNexoColors.current

@Composable
fun NexoTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    colorSet: ColorSet = ColorSet.A,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(mode)
    val colors = if (dark) DarkNexoColors else LightNexoColors
    val scheme = colors.toScheme()
    val header = HeaderColors(colors.background, colors.text, colors.text, lightStatusBar = !dark)
    val blue = if (dark) NexoPalette.IncomeBlueDark else NexoPalette.IncomeBlue
    val red = if (dark) NexoPalette.ExpenseRedDark else NexoPalette.ExpenseRed
    val blueFill = if (dark) colors.primary else blue
    val redFill = if (dark) NexoPalette.ExpenseFillDark else red
    val money = if (colorSet == ColorSet.A) {
        MoneyColors(blue, red, colors.text, blueFill, redFill)
    } else {
        MoneyColors(red, blue, colors.text, redFill, blueFill)
    }
    // Status / navigation bar icons follow the theme brightness.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(
        LocalNexoColors provides colors,
        LocalHeaderColors provides header,
        LocalMoneyColors provides money,
        LocalIsDark provides dark,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = NexoTypography,
            shapes = NexoMaterialShapes,
        ) {
            // Screens without a Scaffold or Surface (the tab roots) still get readable text.
            CompositionLocalProvider(LocalContentColor provides colors.text, content = content)
        }
    }
}
