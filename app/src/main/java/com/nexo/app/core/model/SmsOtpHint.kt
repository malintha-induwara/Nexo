package com.nexo.app.core.model

/** The merchant name from an OTP message, kept for 15 minutes. The code itself is never stored. */
data class SmsOtpHint(
    val id: String,
    val bank: String,
    val merchant: String,
    val amount: Double,
    val currency: String,
    val at: Long,
)
