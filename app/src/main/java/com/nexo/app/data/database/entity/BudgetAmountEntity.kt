package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexo.app.core.model.BudgetAmount

@Entity(tableName = "budget_amounts", indices = [Index("budgetId")])
data class BudgetAmountEntity(
    // "<budgetId>_<periodKey>"
    @PrimaryKey val id: String,
    val budgetId: String,
    // 0 = from the beginning, else year * 100 + month of the labelled period.
    val periodKey: Int,
    val amount: Double,
    val updatedAt: Long,
)

fun BudgetAmountEntity.asExternalModel() = BudgetAmount(
    id = id,
    budgetId = budgetId,
    periodKey = periodKey,
    amount = amount,
    updatedAt = updatedAt,
)

fun BudgetAmount.asEntity() = BudgetAmountEntity(
    id = id,
    budgetId = budgetId,
    periodKey = periodKey,
    amount = amount,
    updatedAt = updatedAt,
)
