package com.nexo.app.core.model

data class Tag(
    val id: String,
    val name: String,
    val orderSeq: Int,
    val isDeleted: Boolean = false,
    val updatedAt: Long,
)
