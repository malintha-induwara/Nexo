package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "transaction_tags", primaryKeys = ["transactionId", "tagId"], indices = [Index("tagId")])
data class TransactionTagEntity(
    val transactionId: String,
    val tagId: String,
    val position: Int,
)
