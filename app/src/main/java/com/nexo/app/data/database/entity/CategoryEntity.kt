package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexo.app.core.model.Category
import com.nexo.app.core.model.CategoryKind

@Entity(tableName = "categories", indices = [Index("parentId")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: CategoryKind,
    val parentId: String? = null,
    val orderSeq: Int,
    val isDeleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    // Null falls back to a suggested emoji or the monogram.
    val emoji: String? = null,
    // Index into the ten category hues; null derives one from the parent or the id.
    val colorIndex: Int? = null,
)

fun CategoryEntity.asExternalModel() = Category(
    id = id,
    name = name,
    kind = kind,
    parentId = parentId,
    orderSeq = orderSeq,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
    emoji = emoji,
    colorIndex = colorIndex,
)

fun Category.asEntity() = CategoryEntity(
    id = id,
    name = name,
    kind = kind,
    parentId = parentId,
    orderSeq = orderSeq,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
    emoji = emoji,
    colorIndex = colorIndex,
)
