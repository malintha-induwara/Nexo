package com.nexo.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.model.MemoColor
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.SymbolPosition
import com.nexo.app.core.model.TxKind

@Entity(tableName = "account_groups")
data class AccountGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: GroupType,
    /** 1..11 for the seeded groups (Cash = 11, Accounts = 1, Card = 2, Debit Card = 3, ...), null for user groups. */
    val builtinId: Int?,
    val orderSeq: Int,
    val status: GroupStatus = GroupStatus.ACTIVE,
    val updatedAt: Long,
)

@Entity(tableName = "accounts", indices = [Index("groupId")])
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val groupId: String,
    val currencyId: String,
    val orderSeq: Int,
    val description: String = "",
    val status: AccountStatus = AccountStatus.ACTIVE,
    val includeInTotals: Boolean = true,
    /** Credit card: paying account. Debit card: the account it draws on. */
    val linkedAccountId: String? = null,
    val settlementDay: Int = 1,
    val paymentDay: Int = 1,
    val transferExpense: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "categories", indices = [Index("parentId")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: CategoryKind,
    val parentId: String? = null,
    val orderSeq: Int,
    val isDeleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    /** Emoji chosen for the category; null falls back to a suggestion or the monogram (see CategoryVisuals). */
    val emoji: String? = null,
    /** Index into the ten category hues; null derives one from the parent or the id. */
    val colorIndex: Int? = null,
)

@Entity(tableName = "currencies")
data class CurrencyEntity(
    /** ISO code for built-in currencies, random UUID for custom ones. */
    @PrimaryKey val id: String,
    val isoCode: String?,
    val name: String,
    val symbol: String,
    val symbolPosition: SymbolPosition,
    val decimals: Int,
    /** Value of one unit in main-currency units. The main currency is always 1. */
    val rate: Double,
    val isMain: Boolean,
    val isVisible: Boolean,
    val orderSeq: Int,
    val updatedAt: Long,
) {
    val isCustom: Boolean get() = isoCode.isNullOrEmpty()
}

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
)

@Entity(tableName = "transaction_tags", primaryKeys = ["transactionId", "tagId"], indices = [Index("tagId")])
data class TransactionTagEntity(
    val transactionId: String,
    val tagId: String,
    val position: Int,
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val orderSeq: Int,
    val isDeleted: Boolean = false,
    val updatedAt: Long,
)

@Entity(tableName = "photos", indices = [Index("transactionId"), Index("fileName")])
data class PhotoEntity(
    @PrimaryKey val id: String,
    val transactionId: String,
    val fileName: String,
    val fileSize: Long,
    val orderIndex: Int,
    val checksum: String,
    val createdAt: Long,
)

@Entity(tableName = "repeat_rules")
data class RepeatRuleEntity(
    @PrimaryKey val id: String,
    /** INCOME, EXPENSE or TRANSFER_OUT. */
    val kind: TxKind,
    val accountId: String?,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val currencyId: String,
    val amount: Double,
    val note: String = "",
    val description: String = "",
    val repeatType: RepeatType,
    /** Next occurrence still to be generated, ISO date. */
    val nextDate: String,
    /** Last allowed occurrence, ISO date; null = no end. */
    val endDate: String? = null,
    /** Day of month the series was created on; monthly steps clamp to it instead of drifting. */
    val anchorDay: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
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

@Entity(tableName = "budgets")
data class BudgetEntity(
    /** "total" for the overall budget, otherwise "cat_<categoryId>". One budget per target. */
    @PrimaryKey val id: String,
    val categoryId: String?,
    val orderSeq: Int,
    val isDeleted: Boolean = false,
    val updatedAt: Long,
) {
    val isTotal: Boolean get() = categoryId == null

    companion object {
        const val TOTAL_ID = "total"
        fun idFor(categoryId: String?) = if (categoryId == null) TOTAL_ID else "cat_$categoryId"
    }
}

@Entity(tableName = "budget_amounts", indices = [Index("budgetId")])
data class BudgetAmountEntity(
    /** "<budgetId>_<periodKey>". */
    @PrimaryKey val id: String,
    val budgetId: String,
    /** 0 = from the beginning, otherwise year * 100 + month of the labelled month period. */
    val periodKey: Int,
    val amount: Double,
    val updatedAt: Long,
) {
    companion object {
        fun idFor(budgetId: String, periodKey: Int) = "${budgetId}_$periodKey"
    }
}

@Entity(tableName = "memos", indices = [Index("date")])
data class MemoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val date: String,
    val dateTime: Long,
    val pinned: Boolean = false,
    val pinnedAt: Long = 0,
    val orderSeq: Int,
    val color: MemoColor = MemoColor.WHITE,
    val updatedAt: Long,
)

/** Book settings: key/value rows that travel with backups. */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
