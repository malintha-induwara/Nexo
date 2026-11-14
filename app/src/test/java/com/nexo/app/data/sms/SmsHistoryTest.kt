package com.nexo.app.data.sms

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.core.common.SystemZoneClock
import com.nexo.app.core.common.money.CurrencyCatalog
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.database.BookSeeder
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.entity.SmsSourceEntity
import com.nexo.app.data.database.entity.TransactionEntity
import com.nexo.app.data.datastore.DevicePreferencesDataSource
import com.nexo.app.data.repository.DefaultSmsInboxRepository
import com.nexo.app.data.repository.DefaultSmsRepository
import com.nexo.app.data.repository.DefaultUserPreferencesRepository
import com.nexo.app.data.sms.parser.BodyDate
import com.nexo.app.data.sms.parser.SmsMessage
import com.nexo.app.testing.fxRatesRepository
import com.nexo.app.testing.referenceDataRepository
import com.nexo.app.testing.smsRepository
import com.nexo.app.testing.transactionsRepository
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Picker scan over a fake inbox of synthetic messages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmsHistoryTest {
    private lateinit var db: NexoDatabase
    private lateinit var history: DefaultSmsInboxRepository
    private lateinit var sms: DefaultSmsRepository
    private val inbox = ArrayList<IncomingSms>()
    private fun at(day: Int, h: Int, m: Int) =
        LocalDateTime.of(2026, 11, day, h, m).atZone(BodyDate.ZONE).toInstant().toEpochMilli()

    private class FakeInbox(context: Context, val messages: List<IncomingSms>) :
        SmsInboxDataSource(context, Dispatchers.IO) {
        override suspend fun read(after: Long, from: Long, until: Long, limit: Int) =
            messages.filter {
                it.receivedAt > after && it.receivedAt >= from &&
                    it.receivedAt < until
            }.sortedBy { it.receivedAt }.take(limit)
    }

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(
                context,
                NexoDatabase::class.java,
            ).allowMainThreadQueries().build()
        BookSeeder(context, db, SystemZoneClock).seedFresh(CurrencyCatalog.find("LKR")!!)
        val refs =
            referenceDataRepository(db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        val prefs = DefaultUserPreferencesRepository(DevicePreferencesDataSource(context))
        val fx = fxRatesRepository(context, db, refs, prefs)
        sms = smsRepository(context, db, refs, prefs, fx)
        history =
            DefaultSmsInboxRepository(
                context,
                prefs,
                refs,
                sms,
                fx,
                transactionsRepository(context, db, refs),
                FakeInbox(context, inbox),
                Dispatchers.IO,
                SystemZoneClock,
            )
        db.smsDao().upsertSource(SmsSourceEntity("COMBANK", "4321", "1", 0))
    }

    @After
    fun tearDown() = db.close()

    private fun receive(body: String, at: Long) {
        inbox += IncomingSms(SmsMessage("COMBANK", body, at), at)
    }

    private fun purchase(merchant: String, amount: String, date: String, cur: String = "LKR") =
        "Dear Cardholder, Purchase at $merchant for $cur $amount on $date has been authorised on your debit card ending #4321."

    @Test
    fun scanParsesCorrelatesAndFlags() = runBlocking {
        receive(
            "Your OTP at Merchant 'Lakeside University Online Payments' for LKR 9,000.00 is 987654",
            at(3, 19, 0),
        )
        receive(
            purchase("HTTPS://WWW.PAY.LAKE 0000000 GB", "9,000.00", "03/11/26 07:01 PM"),
            at(3, 19, 1),
        )
        receive(
            purchase("Mega*Store Singapore SG", "1.00", "04/11/26 10:00 AM", cur = "USD"),
            at(4, 10, 0),
        )
        receive(
            "YOUR TRANSACTION AT Mega*Store ON YOUR CARD ENDING WITH 4321 HAS BEEN CANCELLED.",
            at(4, 11, 0),
        )
        receive(purchase("CORNER SHOP Matara LK", "750.00", "05/11/26 08:00 AM"), at(5, 8, 0))
        receive("Dear Customer, your attempt to login to ComBank Digital failed.", at(5, 9, 0))
        // Already in the ledger: same account and amount, a day apart.
        db.transactionDao().insertAll(
            listOf(
                TransactionEntity(
                    "t", TxKind.EXPENSE, "1",
                    dateTime = at(6, 9, 0),
                    date = "2026-11-06",
                    amount = 750.0,
                    amountMain = 750.0,
                    amountAccount = 750.0,
                    currencyId = "LKR",
                    createdAt = 0,
                    updatedAt = 0,
                ),
            ),
        )

        val rows = history.scanHistory(at(1, 0, 0), at(10, 0, 0))
        assertEquals(3, rows.size)
        val lake = rows.single { it.item.merchantRaw.startsWith("HTTPS") }
        assertEquals("Lakeside University Online Payments", lake.item.merchant)
        assertEquals("1", lake.item.accountId)
        assertTrue(rows.single { it.item.merchant.startsWith("Mega") }.cancelled)
        assertTrue(rows.single { it.item.merchant.startsWith("CORNER") }.possiblyAdded)
        assertFalse(lake.possiblyAdded)
        // Newest first.
        assertEquals(
            listOf(
                "CORNER SHOP Matara",
                "Mega Store Singapore",
                "Lakeside University Online Payments",
            ),
            rows.map {
                it.item.merchant
            },
        )
    }

    @Test
    fun keptRowsAreNotOfferedAgain() = runBlocking {
        receive(purchase("CORNER SHOP Matara LK", "750.00", "05/11/26 08:00 AM"), at(5, 8, 0))
        val first = history.scanHistory(at(1, 0, 0), at(10, 0, 0))
        assertEquals(1, sms.keep(first.map { it.item }).size)
        assertEquals(1, db.smsDao().pendingCount())
        assertTrue(history.scanHistory(at(1, 0, 0), at(10, 0, 0)).isEmpty())
    }

    @Test
    fun scanStopsAtTheRangeEnd() = runBlocking {
        receive(purchase("CORNER SHOP Matara LK", "750.00", "05/11/26 08:00 AM"), at(5, 8, 0))
        receive(purchase("LATE CAFE Galle LK", "300.00", "09/11/26 08:00 AM"), at(9, 8, 0))
        assertEquals(
            listOf("CORNER SHOP Matara"),
            history.scanHistory(at(1, 0, 0), at(8, 0, 0)).map {
                it.item.merchant
            },
        )
    }
}
