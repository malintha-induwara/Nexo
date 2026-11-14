package com.nexo.app.sms.capture

import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.data.RefRepository
import com.nexo.app.domain.RefData
import com.nexo.app.sms.Certainty
import com.nexo.app.sms.SmsKind
import com.nexo.app.sms.categoryKind
import com.nexo.app.sms.data.SmsItemEntity
import com.nexo.app.sms.learningKey
import com.nexo.app.sms.smsCashAccount
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fills a new queue item's account, destination and category (LKSMS-011/012):
 * card digits first, then the bank's default account; categories only from what the user taught Nexo.
 */
@Singleton
class SmsSuggester @Inject constructor(
    private val db: NexoDatabase,
    private val refs: RefRepository,
    private val fx: com.nexo.app.sms.fx.FxRepository,
) {

    suspend fun prepare(item: SmsItemEntity, ref: RefData? = null): SmsItemEntity {
        val r = ref ?: refs.snapshot()
        val dao = db.smsDao()
        fun live(id: String?) = id?.takeIf { a -> r.accountById[a]?.let(r::isAccountVisible) == true }
        fun liveCategory(id: String?) = id?.takeIf { c -> r.categoryById[c]?.let { !it.isDeleted && it.kind == item.kind.categoryKind } == true }

        val account = live(item.accountId)
            ?: live(item.cardLast4?.let { dao.source(item.bank, it)?.accountId })
            ?: live(dao.source(item.bank, "")?.accountId)
        val destination = live(item.toAccountId) ?: if (item.kind == SmsKind.TRANSFER_TO_CASH) r.smsCashAccount() else null
        val category = liveCategory(item.categoryId)
            ?: if (item.kind == SmsKind.TRANSFER_TO_CASH || item.learningKey.isEmpty()) null
            else liveCategory(dao.merchantRule(item.learningKey)?.categoryId)

        var out = item.copy(accountId = account, toAccountId = destination, categoryId = category)
        // A purchase in the account's own currency is not an estimate (NQ-020 `fx_card_purchase`: card currency ≠ wallet currency).
        if (out.certainty == Certainty.ESTIMATED && account != null && r.accountCurrency(account).isoCode.equals(out.currency, ignoreCase = true)) {
            out = out.copy(certainty = Certainty.EXACT, reason = null, feeRoute = null)
        }
        // NQ-011 from local snapshots only; the sync worker fetches and fills "Rate pending" items later.
        return fx.estimate(out, r)
    }
}
