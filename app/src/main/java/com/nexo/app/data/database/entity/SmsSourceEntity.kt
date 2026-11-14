package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index

/** Which account a bank card (or the bank as a whole, when [last4] is empty) belongs to. */
@Entity(tableName = "sms_sources", primaryKeys = ["bank", "last4"], indices = [Index("accountId")])
data class SmsSourceEntity(
    val bank: String,
    /** Card or account digits; "" is the bank's default account. */
    val last4: String,
    val accountId: String,
    val updatedAt: Long,
)
