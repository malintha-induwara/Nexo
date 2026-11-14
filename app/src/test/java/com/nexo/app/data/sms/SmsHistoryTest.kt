package com.nexo.app.data.sms

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.Seeder
import com.nexo.app.data.database.entity.TransactionEntity
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.common.money.CurrencyCatalog
import com.nexo.app.data.datastore.DevicePrefs
import com.nexo.app.data.repository.RefRepository
import com.nexo.app.data.sms.InboxReader
import com.nexo.app.data.sms.IncomingSms
import com.nexo.app.data.sms.SmsSuggester
import com.nexo.app.data.database.entity.SmsSourceEntity
import com.nexo.app.data.network.BankRatesClient
import com.nexo.app.data.repository.FxRepository
import com.nexo.app.data.repository.SmsHistory
import com.nexo.app.data.sms.parser.BodyDate
import com.nexo.app.data.sms.parser.SmsMessage
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
import java.time.LocalDateTime

/** Picker scan over a fake inbox of synthetic messages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmsHistoryTest {
    private lateinit var db: NexoDatabase
    private lateinit var history: SmsHistory
    private val inbox = ArrayList<IncomingSms>()
    private fun at(day: Int, h: Int, m: Int) = LocalDateTime.of(2026, 11, day, h, m).atZone(BodyDate.ZONE).toInstant().toEpochMilli()

    private class FakeInbox(context: Context, val messages: List<IncomingSms>) : InboxReader(context) {
        override fun read(after: Long, from: Long, until: Long, limit: Int) =
            messages.filter { it.receivedAt > after && it.receivedAt >= from && it.receivedAt < until }.sortedBy { it.receivedAt }.take(limit)
    }

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NexoDatabase::class.java).allowMainThreadQueries().build()
        Seeder(context, db).seedFresh(CurrencyCatalog.find("LKR")!!)
        val refs = RefRepository(db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        val prefs = DevicePrefs(context)
        val fx = FxRepository(db, refs, prefs, BankRatesClient())
        history = SmsHistory(db, prefs, refs, FakeInbox(context, inbox), SmsSuggester(db, refs, fx), fx)
        db.smsDao().upsertSource(SmsSourceEntity("COMBANK", "4321", "1", 0))
    }

    @After
    fun tearDown() = db.close()

    private fun sms(body: String, at: Long) {
        inbox += IncomingSms(SmsMessage("COMBANK", body, at), at)
    }

    private fun purchase(merchant: String, amount: String, date: String, cur: String = "LKR") =
        "Dear Cardholder, Purchase at $merchant for $cur $amount on $date has been authorised on your debit card ending #4321."

    @Test
    fun scanParsesCorrelatesAndFlags() = runBlocking {
        sms("Your OTP at Merchant 'Lakeside University Online Payments' for LKR 9,000.00 is 987654", at(3, 19, 0))
        sms(purchase("HTTPS://WWW.PAY.LAKE 0000000 GB", "9,000.00", "03/11/26 07:01 PM"), at(3, 19, 1))
        sms(purchase("Mega*Store Singapore SG", "1.00", "04/11/26 10:00 AM", cur = "USD"), at(4, 10, 0))
        sms("YOUR TRANSACTION AT Mega*Store ON YOUR CARD ENDING WITH 4321 HAS BEEN CANCELLED.", at(4, 11, 0))
        sms(purchase("CORNER SHOP Matara LK", "750.00", "05/11/26 08:00 AM"), at(5, 8, 0))
        sms("Dear Customer, your attempt to login to ComBank Digital failed.", at(5, 9, 0))
        // Already in the ledger: same account and amount, a day apart.
        db.transactionDao().insertAll(
            listOf(TransactionEntity("t", TxKind.EXPENSE, "1", dateTime = at(6, 9, 0), date = "2026-11-06", amount = 750.0, amountMain = 750.0, amountAccount = 750.0, currencyId = "LKR", createdAt = 0, updatedAt = 0)),
        )

        val rows = history.scan(at(1, 0, 0), at(10, 0, 0))
        assertEquals(3, rows.size)
        val lake = rows.single { it.item.merchantRaw.startsWith("HTTPS") }
        assertEquals("Lakeside University Online Payments", lake.item.merchant)
        assertEquals("1", lake.item.accountId)
        assertTrue(rows.single { it.item.merchant.startsWith("Mega") }.cancelled)
        assertTrue(rows.single { it.item.merchant.startsWith("CORNER") }.possiblyAdded)
        assertFalse(lake.possiblyAdded)
        // Newest first.
        assertEquals(listOf("CORNER SHOP Matara", "Mega Store Singapore", "Lakeside University Online Payments"), rows.map { it.item.merchant })
    }

    @Test
    fun keptRowsAreNotOfferedAgain() = runBlocking {
        sms(purchase("CORNER SHOP Matara LK", "750.00", "05/11/26 08:00 AM"), at(5, 8, 0))
        val first = history.scan(at(1, 0, 0), at(10, 0, 0))
        assertEquals(1, history.keep(first.map { it.item }).size)
        assertEquals(1, db.smsDao().pendingCount())
        assertTrue(history.scan(at(1, 0, 0), at(10, 0, 0)).isEmpty())
    }

    @Test
    fun scanStopsAtTheRangeEnd() = runBlocking {
        sms(purchase("CORNER SHOP Matara LK", "750.00", "05/11/26 08:00 AM"), at(5, 8, 0))
        sms(purchase("LATE CAFE Galle LK", "300.00", "09/11/26 08:00 AM"), at(9, 8, 0))
        assertEquals(listOf("CORNER SHOP Matara"), history.scan(at(1, 0, 0), at(8, 0, 0)).map { it.item.merchant })
    }
}
