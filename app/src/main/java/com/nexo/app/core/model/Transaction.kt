package com.nexo.app.core.model

data class Transaction(
    val id: String,
    val kind: TxKind,
    // Null means the pseudo-account "None".
    val accountId: String?,
    val counterpartAccountId: String? = null,
    // Null means "None", or no category for transfers and balance rows.
    val categoryId: String? = null,
    // Recorded from account editing ("Modified Bal.").
    val isBalanceAdjustment: Boolean = false,
    val dateTime: Long,
    // Local ISO date (yyyy-MM-dd) of [dateTime].
    val date: String,
    // In the transaction currency.
    val amount: Double,
    val amountMain: Double,
    val amountAccount: Double,
    val currencyId: String,
    val note: String = "",
    val description: String = "",
    val transferGroupId: String? = null,
    val feeGroupId: String? = null,
    val installmentGroupId: String? = null,
    val installmentIndex: Int = 0,
    val installmentCount: Int = 0,
    val repeatRuleId: String? = null,
    val repeatType: RepeatType? = null,
    // For entries made from pasted or imported text.
    val sourceText: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    // The fee expense of a non-transfer row points at it; transfer fees use [feeGroupId].
    val feeParentId: String? = null,
)
