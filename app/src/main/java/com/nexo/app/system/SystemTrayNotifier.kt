package com.nexo.app.system

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nexo.app.R
import com.nexo.app.core.common.NexoIntents
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.notifications.Notifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

class SystemTrayNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : Notifier {
    private val manager = NotificationManagerCompat.from(context)

    override fun canPost(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    override fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    // Every action routes through the lock first.
    override fun updateQuickAdd(enabled: Boolean) {
        if (!enabled) {
            manager.cancel(ID_QUICK_ADD)
            return
        }
        if (!canPost()) return
        ensureChannels()
        val repost = PendingIntent.getBroadcast(
            context,
            10,
            Intent(context, QuickAddRepostReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_QUICK_ADD)
            .setSmallIcon(R.drawable.ic_stat_nexo)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.quick_add_notification_text))
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                activityIntent(NexoIntents.ACTION_ADD, 20) {
                    putExtra(NexoIntents.EXTRA_TYPE, EntryType.EXPENSE.name)
                },
            )
            .setDeleteIntent(repost)
            .addAction(
                0,
                context.getString(R.string.add_transaction),
                activityIntent(NexoIntents.ACTION_ADD, 21) {
                    putExtra(NexoIntents.EXTRA_TYPE, EntryType.EXPENSE.name)
                },
            )
            .addAction(
                0,
                context.getString(R.string.bookmarks),
                activityIntent(NexoIntents.ACTION_BOOKMARKS, 22),
            )
            .addAction(
                0,
                context.getString(R.string.search),
                activityIntent(NexoIntents.ACTION_SEARCH, 23),
            )
            .build()
        notify(ID_QUICK_ADD, n)
    }

    // Only a count, never message text or amounts.
    override fun postSmsReview(count: Int) {
        if (count <= 0) {
            manager.cancel(ID_SMS)
            return
        }
        if (!canPost()) return
        ensureChannels()
        val n = NotificationCompat.Builder(context, CHANNEL_SMS)
            .setSmallIcon(R.drawable.ic_stat_nexo)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(context.getString(R.string.sms_review_title))
            .setContentText(
                context.resources.getQuantityString(R.plurals.sms_review_count, count, count),
            )
            .setNumber(count)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(activityIntent(NexoIntents.ACTION_REVIEW, 50))
            .build()
        notify(ID_SMS, n)
    }

    override fun postReminder() {
        if (!canPost()) return
        ensureChannels()
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_nexo)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.reminder_text))
            .setAutoCancel(true)
            .setContentIntent(activityIntent(NexoIntents.ACTION_MAIN, 30))
            .build()
        notify(ID_REMINDER, n)
    }

    // One exact alarm at the next occurrence; inexact when exact alarms aren't allowed.
    override fun scheduleReminder(enabled: Boolean, hour: Int, minute: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            40,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
        if (!enabled) return
        val now = LocalDateTime.now(clock)
        var at = now.toLocalDate().atTime(hour, minute)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val ms = at.atZone(clock.zone).toInstant().toEpochMilli()
        if (canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    private fun notify(id: Int, notification: Notification) {
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission revoked meanwhile.
        }
    }

    private fun ensureChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDER,
                context.getString(R.string.alarm_setting),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_QUICK_ADD,
                context.getString(R.string.quick_add),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SMS,
                context.getString(R.string.sms_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.sms_channel_description)
            },
        )
    }

    private fun activityIntent(
        action: String,
        request: Int,
        extra: (Intent.() -> Unit)? = null,
    ): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val intent = Intent().setComponent(launch?.component).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extra?.invoke(this)
        }
        return PendingIntent.getActivity(
            context,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val CHANNEL_REMINDER = "reminder"
        const val CHANNEL_QUICK_ADD = "quick_add"
        const val CHANNEL_SMS = "sms_review"
        const val ID_REMINDER = 1001
        const val ID_QUICK_ADD = 1002
        const val ID_SMS = 1003
    }
}
