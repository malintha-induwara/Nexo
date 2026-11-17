package com.nexo.app.core.common.money

import com.nexo.app.core.model.Currency
import com.nexo.app.core.model.SymbolPosition
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import kotlin.math.abs

// Split so the integer can be set large. Joined, it matches AmountFormatter.format, but with a
// true minus (U+2212) and an optional plus.
data class AmountParts(
    // "", "−" or "+".
    val sign: String,
    // Without the separating space.
    val prefix: String,
    // With locale grouping, e.g. "48,250".
    val integer: String,
    // Separator and decimals, e.g. ".00"; empty for zero-decimal currencies.
    val fraction: String,
    val suffix: String,
) {
    // For accessibility and copying.
    val text: String get() = sign + (if (prefix.isNotEmpty()) "$prefix " else "") + integer +
        fraction +
        suffix

    enum class Sign { NONE, NEGATIVE, ALWAYS }

    companion object {
        const val MINUS = "−"

        fun of(value: Double, currency: Currency?, sign: Sign = Sign.NEGATIVE): AmountParts = of(
            value,
            currency?.symbol ?: "",
            currency?.symbolPosition ?: SymbolPosition.PREFIX,
            currency?.decimals ?: 2,
            sign,
        )

        fun of(
            value: Double,
            symbol: String,
            position: SymbolPosition,
            decimals: Int,
            sign: Sign = Sign.NEGATIVE,
        ): AmountParts {
            val scale = decimals.coerceIn(0, 10)
            val signum = BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).signum()
            val body = AmountFormatter.number(abs(value), scale)
            val separator = DecimalFormatSymbols.getInstance().decimalSeparator
            val cut = if (scale > 0) body.lastIndexOf(separator) else -1
            val integer = if (cut >= 0) body.substring(0, cut) else body
            val fraction = if (cut >= 0) body.substring(cut) else ""
            val signText = when {
                sign == Sign.NONE || signum == 0 -> ""
                signum < 0 -> MINUS
                sign == Sign.ALWAYS -> "+"
                else -> ""
            }
            return AmountParts(
                sign = signText,
                prefix = if (position == SymbolPosition.PREFIX) symbol else "",
                integer = integer,
                fraction = fraction,
                suffix = if (position == SymbolPosition.SUFFIX) symbol else "",
            )
        }
    }
}
