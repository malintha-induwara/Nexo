package com.nexo.app.core.common.money

import com.nexo.app.data.database.entity.CurrencyEntity
import com.nexo.app.core.model.SymbolPosition
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols

/**
 * A formatted amount split for display, so the integer can be set large and the symbol and
 * decimals small. Joining the parts gives the same text as [AmountFormatter.format], except
 * that the sign uses a true minus (U+2212) and a plus can be shown.
 */
data class AmountParts(
    /** "", "−" or "+". */
    val sign: String,
    /** Currency symbol shown before the number, without the separating space. */
    val prefix: String,
    /** Integer digits with locale grouping, e.g. "48,250". */
    val integer: String,
    /** Decimal separator and decimals, e.g. ".00"; empty for zero-decimal currencies. */
    val fraction: String,
    /** Currency symbol shown after the number. */
    val suffix: String,
) {
    /** Plain text for accessibility and copying. */
    val text: String get() = sign + (if (prefix.isNotEmpty()) "$prefix " else "") + integer + fraction + suffix

    enum class Sign { NONE, NEGATIVE, ALWAYS }

    companion object {
        const val MINUS = "−"

        fun of(value: Double, currency: CurrencyEntity?, sign: Sign = Sign.NEGATIVE): AmountParts =
            of(value, currency?.symbol ?: "", currency?.symbolPosition ?: SymbolPosition.PREFIX, currency?.decimals ?: 2, sign)

        fun of(value: Double, symbol: String, position: SymbolPosition, decimals: Int, sign: Sign = Sign.NEGATIVE): AmountParts {
            val scale = decimals.coerceIn(0, 10)
            val signum = BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).signum()
            val body = AmountFormatter.number(kotlin.math.abs(value), scale)
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
