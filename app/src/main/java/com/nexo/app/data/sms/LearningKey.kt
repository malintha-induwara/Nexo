package com.nexo.app.data.sms

import com.nexo.app.core.model.SmsItem
import com.nexo.app.data.sms.parser.MerchantCleaner

// Learned categories are keyed by merchant, or by payee for transfers.
val SmsItem.learningKey: String
    get() = merchantKey.ifEmpty { payee?.let(MerchantCleaner::key).orEmpty() }
