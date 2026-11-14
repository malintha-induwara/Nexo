package com.nexo.app.core.model

import com.nexo.app.data.database.entity.BudgetAmountEntity
import java.time.YearMonth

object BudgetMath {
    fun key(month: YearMonth): Int = month.year * 100 + month.monthValue

    fun nextKey(key: Int): Int {
        val y = key / 100
        val m = key % 100
        return if (m >= 12) (y + 1) * 100 + 1 else y * 100 + m + 1
    }

    /** Amount of the record with the greatest key ≤ [key]; 0 if none. */
    fun effective(records: List<BudgetAmountEntity>, key: Int): Double =
        records.filter { it.periodKey <= key }.maxByOrNull { it.periodKey }?.amount ?: 0.0

    /** "Default Budget": the record with the greatest key. */
    fun latest(records: List<BudgetAmountEntity>): Double = records.maxByOrNull { it.periodKey }?.amount ?: 0.0

    /** Change only month [key]. Returns the records to upsert. */
    fun perMonthEdit(budgetId: String, records: List<BudgetAmountEntity>, key: Int, amount: Double, now: Long): List<BudgetAmountEntity> {
        val out = ArrayList<BudgetAmountEntity>()
        val next = nextKey(key)
        if (records.none { it.periodKey == next }) {
            out += BudgetAmountEntity(BudgetAmountEntity.idFor(budgetId, next), budgetId, next, effective(records, next), now)
        }
        out += BudgetAmountEntity(BudgetAmountEntity.idFor(budgetId, key), budgetId, key, amount, now)
        return out
    }

    /** From next month on, months that followed the old default get [amount]. */
    fun defaultEdit(budgetId: String, records: List<BudgetAmountEntity>, currentKey: Int, amount: Double, now: Long): List<BudgetAmountEntity> {
        val next = nextKey(currentKey)
        val working = records.toMutableList()
        val out = ArrayList<BudgetAmountEntity>()
        if (working.none { it.periodKey == next }) {
            val pinned = BudgetAmountEntity(BudgetAmountEntity.idFor(budgetId, next), budgetId, next, effective(records, next), now)
            working += pinned
        }
        val latest = latest(working)
        for (r in working) {
            if (r.periodKey > currentKey && r.amount == latest) out += r.copy(amount = amount, updatedAt = now)
            else if (records.none { it.id == r.id }) out += r
        }
        return out
    }

    /** Truncated percentage; null means "-". */
    fun rate(used: Double, budget: Double): Int? {
        if (budget == 0.0) return if (used > 0) 100 else 0
        val r = used / budget * 100
        if (r.isInfinite() || r.isNaN()) return null
        val t = r.toLong()
        return if (t >= 10_000) null else t.toInt()
    }
}
