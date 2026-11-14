package com.nexo.app.sms.capture

import android.content.Context
import android.util.Log
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.settings.DevicePrefs
import com.nexo.app.core.settings.DeviceSettings
import com.nexo.app.sms.SmsCorrelator
import com.nexo.app.sms.data.RoomSmsStore
import com.nexo.app.sms.parse.Redactor
import com.nexo.app.sms.parse.SmsMessage
import com.nexo.app.sms.parse.SmsParser
import com.nexo.app.work.Notifications
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** A message plus the moment the phone received it (inbox `date`), which is what the start point is compared with. */
data class IncomingSms(val message: SmsMessage, val receivedAt: Long)

/**
 * The single path from an SMS to the Review Queue (spec 01 BR-1): gate → parse → correlate → suggest → store → notify.
 * Realtime and catch-up both come through here, one message at a time.
 */
@Singleton
class SmsIntake @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: NexoDatabase,
    private val prefs: DevicePrefs,
    private val suggester: SmsSuggester,
) {
    private val mutex = Mutex()
    private val correlator by lazy { SmsCorrelator(RoomSmsStore(db.smsDao())) }

    /** Returns how many queue items were created or changed. */
    suspend fun receive(batch: List<IncomingSms>, notify: Boolean = true): Int = receive(batch, prefs.current(), notify)

    internal suspend fun receive(batch: List<IncomingSms>, s: DeviceSettings, notify: Boolean): Int = mutex.withLock {
        // LKSMS-002 / NQ-031: only while capture is active, and never before the start point (BR-Q6).
        if (!s.setupDone || !s.smsCaptureEnabled || s.smsCaptureStartedAt == 0L) return@withLock 0
        val parser = SmsParser(s.smsCustomSenders)
        var changed = 0
        for (incoming in batch.sortedBy { it.message.timestamp }) {
            if (incoming.receivedAt < s.smsCaptureStartedAt) continue
            val outcome = try {
                val parsed = parser.parse(incoming.message)
                val text = Redactor.stored(incoming.message.body, s.smsStoreFullText)
                correlator.accept(incoming.message, parsed, text, System.currentTimeMillis()) { suggester.prepare(it) }
            } catch (e: Exception) {
                // Never log message text.
                Log.w(TAG, "SMS intake failed: ${e.javaClass.simpleName}")
                continue
            }
            if (outcome.changesQueue) changed++
        }
        if (changed > 0 && notify) notifyPending()
        changed
    }

    suspend fun notifyPending() {
        val since = prefs.current().smsSeenAt
        Notifications.postSmsReview(context, db.smsDao().countPendingSince(since))
    }

    private val SmsCorrelator.Outcome.changesQueue: Boolean
        get() = this is SmsCorrelator.Outcome.Created || this is SmsCorrelator.Outcome.Upgraded ||
            this is SmsCorrelator.Outcome.Reversal || this is SmsCorrelator.Outcome.Cancelled

    private companion object {
        const val TAG = "SmsIntake"
    }
}
