package com.nexo.app.domain

import com.nexo.app.core.model.Budget
import com.nexo.app.core.model.BudgetAmount
import com.nexo.app.core.model.BudgetMath
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.Transaction
import com.nexo.app.core.model.TxKind
import java.math.BigDecimal

data class BudgetRow(
    /** Real budget id; null for a virtual parent or the computed total. */
    val budgetId: String?,
    val categoryId: String?,
    val name: String,
    val amount: Double,
    val used: Double,
    val children: List<BudgetRow> = emptyList(),
    val isTotal: Boolean = false,
) {
    val remaining: Double get() = amount - used
    val rate: Int? get() = BudgetMath.rate(used, amount)
    val fill: Float get() = if (amount <= 0) (if (used > 0) 1f else 0f) else (used / amount).toFloat().coerceIn(0f, 1f)
}

data class BudgetBlockData(val total: BudgetRow?, val rows: List<BudgetRow>)

/** Budget rows for one month period. */
object BudgetCalc {
    fun usedByCategory(ref: RefData, rows: List<Transaction>): Pair<Map<String?, Double>, Map<String, Double>> {
        val main = HashMap<String?, BigDecimal>()
        val sub = HashMap<String, BigDecimal>()
        rows.filter { it.kind == TxKind.EXPENSE }.forEach { t ->
            val m = if (t.isBalanceAdjustment) "__modified__" else ref.mainCategoryId(t.categoryId)
            main[m] = (main[m] ?: BigDecimal.ZERO) + BigDecimal.valueOf(t.amountMain)
            ref.subCategoryId(t.categoryId)?.let { s -> sub[s] = (sub[s] ?: BigDecimal.ZERO) + BigDecimal.valueOf(t.amountMain) }
        }
        return main.mapValues { it.value.toDouble() } to sub.mapValues { it.value.toDouble() }
    }

    /**
     * [forSettings] keeps zero-amount rows (Budget Settings lists them) and skips the computed total.
     */
    fun compute(
        ref: RefData,
        budgets: List<Budget>,
        amounts: List<BudgetAmount>,
        expenseRows: List<Transaction>,
        key: Int,
        forSettings: Boolean = false,
    ): BudgetBlockData {
        val (usedMain, usedSub) = usedByCategory(ref, expenseRows)
        val byBudget = amounts.groupBy { it.budgetId }
        fun amountOf(b: Budget) = BudgetMath.effective(byBudget[b.id].orEmpty(), key)
        val live = budgets.filter { !it.isDeleted }
        val catBudgets = live.filter { b ->
            val c = b.categoryId?.let { ref.categoryById[it] } ?: return@filter false
            !c.isDeleted && (c.parentId == null || ref.categoryById[c.parentId]?.isDeleted == false)
        }
        val mainBudgets = catBudgets.filter { ref.categoryById[it.categoryId]?.parentId == null }.associateBy { it.categoryId!! }
        val subBudgets = catBudgets.filter { ref.categoryById[it.categoryId]?.parentId != null }.groupBy { ref.categoryById[it.categoryId]!!.parentId!! }
        val subsOn = ref.settings.subcategories
        val mainIds = (mainBudgets.keys + if (subsOn) subBudgets.keys else emptySet())
        val ordered = ref.categories.filter { it.id in mainIds }.sortedWith(compareBy({ it.orderSeq }, { it.createdAt }))
        val rows = ordered.mapNotNull { cat ->
            val b = mainBudgets[cat.id]
            val subs = subBudgets[cat.id].orEmpty()
            val subSum = subs.sumOf { amountOf(it) }
            val own = b?.let { amountOf(it) } ?: 0.0
            val amount = if (own == 0.0) subSum else own
            val children = if (subsOn) subs.sortedBy { ref.categoryById[it.categoryId]?.orderSeq ?: 0 }.mapNotNull { sb ->
                val a = amountOf(sb)
                if (a == 0.0 && !forSettings) null
                else BudgetRow(sb.id, sb.categoryId, ref.categoryName(sb.categoryId) ?: "", a, usedSub[sb.categoryId] ?: 0.0)
            } else emptyList()
            if (amount == 0.0 && !forSettings) return@mapNotNull null
            BudgetRow(b?.id, cat.id, cat.name, amount, usedMain[cat.id] ?: 0.0, children)
        }
        val total = live.firstOrNull { it.isTotal }
        val totalAmount = total?.let { amountOf(it) } ?: 0.0
        val totalRow = when {
            total != null && (totalAmount != 0.0 || forSettings) ->
                BudgetRow(total.id, null, "", totalAmount, usedMain.values.sum(), isTotal = true)
            forSettings -> null
            rows.isEmpty() -> null
            else -> BudgetRow(null, null, "", rows.sumOf { it.amount }, rows.sumOf { it.used }, isTotal = true)
        }
        return BudgetBlockData(totalRow, rows)
    }
}
