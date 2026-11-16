package com.nexo.app.data.sms

import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.SmsItem
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.categoryKind
import com.nexo.app.core.model.smsCashAccount
import com.nexo.app.data.database.dao.SmsDao
import com.nexo.app.data.repository.FxRatesRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import javax.inject.Inject

/**
 * Fills a new queue item's account, destination and category:
 * card digits first, then the bank's default account; categories only from what the user taught Nexo.
 */
class SmsSuggester @Inject constructor(
    private val dao: SmsDao,
    private val refs: ReferenceDataRepository,
    private val fx: FxRatesRepository,
) {

    suspend fun prepare(item: SmsItem, ref: RefData? = null): SmsItem {
        val r = ref ?: refs.getRefData()
        fun live(id: String?) =
            id?.takeIf { a -> r.accountById[a]?.let(r::isAccountVisible) == true }
        fun liveCategory(id: String?) = id?.takeIf { c ->
            r.categoryById[c]?.let {
                !it.isDeleted &&
                    it.kind == item.kind.categoryKind
            } ==
                true
        }

        val account = live(item.accountId)
            ?: live(item.cardLast4?.let { dao.source(item.bank, it)?.accountId })
            ?: live(dao.source(item.bank, "")?.accountId)
        val destination =
            live(item.toAccountId)
                ?: if (item.kind == SmsKind.TRANSFER_TO_CASH) r.smsCashAccount() else null
        val category = liveCategory(item.categoryId)
            ?: if (item.kind == SmsKind.TRANSFER_TO_CASH || item.learningKey.isEmpty()) {
                null
            } else {
                liveCategory(dao.merchantRule(item.learningKey)?.categoryId)
            }

        var out = item.copy(accountId = account, toAccountId = destination, categoryId = category)
        // A purchase in the account's own currency is not an estimate.
        if (out.certainty == Certainty.ESTIMATED && account != null &&
            r.accountCurrency(account).isoCode.equals(out.currency, ignoreCase = true)
        ) {
            out = out.copy(certainty = Certainty.EXACT, reason = null, feeRoute = null)
        }
        // Local snapshots only; the sync worker fetches and fills "Rate pending" items later.
        return fx.estimate(out, r)
    }
}
