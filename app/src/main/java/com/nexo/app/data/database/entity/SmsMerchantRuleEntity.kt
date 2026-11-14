package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Learned merchant → category. Keyed on the cleaned merchant key. */
@Entity(tableName = "sms_merchant_rules")
data class SmsMerchantRuleEntity(
    @PrimaryKey val merchantKey: String,
    val categoryId: String,
    val updatedAt: Long,
)
