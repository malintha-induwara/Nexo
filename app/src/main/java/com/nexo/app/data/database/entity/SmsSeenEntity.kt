package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Hash of each message capture already took in, as the inbox is read again for a day.
// The text itself is never kept.
@Entity(tableName = "sms_seen", indices = [Index("receivedAt")])
data class SmsSeenEntity(@PrimaryKey val id: String, val receivedAt: Long)
