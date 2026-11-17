package com.nexo.app.system

import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.notifications.Notifier
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

// Android drops the quick-add notification and the reminder alarm on a reboot, and an alarm
// fires only once, so both are put back from here.
class StandingNotifications @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val refs: ReferenceDataRepository,
    private val notifier: Notifier,
    private val clock: Clock,
) {
    // Only once setup is done.
    suspend fun refreshQuickAdd() {
        if (!prefs.getDeviceSettings().setupDone) return
        notifier.updateQuickAdd(refs.getRefData().settings.quickAddNotification)
    }

    // Reminds on the chosen weekdays, then sets the next alarm.
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

    suspend fun restore() {
        refreshQuickAdd()
        scheduleReminder(prefs.getDeviceSettings())
    }

    private fun scheduleReminder(s: DeviceSettings) =
        notifier.scheduleReminder(s.reminderEnabled, s.reminderHour, s.reminderMinute)
}
