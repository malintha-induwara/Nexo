package com.nexo.app.data.repository

import com.nexo.app.core.model.Budget
import com.nexo.app.core.model.BudgetAmount
import kotlinx.coroutines.flow.Flow

// Budgets with effective-from monthly amounts.
interface BudgetsRepository {
    fun getBudgetsStream(): Flow<List<Budget>>

    fun getBudgetAmountsStream(): Flow<List<BudgetAmount>>

    // [currentKey] is the label key of the current month.
    suspend fun add(categoryId: String?, amount: Double, currentKey: Int)

    suspend fun setMonth(budgetId: String, key: Int, amount: Double)

    suspend fun setDefault(budgetId: String, currentKey: Int, amount: Double)

    // Soft delete; the amounts stay for a later revival.
    suspend fun delete(budgetId: String)
}
