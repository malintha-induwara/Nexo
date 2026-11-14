package com.nexo.app.data.repository

import android.content.Context
import android.util.Log
import com.nexo.app.core.common.Dispatcher
import com.nexo.app.core.common.NexoDispatchers
import com.nexo.app.core.common.period.toIsoText
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.HistoryRow
import com.nexo.app.core.model.SmsStatus
import com.nexo.app.data.sms.MemorySmsStore
import com.nexo.app.data.sms.SmsCorrelator
import com.nexo.app.data.sms.SmsInboxDataSource
import com.nexo.app.data.sms.SmsPermissions
import com.nexo.app.data.sms.ledgerAmount
import com.nexo.app.data.sms.parser.Redactor
import com.nexo.app.data.sms.parser.SmsParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class DefaultSmsInboxRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferencesRepository,
    private val refs: ReferenceDataRepository,
    private val sms: SmsRepository,
    private val fx: FxRatesRepository,
    private val transactions: TransactionsRepository,
    private val inbox: SmsInboxDataSource,
    @Dispatcher(NexoDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : SmsInboxRepository {
    private val mutex = Mutex()

    override fun hasPermission(): Boolean = SmsPermissions.granted(context)

    override suspend fun refresh(now: Long) = mutex.withLock {
        val s = prefs.getDeviceSettings()
        if (!s.setupDone) return@withLock
        val active = s.smsCaptureEnabled && hasPermission()
        when {
            active && s.smsCaptureStartedAt == 0L -> prefs.markSmsCaptureStarted(now)
            !active && s.smsCaptureStartedAt != 0L -> prefs.markSmsCaptureStopped(now)
        }
        if (active) catchUp()
        sms.sweep(now)
    }

    override suspend fun setCaptureEnabled(on: Boolean) {
        prefs.setSmsCaptureEnabled(on)
        refresh()
    }

    /** Messages missed while the process wasn't running. Never reaches back past the start point. */
    private suspend fun catchUp() {
        val s = prefs.getDeviceSettings()
        var after = maxOf(s.smsLastProcessedAt, s.smsCaptureStartedAt - 1)
        while (true) {
            val rows = try {
                inbox.read(after = after, from = s.smsCaptureStartedAt)
            } catch (e: Exception) {
                Log.w(TAG, "Inbox read failed: ${e.javaClass.simpleName}")
                return
            }
            if (rows.isEmpty()) return
            sms.receive(rows)
            after = rows.maxOf { it.receivedAt }
            prefs.setSmsLastProcessed(after)
            if (rows.size < PAGE) return
        }
    }

    override suspend fun upperBound(until: Long): Long {
        val started = prefs.getDeviceSettings().smsCaptureStartedAt
        return if (started > 0) minOf(until, started) else until
    }

    override suspend fun scanHistory(from: Long, until: Long): List<HistoryRow> = withContext(ioDispatcher) {
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
            correlator.accept(incoming.message, parsed, Redactor.stored(incoming.message.body, s.smsStoreFullText), now) { sms.prepare(it, ref) }
        }
        val candidates = store.items.values.toList()
        val known = sms.getKnownDedupeKeys(candidates.map { it.dedupeKey })
        val fresh = candidates.filter { it.dedupeKey !in known }.map {
            // Past days may need that day's board from the API.
            if (it.certainty == Certainty.ESTIMATED && it.estimatedAmount == null) fx.estimate(it, ref, allowNetwork = true) else it
        }
        val zone = ZoneId.systemDefault()
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val ledger = transactions.getRange(day(from).minusDays(1).toIsoText(), day(bound).plusDays(1).toIsoText())
        fresh.sortedByDescending { it.occurredAt }.map { item ->
            val date = day(item.occurredAt)
            val amount = ledgerAmount(item)
            val match = item.accountId != null && ledger.any { t ->
                t.accountId == item.accountId && abs(t.amountAccount - amount) < 0.005 &&
                    abs(ChronoUnit.DAYS.between(LocalDate.parse(t.date), date)) <= 1
            }
            HistoryRow(item, cancelled = item.status == SmsStatus.CANCELLED, possiblyAdded = match)
        }
    }

    private companion object {
        const val TAG = "SmsInboxRepository"
        const val PAGE = 500

        /** Very large inboxes are read up to this many messages per scan. */
        const val MAX_MESSAGES = 5_000
    }
}
