package com.nexo.app.domain.sms

import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.Currency
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.SmsItem
import com.nexo.app.core.model.SmsKind
import javax.inject.Inject

/** The ledger currency and amount of a queue item: foreign estimates are booked in the account's currency. */
class GetSmsLedgerAmountUseCase @Inject constructor() {
    operator fun invoke(item: SmsItem, ref: RefData): Pair<Currency, Double> {
        val account = ref.accountCurrency(
            if (item.kind == SmsKind.TRANSFER_IN) {
                item.toAccountId
            } else {
                item.accountId
            },
        )
        if (item.certainty == Certainty.ESTIMATED || item.actualAmount != null) {
            return account to (item.actualAmount ?: item.estimatedAmount ?: item.amount)
        }
        if (account.isoCode.equals(item.currency, ignoreCase = true)) return account to item.amount
        val match = ref.currencies.firstOrNull {
            it.isoCode.equals(item.currency, ignoreCase = true)
        }
        return (match ?: account) to item.amount
    }
}
