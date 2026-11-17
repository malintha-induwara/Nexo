package com.nexo.app.domain.entry

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.SmsStatus
import com.nexo.app.core.model.TxDraft
import com.nexo.app.data.database.entity.SmsItemEntity
import com.nexo.app.data.database.entity.asExternalModel
import com.nexo.app.testing.TestBook
import java.time.LocalDateTime
import java.util.UUID
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

// Seeded LKR book: accounts "1" (bank) and "11" (cash), category "6" (Food).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SaveEntryUseCaseTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var book: TestBook

    @Before
    fun setUp() {
        book = TestBook(ApplicationProvider.getApplicationContext<Context>(), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        book.close()
    }

    private suspend fun expense(note: String, categoryId: String? = "6", amount: Double = 100.0) =
        TxDraft(
            type = EntryType.EXPENSE,
            accountId = "1",
            categoryId = categoryId,
            dateTime = LocalDateTime.of(2026, 11, 10, 12, 0),
            amount = amount,
            currencyId = book.refs.getRefData().mainCurrency.id,
            note = note,
        )

    private suspend fun pendingSms(amount: Double, estimated: Double?) = SmsItemEntity(
        id = UUID.randomUUID().toString(),
        certainty = if (estimated != null) Certainty.ESTIMATED else Certainty.EXACT,
        kind = SmsKind.EXPENSE,
        bank = "COMBANK",
        sender = "COMBANK",
        template = "purchase",
        amount = amount,
        currency = "LKR",
        merchant = "Green Leaf Cafe",
        merchantKey = "green leaf cafe",
        cardLast4 = "4321",
        payee = null,
        occurredAt = AT,
        smsAt = AT,
        capturedAt = AT,
        dedupeKey = UUID.randomUUID().toString(),
        text = "Purchase at GREEN LEAF CAFE",
        accountId = "1",
        toAccountId = null,
        categoryId = "6",
        feeRoute = null,
        estimatedAmount = estimated,
        updatedAt = AT,
    ).also { book.db.smsDao().insert(it) }.asExternalModel()

    @Test
    fun savingFromTheReviewQueueApprovesTheItemAndBooksItsFee() = runBlocking {
        val item = pendingSms(amount = 2400.0, estimated = 2400.0)
        val result = book.saveEntry(
            EntrySave(
                draft = expense("Green Leaf Cafe", amount = 2450.0),
                smsReview = SmsReview(item.id, item, amount = 2450.0, fee = 25.0),
            ),
        ) as SaveEntryResult.Saved

        val after = book.sms.getItem(item.id)!!
        assertEquals(SmsStatus.APPROVED, after.status)
        assertEquals(result.id, after.transactionId)
        // The confirmed amount is recorded as what the bank actually charged.
        assertEquals(2450.0, after.actualAmount!!, 0.0)
        assertEquals(Certainty.EXACT, after.certainty)
        val fee = book.transactions.getRange("2026-11-10", "2026-11-10")
            .single { it.feeParentId == result.id }
        assertEquals(25.0, fee.amount, 0.0)
        assertEquals(book.refs.getRefData().settings.bankFeeCategoryId, fee.categoryId)
    }

    @Test
    fun categorisingAnUnassignedRowCategorisesOthersWithTheSameNote() = runBlocking {
        val first = book.transactions.save(expense("Bus", categoryId = null))
        val second = book.transactions.save(expense("Bus", categoryId = null))
        val other = book.transactions.save(expense("Taxi", categoryId = null))
        val original = book.transactions.getTransaction(first)!!

        val result = book.saveEntry(
            EntrySave(
                draft = expense("Bus").copy(editingId = first),
                uncategorizedOriginal = original,
            ),
        ) as SaveEntryResult.Saved

        assertEquals(1, result.sameNoteAssigned)
        assertEquals("6", book.transactions.getTransaction(second)!!.categoryId)
        assertEquals(null, book.transactions.getTransaction(other)!!.categoryId)
    }

    @Test
    fun recentNotesAreSuggested() = runBlocking {
        book.transactions.save(expense("Coffee").copy(dateTime = LocalDateTime.now(book.clock)))
        assertTrue(book.getNoteSuggestions().any { it.note == "Coffee" })
    }

    private companion object {
        const val AT = 1_793_700_000_000L
    }
}
