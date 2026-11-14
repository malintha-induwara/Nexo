package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexo.app.core.model.AccountStatus

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
