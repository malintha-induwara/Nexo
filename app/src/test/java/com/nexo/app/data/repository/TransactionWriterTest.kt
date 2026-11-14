package com.nexo.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.FeeDraft
import com.nexo.app.core.model.TxDraft
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.Seeder
import com.nexo.app.data.file.AppDirs
import com.nexo.app.data.file.PhotoStore
import com.nexo.app.widget.WidgetRefresher
import java.time.LocalDateTime
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransactionWriterTest {
    private lateinit var db: NexoDatabase
    private lateinit var writer: TransactionWriter

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NexoDatabase::class.java).allowMainThreadQueries().build()
        Seeder(context, db).seedFresh(com.nexo.app.core.common.money.CurrencyCatalog.find("USD")!!)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val refs = RefRepository(db, scope)
        writer = TransactionWriter(context, db, refs, PhotoStore(context, AppDirs(context), db, Dispatchers.IO), WidgetRefresher(context, scope))
    }

    @After
    fun tearDown() = db.close()

    private val at = LocalDateTime.of(2024, 5, 31, 23, 59, 59, 999_000_000)

    @Test
    fun transferWithFeeIsCreatedAndDeletedTogether() = runBlocking {
        val id = writer.save(TxDraft(type = EntryType.TRANSFER, accountId = "1", toAccountId = "11", dateTime = at, amount = 100.0, currencyId = "USD", fee = FeeDraft(1.5, "USD")))
        val rows = db.transactionDao().getAll()
        assertEquals(3, rows.size)
        val out = rows.single { it.kind == TxKind.TRANSFER_OUT }
        val inLeg = rows.single { it.kind == TxKind.TRANSFER_IN }
        val fee = rows.single { it.kind == TxKind.EXPENSE }
        assertEquals(id, out.id)
        assertEquals(out.transferGroupId, inLeg.transferGroupId)
        assertEquals(out.feeGroupId, fee.feeGroupId)
        // The fee would cross midnight, so it keeps the transfer's timestamp.
        assertEquals(out.dateTime, fee.dateTime)
        assertEquals("1", fee.accountId)
        writer.delete(listOf(inLeg.id))
        assertEquals(0, db.transactionDao().count())
    }

    @Test
    fun transferConvertedToExpenseDropsPartnerAndFee() = runBlocking {
        val id = writer.save(TxDraft(type = EntryType.TRANSFER, accountId = "1", toAccountId = "11", dateTime = at.minusHours(2), amount = 50.0, currencyId = "USD", fee = FeeDraft(1.0, "USD")))
        writer.save(TxDraft(editingId = id, type = EntryType.EXPENSE, accountId = "1", categoryId = "6", dateTime = at.minusHours(2), amount = 50.0, currencyId = "USD"))
        val rows = db.transactionDao().getAll()
        assertEquals(1, rows.size)
        assertEquals(TxKind.EXPENSE, rows.single().kind)
        assertNull(rows.single().transferGroupId)
    }

    @Test
    fun installmentsSplitAcrossMonths() = runBlocking {
        writer.save(TxDraft(type = EntryType.EXPENSE, accountId = "2", categoryId = "6", dateTime = LocalDateTime.of(2024, 1, 31, 10, 0), amount = 100.0, currencyId = "USD", installments = 3))
        val rows = db.transactionDao().getAll().sortedBy { it.date }
        assertEquals(listOf("2024-01-31", "2024-02-29", "2024-03-31"), rows.map { it.date })
        assertEquals(listOf(33.34, 33.33, 33.33), rows.map { it.amount })
        assertTrue(rows.all { it.installmentCount == 3 && it.installmentGroupId == rows.first().installmentGroupId })
    }
}
