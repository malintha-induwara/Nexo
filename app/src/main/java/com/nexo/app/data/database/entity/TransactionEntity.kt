package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexo.app.core.model.RepeatType
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
    /** Null means the pseudo-account "None". */
    val accountId: String?,
    /** Other account of a transfer leg. */
    val counterpartAccountId: String? = null,
    /** Null means the pseudo-category "None" (or no category for transfers and balance rows). */
    val categoryId: String? = null,
    /** Balance correction recorded from account editing ("Modified Bal."). */
    val isBalanceAdjustment: Boolean = false,
    val dateTime: Long,
    /** Local calendar date, ISO yyyy-MM-dd. Always equals the local date of [dateTime]. */
    val date: String,
    /** Amount in the transaction currency. */
    val amount: Double,
    /** Amount in the main currency. */
    val amountMain: Double,
    /** Amount in the account's currency. */
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
    /** Original message text for entries created from pasted / imported text. */
    val sourceText: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * Bank fee charged on a non-transfer row: the fee expense points at its parent.
     * Transfer fees keep using [feeGroupId].
     */
    val feeParentId: String? = null,
)
