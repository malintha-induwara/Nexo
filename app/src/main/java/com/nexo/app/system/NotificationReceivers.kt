package com.nexo.app.system

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The daily reminder alarm went off. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) =
        handleAsync(context) { notifications().onReminderAlarm() }
}

/** The quick-add notification was swiped away; it comes back while the setting is on. */
class QuickAddRepostReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) =
        handleAsync(context) { notifications().refreshQuickAdd() }
}

/** After a reboot, an app update or an exact-alarm permission change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) {
            return
        }
        handleAsync(context) { notifications().restore() }
    }
}
