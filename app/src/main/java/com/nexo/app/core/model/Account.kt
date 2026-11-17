package com.nexo.app.core.model

data class Account(
    val id: String,
    val name: String,
    val groupId: String,
    val currencyId: String,
    val orderSeq: Int,
    val description: String = "",
    val status: AccountStatus = AccountStatus.ACTIVE,
    val includeInTotals: Boolean = true,
    // Credit card: the paying account. Debit card: the account it draws on.
    val linkedAccountId: String? = null,
    val settlementDay: Int = 1,
    val paymentDay: Int = 1,
    val transferExpense: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
