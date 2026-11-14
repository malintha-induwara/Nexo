package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexo.app.core.model.SmsMerchantRule

/** Learned merchant → category. Keyed on the cleaned merchant key. */
@Entity(tableName = "sms_merchant_rules")
data class SmsMerchantRuleEntity(
    @PrimaryKey val merchantKey: String,
    val categoryId: String,
    val updatedAt: Long,
)

fun SmsMerchantRuleEntity.asExternalModel() = SmsMerchantRule(
    merchantKey = merchantKey,
    categoryId = categoryId,
    updatedAt = updatedAt,
)

fun SmsMerchantRule.asEntity() = SmsMerchantRuleEntity(
    merchantKey = merchantKey,
    categoryId = categoryId,
    updatedAt = updatedAt,
)
