package com.nexo.app.core.model

/** Which account a bank card (or the bank as a whole, when [last4] is empty) belongs to. */
data class SmsSource(
    val bank: String,
    /** Card or account digits; "" is the bank's default account. */
    val last4: String,
    val accountId: String,
    val updatedAt: Long,
)
