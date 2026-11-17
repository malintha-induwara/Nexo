package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexo.app.core.model.Budget

@Entity(tableName = "budgets")
data class BudgetEntity(
    // "total" or "cat_<categoryId>": one budget per target.
    @PrimaryKey val id: String,
    val categoryId: String?,
    val orderSeq: Int,
    val isDeleted: Boolean = false,
    val updatedAt: Long,
)

fun BudgetEntity.asExternalModel() = Budget(
    id = id,
    categoryId = categoryId,
    orderSeq = orderSeq,
    isDeleted = isDeleted,
    updatedAt = updatedAt,
)

fun Budget.asEntity() = BudgetEntity(
    id = id,
    categoryId = categoryId,
    orderSeq = orderSeq,
    isDeleted = isDeleted,
    updatedAt = updatedAt,
)
