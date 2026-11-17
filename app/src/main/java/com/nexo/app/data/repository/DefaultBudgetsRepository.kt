package com.nexo.app.data.repository

import androidx.room.withTransaction
import com.nexo.app.core.model.Budget
import com.nexo.app.core.model.BudgetAmount
import com.nexo.app.core.model.BudgetMath
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.entity.BudgetAmountEntity
import com.nexo.app.data.database.entity.BudgetEntity
import com.nexo.app.data.database.entity.asEntity
import com.nexo.app.data.database.entity.asExternalModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultBudgetsRepository @Inject constructor(
    private val db: NexoDatabase,
    private val clock: Clock,
) : BudgetsRepository {
    override fun getBudgetsStream(): Flow<List<Budget>> =
        db.budgetDao().observeBudgets().map { rows ->
            rows.map(BudgetEntity::asExternalModel)
        }
    override fun getBudgetAmountsStream(): Flow<List<BudgetAmount>> =
        db.budgetDao().observeAmounts().map { rows ->
            rows.map(BudgetAmountEntity::asExternalModel)
        }

    private fun now() = clock.millis()

    override suspend fun add(categoryId: String?, amount: Double, currentKey: Int) =
        db.withTransaction {
            val id = Budget.idFor(categoryId)
            val existing = db.budgetDao().getBudget(id)
            when {
                existing == null -> {
                    db.budgetDao().upsertBudgets(
                        listOf(
                            BudgetEntity(
                                id,
                                categoryId,
                                db.budgetDao().maxOrder() + 1,
                                false,
                                now(),
                            ),
                        ),
                    )
                    db.budgetDao().upsertAmounts(
                        listOf(BudgetAmountEntity(BudgetAmount.idFor(id, 0), id, 0, amount, now())),
                    )
                }

                !existing.isDeleted -> setMonth(id, currentKey, amount)

                else -> {
                    db.budgetDao().upsertBudgets(
                        listOf(existing.copy(isDeleted = false, updatedAt = now())),
                    )
                    val records = db.budgetDao().amountsFor(id)
                    if (records.isEmpty()) {
                        db.budgetDao().upsertAmounts(
                            listOf(
                                BudgetAmountEntity(BudgetAmount.idFor(id, 0), id, 0, amount, now()),
                            ),
                        )
                    } else {
                        db.budgetDao().upsertAmounts(
                            records.map {
                                it.copy(amount = amount, updatedAt = now())
                            },
                        )
                    }
                }
            }
        }

    override suspend fun setMonth(budgetId: String, key: Int, amount: Double) {
        val records = db.budgetDao().amountsFor(budgetId)
        db.budgetDao().upsertAmounts(
            BudgetMath.perMonthEdit(
                budgetId,
                records.map(BudgetAmountEntity::asExternalModel),
                key,
                amount,
                now(),
            ).map(BudgetAmount::asEntity),
        )
    }

    override suspend fun setDefault(budgetId: String, currentKey: Int, amount: Double) {
        val records = db.budgetDao().amountsFor(budgetId)
        if (records.isEmpty()) {
            db.budgetDao().upsertAmounts(
                listOf(
                    BudgetAmountEntity(BudgetAmount.idFor(budgetId, 0), budgetId, 0, amount, now()),
                ),
            )
            return
        }
        db.budgetDao().upsertAmounts(
            BudgetMath.defaultEdit(
                budgetId,
                records.map(BudgetAmountEntity::asExternalModel),
                currentKey,
                amount,
                now(),
            ).map(BudgetAmount::asEntity),
        )
    }

    override suspend fun delete(budgetId: String) = db.withTransaction {
        val b = db.budgetDao().getBudget(budgetId) ?: return@withTransaction
        db.budgetDao().upsertBudgets(listOf(b.copy(isDeleted = true, updatedAt = now())))
    }
}
