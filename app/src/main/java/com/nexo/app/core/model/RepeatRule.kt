package com.nexo.app.core.model

data class RepeatRule(
    val id: String,
    // INCOME, EXPENSE or TRANSFER_OUT.
    val kind: TxKind,
    val accountId: String?,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val currencyId: String,
    val amount: Double,
    val note: String = "",
    val description: String = "",
    val repeatType: RepeatType,
    // Next occurrence still to be generated, ISO date.
    val nextDate: String,
    // ISO date; null = no end.
    val endDate: String? = null,
    // Monthly steps clamp to this day instead of drifting.
    val anchorDay: Int,
    val createdAt: Long,
    val updatedAt: Long,
)
