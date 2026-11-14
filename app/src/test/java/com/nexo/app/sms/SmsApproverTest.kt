package com.nexo.app.sms

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.database.Seeder
import com.nexo.app.core.files.AppDirs
import com.nexo.app.core.files.PhotoStore
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.money.CurrencyCatalog
import com.nexo.app.data.RefRepository
import com.nexo.app.data.TransactionWriter
import com.nexo.app.sms.approve.ApproveResult
import com.nexo.app.sms.approve.SmsApprover
import com.nexo.app.sms.capture.SmsSuggester
import com.nexo.app.sms.data.SmsItemEntity
import com.nexo.app.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** NQ-003 Approve / Deny / Edit against a seeded LKR book (accounts "1" bank, "11" cash; category "6" Food). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmsApproverTest {
    private lateinit var db: NexoDatabase
    private lateinit var approver: SmsApprover
    private val at = 1_793_700_000_000L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NexoDatabase::class.java).allowMainThreadQueries().build()
        Seeder(context, db).seedFresh(CurrencyCatalog.find("LKR")!!)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val refs = RefRepository(db, scope)
        val writer = TransactionWriter(context, db, refs, PhotoStore(context, AppDirs(context), db), WidgetRefresher(context, scope))
        approver = SmsApprover(db, refs, writer, SmsSuggester(db, refs))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun item(
        kind: SmsKind = SmsKind.EXPENSE,
        amount: Double = 2450.0,
        currency: String = "LKR",
        accountId: String? = "1",
        toAccountId: String? = null,
        categoryId: String? = "6",
        certainty: Certainty = Certainty.EXACT,
        card: String? = "4321",
        merchant: String = "Green Leaf Cafe",
        payee: String? = null,
    ): SmsItemEntity {
        val e = SmsItemEntity(
            id = UUID.randomUUID().toString(), certainty = certainty, kind = kind, bank = "COMBANK", sender = "COMBANK",
            template = "purchase", amount = amount, currency = currency, merchant = merchant,
            merchantKey = merchant.lowercase(), cardLast4 = card, payee = payee, occurredAt = at, smsAt = at, capturedAt = at,
            dedupeKey = UUID.randomUUID().toString(), text = "Purchase at GREEN LEAF CAFE", accountId = accountId,
            toAccountId = toAccountId, categoryId = categoryId, updatedAt = at,
        )
        db.smsDao().insert(e)
        return e
    }

    @Test
    fun approvingAnExpenseWritesOneRowAndLearns() = runBlocking {
        val i = item()
        val r = approver.approve(i.id) as ApproveResult.Done
        val tx = db.transactionDao().getById(r.transactionId)!!
        assertEquals(TxKind.EXPENSE, tx.kind)
        assertEquals("1", tx.accountId)
        assertEquals("6", tx.categoryId)
        assertEquals(2450.0, tx.amount, 0.0)
        assertEquals("Green Leaf Cafe", tx.note)
        assertEquals("Purchase at GREEN LEAF CAFE", tx.sourceText)

        val after = db.smsDao().item(i.id)!!
        assertEquals(SmsStatus.APPROVED, after.status)
        assertEquals(r.transactionId, after.transactionId)
        assertEquals("1", db.smsDao().source("COMBANK", "4321")!!.accountId)
        assertEquals("6", db.smsDao().merchantRule("green leaf cafe")!!.categoryId)
        // Approving twice does nothing.
        assertEquals(ApproveResult.Gone, approver.approve(i.id))
        assertEquals(1, db.transactionDao().count())
    }

    @Test
    fun incompleteItemsAreNotApproved() = runBlocking {
        val i = item(categoryId = null)
        val r = approver.approve(i.id) as ApproveResult.Incomplete
        assertEquals(setOf(Missing.CATEGORY), r.missing)
        assertEquals(0, db.transactionDao().count())
    }

    @Test
    fun withdrawalBecomesATransferToCash() = runBlocking {
        val i = item(kind = SmsKind.TRANSFER_TO_CASH, amount = 6000.0, toAccountId = "11", categoryId = null, merchant = "MATARA-2 BR")
        approver.approve(i.id) as ApproveResult.Done
        val rows = db.transactionDao().getAll()
        assertEquals(setOf(TxKind.TRANSFER_OUT, TxKind.TRANSFER_IN), rows.map { it.kind }.toSet())
        assertEquals("1", rows.single { it.kind == TxKind.TRANSFER_OUT }.accountId)
        assertEquals("11", rows.single { it.kind == TxKind.TRANSFER_IN }.accountId)
    }

    @Test
    fun transferFeeIsWrittenWithTheTransfer() = runBlocking {
        val i = item(kind = SmsKind.TRANSFER_TO_CASH, amount = 5000.0, toAccountId = "11", categoryId = null)
        approver.approve(i.id, fee = 30.0) as ApproveResult.Done
        val fee = db.transactionDao().getAll().single { it.kind == TxKind.EXPENSE }
        assertEquals(30.0, fee.amount, 0.0)
        assertEquals("1", fee.accountId)
    }

    @Test
    fun paymentToAPayeeWithACategoryIsAnExpense() = runBlocking {
        val i = item(kind = SmsKind.TRANSFER_OUT, amount = 1500.0, categoryId = "7", merchant = "", payee = "J Silva", card = null)
        val r = approver.approve(i.id) as ApproveResult.Done
        val tx = db.transactionDao().getById(r.transactionId)!!
        assertEquals(TxKind.EXPENSE, tx.kind)
        assertEquals("J Silva", tx.note)
        assertEquals("7", db.smsDao().merchantRule("j silva")!!.categoryId)
        // No card: the account becomes the bank's default.
        assertEquals("1", db.smsDao().source("COMBANK", "")!!.accountId)
    }

    @Test
    fun denyAndRestore() = runBlocking {
        val i = item()
        val denied = approver.deny(listOf(i.id), DenyReason.DIDNT_HAPPEN)
        assertEquals(1, denied.size)
        assertEquals(SmsStatus.DENIED, db.smsDao().item(i.id)!!.status)
        assertEquals(DenyReason.DIDNT_HAPPEN, db.smsDao().item(i.id)!!.denyReason)
        approver.restore(listOf(i.id))
        val back = db.smsDao().item(i.id)!!
        assertEquals(SmsStatus.PENDING, back.status)
        assertNull(back.denyReason)
        assertEquals(0, db.transactionDao().count())
    }

    @Test
    fun restoringAnExpiredItemRestartsItsWindow() = runBlocking {
        val i = item()
        db.smsDao().expire(before = at + 1, now = at + 2)
        approver.restore(listOf(i.id))
        val back = db.smsDao().item(i.id)!!
        assertEquals(SmsStatus.PENDING, back.status)
        assertTrue(back.capturedAt > at)
    }

    @Test
    fun actualAmountSettlesAForeignEstimate() = runBlocking {
        val i = item(amount = 20.0, currency = "USD", certainty = Certainty.ESTIMATED)
        db.smsDao().update(i.copy(estimatedAmount = 6730.0))
        approver.setAmount(i.id, 6912.4)
        val edited = db.smsDao().item(i.id)!!
        assertEquals(Certainty.EXACT, edited.certainty)
        assertEquals(6912.4, edited.actualAmount!!, 0.0)
        assertEquals(20.0, edited.amount, 0.0)

        val r = approver.approve(i.id) as ApproveResult.Done
        val tx = db.transactionDao().getById(r.transactionId)!!
        assertEquals("LKR", tx.currencyId)
        assertEquals(6912.4, tx.amount, 0.0)
    }

    @Test
    fun unverifiedStaysUnverifiedWhenEdited() = runBlocking {
        val i = item(kind = SmsKind.TRANSFER_OUT, certainty = Certainty.UNVERIFIED, categoryId = null, card = null, merchant = "")
        approver.setAmount(i.id, 3500.0)
        val edited = db.smsDao().item(i.id)!!
        assertEquals(Certainty.UNVERIFIED, edited.certainty)
        assertEquals(3500.0, edited.amount, 0.0)
    }

    @Test
    fun mappingACardFillsEveryPendingItemOnIt() = runBlocking {
        val a = item(accountId = null, card = "1111")
        val b = item(accountId = null, card = "1111", merchant = "Other")
        val c = item(accountId = null, card = "2222")
        approver.mapCard("COMBANK", "1111", "1")
        assertEquals("1", db.smsDao().item(a.id)!!.accountId)
        assertEquals("1", db.smsDao().item(b.id)!!.accountId)
        assertNull(db.smsDao().item(c.id)!!.accountId)
        assertEquals("1", db.smsDao().source("COMBANK", "1111")!!.accountId)
    }
}
