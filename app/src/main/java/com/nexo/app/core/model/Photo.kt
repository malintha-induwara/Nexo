package com.nexo.app.core.model

data class Photo(
    val id: String,
    val transactionId: String,
    val fileName: String,
    val fileSize: Long,
    val orderIndex: Int,
    val checksum: String,
    val createdAt: Long,
)
