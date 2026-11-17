package com.nexo.app.data.sms

import com.nexo.app.core.model.FxSnapshot
import com.nexo.app.core.model.RateChoice
import com.nexo.app.core.model.RateSide
import java.math.BigDecimal
import java.math.RoundingMode

object RateChooser {
    // [board] has one row per bank. Fallbacks: issuer's rate, bank median, then the official rate
    // plus half the typical spread (minus for money coming in).
    fun choose(board: List<FxSnapshot>, issuer: String?, side: RateSide): RateChoice? {
        fun value(r: FxSnapshot) = if (side == RateSide.SELL) r.sell else r.buy
        board.firstOrNull {
            it.bank == issuer
        }?.let { row -> value(row)?.let { return RateChoice(it, row.bank, row.capturedAt) } }
        val newest = board.maxOfOrNull { it.capturedAt }
        median(board.mapNotNull(::value))?.let { return RateChoice(it, RateChoice.MEDIAN, newest) }
        val official = median(board.mapNotNull { it.official }) ?: return null
        val halfSpread = (median(board.mapNotNull { it.spreadPct }) ?: 0.0) / 2 / 100
        val rate = if (side == RateSide.SELL) {
            official * (1 + halfSpread)
        } else {
            official * (1 - halfSpread)
        }
        return RateChoice(rate, RateChoice.OFFICIAL, newest)
    }

    fun estimate(original: Double, rate: Double, decimals: Int = 2): Double = BigDecimal.valueOf(
        original,
    ).multiply(BigDecimal.valueOf(rate)).setScale(decimals, RoundingMode.HALF_UP).toDouble()

    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
