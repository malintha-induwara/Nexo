package com.nexo.app.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

object Installments {
    /** Floor the per-row amount to the currency decimals; the first row carries the remainder. */
    fun split(total: Double, count: Int, decimals: Int): List<Double> {
        require(count > 0)
        val t = BigDecimal.valueOf(total).setScale(decimals, RoundingMode.HALF_UP)
        val base = t.divide(BigDecimal.valueOf(count.toLong()), decimals, RoundingMode.DOWN)
        val first = t - base * BigDecimal.valueOf((count - 1).toLong())
        return listOf(first.toDouble()) + List(count - 1) { base.toDouble() }
    }

    /** Row k falls k−1 months after [start] on the same day, clamped to short months. */
    fun dates(start: LocalDate, count: Int): List<LocalDate> = (0 until count).map { start.plusMonths(it.toLong()) }
}
