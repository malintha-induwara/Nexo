package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.Transaction
import com.nexo.app.core.model.TxKind

@Entity(
    tableName = "transactions",
    indices = [
        Index("date"),
        Index("accountId"),
        Index("counterpartAccountId"),
        Index("categoryId"),
        Index("transferGroupId"),
        Index("feeGroupId"),
        Index("installmentGroupId"),
        Index("feeParentId"),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val kind: TxKind,
    // Null means the "None" pseudo-account.
    val accountId: String?,
    val counterpartAccountId: String? = null,
    // Null means the "None" pseudo-category (or no category for transfers and balance rows).
    val categoryId: String? = null,
    // Balance correction from account editing ("Modified Bal.").
    val isBalanceAdjustment: Boolean = false,
    val dateTime: Long,
    // Local ISO date (yyyy-MM-dd); always the local date of dateTime.
    val date: String,
    // In the transaction currency.
    val amount: Double,
    // In the main currency.
    val amountMain: Double,
    // In the account's currency.
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
    // Original message text for entries created from pasted or imported text.
    val sourceText: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    // Fee on a non-transfer row points at its parent; transfer fees use feeGroupId.
    val feeParentId: String? = null,
)

fun TransactionEntity.asExternalModel() = Transaction(
    id = id,
    kind = kind,
    accountId = accountId,
    counterpartAccountId = counterpartAccountId,
    categoryId = categoryId,
    isBalanceAdjustment = isBalanceAdjustment,
    dateTime = dateTime,
    date = date,
    amount = amount,
    amountMain = amountMain,
    amountAccount = amountAccount,
    currencyId = currencyId,
    note = note,
    description = description,
    transferGroupId = transferGroupId,
    feeGroupId = feeGroupId,
    installmentGroupId = installmentGroupId,
    installmentIndex = installmentIndex,
    installmentCount = installmentCount,
    repeatRuleId = repeatRuleId,
    repeatType = repeatType,
    sourceText = sourceText,
    createdAt = createdAt,
    updatedAt = updatedAt,
    feeParentId = feeParentId,
)

fun Transaction.asEntity() = TransactionEntity(
    id = id,
    kind = kind,
    accountId = accountId,
    counterpartAccountId = counterpartAccountId,
    categoryId = categoryId,
    isBalanceAdjustment = isBalanceAdjustment,
    dateTime = dateTime,
    date = date,
    amount = amount,
    amountMain = amountMain,
    amountAccount = amountAccount,
    currencyId = currencyId,
    note = note,
    description = description,
    transferGroupId = transferGroupId,
    feeGroupId = feeGroupId,
    installmentGroupId = installmentGroupId,
    installmentIndex = installmentIndex,
    installmentCount = installmentCount,
    repeatRuleId = repeatRuleId,
    repeatType = repeatType,
    sourceText = sourceText,
    createdAt = createdAt,
    updatedAt = updatedAt,
    feeParentId = feeParentId,
)
