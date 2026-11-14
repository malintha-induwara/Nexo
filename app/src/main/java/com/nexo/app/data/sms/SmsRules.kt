package com.nexo.app.data.sms

import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.SmsKind
import com.nexo.app.data.database.entity.SmsItemEntity

/** What an item still lacks before it can be approved. Separate from [Certainty]. */
enum class Missing { ACCOUNT, DESTINATION, CATEGORY, AMOUNT }

/** Category kind a queue item is filed under. */
val SmsKind.categoryKind: CategoryKind get() = if (isInflow) CategoryKind.INCOME else CategoryKind.EXPENSE

fun SmsItemEntity.missing(): Set<Missing> = buildSet {
    if (accountId == null) add(Missing.ACCOUNT)
    when (kind) {
        SmsKind.TRANSFER_TO_CASH, SmsKind.CARD_PAYMENT, SmsKind.TRANSFER_IN -> if (toAccountId == null) add(Missing.DESTINATION)
        // To a payee outside Nexo: either an account (a transfer) or a category (an expense) will do.
        SmsKind.TRANSFER_OUT -> if (toAccountId == null && categoryId == null) add(Missing.DESTINATION)
        else -> if (categoryId == null) add(Missing.CATEGORY)
    }
    // "Rate pending" — no account-currency amount yet.
    if (certainty == Certainty.ESTIMATED && estimatedAmount == null && actualAmount == null) add(Missing.AMOUNT)
}

val SmsItemEntity.needsDetails: Boolean get() = missing().isNotEmpty()

/** "Needs attention": not exact, or incomplete. */
val SmsItemEntity.needsAttention: Boolean get() = certainty != Certainty.EXACT || needsDetails

/** The key learned categories are stored under: the merchant, or the payee of a transfer. */
val SmsItemEntity.learningKey: String
    get() = merchantKey.ifEmpty { payee?.let(com.nexo.app.data.sms.parser.MerchantCleaner::key).orEmpty() }

/** Where withdrawals go. The chosen account, else the first visible account in the Cash group. */
fun RefData.smsCashAccount(): String? {
    val chosen = settings.smsCashAccountId.takeIf { it.isNotEmpty() }?.let { accountById[it] }?.takeIf { isAccountVisible(it) }
    return chosen?.id ?: pickerAccounts.firstOrNull { groupById[it.groupId]?.builtinId == CASH_GROUP }?.id
}

private const val CASH_GROUP = 11
