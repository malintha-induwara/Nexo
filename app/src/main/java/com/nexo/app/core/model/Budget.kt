package com.nexo.app.core.model

data class Budget(
    /** "total" for the overall budget, otherwise "cat_<categoryId>". One budget per target. */
    val id: String,
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
