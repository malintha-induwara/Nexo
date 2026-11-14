package com.nexo.app.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.core.notifications.Notifier
import com.nexo.app.testing.MainDispatcherRule
import com.nexo.app.testing.userPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class RecordingNotifier : Notifier {
        val reminders = mutableListOf<Triple<Boolean, Int, Int>>()

        override fun postSmsReview(count: Int) = Unit

        override fun updateQuickAdd(enabled: Boolean) = Unit

        override fun scheduleReminder(enabled: Boolean, hour: Int, minute: Int) {
            reminders += Triple(enabled, hour, minute)
        }

        override fun canPost(): Boolean = true

        override fun canScheduleExactAlarms(): Boolean = true
    }

    @Test
    fun settingTheReminderStoresItAndSchedulesTheAlarm() = runTest {
        val prefs = userPreferencesRepository(ApplicationProvider.getApplicationContext<Context>())
        val notifier = RecordingNotifier()
        val vm = ReminderViewModel(prefs, notifier)

        vm.setReminder(true, 21, 30, setOf(1, 3, 5)).join()

        val s = prefs.getDeviceSettings()
        assertTrue(s.reminderEnabled)
        assertEquals(21 to 30, s.reminderHour to s.reminderMinute)
        assertEquals(setOf(1, 3, 5), s.reminderDays)
        assertEquals(listOf(Triple(true, 21, 30)), notifier.reminders)
    }
}
