package com.nexo.app.data.sms

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.core.common.SystemZoneClock
import com.nexo.app.core.common.money.CurrencyCatalog
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.model.Missing
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.SymbolPosition
import com.nexo.app.core.model.missing
import com.nexo.app.core.model.needsDetails
import com.nexo.app.data.database.BookSeeder
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.entity.AccountEntity
import com.nexo.app.data.database.entity.CurrencyEntity
import com.nexo.app.data.database.entity.SmsMerchantRuleEntity
import com.nexo.app.data.database.entity.SmsSourceEntity
import com.nexo.app.data.database.entity.asExternalModel
import com.nexo.app.data.repository.DefaultSmsRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.sms.parser.SmsMessage
import com.nexo.app.testing.fixedClock
import com.nexo.app.testing.referenceDataRepository
import com.nexo.app.testing.smsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Gating, suggestions and stored text, against a seeded in-memory book (main currency LKR). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmsIntakeTest {
    private lateinit var db: NexoDatabase
    private lateinit var refs: ReferenceDataRepository
    private lateinit var intake: DefaultSmsRepository
    private val start = 1_793_700_000_000L
    private val active = DeviceSettings(setupDone = true, smsCaptureStartedAt = start)

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(
                context,
                NexoDatabase::class.java,
            ).allowMainThreadQueries().build()
        BookSeeder(context, db, SystemZoneClock).seedFresh(CurrencyCatalog.find("LKR")!!)
        refs = referenceDataRepository(db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        intake = smsRepository(context, db, refs)
    }

    @After
    fun tearDown() = db.close()

    private fun purchase(
        amount: String = "2,450.00",
        cur: String = "LKR",
        card: String = "4321",
        merchant: String = "GREEN LEAF CAFE Kandy LK",
    ) =
        "Dear Cardholder, Purchase at $merchant for $cur $amount on 03/11/26 07:45 PM has been authorised on your debit card ending #$card."

    private suspend fun send(
        body: String,
        receivedAt: Long = start + 1_000,
        s: DeviceSettings = active,
    ) = intake.receive(
        listOf(IncomingSms(SmsMessage("COMBANK", body, receivedAt), receivedAt)),
        s,
        notify = false,
    )

    private suspend fun only() = db.smsDao().pending().single().asExternalModel()

    @Test
    fun nothingIsCapturedBeforeTheStartPointOrWhileOff() = runBlocking {
        assertEquals(0, send(purchase(), receivedAt = start - 1))
        assertEquals(0, send(purchase(), s = active.copy(smsCaptureEnabled = false)))
        assertEquals(0, send(purchase(), s = active.copy(smsCaptureStartedAt = 0)))
        assertEquals(0, db.smsDao().pendingCount())
        assertEquals(1, send(purchase()))
    }

    @Test
    fun unknownCardNeedsDetails() = runBlocking {
        send(purchase())
        val item = only()
        assertNull(item.accountId)
        assertEquals(setOf(Missing.ACCOUNT, Missing.CATEGORY), item.missing())
    }

    @Test
    fun cardMappingAndLearnedCategoryAreSuggested() = runBlocking {
        db.smsDao().upsertSource(SmsSourceEntity("COMBANK", "4321", "1", 0))
        db.smsDao().upsertMerchantRule(SmsMerchantRuleEntity("green leaf cafe kandy", "6", 0))
        send(purchase())
        val item = only()
        assertEquals("1", item.accountId)
        assertEquals("6", item.categoryId)
        assertFalse(item.needsDetails)
    }

    @Test
    fun bankDefaultAccountCoversUnmappedCards() = runBlocking {
        db.smsDao().upsertSource(SmsSourceEntity("COMBANK", "", "1", 0))
        send(purchase(card = "9999"))
        assertEquals("1", only().accountId)
    }

    @Test
    fun withdrawalGoesToTheCashAccount() = runBlocking {
        send(
            "Withdrawal at MATARA-2 BR MATARA SPLK for LKR 6,000.00 on 03/11/26 07:45 PM from card ending #4321.",
        )
        val item = only()
        assertEquals(SmsKind.TRANSFER_TO_CASH, item.kind)
        assertEquals("11", item.toAccountId)
        assertNull(item.categoryId)
    }

    @Test
    fun foreignPurchaseOnAnAccountInThatCurrencyIsExact() = runBlocking {
        val now = fixedClock.millis()
        db.currencyDao().upsert(
            listOf(
                CurrencyEntity(
                    "USD",
                    "USD",
                    "US Dollar",
                    "$",
                    SymbolPosition.PREFIX,
                    2,
                    330.0,
                    false,
                    true,
                    1,
                    now,
                ),
            ),
        )
        db.accountDao().upsert(
            listOf(
                AccountEntity(
                    "usd",
                    "Dollar card",
                    "1",
                    "USD",
                    9,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        db.smsDao().upsertSource(SmsSourceEntity("COMBANK", "1111", "usd", 0))
        send(purchase(amount = "20.00", cur = "USD", card = "1111"))
        val item = only()
        assertEquals(Certainty.EXACT, item.certainty)
        assertNull(item.feeRoute)

        send(purchase(amount = "5.00", cur = "USD", card = "4321"))
        assertEquals(
            Certainty.ESTIMATED,
            db.smsDao().pending().first {
                it.cardLast4 == "4321"
            }.certainty,
        )
    }

    @Test
    fun storedTextIsRedactedUnlessFullTextIsKept() = runBlocking {
        send(purchase())
        assertFalse(only().text.contains("4321"))
        db.smsDao().deleteAllItems()
        send(purchase(), s = active.copy(smsStoreFullText = true))
        assertTrue(only().text.contains("4321"))
    }

    @Test
    fun approvalCodeIsNeverStored() = runBlocking {
        send(
            "ComBank Digital-Transfer to Other Banks LKR 7,500.00 attempted. Please use code 987654 to approve. Do NOT share this number with anyone.",
            s = active.copy(smsStoreFullText = true),
        )
        val item = only()
        assertEquals(Certainty.UNVERIFIED, item.certainty)
        assertFalse(item.text.contains("987654"))
    }

    @Test
    fun expiredItemsLeaveTheQueue() = runBlocking {
        send(purchase())
        val captured = only().capturedAt
        db.smsDao().expire(before = captured + 1, now = captured + 2)
        assertEquals(0, db.smsDao().pendingCount())
    }
}
