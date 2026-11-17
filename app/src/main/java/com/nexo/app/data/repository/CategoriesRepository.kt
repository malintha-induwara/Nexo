package com.nexo.app.data.repository

import com.nexo.app.core.model.CategoryKind

// Income and expense categories, one level of subcategories deep.
interface CategoriesRepository {
    // Returns the new category's id.
    suspend fun add(
        kind: CategoryKind,
        name: String,
        parentId: String?,
        emoji: String? = null,
        colorIndex: Int? = null,
    ): String

    // Null [emoji] or [colorIndex] means "derive automatically".
    suspend fun update(
        id: String,
        name: String,
        parentId: String?,
        emoji: String?,
        colorIndex: Int?,
    )

    // Soft delete; a main category takes its subcategories with it.
    suspend fun delete(id: String)

    suspend fun reorder(ids: List<String>)

    suspend fun toMain(id: String)

    // The source and all its subcategories move under [targetId].
    suspend fun toSub(sourceId: String, targetId: String)
}
