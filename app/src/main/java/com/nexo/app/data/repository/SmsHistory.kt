package com.nexo.app.data.repository

import com.nexo.app.core.common.Dispatcher
import com.nexo.app.core.common.NexoDispatchers
import com.nexo.app.core.common.period.toIsoText
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.SmsStatus
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.entity.SmsItemEntity
import com.nexo.app.data.sms.InboxReader
import com.nexo.app.data.sms.MemorySmsStore
import com.nexo.app.data.sms.SmsCorrelator
import com.nexo.app.data.sms.SmsSuggester
import com.nexo.app.data.sms.parser.Redactor
import com.nexo.app.data.sms.parser.SmsParser
import com.nexo.app.domain.sms.SmsApprover
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** A past message the picker can offer. */
data class HistoryRow(
    val item: SmsItemEntity,
    /** Cancelled later in the same period: shown struck through, never selectable. */
    val cancelled: Boolean,
    /** A transaction already in Nexo matches it (same account and amount, date ±1 day). */
    val possiblyAdded: Boolean,
)

/**
 * "Add past transactions": reads the inbox from before the start point (or a re-enable gap), parses it the same way as
 * live capture, and drops anything Nexo already has. Nothing is written until the user picks rows.
 */
@Singleton
class SmsHistory @Inject constructor(
    private val db: NexoDatabase,
    private val prefs: UserPreferencesRepository,
    private val refs: ReferenceDataRepository,
    private val inbox: InboxReader,
    private val suggester: SmsSuggester,
    private val fx: FxRepository,
    @Dispatcher(NexoDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    /** Live capture covers everything from the start point on, so the picker stops there. */
    suspend fun upperBound(until: Long): Long {
        val started = prefs.getDeviceSettings().smsCaptureStartedAt
        return if (started > 0) minOf(until, started) else until
    }

    suspend fun scan(from: Long, until: Long): List<HistoryRow> = withContext(ioDispatcher) {
        val s = prefs.getDeviceSettings()
        val bound = upperBound(until)
        if (from >= bound) return@withContext emptyList()
        val ref = refs.getRefData()
        val parser = SmsParser(s.smsCustomSenders)
        val store = MemorySmsStore()
        val correlator = SmsCorrelator(store)
        val now = System.currentTimeMillis()
        // Oldest first, so OTP names and cancellations line up as they did on the phone.
        for (incoming in inbox.read(after = from - 1, from = from, until = bound, limit = MAX_MESSAGES).sortedBy { it.message.timestamp }) {
            val parsed = parser.parse(incoming.message)
            correlator.accept(incoming.message, parsed, Redactor.stored(incoming.message.body, s.smsStoreFullText), now) { suggester.prepare(it, ref) }
        }
        val candidates = store.items.values.toList()
        val known = candidates.map { it.dedupeKey }.chunked(500).flatMap { db.smsDao().existingKeys(it) }.toSet()
        val fresh = candidates.filter { it.dedupeKey !in known }.map {
            // Past days may need that day's board from the API.
            if (it.certainty == Certainty.ESTIMATED && it.estimatedAmount == null) fx.estimate(it, ref, allowNetwork = true) else it
        }
        val zone = ZoneId.systemDefault()
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val ledger = db.transactionDao().getRange(day(from).minusDays(1).toIsoText(), day(bound).plusDays(1).toIsoText())
        fresh.sortedByDescending { it.occurredAt }.map { item ->
            val date = day(item.occurredAt)
            val amount = SmsApprover.ledgerAmount(item)
            val match = item.accountId != null && ledger.any { t ->
                t.accountId == item.accountId && abs(t.amountAccount - amount) < 0.005 &&
                    abs(ChronoUnit.DAYS.between(LocalDate.parse(t.date), date)) <= 1
            }
            HistoryRow(item, cancelled = item.status == SmsStatus.CANCELLED, possiblyAdded = match)
        }
    }

    /** Stores the chosen rows as queue items so they can be approved like any other. Returns the stored ids. */
    suspend fun keep(items: List<SmsItemEntity>): List<String> {
        val now = System.currentTimeMillis()
        return items.filter { it.status == SmsStatus.PENDING }.mapNotNull { item ->
            val stored = item.copy(capturedAt = now, updatedAt = now)
            if (db.smsDao().insert(stored) != -1L) stored.id else null
        }
    }

    companion object {
        /** Very large inboxes are read up to this many messages per scan. */
        const val MAX_MESSAGES = 5_000
    }
}
