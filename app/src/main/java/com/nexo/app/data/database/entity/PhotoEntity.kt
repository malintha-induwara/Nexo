package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexo.app.core.model.Photo

@Entity(tableName = "photos", indices = [Index("transactionId"), Index("fileName")])
data class PhotoEntity(
    @PrimaryKey val id: String,
    val transactionId: String,
    val fileName: String,
    val fileSize: Long,
    val orderIndex: Int,
    val checksum: String,
    val createdAt: Long,
)

fun PhotoEntity.asExternalModel() = Photo(
    id = id,
    transactionId = transactionId,
    fileName = fileName,
    fileSize = fileSize,
    orderIndex = orderIndex,
    checksum = checksum,
    createdAt = createdAt,
)

fun Photo.asEntity() = PhotoEntity(
    id = id,
    transactionId = transactionId,
    fileName = fileName,
    fileSize = fileSize,
    orderIndex = orderIndex,
    checksum = checksum,
    createdAt = createdAt,
)
