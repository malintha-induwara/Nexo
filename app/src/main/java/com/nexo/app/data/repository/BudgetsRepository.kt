package com.nexo.app.data.repository

import com.nexo.app.data.database.entity.BudgetAmountEntity
import com.nexo.app.data.database.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

/** Budgets with effective-from monthly amounts. */
interface BudgetsRepository {
    fun getBudgetsStream(): Flow<List<BudgetEntity>>

    fun getBudgetAmountsStream(): Flow<List<BudgetAmountEntity>>

    /** [currentKey] is the label key of the current month. */
    suspend fun add(categoryId: String?, amount: Double, currentKey: Int)

    suspend fun setMonth(budgetId: String, key: Int, amount: Double)

    suspend fun setDefault(budgetId: String, currentKey: Int, amount: Double)

    /** Soft delete; the amounts stay stored for a later revival. */
    suspend fun delete(budgetId: String)
}
