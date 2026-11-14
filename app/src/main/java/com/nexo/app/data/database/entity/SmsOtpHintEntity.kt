package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** The merchant name from an OTP message, kept for 15 minutes. The code itself is never stored. */
@Entity(tableName = "sms_otp_hints", indices = [Index("at")])
data class SmsOtpHintEntity(
    @PrimaryKey val id: String,
    val bank: String,
    val merchant: String,
    val amount: Double,
    val currency: String,
    val at: Long,
)
