package com.nexo.app.system

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexo.app.core.common.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/** The daily reminder alarm went off. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var notifications: StandingNotifications

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) =
        handleAsync(scope) { notifications.onReminderAlarm() }
}

/** The quick-add notification was swiped away; it comes back while the setting is on. */
@AndroidEntryPoint
class QuickAddRepostReceiver : BroadcastReceiver() {
    @Inject lateinit var notifications: StandingNotifications

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) =
        handleAsync(scope) { notifications.refreshQuickAdd() }
}

/** After a reboot, an app update or an exact-alarm permission change. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var notifications: StandingNotifications

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) {
            return
        }
        handleAsync(scope) { notifications.restore() }
    }
}
