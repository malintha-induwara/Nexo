package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexo.app.core.model.MemoColor

@Entity(tableName = "memos", indices = [Index("date")])
data class MemoEntity(
    @PrimaryKey val id: String,
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
