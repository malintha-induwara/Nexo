package com.nexo.app.work

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nexo.app.MainActivity
import com.nexo.app.R
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.settings.DevicePrefs
import com.nexo.app.data.RefRepository
import com.nexo.app.ui.IntentRouter
import com.nexo.app.widget.WidgetRefresher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationsEntryPoint {
    fun prefs(): DevicePrefs
    fun refs(): RefRepository
    fun widgets(): WidgetRefresher
}

object Notifications {
    const val CHANNEL_REMINDER = "reminder"
    const val CHANNEL_QUICK_ADD = "quick_add"
    const val CHANNEL_SMS = "sms_review"
    private const val ID_REMINDER = 1001
    private const val ID_QUICK_ADD = 1002
    private const val ID_SMS = 1003

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, context.getString(R.string.alarm_setting), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_QUICK_ADD, context.getString(R.string.quick_add), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SMS, context.getString(R.string.sms_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.sms_channel_description)
            },
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun activityIntent(context: Context, action: String, request: Int, extra: (Intent.() -> Unit)? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extra?.invoke(this)
        }
        return PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Persistent quick-add notification (CFG-052). Every action routes through the lock first. */
    fun updateQuickAdd(context: Context, enabled: Boolean) {
        val nm = NotificationManagerCompat.from(context)
        if (!enabled) {
            nm.cancel(ID_QUICK_ADD)
            return
        }
        if (!canPost(context)) return
        ensureChannels(context)
        val repost = PendingIntent.getBroadcast(context, 10, Intent(context, QuickAddRepostReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_QUICK_ADD)
            .setSmallIcon(R.drawable.ic_stat_nexo)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.quick_add_notification_text))
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(activityIntent(context, IntentRouter.ACTION_ADD, 20) { putExtra(IntentRouter.EXTRA_TYPE, EntryType.EXPENSE.name) })
            .setDeleteIntent(repost)
            .addAction(0, context.getString(R.string.add_transaction), activityIntent(context, IntentRouter.ACTION_ADD, 21) { putExtra(IntentRouter.EXTRA_TYPE, EntryType.EXPENSE.name) })
            .addAction(0, context.getString(R.string.bookmarks), activityIntent(context, IntentRouter.ACTION_BOOKMARKS, 22))
            .addAction(0, context.getString(R.string.search), activityIntent(context, IntentRouter.ACTION_SEARCH, 23))
            .build()
        try {
            nm.notify(ID_QUICK_ADD, n)
        } catch (_: SecurityException) {
            // Notification permission revoked meanwhile.
        }
    }

    /** LKSMS-015: only a count, never message text or amounts. [count] 0 removes it. */
    fun postSmsReview(context: Context, count: Int) {
        val nm = NotificationManagerCompat.from(context)
        if (count <= 0) {
            nm.cancel(ID_SMS)
            return
        }
        if (!canPost(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_SMS)
            .setSmallIcon(R.drawable.ic_stat_nexo)
            .setContentTitle(context.getString(R.string.sms_review_title))
            .setContentText(context.resources.getQuantityString(R.plurals.sms_review_count, count, count))
            .setNumber(count)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(activityIntent(context, IntentRouter.ACTION_REVIEW, 50))
            .build()
        try {
            nm.notify(ID_SMS, n)
        } catch (_: SecurityException) {
            // Notification permission revoked meanwhile.
        }
    }

    fun postReminder(context: Context) {
        if (!canPost(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_nexo)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.reminder_text))
            .setAutoCancel(true)
            .setContentIntent(activityIntent(context, IntentRouter.ACTION_MAIN, 30))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID_REMINDER, n)
        } catch (_: SecurityException) {
            // Notification permission revoked meanwhile.
        }
    }
}

/** Daily reminder alarm (CFG-050): one exact alarm at the next occurrence; inexact when exact alarms aren't allowed. */
object ReminderScheduler {
    fun schedule(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(context, 40, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
        if (!enabled) return
        val now = LocalDateTime.now()
        var at = LocalDate.now().atTime(hour, minute)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}

private fun Context.entry() = EntryPointAccessors.fromApplication(applicationContext, NotificationsEntryPoint::class.java)

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val s = context.entry().prefs().current()
                // DayOfWeek 1 = Monday … 7 = Sunday.
                if (s.reminderEnabled && LocalDate.now().dayOfWeek.value in s.reminderDays && s.setupDone) Notifications.postReminder(context)
                ReminderScheduler.schedule(context, s.reminderEnabled, s.reminderHour, s.reminderMinute)
            } finally {
                pending.finish()
            }
        }
    }
}

class QuickAddRepostReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val e = context.entry()
                if (e.prefs().current().setupDone && e.refs().snapshot().settings.quickAddNotification) Notifications.updateQuickAdd(context, true)
            } finally {
                pending.finish()
            }
        }
    }
}

/** After a reboot: re-post quick add, refresh widgets and re-schedule the reminder (CFG-050). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val e = context.entry()
                val s = e.prefs().current()
                if (s.setupDone) {
                    Notifications.updateQuickAdd(context, e.refs().snapshot().settings.quickAddNotification)
                    e.widgets().refresh()
                }
                ReminderScheduler.schedule(context, s.reminderEnabled, s.reminderHour, s.reminderMinute)
            } finally {
                pending.finish()
            }
        }
    }
}
