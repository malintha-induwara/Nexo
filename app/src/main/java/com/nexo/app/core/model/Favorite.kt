package com.nexo.app.core.model

data class Favorite(
    val id: String,
    val kind: TxKind,
    val accountId: String?,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val currencyId: String,
    val amount: Double,
    val note: String = "",
    val description: String = "",
    val orderSeq: Int,
    val updatedAt: Long,
)
