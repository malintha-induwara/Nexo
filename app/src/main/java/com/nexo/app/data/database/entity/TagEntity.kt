package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexo.app.core.model.Tag

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val orderSeq: Int,
    val isDeleted: Boolean = false,
    val updatedAt: Long,
)

fun TagEntity.asExternalModel() = Tag(
    id = id,
    name = name,
    orderSeq = orderSeq,
    isDeleted = isDeleted,
    updatedAt = updatedAt,
)

fun Tag.asEntity() = TagEntity(
    id = id,
    name = name,
    orderSeq = orderSeq,
    isDeleted = isDeleted,
    updatedAt = updatedAt,
)
