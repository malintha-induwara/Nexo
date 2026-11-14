package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    /** "total" for the overall budget, otherwise "cat_<categoryId>". One budget per target. */
    @PrimaryKey val id: String,
    val categoryId: String?,
    val orderSeq: Int,
    val isDeleted: Boolean = false,
    val updatedAt: Long,
) {
    val isTotal: Boolean get() = categoryId == null

    companion object {
        const val TOTAL_ID = "total"
        fun idFor(categoryId: String?) = if (categoryId == null) TOTAL_ID else "cat_$categoryId"
    }
}
