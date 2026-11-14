package com.nexo.app.ui.entry

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.RepeatRule
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.TxDraft
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.database.entity.SmsItemEntity
import com.nexo.app.data.file.AppDirs
import com.nexo.app.testing.TestBook
import com.nexo.app.testing.fixedClock
import com.nexo.app.testing.fixedToday
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** One opening per mode; the seeded LKR book has accounts "1" (bank) and "11" (cash) and category "6" (Food). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EntryFormLoaderTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var book: TestBook
    private val texts = EntryTexts(paymentNote = "Card payment")
    private val lastWeek = LocalDate.of(2026, 11, 7)

    @Before
    fun setUp() {
        book =
            TestBook(
                ApplicationProvider.getApplicationContext<Context>(),
                scope,
                clock = fixedClock,
            )
    }

    @After
    fun tearDown() {
        scope.cancel()
        book.close()
    }

    private suspend fun load(key: EntryKey) =
        book.entryLoader.load(key, book.refs.getRefData(), texts)

    private suspend fun saveLunch(): String = book.transactions.save(
        TxDraft(
            type = EntryType.EXPENSE,
            accountId = "11",
            categoryId = "6",
            dateTime = lastWeek.atTime(13, 0),
            amount = 1200.0,
            currencyId = "LKR",
            note = "Lunch",
        ),
    )

    @Test
    fun editingOpensTheStoredRowForViewing() = runBlocking {
        val id = saveLunch()
        val loaded = load(EntryKey(mode = EntryMode.EDIT, txId = id))
        assertEquals(id, loaded.original?.id)
        val form = loaded.form
        assertTrue(form.viewing)
        assertEquals(EntryMode.EDIT, form.mode)
        assertEquals(id, form.originalId)
        assertEquals(lastWeek, form.date)
        assertEquals(1200.0, form.amount)
        assertEquals("Lunch", form.note)
    }

    @Test
    fun aCopyWithTodayIsANewEntryDatedToday() = runBlocking {
        val id = saveLunch()
        val loaded = load(EntryKey(mode = EntryMode.COPY, txId = id, copyWithToday = true))
        assertNull(loaded.original)
        assertNull(loaded.form.originalId)
        assertEquals(EntryMode.NEW, loaded.form.mode)
        assertEquals(fixedToday, loaded.form.date)
        assertEquals("Lunch", loaded.form.note)
    }

    @Test
    fun aBookmarkFillsTheFormForToday() = runBlocking {
        book.favorites.addFromTransaction(book.transactions.getTransaction(saveLunch())!!)
        val favorite = book.favorites.getFavoritesStream().first().single()
        val form = load(EntryKey(mode = EntryMode.FAVORITE, favoriteId = favorite.id)).form
        assertEquals(EntryMode.FAVORITE, form.mode)
        assertEquals(fixedToday, form.date)
        assertEquals("11", form.accountId)
        assertEquals("6", form.categoryId)
        assertEquals(1200.0, form.amount)
    }

    @Test
    fun aRepeatRuleOpensOnItsNextDate() = runBlocking {
        val rule = RepeatRule(
            id = "rent",
            kind = TxKind.EXPENSE,
            accountId = "1",
            categoryId = "6",
            currencyId = "LKR",
            amount = 50_000.0,
            note = "Rent",
            repeatType = RepeatType.MONTHLY,
            nextDate = "2026-12-01",
            anchorDay = 1,
            createdAt = 0,
            updatedAt = 0,
        )
        book.repeats.save(rule)
        val loaded = load(EntryKey(mode = EntryMode.REPEAT_EDIT, repeatRuleId = "rent"))
        assertEquals("rent", loaded.originalRule?.id)
        assertEquals(LocalDate.of(2026, 12, 1), loaded.form.date)
        assertEquals(RepeatType.MONTHLY, loaded.form.repeat)
        assertEquals(EntryType.EXPENSE, loaded.form.type)
    }

    @Test
    fun anSmsItemOpensWithWhatTheMessageSaid() = runBlocking {
        val at = fixedClock.millis()
        val item = SmsItemEntity(
            id = "sms1",
            certainty = Certainty.EXACT,
            kind = SmsKind.EXPENSE,
            bank = "COMBANK",
            sender = "COMBANK",
            template = "purchase",
            amount = 2450.0,
            currency = "LKR",
            merchant = "Green Leaf Cafe",
            merchantKey = "green leaf cafe",
            cardLast4 = "4321",
            occurredAt = at,
            smsAt = at,
            capturedAt = at,
            dedupeKey = "sms1",
            text = "Purchase at GREEN LEAF CAFE",
            accountId = "1",
            categoryId = "6",
            updatedAt = at,
        )
        book.db.smsDao().insert(item)
        val loaded = load(EntryKey(mode = EntryMode.SMS, smsItemId = "sms1"))
        assertEquals("sms1", loaded.smsItem?.id)
        assertEquals(EntryMode.SMS, loaded.form.mode)
        assertEquals("Green Leaf Cafe", loaded.form.note)
        assertEquals(2450.0, loaded.form.amount)
        assertEquals("1", loaded.form.accountId)
        assertEquals("Purchase at GREEN LEAF CAFE", loaded.form.sourceText)
    }

    @Test
    fun anImportRowOpensWithItsFields() = runBlocking {
        val ref = book.refs.getRefData()
        val cash = ref.accountById.getValue("11").name
        val food = ref.categoryById.getValue("6").name
        File(AppDirs(book.context).imports, "rows.tsv").writeText(
            "Date\tAccount\tCategory\tSubcategory\tNote\tAmount\tType\n" +
                "2026-11-07 13:00\t$cash\t$food\t\tLunch\t1200\texpense\n",
        )
        val form = load(
            EntryKey(
                mode = EntryMode.IMPORT_FIX,
                importFile = "rows.tsv",
                importLine = 0,
                importOrder = "YMD",
            ),
        ).form
        assertEquals(EntryMode.IMPORT_FIX, form.mode)
        assertEquals(lastWeek, form.date)
        assertEquals("11", form.accountId)
        assertEquals("6", form.categoryId)
        assertEquals(1200.0, form.amount)
        assertEquals("Lunch", form.note)
    }

    @Test
    fun aMissingRowFallsBackToANewEntry() = runBlocking {
        val loaded = load(EntryKey(mode = EntryMode.EDIT, txId = "gone"))
        assertNull(loaded.original)
        assertNotNull(loaded.form)
        assertEquals(EntryMode.EDIT, loaded.form.mode)
        assertEquals(fixedToday, loaded.form.date)
    }
}
