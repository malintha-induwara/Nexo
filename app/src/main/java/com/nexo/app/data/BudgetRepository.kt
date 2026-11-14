package com.nexo.app.data

import androidx.room.withTransaction
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.database.entity.BudgetAmountEntity
import com.nexo.app.core.database.entity.BudgetEntity
import com.nexo.app.domain.BudgetMath
import javax.inject.Inject
import javax.inject.Singleton

/** Budgets with effective-from monthly amounts. */
@Singleton
class BudgetRepository @Inject constructor(private val db: NexoDatabase) {
    val budgets = db.budgetDao().observeBudgets()
    val amounts = db.budgetDao().observeAmounts()

    private fun now() = System.currentTimeMillis()

    /** [currentKey] is the label key of the current month. */
    suspend fun add(categoryId: String?, amount: Double, currentKey: Int) = db.withTransaction {
        val id = BudgetEntity.idFor(categoryId)
        val existing = db.budgetDao().getBudget(id)
        when {
            existing == null -> {
                db.budgetDao().upsertBudgets(listOf(BudgetEntity(id, categoryId, db.budgetDao().maxOrder() + 1, false, now())))
                db.budgetDao().upsertAmounts(listOf(BudgetAmountEntity(BudgetAmountEntity.idFor(id, 0), id, 0, amount, now())))
            }
            !existing.isDeleted -> setMonth(id, currentKey, amount)
            else -> {
                db.budgetDao().upsertBudgets(listOf(existing.copy(isDeleted = false, updatedAt = now())))
                val records = db.budgetDao().amountsFor(id)
                if (records.isEmpty()) db.budgetDao().upsertAmounts(listOf(BudgetAmountEntity(BudgetAmountEntity.idFor(id, 0), id, 0, amount, now())))
                else db.budgetDao().upsertAmounts(records.map { it.copy(amount = amount, updatedAt = now()) })
            }
        }
    }

    suspend fun setMonth(budgetId: String, key: Int, amount: Double) {
        val records = db.budgetDao().amountsFor(budgetId)
        db.budgetDao().upsertAmounts(BudgetMath.perMonthEdit(budgetId, records, key, amount, now()))
    }

    suspend fun setDefault(budgetId: String, currentKey: Int, amount: Double) {
        val records = db.budgetDao().amountsFor(budgetId)
        if (records.isEmpty()) {
            db.budgetDao().upsertAmounts(listOf(BudgetAmountEntity(BudgetAmountEntity.idFor(budgetId, 0), budgetId, 0, amount, now())))
            return
        }
        db.budgetDao().upsertAmounts(BudgetMath.defaultEdit(budgetId, records, currentKey, amount, now()))
    }

    /** Soft delete; the amounts stay stored for a later revival. */
    suspend fun delete(budgetId: String) = db.withTransaction {
        val b = db.budgetDao().getBudget(budgetId) ?: return@withTransaction
        db.budgetDao().upsertBudgets(listOf(b.copy(isDeleted = true, updatedAt = now())))
    }

    /** Main-currency change with data retention multiplies every amount by R. */
    suspend fun rescale(factor: Double, decimals: Int) {
        val all = db.budgetDao().getAmounts()
        db.budgetDao().upsertAmounts(all.map { it.copy(amount = com.nexo.app.core.money.Money.round(it.amount * factor, decimals), updatedAt = now()) })
    }
}
