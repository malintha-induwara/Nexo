package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val orderSeq: Int,
    val isDeleted: Boolean = false,
    val updatedAt: Long,
)
