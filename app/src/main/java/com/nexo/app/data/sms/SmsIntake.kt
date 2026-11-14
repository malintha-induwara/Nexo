package com.nexo.app.data.sms

import android.content.Context
import android.util.Log
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.notifications.Notifications
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.dao.RoomSmsStore
import com.nexo.app.data.repository.UserPreferencesRepository
import com.nexo.app.data.sms.parser.Redactor
import com.nexo.app.data.sms.parser.SmsMessage
import com.nexo.app.data.sms.parser.SmsParser
import com.nexo.app.data.work.FxSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A message plus the moment the phone received it (inbox `date`), which is what the start point is compared with. */
data class IncomingSms(val message: SmsMessage, val receivedAt: Long)

/**
 * The single path from an SMS to the Review Queue: gate → parse → correlate → suggest → store → notify.
 * Realtime and catch-up both come through here, one message at a time.
 */
@Singleton
class SmsIntake @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: NexoDatabase,
    private val prefs: UserPreferencesRepository,
    private val suggester: SmsSuggester,
) {
    private val mutex = Mutex()
    private val correlator by lazy { SmsCorrelator(RoomSmsStore(db.smsDao())) }

    /** Returns how many queue items were created or changed. [notify] also posts the notification and schedules rate syncs. */
    suspend fun receive(batch: List<IncomingSms>, notify: Boolean = true): Int = receive(batch, prefs.getDeviceSettings(), notify)

    internal suspend fun receive(batch: List<IncomingSms>, s: DeviceSettings, notify: Boolean): Int = mutex.withLock {
        // Only while capture is active, and never before the start point.
        if (!s.setupDone || !s.smsCaptureEnabled || s.smsCaptureStartedAt == 0L) return@withLock 0
        val parser = SmsParser(s.smsCustomSenders)
        var changed = 0
        var foreign = false
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
            if ((outcome as? SmsCorrelator.Outcome.Created)?.item?.certainty == Certainty.ESTIMATED) foreign = true
        }
        if (notify) {
            if (changed > 0) notifyPending()
            // A new foreign-currency item asks for fresh rates once the phone is online.
            if (foreign) FxSyncWorker.enqueue(context)
        }
        changed
    }

    suspend fun notifyPending() {
        val since = prefs.getDeviceSettings().smsSeenAt
        Notifications.postSmsReview(context, db.smsDao().countPendingSince(since))
    }

    private val SmsCorrelator.Outcome.changesQueue: Boolean
        get() = this is SmsCorrelator.Outcome.Created || this is SmsCorrelator.Outcome.Upgraded ||
            this is SmsCorrelator.Outcome.Reversal || this is SmsCorrelator.Outcome.Cancelled

    private companion object {
        const val TAG = "SmsIntake"
    }
}
