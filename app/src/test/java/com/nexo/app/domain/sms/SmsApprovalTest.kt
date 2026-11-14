package com.nexo.app.domain.sms

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.core.common.money.CurrencyCatalog
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.DenyReason
import com.nexo.app.core.model.FeeChoice
import com.nexo.app.core.model.FeeRoute
import com.nexo.app.core.model.SmsItem
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.SmsStatus
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.database.BookSeeder
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.entity.SmsItemEntity
import com.nexo.app.data.database.entity.asEntity
import com.nexo.app.data.database.entity.asExternalModel
import com.nexo.app.data.datastore.DevicePreferencesDataSource
import com.nexo.app.data.repository.DefaultUserPreferencesRepository
import com.nexo.app.data.repository.SmsRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.data.sms.Missing
import com.nexo.app.testing.referenceDataRepository
import com.nexo.app.testing.smsRepository
import com.nexo.app.testing.transactionsRepository
import java.util.UUID
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

/** Approve / Deny / Edit against a seeded LKR book (accounts "1" bank, "11" cash; category "6" Food). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmsApprovalTest {
    private lateinit var db: NexoDatabase
    private lateinit var approveSms: ApproveSmsUseCase
    private lateinit var getFeePlan: GetSmsFeePlanUseCase
    private lateinit var transactions: TransactionsRepository
    private lateinit var sms: SmsRepository
    private val at = 1_793_700_000_000L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(
                context,
                NexoDatabase::class.java,
            ).allowMainThreadQueries().build()
        BookSeeder(context, db).seedFresh(CurrencyCatalog.find("LKR")!!)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val refs = referenceDataRepository(db, scope)
        transactions = transactionsRepository(context, db, refs)
        sms = smsRepository(context, db, refs)
        approveSms =
            ApproveSmsUseCase(
                sms,
                refs,
                transactions,
                GetSmsDraftUseCase(GetSmsLedgerAmountUseCase()),
            )
        getFeePlan = GetSmsFeePlanUseCase(sms, refs)
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
        feeRoute: FeeRoute? = null,
        estimated: Double? = null,
    ): SmsItem {
        val e = SmsItemEntity(
            id = UUID.randomUUID().toString(),
            certainty = certainty,
            kind = kind,
            bank = "COMBANK",
            sender = "COMBANK",
            template = "purchase", amount = amount, currency = currency, merchant = merchant,
            merchantKey = merchant.lowercase(),
            cardLast4 = card,
            payee = payee,
            occurredAt = at,
            smsAt = at,
            capturedAt = at,
            dedupeKey = UUID.randomUUID().toString(),
            text = "Purchase at GREEN LEAF CAFE",
            accountId = accountId,
            toAccountId = toAccountId,
            categoryId = categoryId,
            feeRoute = feeRoute,
            estimatedAmount = estimated,
            updatedAt = at,
        )
        db.smsDao().insert(e)
        return e.asExternalModel()
    }

    @Test
    fun approvingAnExpenseWritesOneRowAndLearns() = runBlocking {
        val i = item()
        val r = approveSms(i.id) as ApproveResult.Done
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
        assertEquals(ApproveResult.Gone, approveSms(i.id))
        assertEquals(1, db.transactionDao().count())
    }

    @Test
    fun incompleteItemsAreNotApproved() = runBlocking {
        val i = item(categoryId = null)
        val r = approveSms(i.id) as ApproveResult.Incomplete
        assertEquals(setOf(Missing.CATEGORY), r.missing)
        assertEquals(0, db.transactionDao().count())
    }

    @Test
    fun withdrawalBecomesATransferToCash() = runBlocking {
        val i =
            item(
                kind = SmsKind.TRANSFER_TO_CASH,
                amount = 6000.0,
                toAccountId = "11",
                categoryId = null,
                merchant = "MATARA-2 BR",
            )
        approveSms(i.id) as ApproveResult.Done
        val rows = db.transactionDao().getAll()
        assertEquals(setOf(TxKind.TRANSFER_OUT, TxKind.TRANSFER_IN), rows.map { it.kind }.toSet())
        assertEquals("1", rows.single { it.kind == TxKind.TRANSFER_OUT }.accountId)
        assertEquals("11", rows.single { it.kind == TxKind.TRANSFER_IN }.accountId)
    }

    @Test
    fun transferFeeIsWrittenWithTheTransfer() = runBlocking {
        val i =
            item(
                kind = SmsKind.TRANSFER_TO_CASH,
                amount = 5000.0,
                toAccountId = "11",
                categoryId = null,
            )
        approveSms(i.id, FeeChoice(30.0)) as ApproveResult.Done
        val fee = db.transactionDao().getAll().single { it.kind == TxKind.EXPENSE }
        assertEquals(30.0, fee.amount, 0.0)
        assertEquals("1", fee.accountId)
    }

    @Test
    fun paymentToAPayeeWithACategoryIsAnExpense() = runBlocking {
        val i =
            item(
                kind = SmsKind.TRANSFER_OUT,
                amount = 1500.0,
                categoryId = "7",
                merchant = "",
                payee = "J Silva",
                card = null,
            )
        val r = approveSms(i.id) as ApproveResult.Done
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
        val denied = sms.deny(listOf(i.id), DenyReason.DIDNT_HAPPEN)
        assertEquals(1, denied.size)
        assertEquals(SmsStatus.DENIED, db.smsDao().item(i.id)!!.status)
        assertEquals(DenyReason.DIDNT_HAPPEN, db.smsDao().item(i.id)!!.denyReason)
        sms.restore(listOf(i.id))
        val back = db.smsDao().item(i.id)!!
        assertEquals(SmsStatus.PENDING, back.status)
        assertNull(back.denyReason)
        assertEquals(0, db.transactionDao().count())
    }

    @Test
    fun restoringAnExpiredItemRestartsItsWindow() = runBlocking {
        val i = item()
        db.smsDao().expire(before = at + 1, now = at + 2)
        sms.restore(listOf(i.id))
        val back = db.smsDao().item(i.id)!!
        assertEquals(SmsStatus.PENDING, back.status)
        assertTrue(back.capturedAt > at)
    }

    @Test
    fun actualAmountSettlesAForeignEstimate() = runBlocking {
        val i = item(amount = 20.0, currency = "USD", certainty = Certainty.ESTIMATED)
        db.smsDao().update(i.copy(estimatedAmount = 6730.0).asEntity())
        sms.setAmount(i.id, 6912.4)
        val edited = db.smsDao().item(i.id)!!
        assertEquals(Certainty.EXACT, edited.certainty)
        assertEquals(6912.4, edited.actualAmount!!, 0.0)
        assertEquals(20.0, edited.amount, 0.0)

        val r = approveSms(i.id) as ApproveResult.Done
        val tx = db.transactionDao().getById(r.transactionId)!!
        assertEquals("LKR", tx.currencyId)
        assertEquals(6912.4, tx.amount, 0.0)
    }

    @Test
    fun unverifiedStaysUnverifiedWhenEdited() = runBlocking {
        val i =
            item(
                kind = SmsKind.TRANSFER_OUT,
                certainty = Certainty.UNVERIFIED,
                categoryId = null,
                card = null,
                merchant = "",
            )
        sms.setAmount(i.id, 3500.0)
        val edited = db.smsDao().item(i.id)!!
        assertEquals(Certainty.UNVERIFIED, edited.certainty)
        assertEquals(3500.0, edited.amount, 0.0)
    }

    @Test
    fun mappingACardFillsEveryPendingItemOnIt() = runBlocking {
        val a = item(accountId = null, card = "1111")
        val b = item(accountId = null, card = "1111", merchant = "Other")
        val c = item(accountId = null, card = "2222")
        sms.mapCard("COMBANK", "1111", "1")
        assertEquals("1", db.smsDao().item(a.id)!!.accountId)
        assertEquals("1", db.smsDao().item(b.id)!!.accountId)
        assertNull(db.smsDao().item(c.id)!!.accountId)
        assertEquals("1", db.smsDao().source("COMBANK", "1111")!!.accountId)
    }

    @Test
    fun feeOnAnExpenseIsALinkedBankFeeRowThatGoesWithItsParent() = runBlocking {
        val i =
            item(
                kind = SmsKind.TRANSFER_OUT,
                amount = 3600.0,
                categoryId = "7",
                card = null,
                merchant = "",
                payee = "J Silva",
                feeRoute = FeeRoute.INTERBANK_TRANSFER,
            )
        val r = approveSms(i.id, FeeChoice(30.0)) as ApproveResult.Done
        val fee = db.transactionDao().getById(r.feeId!!)!!
        assertEquals(TxKind.EXPENSE, fee.kind)
        assertEquals(r.transactionId, fee.feeParentId)
        assertEquals("1", fee.accountId)
        assertEquals(30.0, fee.amount, 0.0)
        val bankFee = db.categoryDao().getById(fee.categoryId!!)!!
        assertEquals("Bank fee", bankFee.name)
        // The category is created once and found again by id, even after a rename.
        db.categoryDao().upsert(listOf(bankFee.copy(name = "Charges")))
        assertEquals(bankFee.id, transactions.bankFeeCategory())

        transactions.delete(listOf(r.transactionId))
        assertEquals(0, db.transactionDao().count())
    }

    @Test
    fun deletingOnlyTheFeeKeepsTheParent() = runBlocking {
        val i = item(feeRoute = FeeRoute.INTERBANK_TRANSFER)
        val r = approveSms(i.id, FeeChoice(25.0)) as ApproveResult.Done
        transactions.delete(listOf(r.feeId!!))
        assertEquals(1, db.transactionDao().count())
        assertTrue(db.transactionDao().getById(r.transactionId) != null)
    }

    @Test
    fun transferFeeUsesTheBankFeeCategory() = runBlocking {
        val i =
            item(
                kind = SmsKind.TRANSFER_TO_CASH,
                amount = 5000.0,
                toAccountId = "11",
                categoryId = null,
                feeRoute = FeeRoute.OTHER_BANK_ATM,
            )
        val r = approveSms(i.id, FeeChoice(30.0)) as ApproveResult.Done
        val fee = db.transactionDao().getById(r.feeId!!)!!
        assertEquals("Bank fee", db.categoryDao().getById(fee.categoryId!!)!!.name)
    }

    @Test
    fun feePlansFollowThePreset() = runBlocking {
        val first = item(feeRoute = FeeRoute.INTERBANK_TRANSFER)
        assertEquals(FeePlan.Ask(FeeRoute.INTERBANK_TRANSFER, null), getFeePlan(first))
        // Within the bank defaults to never asking.
        assertEquals(FeePlan.None, getFeePlan(item(feeRoute = FeeRoute.OWN_BANK_TRANSFER)))

        approveSms(first.id, FeeChoice(30.0))
        assertEquals(
            FeePlan.Ask(FeeRoute.INTERBANK_TRANSFER, 30.0),
            getFeePlan(item(feeRoute = FeeRoute.INTERBANK_TRANSFER)),
        )

        val remembered = item(feeRoute = FeeRoute.INTERBANK_TRANSFER)
        approveSms(remembered.id, FeeChoice(35.0, remember = true))
        assertEquals(
            FeePlan.Auto(FeeRoute.INTERBANK_TRANSFER, 35.0),
            getFeePlan(item(feeRoute = FeeRoute.INTERBANK_TRANSFER)),
        )

        val never = item(feeRoute = FeeRoute.INTERBANK_TRANSFER)
        approveSms(never.id, FeeChoice(null, remember = true))
        assertEquals(FeePlan.None, getFeePlan(item(feeRoute = FeeRoute.INTERBANK_TRANSFER)))
        assertEquals(35.0, db.smsDao().feePreset("1", FeeRoute.INTERBANK_TRANSFER)!!.lastFee!!, 0.0)
    }

    @Test
    fun fxDifferenceIsAskedOnlyAboveTheTolerance() = runBlocking {
        val close =
            item(
                amount = 20.0,
                currency = "USD",
                certainty = Certainty.ESTIMATED,
                feeRoute = FeeRoute.FX_CARD_PURCHASE,
                estimated = 6730.0,
            )
        assertEquals(FeePlan.None, getFeePlan(close))
        sms.setAmount(close.id, 6760.0)
        assertEquals(FeePlan.None, getFeePlan(sms.getItem(close.id)!!))
        sms.setAmount(close.id, 6912.4)
        val plan = getFeePlan(sms.getItem(close.id)!!) as FeePlan.Ask
        assertTrue(plan.fxDifference)
        assertEquals(182.4, plan.prefill!!, 0.001)
    }

    @Test
    fun fxDifferenceRecordedAsAFeeKeepsThePurchaseAtTheEstimate() = runBlocking {
        val i =
            item(
                amount = 20.0,
                currency = "USD",
                certainty = Certainty.ESTIMATED,
                feeRoute = FeeRoute.FX_CARD_PURCHASE,
                estimated = 6730.0,
            )
        sms.setAmount(i.id, 6912.4)
        val r = approveSms(i.id, FeeChoice(182.4)) as ApproveResult.Done
        assertEquals(6730.0, db.transactionDao().getById(r.transactionId)!!.amount, 0.0)
        assertEquals(182.4, db.transactionDao().getById(r.feeId!!)!!.amount, 0.001)
        // A difference never becomes the route's "last fee".
        assertNull(db.smsDao().feePreset("1", FeeRoute.FX_CARD_PURCHASE)!!.lastFee)
    }

    @Test
    fun fxDifferenceIncludedInThePurchaseHasNoFeeRow() = runBlocking {
        val i =
            item(
                amount = 20.0,
                currency = "USD",
                certainty = Certainty.ESTIMATED,
                feeRoute = FeeRoute.FX_CARD_PURCHASE,
                estimated = 6730.0,
            )
        sms.setAmount(i.id, 6912.4)
        val r = approveSms(i.id, FeeChoice(182.4, includeInPurchase = true)) as ApproveResult.Done
        assertNull(r.feeId)
        assertEquals(6912.4, db.transactionDao().getById(r.transactionId)!!.amount, 0.0)
        assertEquals(1, db.transactionDao().count())
    }
}
