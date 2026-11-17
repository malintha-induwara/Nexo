package com.nexo.app.core.model

data class BudgetAmount(
    // "<budgetId>_<periodKey>".
    val id: String,
    val budgetId: String,
    // 0 = from the beginning, else year * 100 + month of the labelled period.
    val periodKey: Int,
    val amount: Double,
    val updatedAt: Long,
) {
    companion object {
        fun idFor(budgetId: String, periodKey: Int) = "${budgetId}_$periodKey"
    }
}
