package com.nexo.app.data.sms

import com.nexo.app.core.model.SmsItem
import com.nexo.app.data.sms.parser.MerchantCleaner

/** The key learned categories are stored under: the merchant, or the payee of a transfer. */
val SmsItem.learningKey: String
    get() = merchantKey.ifEmpty { payee?.let(MerchantCleaner::key).orEmpty() }
