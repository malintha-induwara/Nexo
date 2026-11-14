package com.nexo.app.sms.fx

import com.nexo.app.sms.RateSide
import com.nexo.app.sms.data.FxSnapshotEntity
import java.math.BigDecimal
import java.math.RoundingMode

/** A chosen rate: LKR per unit, where it came from (an API bank code, [MEDIAN], [OFFICIAL] or [MANUAL]), and when. */
data class RateChoice(val rate: Double, val source: String, val snapshotAt: Long?) {
    companion object {
        const val MEDIAN = "median"
        const val OFFICIAL = "official"
        const val MANUAL = "manual"
    }
}

/** Which rate converts a foreign-currency movement, and the estimate it gives. */
object RateChooser {
    /**
     * [board] is one row per bank for the currency. Fallbacks, in order: the issuing bank's own rate; the median across
     * banks; the CBSL official rate plus half a typical spread (minus, for money coming in); otherwise null.
     */
    fun choose(board: List<FxSnapshotEntity>, issuer: String?, side: RateSide): RateChoice? {
        fun value(r: FxSnapshotEntity) = if (side == RateSide.SELL) r.sell else r.buy
        board.firstOrNull { it.bank == issuer }?.let { row -> value(row)?.let { return RateChoice(it, row.bank, row.capturedAt) } }
        val newest = board.maxOfOrNull { it.capturedAt }
        median(board.mapNotNull(::value))?.let { return RateChoice(it, RateChoice.MEDIAN, newest) }
        val official = median(board.mapNotNull { it.official }) ?: return null
        val halfSpread = (median(board.mapNotNull { it.spreadPct }) ?: 0.0) / 2 / 100
        val rate = if (side == RateSide.SELL) official * (1 + halfSpread) else official * (1 - halfSpread)
        return RateChoice(rate, RateChoice.OFFICIAL, newest)
    }

    /** `round_half_up(original × rate, 2)` in decimal arithmetic. */
    fun estimate(original: Double, rate: Double, decimals: Int = 2): Double =
        BigDecimal.valueOf(original).multiply(BigDecimal.valueOf(rate)).setScale(decimals, RoundingMode.HALF_UP).toDouble()

    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
