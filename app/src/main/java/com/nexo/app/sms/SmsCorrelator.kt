package com.nexo.app.sms

import com.nexo.app.sms.data.SmsItemEntity
import com.nexo.app.sms.data.SmsOtpHintEntity
import com.nexo.app.sms.parse.DedupeKeys
import com.nexo.app.sms.parse.IgnoreReason
import com.nexo.app.sms.parse.MerchantCleaner
import com.nexo.app.sms.parse.ParsedSms
import com.nexo.app.sms.parse.SmsMessage
import java.util.UUID

/** What the correlator needs from storage: Room for live capture, memory for the history picker and tests. */
interface SmsStore {
    suspend fun byDedupeKey(key: String): SmsItemEntity?

    /** Pending approval-code item with the same route and amount since [since] (NQ-004 retry merge). */
    suspend fun pendingRetry(bank: String, route: String, amount: Double, currency: String, since: Long): SmsItemEntity?

    /** Pending unverified transfer-out with the same amount since [since] (NQ-004 confirmation upgrade). */
    suspend fun upgradeCandidate(bank: String, amount: Double, currency: String, since: Long): SmsItemEntity?

    /** Card purchases on [cardLast4] in [since, until], newest first (CB-004). */
    suspend fun purchasesOnCard(bank: String, cardLast4: String, since: Long, until: Long): List<SmsItemEntity>

    suspend fun insert(item: SmsItemEntity)
    suspend fun update(item: SmsItemEntity)

    suspend fun putHint(hint: SmsOtpHintEntity)

    /** Removes and returns the newest hint with this amount in [since, until]. */
    suspend fun takeHint(bank: String, amount: Double, currency: String, since: Long, until: Long): SmsOtpHintEntity?
}

/**
 * Applies the stateful rules across messages: dedupe (CB-010), OTP enrichment (CB-003), cancellations (CB-004),
 * approval-code retries and later confirmations (NQ-004).
 */
class SmsCorrelator(private val store: SmsStore) {

    sealed interface Outcome {
        data class Created(val item: SmsItemEntity) : Outcome
        data class Merged(val item: SmsItemEntity) : Outcome
        data class Upgraded(val item: SmsItemEntity) : Outcome
        data class Cancelled(val item: SmsItemEntity) : Outcome

        /** The cancelled purchase was already approved: a money-back item now waits in the queue. */
        data class Reversal(val item: SmsItemEntity, val original: SmsItemEntity) : Outcome
        data class Duplicate(val existing: SmsItemEntity) : Outcome
        data object HintStored : Outcome
        data class Skipped(val reason: IgnoreReason, val detail: String = "") : Outcome
    }

    /**
     * [text] is the body as it should be stored. [prepare] adds suggestions (account, category) before the insert.
     * [now] is the capture time.
     */
    suspend fun accept(
        msg: SmsMessage,
        parsed: ParsedSms,
        text: String,
        now: Long,
        prepare: suspend (SmsItemEntity) -> SmsItemEntity = { it },
    ): Outcome = when (parsed) {
        is ParsedSms.Ignored -> Outcome.Skipped(parsed.reason, parsed.detail)
        is ParsedSms.OtpHint -> {
            store.putHint(
                SmsOtpHintEntity(uuid(), parsed.bank.name, parsed.merchant, parsed.amount.toDouble(), parsed.currency, msg.timestamp),
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
        prepare: suspend (SmsItemEntity) -> SmsItemEntity,
    ): Outcome {
        store.byDedupeKey(m.dedupeKey)?.let { return Outcome.Duplicate(it) }
        val bank = m.bank.name
        val amount = m.amount.toDouble()

        if (m.template == TRANSFER_CODE && m.route != null) {
            store.pendingRetry(bank, m.route, amount, m.currency, msg.timestamp - RETRY_MS)?.let { existing ->
                val merged = existing.copy(evidenceCount = existing.evidenceCount + 1, updatedAt = now)
                store.update(merged)
                return Outcome.Merged(merged)
            }
        }
        if (m.kind == SmsKind.TRANSFER_OUT && m.certainty == Certainty.EXACT) {
            store.upgradeCandidate(bank, amount, m.currency, msg.timestamp - UPGRADE_MS)?.let { existing ->
                val upgraded = existing.copy(
                    certainty = Certainty.EXACT, reason = null, template = m.template,
                    payee = m.payee ?: existing.payee, reference = m.reference ?: existing.reference,
                    occurredAt = m.occurredAt, evidenceCount = existing.evidenceCount + 1, updatedAt = now,
                )
                store.update(upgraded)
                return Outcome.Upgraded(upgraded)
            }
        }

        var merchant = m.merchant
        var fromOtp = false
        if (m.template == PURCHASE) {
            store.takeHint(bank, amount, m.currency, msg.timestamp - HINT_MS, msg.timestamp)?.let { hint ->
                val better = MerchantCleaner.purchase(hint.merchant)
                if (better.name.length > merchant.name.length) {
                    merchant = merchant.copy(name = better.name, key = better.key)
                    fromOtp = true
                }
            }
        }

        val item = prepare(
            SmsItemEntity(
                id = uuid(), certainty = m.certainty, reason = m.reason, kind = m.kind, bank = bank, sender = msg.sender,
                template = m.template, amount = amount, currency = m.currency,
                merchantRaw = merchant.raw, merchant = merchant.name, merchantKey = merchant.key, merchantFromOtp = fromOtp,
                cardLast4 = m.cardLast4, payee = m.payee, reference = m.reference, route = m.route, feeRoute = m.feeRoute,
                occurredAt = m.occurredAt, smsAt = msg.timestamp, capturedAt = now, dedupeKey = m.dedupeKey, text = text,
                updatedAt = now,
            ),
        )
        store.insert(item)
        return Outcome.Created(item)
    }

    private suspend fun cancel(msg: SmsMessage, c: ParsedSms.Cancel, now: Long): Outcome {
        val target = store.purchasesOnCard(c.bank.name, c.cardLast4, msg.timestamp - CANCEL_MS, msg.timestamp)
            .firstOrNull { it.status != SmsStatus.CANCELLED && it.merchantRaw.startsWith(c.merchant.raw, ignoreCase = true) }
            ?: return Outcome.Skipped(IgnoreReason.ORPHAN_CANCEL)
        return when (target.status) {
            SmsStatus.PENDING -> {
                val cancelled = target.copy(status = SmsStatus.CANCELLED, decidedAt = now, evidenceCount = target.evidenceCount + 1, updatedAt = now)
                store.update(cancelled)
                Outcome.Cancelled(cancelled)
            }
            SmsStatus.APPROVED -> {
                val key = DedupeKeys.reversal(target.id)
                store.byDedupeKey(key)?.let { return Outcome.Duplicate(it) }
                val reversal = target.copy(
                    id = uuid(), status = SmsStatus.PENDING, certainty = Certainty.EXACT, reason = null,
                    kind = SmsKind.INCOME, template = CANCELLED, feeRoute = null, smsAt = msg.timestamp,
                    occurredAt = msg.timestamp, capturedAt = now, dedupeKey = key, evidenceCount = 1, transactionId = null,
                    linkedItemId = target.id, decidedAt = null, denyReason = null, updatedAt = now,
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

/** In-memory [SmsStore]: the history picker's scratch space, and the test double. */
class MemorySmsStore : SmsStore {
    val items = LinkedHashMap<String, SmsItemEntity>()
    val hints = ArrayList<SmsOtpHintEntity>()

    override suspend fun byDedupeKey(key: String) = items.values.firstOrNull { it.dedupeKey == key }

    override suspend fun pendingRetry(bank: String, route: String, amount: Double, currency: String, since: Long) =
        items.values.filter {
            it.status == SmsStatus.PENDING && it.bank == bank && it.template == SmsCorrelator.TRANSFER_CODE && it.route == route &&
                it.amount == amount && it.currency == currency && it.smsAt >= since
        }.maxByOrNull { it.smsAt }

    override suspend fun upgradeCandidate(bank: String, amount: Double, currency: String, since: Long) =
        items.values.filter {
            it.status == SmsStatus.PENDING && it.bank == bank && it.kind == SmsKind.TRANSFER_OUT &&
                it.certainty == Certainty.UNVERIFIED && it.amount == amount && it.currency == currency && it.smsAt >= since
        }.maxByOrNull { it.smsAt }

    override suspend fun purchasesOnCard(bank: String, cardLast4: String, since: Long, until: Long) =
        items.values.filter {
            it.bank == bank && it.template == SmsCorrelator.PURCHASE && it.cardLast4 == cardLast4 && it.smsAt in since..until
        }.sortedByDescending { it.smsAt }

    override suspend fun insert(item: SmsItemEntity) {
        items[item.id] = item
    }

    override suspend fun update(item: SmsItemEntity) {
        items[item.id] = item
    }

    override suspend fun putHint(hint: SmsOtpHintEntity) {
        hints += hint
    }

    override suspend fun takeHint(bank: String, amount: Double, currency: String, since: Long, until: Long): SmsOtpHintEntity? {
        val hit = hints.filter { it.bank == bank && it.amount == amount && it.currency == currency && it.at in since..until }.maxByOrNull { it.at }
        if (hit != null) hints.remove(hit)
        return hit
    }
}
