package com.nexo.app.core.common.money

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

object Money {
    /** Half-up rounding to [decimals] digits, done in decimal arithmetic so binary noise never flips a digit. */
    fun round(value: Double, decimals: Int): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return BigDecimal.valueOf(value).setScale(decimals.coerceIn(0, 10), RoundingMode.HALF_UP).toDouble()
    }

    fun isZero(value: Double, decimals: Int = 8): Boolean = abs(round(value, decimals)) == 0.0

    fun sum(values: Iterable<Double>): Double =
        values.fold(BigDecimal.ZERO) { acc, v -> acc + BigDecimal.valueOf(v) }.toDouble()

    fun add(a: Double, b: Double): Double = (BigDecimal.valueOf(a) + BigDecimal.valueOf(b)).toDouble()

    fun subtract(a: Double, b: Double): Double = (BigDecimal.valueOf(a) - BigDecimal.valueOf(b)).toDouble()

    fun multiply(a: Double, b: Double): Double = (BigDecimal.valueOf(a) * BigDecimal.valueOf(b)).toDouble()

    fun divide(a: Double, b: Double, scale: Int = 12): Double =
        if (b == 0.0) 0.0 else BigDecimal.valueOf(a).divide(BigDecimal.valueOf(b), scale, RoundingMode.HALF_UP).toDouble()
}
