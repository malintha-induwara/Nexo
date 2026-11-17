package com.nexo.app.data.sms

import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.SmsItem
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.SmsMessage
import com.nexo.app.core.model.SmsOtpHint
import com.nexo.app.core.model.SmsStatus
import com.nexo.app.data.sms.parser.DedupeKeys
import com.nexo.app.data.sms.parser.IgnoreReason
import com.nexo.app.data.sms.parser.MerchantCleaner
import com.nexo.app.data.sms.parser.ParsedSms
import java.util.UUID

// Room backs live capture; [MemorySmsStore] backs the history picker and tests.
interface SmsStore {
    suspend fun byDedupeKey(key: String): SmsItem?

    // Pending approval-code item with the same route and amount.
    suspend fun pendingRetry(
        bank: String,
        route: String,
        amount: Double,
        currency: String,
        since: Long,
    ): SmsItem?

    // Pending unverified transfer-out with the same amount.
    suspend fun upgradeCandidate(
        bank: String,
        amount: Double,
        currency: String,
        since: Long,
    ): SmsItem?

    // Bank-confirmed transfer-out with the same amount, in any status.
    suspend fun confirmedTransfer(
        bank: String,
        amount: Double,
        currency: String,
        since: Long,
        until: Long,
    ): SmsItem?

    // Newest first.
    suspend fun purchasesOnCard(
        bank: String,
        cardLast4: String,
        since: Long,
        until: Long,
    ): List<SmsItem>

    suspend fun insert(item: SmsItem)
    suspend fun update(item: SmsItem)

    suspend fun putHint(hint: SmsOtpHint)

    // Removes and returns the newest matching hint.
    suspend fun takeHint(
        bank: String,
        amount: Double,
        currency: String,
        since: Long,
        until: Long,
    ): SmsOtpHint?
}

// Rules that span messages: dedupe, OTP merchants, cancellations, code retries, confirmations.
class SmsCorrelator(private val store: SmsStore) {

    sealed interface Outcome {
        data class Created(val item: SmsItem) : Outcome
        data class Merged(val item: SmsItem) : Outcome
        data class Upgraded(val item: SmsItem) : Outcome
        data class Cancelled(val item: SmsItem) : Outcome

        // Cancelled after approval: a money-back item now waits in the queue.
        data class Reversal(val item: SmsItem, val original: SmsItem) : Outcome
        data class Duplicate(val existing: SmsItem) : Outcome
        data object HintStored : Outcome
        data class Skipped(val reason: IgnoreReason, val detail: String = "") : Outcome
    }

    // [text] is the body to store; [prepare] fills account and category before the insert.
    suspend fun accept(
        msg: SmsMessage,
        parsed: ParsedSms,
        text: String,
        now: Long,
        prepare: suspend (SmsItem) -> SmsItem = { it },
    ): Outcome = when (parsed) {
        is ParsedSms.Ignored -> Outcome.Skipped(parsed.reason, parsed.detail)

        is ParsedSms.OtpHint -> {
            store.putHint(
                SmsOtpHint(
                    uuid(),
                    parsed.bank.name,
                    parsed.merchant,
                    parsed.amount.toDouble(),
                    parsed.currency,
                    msg.timestamp,
                ),
            )
            Outcome.HintStored
        }

        is ParsedSms.Cancel -> cancel(msg, parsed, now)

        is ParsedSms.Movement -> movement(msg, parsed, text, now, prepare)
    }

    private suspend fun movement(
        msg: SmsMessage,
        m: ParsedSms.Movement,
        text: String,
        now: Long,
        prepare: suspend (SmsItem) -> SmsItem,
    ): Outcome {
        store.byDedupeKey(m.dedupeKey)?.let { return Outcome.Duplicate(it) }
        val bank = m.bank.name
        val amount = m.amount.toDouble()

        if (m.template == TRANSFER_CODE && m.route != null) {
            store.pendingRetry(
                bank,
                m.route,
                amount,
                m.currency,
                msg.timestamp - RETRY_MS,
            )?.let { existing ->
                val merged = existing.copy(
                    evidenceCount = existing.evidenceCount + 1,
                    updatedAt = now,
                )
                store.update(merged)
                return Outcome.Merged(merged)
            }
            // A late code message can arrive after the bank's confirmation already made the item.
            store.confirmedTransfer(
                bank,
                amount,
                m.currency,
                msg.timestamp,
                msg.timestamp + RETRY_MS,
            )?.let { return Outcome.Duplicate(it) }
        }
        if (m.kind == SmsKind.TRANSFER_OUT && m.certainty == Certainty.EXACT) {
            store.upgradeCandidate(
                bank,
                amount,
                m.currency,
                msg.timestamp - UPGRADE_MS,
            )?.let { existing ->
                val upgraded = existing.copy(
                    certainty = Certainty.EXACT,
                    reason = null,
                    template = m.template,
                    payee = m.payee ?: existing.payee,
                    reference = m.reference ?: existing.reference,
                    occurredAt = m.occurredAt,
                    evidenceCount = existing.evidenceCount + 1,
                    updatedAt = now,
                )
                store.update(upgraded)
                return Outcome.Upgraded(upgraded)
            }
        }

        var merchant = m.merchant
        var fromOtp = false
        if (m.template == PURCHASE) {
            store.takeHint(
                bank,
                amount,
                m.currency,
                msg.timestamp - HINT_MS,
                msg.timestamp,
            )?.let { hint ->
                val better = MerchantCleaner.purchase(hint.merchant)
                if (better.name.length > merchant.name.length) {
                    merchant = merchant.copy(name = better.name, key = better.key)
                    fromOtp = true
                }
            }
        }

        val item = prepare(
            SmsItem(
                id = uuid(),
                certainty = m.certainty,
                reason = m.reason,
                kind = m.kind,
                bank = bank,
                sender = msg.sender,
                template = m.template, amount = amount, currency = m.currency,
                merchantRaw = merchant.raw,
                merchant = merchant.name,
                merchantKey = merchant.key,
                merchantFromOtp = fromOtp,
                cardLast4 = m.cardLast4,
                payee = m.payee,
                reference = m.reference,
                route = m.route,
                feeRoute = m.feeRoute,
                occurredAt = m.occurredAt,
                smsAt = msg.timestamp,
                capturedAt = now,
                dedupeKey = m.dedupeKey,
                text = text,
                updatedAt = now, statedFee = m.statedFee?.toDouble(),
            ),
        )
        store.insert(item)
        return Outcome.Created(item)
    }

    private suspend fun cancel(msg: SmsMessage, c: ParsedSms.Cancel, now: Long): Outcome {
        val target =
            store.purchasesOnCard(
                c.bank.name,
                c.cardLast4,
                msg.timestamp - CANCEL_MS,
                msg.timestamp,
            )
                .firstOrNull {
                    it.status != SmsStatus.CANCELLED &&
                        it.merchantRaw.startsWith(c.merchant.raw, ignoreCase = true)
                }
                ?: return Outcome.Skipped(IgnoreReason.ORPHAN_CANCEL)
        return when (target.status) {
            SmsStatus.PENDING -> {
                val cancelled = target.copy(
                    status = SmsStatus.CANCELLED,
                    decidedAt = now,
                    evidenceCount =
                        target.evidenceCount + 1,
                    updatedAt = now,
                )
                store.update(cancelled)
                Outcome.Cancelled(cancelled)
            }

            SmsStatus.APPROVED -> {
                val key = DedupeKeys.reversal(target.id)
                store.byDedupeKey(key)?.let { return Outcome.Duplicate(it) }
                val reversal = target.copy(
                    id = uuid(),
                    status = SmsStatus.PENDING,
                    certainty = Certainty.EXACT,
                    reason = null,
                    kind = SmsKind.INCOME,
                    template = CANCELLED,
                    feeRoute = null,
                    smsAt = msg.timestamp,
                    occurredAt = msg.timestamp,
                    capturedAt = now,
                    dedupeKey = key,
                    evidenceCount = 1,
                    transactionId = null,
                    linkedItemId = target.id, decidedAt = null, denyReason = null, updatedAt = now,
                    // Money back is income; the purchase's expense category doesn't apply.
                    categoryId = null, toAccountId = null, actualAmount = null,
                )
                store.insert(reversal)
                Outcome.Reversal(reversal, target)
            }

            // Denied or expired: nothing was recorded, so there is nothing to undo.
            else -> Outcome.Skipped(IgnoreReason.ORPHAN_CANCEL)
        }
    }

    private fun uuid() = UUID.randomUUID().toString()

    companion object {
        const val PURCHASE = "purchase"
        const val TRANSFER_CODE = "transfer_code"
        const val CANCELLED = "cancelled"
        const val HINT_MS = 15 * 60 * 1000L
        const val RETRY_MS = 15 * 60 * 1000L
        const val UPGRADE_MS = 48 * 60 * 60 * 1000L
        const val CANCEL_MS = 7 * 24 * 60 * 60 * 1000L
    }
}

class MemorySmsStore : SmsStore {
    val items = LinkedHashMap<String, SmsItem>()
    val hints = ArrayList<SmsOtpHint>()

    override suspend fun byDedupeKey(key: String) = items.values.firstOrNull { it.dedupeKey == key }

    override suspend fun pendingRetry(
        bank: String,
        route: String,
        amount: Double,
        currency: String,
        since: Long,
    ) = items.values.filter {
        it.status == SmsStatus.PENDING && it.bank == bank &&
            it.template == SmsCorrelator.TRANSFER_CODE &&
            it.route == route &&
            it.amount == amount && it.currency == currency && it.smsAt >= since
    }.maxByOrNull { it.smsAt }

    override suspend fun upgradeCandidate(
        bank: String,
        amount: Double,
        currency: String,
        since: Long,
    ) = items.values.filter {
        it.status == SmsStatus.PENDING && it.bank == bank && it.kind == SmsKind.TRANSFER_OUT &&
            it.certainty == Certainty.UNVERIFIED && it.amount == amount &&
            it.currency == currency &&
            it.smsAt >= since
    }.maxByOrNull { it.smsAt }

    override suspend fun confirmedTransfer(
        bank: String,
        amount: Double,
        currency: String,
        since: Long,
        until: Long,
    ) = items.values.filter {
        it.bank == bank && it.kind == SmsKind.TRANSFER_OUT && it.certainty == Certainty.EXACT &&
            it.template != SmsCorrelator.TRANSFER_CODE && it.amount == amount &&
            it.currency == currency &&
            it.smsAt in since..until
    }.maxByOrNull { it.smsAt }

    override suspend fun purchasesOnCard(
        bank: String,
        cardLast4: String,
        since: Long,
        until: Long,
    ) = items.values.filter {
        it.bank == bank && it.template == SmsCorrelator.PURCHASE && it.cardLast4 == cardLast4 &&
            it.smsAt in since..until
    }.sortedByDescending { it.smsAt }

    override suspend fun insert(item: SmsItem) {
        items[item.id] = item
    }

    override suspend fun update(item: SmsItem) {
        items[item.id] = item
    }

    override suspend fun putHint(hint: SmsOtpHint) {
        hints += hint
    }

    override suspend fun takeHint(
        bank: String,
        amount: Double,
        currency: String,
        since: Long,
        until: Long,
    ): SmsOtpHint? {
        val hit = hints.filter {
            it.bank == bank && it.amount == amount && it.currency == currency &&
                it.at in since..until
        }.maxByOrNull { it.at }
        if (hit != null) hints.remove(hit)
        return hit
    }
}
