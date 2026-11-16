package com.nexo.app.data.sms

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.core.common.money.CurrencyCatalog
import com.nexo.app.core.model.IncomingSms
import com.nexo.app.core.model.SmsMessage
import com.nexo.app.data.database.BookSeeder
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.entity.asExternalModel
import com.nexo.app.data.repository.DefaultSmsInboxRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import com.nexo.app.testing.fxRatesRepository
import com.nexo.app.testing.referenceDataRepository
import com.nexo.app.testing.smsRepository
import com.nexo.app.testing.transactionsRepository
import com.nexo.app.testing.userPreferencesRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The inbox check: what the broadcast missed, and messages Android released hours after they arrived. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmsCatchUpTest {
    private class MovingClock(var now: Long) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(now)
    }

    private class FakeInbox(context: Context, val messages: List<IncomingSms>) :
        SmsInboxDataSource(context, Dispatchers.IO) {
        override suspend fun read(after: Long, from: Long, until: Long, limit: Int) =
            messages.filter {
                it.receivedAt > after && it.receivedAt >= from &&
                    it.receivedAt < until
            }.sortedBy { it.receivedAt }.take(limit)
    }

    private val minute = 60_000L
    private val hour = 60 * minute
    private val t0 = 1_794_700_000_000L
    private val clock = MovingClock(t0)
    private val messages = ArrayList<IncomingSms>()
    private lateinit var db: NexoDatabase
    private lateinit var prefs: UserPreferencesRepository
    private lateinit var inbox: DefaultSmsInboxRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
        )
        db =
            Room.inMemoryDatabaseBuilder(
                context,
                NexoDatabase::class.java,
            ).allowMainThreadQueries().build()
        BookSeeder(context, db, clock).seedFresh(CurrencyCatalog.find("LKR")!!)
        val refs =
            referenceDataRepository(db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        prefs = userPreferencesRepository(context)
        val fx = fxRatesRepository(context, db, refs, prefs, clock)
        inbox =
            DefaultSmsInboxRepository(
                context,
                prefs,
                refs,
                smsRepository(context, db, refs, prefs, fx, clock),
                fx,
                transactionsRepository(context, db, refs, clock),
                FakeInbox(context, messages),
                Dispatchers.IO,
                clock,
            )
        prefs.setSetupDone(true)
        prefs.setSmsCaptureEnabled(true)
        prefs.markSmsCaptureStopped(t0 - 1)
        // Capture starts at t0.
        inbox.refresh()
    }

    @After
    fun tearDown() = runBlocking {
        prefs.markSmsCaptureStopped(clock.now)
        prefs.setSetupDone(false)
        db.close()
    }

    private fun arrive(body: String, at: Long, sender: String = "COMBANK") {
        messages += IncomingSms(SmsMessage(sender, body, at - 1_000), at)
    }

    private fun code(amount: String) =
        "ComBank Digital-Transfer within ComBank LKR $amount attempted. Please use code 987654 to approve. Do NOT share this number with anyone."

    private suspend fun check(at: Long) {
        clock.now = at
        inbox.refresh()
    }

    private suspend fun pending() = db.smsDao().pending().map { it.asExternalModel() }

    @Test
    fun aMessageReleasedLaterIsCapturedOnce() = runBlocking {
        // A newer message is read while the code message is still kept back.
        arrive("Your parcel is out for delivery.", t0 + 10 * minute, sender = "Courier")
        check(t0 + 20 * minute)
        assertEquals(0, pending().size)

        // It shows up with its original arrival time, older than what was already read.
        arrive(code("2,341.00"), t0 + minute)
        check(t0 + 4 * hour)
        assertEquals(2341.0, pending().single().amount, 0.0)

        check(t0 + 5 * hour)
        check(t0 + 6 * hour)
        assertEquals(1, pending().single().evidenceCount)
    }

    @Test
    fun onlyTheLastDayIsReadAgain() = runBlocking {
        arrive("Your parcel is out for delivery.", t0 + 10 * minute, sender = "Courier")
        check(t0 + 20 * minute)

        arrive(code("2,341.00"), t0 + minute)
        check(t0 + 30 * hour)
        assertEquals(0, pending().size)
    }

    @Test
    fun aLongGapIsStillReadFromWhereTheLastCheckStopped() = runBlocking {
        arrive(code("2,341.00"), t0 + hour)
        arrive(code("900.00"), t0 + 40 * hour)
        check(t0 + 72 * hour)
        assertEquals(2, pending().size)
    }
}
