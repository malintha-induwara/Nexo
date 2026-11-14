package com.nexo.app.sms.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.settings.DevicePrefs
import com.nexo.app.sms.SmsStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

object SmsPermissions {
    val ALL = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

    fun granted(context: Context): Boolean =
        ALL.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}

/**
 * The capture switch's state machine and queue housekeeping.
 * Call [refresh] at app start, on every foreground, and after the switch or the permission changes.
 */
@Singleton
class SmsCapture @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: DevicePrefs,
    private val db: NexoDatabase,
    private val intake: SmsIntake,
    private val inbox: InboxReader,
) {
    private val mutex = Mutex()

    fun hasPermission(): Boolean = SmsPermissions.granted(context)

    /** Sets or clears the start point, catches up on missed messages, and expires old items. */
    suspend fun refresh(now: Long = System.currentTimeMillis()) = mutex.withLock {
        val s = prefs.current()
        if (!s.setupDone) return@withLock
        val active = s.smsCaptureEnabled && hasPermission()
        when {
            active && s.smsCaptureStartedAt == 0L -> prefs.markSmsCaptureStarted(now)
            !active && s.smsCaptureStartedAt != 0L -> prefs.markSmsCaptureStopped(now)
        }
        if (active) catchUp()
        sweep(now)
    }

    suspend fun setEnabled(on: Boolean) {
        prefs.setSmsCaptureEnabled(on)
        refresh()
    }

    /** Messages missed while the process wasn't running. Never reaches back past the start point. */
    private suspend fun catchUp() {
        val s = prefs.current()
        var after = maxOf(s.smsLastProcessedAt, s.smsCaptureStartedAt - 1)
        while (true) {
            val rows = try {
                inbox.read(after = after, from = s.smsCaptureStartedAt)
            } catch (e: Exception) {
                Log.w(TAG, "Inbox read failed: ${e.javaClass.simpleName}")
                return
            }
            if (rows.isEmpty()) return
            intake.receive(rows)
            after = rows.maxOf { it.receivedAt }
            prefs.setSmsLastProcessed(after)
            if (rows.size < PAGE) return
        }
    }

    /** Queue window and the retention of decided items. */
    suspend fun sweep(now: Long) {
        val dao = db.smsDao()
        dao.expire(before = now - QUEUE_WINDOW_MS, now = now)
        dao.purge(SmsStatus.DENIED, now - DENIED_KEEP_MS)
        dao.purge(SmsStatus.EXPIRED, now - EXPIRED_KEEP_MS)
        dao.purge(SmsStatus.CANCELLED, now - DENIED_KEEP_MS)
        dao.purgeApproved(now - APPROVED_KEEP_MS)
        dao.purgeHints(now - HINT_KEEP_MS)
    }

    companion object {
        private const val TAG = "SmsCapture"
        private const val PAGE = 500
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        const val QUEUE_WINDOW_DAYS = 30
        const val QUEUE_WINDOW_MS = QUEUE_WINDOW_DAYS * DAY_MS
        const val DENIED_KEEP_MS = 30 * DAY_MS
        const val EXPIRED_KEEP_MS = 7 * DAY_MS

        /** Long enough for late cancellations and editing an FX estimate later. */
        const val APPROVED_KEEP_MS = 90 * DAY_MS
        const val HINT_KEEP_MS = 15 * 60 * 1000L
    }
}
