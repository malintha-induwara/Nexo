package com.nexo.app.core.model

data class Category(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val parentId: String? = null,
    val orderSeq: Int,
    val isDeleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    // Null falls back to a suggested emoji or the monogram.
    val emoji: String? = null,
    // One of the ten hues; null derives one from the parent or the id.
    val colorIndex: Int? = null,
)
