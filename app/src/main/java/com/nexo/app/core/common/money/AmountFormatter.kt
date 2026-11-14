package com.nexo.app.core.common.money

import com.nexo.app.data.database.entity.CurrencyEntity
import com.nexo.app.core.model.SymbolPosition
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Formats amounts: half-up rounding, locale grouping, fixed decimals, symbol placement. */
object AmountFormatter {
    private val cache = HashMap<Pair<Locale, Int>, DecimalFormat>()

    private fun format(decimals: Int): DecimalFormat {
        val locale = Locale.getDefault()
        return synchronized(cache) {
            cache.getOrPut(locale to decimals) {
                val pattern = if (decimals > 0) "#,##0." + "0".repeat(decimals) else "#,##0"
                DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale)).apply {
                    roundingMode = RoundingMode.HALF_UP
                }
            }
        }
    }

    /** Number only, e.g. "1,234.50". Keeps a minus sign for values between -1 and 0. */
    fun number(value: Double, decimals: Int): String {
        val rounded = BigDecimal.valueOf(value).setScale(decimals.coerceIn(0, 10), RoundingMode.HALF_UP)
        val text = synchronized(cache) { format(decimals).format(rounded.abs()) }
        return if (rounded.signum() < 0) "-$text" else text
    }

    fun format(value: Double, symbol: String, position: SymbolPosition, decimals: Int): String {
        val negative = BigDecimal.valueOf(value).setScale(decimals.coerceIn(0, 10), RoundingMode.HALF_UP).signum() < 0
        val body = number(kotlin.math.abs(value), decimals)
        val text = if (position == SymbolPosition.PREFIX) "$symbol $body" else "$body$symbol"
        return if (negative) "-$text" else text
    }

    fun format(value: Double, currency: CurrencyEntity?): String =
        if (currency == null) number(value, 2) else format(value, currency.symbol, currency.symbolPosition, currency.decimals)

    /** Exchange-rate display: two decimals when ≥ 1, otherwise enough to show two significant digits. */
    fun rate(rate: Double): String {
        if (rate == 0.0) return "0"
        val abs = kotlin.math.abs(rate)
        val scale = if (abs >= 1) 2 else {
            var s = 2
            var v = abs
            while (v < 0.1 && s < 11) {
                v *= 10
                s++
            }
            s
        }
        return BigDecimal.valueOf(rate).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}
