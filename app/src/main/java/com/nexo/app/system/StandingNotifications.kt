package com.nexo.app.system

import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.notifications.Notifier
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Keeps the notifications that live outside the app in line with the settings: the persistent
 * quick-add notification and the daily reminder alarm. Android drops both on a reboot, and an
 * alarm fires only once, so they are put back from here.
 */
class StandingNotifications @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val refs: ReferenceDataRepository,
    private val notifier: Notifier,
    private val clock: Clock,
) {
    /** Shows or removes the quick-add notification to match the setting, once setup is done. */
    suspend fun refreshQuickAdd() {
        if (!prefs.getDeviceSettings().setupDone) return
        notifier.updateQuickAdd(refs.getRefData().settings.quickAddNotification)
    }

    /** The reminder alarm went off: remind on the chosen weekdays, then set the next alarm. */
    suspend fun onReminderAlarm() {
        val s = prefs.getDeviceSettings()
        // DayOfWeek 1 = Monday … 7 = Sunday.
        if (s.setupDone && s.reminderEnabled &&
            LocalDate.now(clock).dayOfWeek.value in s.reminderDays
        ) {
            notifier.postReminder()
        }
        scheduleReminder(s)
    }

    /** After a reboot or an app update, when Android has dropped both. */
    suspend fun restore() {
        refreshQuickAdd()
        scheduleReminder(prefs.getDeviceSettings())
    }

    private fun scheduleReminder(s: DeviceSettings) =
        notifier.scheduleReminder(s.reminderEnabled, s.reminderHour, s.reminderMinute)
}
