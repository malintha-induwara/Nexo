package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "budget_amounts", indices = [Index("budgetId")])
data class BudgetAmountEntity(
    /** "<budgetId>_<periodKey>". */
    @PrimaryKey val id: String,
    val budgetId: String,
    /** 0 = from the beginning, otherwise year * 100 + month of the labelled month period. */
    val periodKey: Int,
    val amount: Double,
    val updatedAt: Long,
) {
    companion object {
        fun idFor(budgetId: String, periodKey: Int) = "${budgetId}_$periodKey"
    }
}
