package com.nexo.app.core.model

data class SmsSource(
    val bank: String,
    // Card or account digits; "" is the bank's default account.
    val last4: String,
    val accountId: String,
    val updatedAt: Long,
)
