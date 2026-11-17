package com.nexo.app.core.model

// What an item still lacks before approval; separate from Certainty.
enum class Missing { ACCOUNT, DESTINATION, CATEGORY, AMOUNT }

val SmsKind.categoryKind: CategoryKind
    get() = if (isInflow) CategoryKind.INCOME else CategoryKind.EXPENSE

fun SmsItem.missing(): Set<Missing> = buildSet {
    if (accountId == null) add(Missing.ACCOUNT)
    when (kind) {
        SmsKind.TRANSFER_TO_CASH, SmsKind.CARD_PAYMENT, SmsKind.TRANSFER_IN -> if (toAccountId ==
            null
        ) {
            add(Missing.DESTINATION)
        }

        // A payee outside Nexo: an account (transfer) or a category (expense) will do.
        SmsKind.TRANSFER_OUT -> if (toAccountId == null && categoryId == null) {
            add(Missing.DESTINATION)
        }

        else -> if (categoryId == null) add(Missing.CATEGORY)
    }
    // "Rate pending": no account-currency amount yet.
    if (certainty == Certainty.ESTIMATED && estimatedAmount == null && actualAmount == null) {
        add(Missing.AMOUNT)
    }
}

val SmsItem.needsDetails: Boolean get() = missing().isNotEmpty()

val SmsItem.needsAttention: Boolean get() = certainty != Certainty.EXACT || needsDetails

fun RefData.smsCashAccount(): String? {
    val chosen = settings.smsCashAccountId.takeIf {
        it.isNotEmpty()
    }?.let { accountById[it] }?.takeIf { isAccountVisible(it) }
    return chosen?.id
        ?: pickerAccounts.firstOrNull { groupById[it.groupId]?.builtinId == CASH_GROUP }?.id
}

private const val CASH_GROUP = 11

// In the account's currency for foreign items.
fun ledgerAmount(item: SmsItem): Double = item.actualAmount ?: item.estimatedAmount ?: item.amount

fun bankName(item: SmsItem): String = Banks.byName(item.bank)?.displayName ?: Bank.OTHER.displayName

// Used as the row title and the transaction note.
fun noteFor(item: SmsItem): String = item.merchant.ifEmpty { item.payee.orEmpty() }
    .ifEmpty { item.route?.let { "${bankName(item)} transfer $it" }.orEmpty() }
    .ifEmpty { bankName(item) }
