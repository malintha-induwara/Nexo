package com.nexo.app.system

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.testing.FakeNotifier
import com.nexo.app.testing.TestBook
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StandingNotificationsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val clock = Clock.fixed(Instant.parse("2026-11-16T09:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.now(clock).dayOfWeek.value
    private val notifier = FakeNotifier()
    private lateinit var book: TestBook
    private lateinit var notifications: StandingNotifications

    @Before
    fun setUp() {
        book = TestBook(ApplicationProvider.getApplicationContext<Context>(), scope)
        notifications = StandingNotifications(book.prefs, book.refs, notifier, clock)
    }

    @After
    fun tearDown() {
        scope.cancel()
        book.close()
    }

    @Test
    fun quickAddWaitsForSetup() = runBlocking {
        book.prefs.setSetupDone(false)
        notifications.refreshQuickAdd()
        assertTrue(notifier.quickAdd.isEmpty())

        book.prefs.setSetupDone(true)
        notifications.refreshQuickAdd()
        // The seeded book has the quick-add notification off.
        assertEquals(listOf(false), notifier.quickAdd)
    }

    @Test
    fun theReminderShowsOnlyOnChosenDaysAndAlwaysSetsTheNextAlarm() = runBlocking {
        book.prefs.setSetupDone(true)
        book.prefs.setReminder(true, 20, 30, setOf(today))
        notifications.onReminderAlarm()
        assertEquals(1, notifier.reminders)

        book.prefs.setReminder(true, 20, 30, setOf(today % 7 + 1))
        notifications.onReminderAlarm()
        assertEquals(1, notifier.reminders)
        assertEquals(List(2) { Triple(true, 20, 30) }, notifier.reminderAlarms)
    }
}
