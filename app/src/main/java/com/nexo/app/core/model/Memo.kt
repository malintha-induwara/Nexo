package com.nexo.app.core.model

data class Memo(
    val id: String,
    val title: String,
    val content: String,
    val date: String,
    val dateTime: Long,
    val pinned: Boolean = false,
    val pinnedAt: Long = 0,
    val orderSeq: Int,
    val color: MemoColor = MemoColor.WHITE,
    val updatedAt: Long,
)
