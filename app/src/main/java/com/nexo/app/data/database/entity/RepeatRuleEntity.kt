package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexo.app.core.model.RepeatRule
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.TxKind

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

fun RepeatRuleEntity.asExternalModel() = RepeatRule(
    id = id,
    kind = kind,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    currencyId = currencyId,
    amount = amount,
    note = note,
    description = description,
    repeatType = repeatType,
    nextDate = nextDate,
    endDate = endDate,
    anchorDay = anchorDay,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun RepeatRule.asEntity() = RepeatRuleEntity(
    id = id,
    kind = kind,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    currencyId = currencyId,
    amount = amount,
    note = note,
    description = description,
    repeatType = repeatType,
    nextDate = nextDate,
    endDate = endDate,
    anchorDay = anchorDay,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
