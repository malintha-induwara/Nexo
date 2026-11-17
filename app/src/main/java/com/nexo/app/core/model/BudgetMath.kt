package com.nexo.app.core.model

import java.time.YearMonth

object BudgetMath {
    fun key(month: YearMonth): Int = month.year * 100 + month.monthValue

    fun nextKey(key: Int): Int {
        val y = key / 100
        val m = key % 100
        return if (m >= 12) (y + 1) * 100 + 1 else y * 100 + m + 1
    }

    // The record with the greatest key ≤ [key]; 0 if none.
    fun effective(records: List<BudgetAmount>, key: Int): Double =
        records.filter { it.periodKey <= key }.maxByOrNull { it.periodKey }?.amount ?: 0.0

    // "Default Budget" is the record with the greatest key.
    fun latest(records: List<BudgetAmount>): Double =
        records.maxByOrNull { it.periodKey }?.amount ?: 0.0

    // Changes only month [key]; returns the records to upsert.
    fun perMonthEdit(
        budgetId: String,
        records: List<BudgetAmount>,
        key: Int,
        amount: Double,
        now: Long,
    ): List<BudgetAmount> {
        val out = ArrayList<BudgetAmount>()
        val next = nextKey(key)
        if (records.none { it.periodKey == next }) {
            out +=
                BudgetAmount(
                    BudgetAmount.idFor(budgetId, next),
                    budgetId,
                    next,
                    effective(records, next),
                    now,
                )
        }
        out += BudgetAmount(BudgetAmount.idFor(budgetId, key), budgetId, key, amount, now)
        return out
    }

    // From next month on, months that followed the old default get [amount].
    fun defaultEdit(
        budgetId: String,
        records: List<BudgetAmount>,
        currentKey: Int,
        amount: Double,
        now: Long,
    ): List<BudgetAmount> {
        val next = nextKey(currentKey)
        val working = records.toMutableList()
        val out = ArrayList<BudgetAmount>()
        if (working.none { it.periodKey == next }) {
            val pinned =
                BudgetAmount(
                    BudgetAmount.idFor(budgetId, next),
                    budgetId,
                    next,
                    effective(records, next),
                    now,
                )
            working += pinned
        }
        val latest = latest(working)
        for (r in working) {
            if (r.periodKey > currentKey && r.amount == latest) {
                out += r.copy(amount = amount, updatedAt = now)
            } else if (records.none { it.id == r.id }) {
                out += r
            }
        }
        return out
    }

    // Truncated; null shows as "-".
    fun rate(used: Double, budget: Double): Int? {
        if (budget == 0.0) return if (used > 0) 100 else 0
        val r = used / budget * 100
        if (r.isInfinite() || r.isNaN()) return null
        val t = r.toLong()
        return if (t >= 10_000) null else t.toInt()
    }
}
