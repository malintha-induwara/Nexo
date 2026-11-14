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
    /** Emoji chosen for the category; null falls back to a suggestion or the monogram (see CategoryVisuals). */
    val emoji: String? = null,
    /** Index into the ten category hues; null derives one from the parent or the id. */
    val colorIndex: Int? = null,
)
