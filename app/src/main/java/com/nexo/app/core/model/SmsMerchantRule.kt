package com.nexo.app.core.model

/** Learned merchant → category. Keyed on the cleaned merchant key. */
data class SmsMerchantRule(val merchantKey: String, val categoryId: String, val updatedAt: Long)
