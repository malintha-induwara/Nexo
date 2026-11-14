package com.nexo.app.ui.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nexo.app.R

/**
 * Nunito (rounded) carries every amount: its digits are tabular by default and stay soft at
 * display sizes. Figtree (flat terminals) carries all text and stays crisp at 13sp on dark.
 * Both are variable fonts. The weight axis must be set explicitly: the plain `Font(resId, weight)`
 * overload loads the file's default instance (ExtraLight / Light) whatever weight it is tagged with.
 */
private fun variable(resId: Int, weight: FontWeight) =
    Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val Nunito = FontFamily(
    variable(R.font.nunito_variable, FontWeight.Medium),
    variable(R.font.nunito_variable, FontWeight.SemiBold),
    variable(R.font.nunito_variable, FontWeight.Bold),
    variable(R.font.nunito_variable, FontWeight.ExtraBold),
    variable(R.font.nunito_variable, FontWeight.Black),
)

val Figtree = FontFamily(
    variable(R.font.figtree_variable, FontWeight.Normal),
    variable(R.font.figtree_variable, FontWeight.Medium),
    variable(R.font.figtree_variable, FontWeight.SemiBold),
    variable(R.font.figtree_variable, FontWeight.Bold),
)

private val Trimmed = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun figtree(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) = TextStyle(
    fontFamily = Figtree,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    lineHeightStyle = Trimmed,
)

private fun nunito(size: Int, line: Int, weight: FontWeight = FontWeight.ExtraBold, tracking: Double = 0.0) = TextStyle(
    fontFamily = Nunito,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    lineHeightStyle = Trimmed,
)

/** Scale: hero 56 · section amount 22 · title 20 · row title 16 · body 15 · meta 13 · caps 11. */
val NexoTypography = Typography(
    displayLarge = nunito(56, 60, tracking = -0.02),
    displayMedium = nunito(44, 48, tracking = -0.02),
    displaySmall = nunito(34, 40, tracking = -0.01),
    headlineLarge = figtree(28, 34, FontWeight.SemiBold, -0.01),
    headlineMedium = figtree(24, 30, FontWeight.SemiBold, -0.01),
    headlineSmall = nunito(22, 28),
    titleLarge = figtree(20, 26, FontWeight.SemiBold, -0.005),
    titleMedium = figtree(16, 22, FontWeight.SemiBold),
    titleSmall = figtree(14, 20, FontWeight.SemiBold),
    bodyLarge = figtree(16, 22),
    bodyMedium = figtree(15, 21),
    bodySmall = figtree(13, 18),
    labelLarge = figtree(14, 20, FontWeight.SemiBold, 0.005),
    labelMedium = figtree(12, 16, FontWeight.Medium, 0.01),
    labelSmall = figtree(11, 14, FontWeight.SemiBold, 0.06),
)

/** Styles outside the Material slots. */
object NexoType {
    /** The single dominant number on a screen. */
    val Hero = nunito(56, 60, tracking = -0.02)
    /** Large amount inside a section, e.g. the entry display while typing small sums. */
    val AmountLarge = nunito(22, 28)
    /** The figure a card is about: an account's balance, a category's total. */
    val Balance = nunito(19, 24)
    /** Trailing amount on a 64dp row. */
    val Amount = nunito(16, 22, FontWeight.Bold)
    val AmountSmall = nunito(13, 18, FontWeight.Bold)
    val Keypad = nunito(26, 30, FontWeight.Bold)
    /** Upper-case section and day labels. Callers upper-case the text themselves. */
    val Caps = figtree(11, 14, FontWeight.SemiBold, 0.06)
    val RowTitle = figtree(16, 22, FontWeight.Medium)
    val Meta = figtree(13, 18)
}
