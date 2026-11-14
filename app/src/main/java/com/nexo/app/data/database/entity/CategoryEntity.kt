package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
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
    /** Emoji chosen for the category; null falls back to a suggestion or the monogram (see CategoryVisuals). */
    val emoji: String? = null,
    /** Index into the ten category hues; null derives one from the parent or the id. */
    val colorIndex: Int? = null,
)
