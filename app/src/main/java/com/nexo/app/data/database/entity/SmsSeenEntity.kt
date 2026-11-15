package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A message capture has already taken in, as a hash of it: the inbox is read again for a day, and the text itself
 * is never kept here.
 */
@Entity(tableName = "sms_seen", indices = [Index("receivedAt")])
data class SmsSeenEntity(@PrimaryKey val id: String, val receivedAt: Long)
