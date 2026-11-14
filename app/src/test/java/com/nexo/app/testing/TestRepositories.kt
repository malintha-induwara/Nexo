package com.nexo.app.testing

import android.content.Context
import androidx.room.Room
import com.nexo.app.core.common.money.CurrencyCatalog
import com.nexo.app.core.notifications.Notifier
import com.nexo.app.data.database.BookSeeder
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.datastore.DevicePreferencesDataSource
import com.nexo.app.data.file.AppDirs
import com.nexo.app.data.file.PhotoFileDataSource
import com.nexo.app.data.network.FxRatesNetworkDataSource
import com.nexo.app.data.receipt.ReceiptTextDataSource
import com.nexo.app.data.repository.DefaultAccountsRepository
import com.nexo.app.data.repository.DefaultBookSettingsRepository
import com.nexo.app.data.repository.DefaultBudgetsRepository
import com.nexo.app.data.repository.DefaultFavoritesRepository
import com.nexo.app.data.repository.DefaultFxRatesRepository
import com.nexo.app.data.repository.DefaultMemosRepository
import com.nexo.app.data.repository.DefaultPhotosRepository
import com.nexo.app.data.repository.DefaultReceiptRepository
import com.nexo.app.data.repository.DefaultReferenceDataRepository
import com.nexo.app.data.repository.DefaultRepeatRulesRepository
import com.nexo.app.data.repository.DefaultSmsInboxRepository
import com.nexo.app.data.repository.DefaultSmsRepository
import com.nexo.app.data.repository.DefaultTagsRepository
import com.nexo.app.data.repository.DefaultTransactionImportRepository
import com.nexo.app.data.repository.DefaultTransactionsRepository
import com.nexo.app.data.repository.DefaultUserPreferencesRepository
import com.nexo.app.data.repository.FxRatesRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import com.nexo.app.data.sms.SmsInboxDataSource
import com.nexo.app.data.sms.SmsSuggester
import com.nexo.app.domain.sms.ApproveSmsUseCase
import com.nexo.app.domain.sms.GetSmsDraftUseCase
import com.nexo.app.domain.sms.GetSmsFeePlanUseCase
import com.nexo.app.domain.sms.GetSmsLedgerAmountUseCase
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Real repositories over an in-memory [NexoDatabase], wired the way Hilt wires them. */
fun referenceDataRepository(db: NexoDatabase, scope: CoroutineScope): ReferenceDataRepository =
    DefaultReferenceDataRepository(
        db.accountGroupDao(), db.accountDao(), db.categoryDao(), db.currencyDao(), db.tagDao(),
        DefaultBookSettingsRepository(db.settingDao()), scope,
    )

fun transactionsRepository(context: Context, db: NexoDatabase, refs: ReferenceDataRepository): TransactionsRepository =
    DefaultTransactionsRepository(
        context, db, refs, DefaultBookSettingsRepository(db.settingDao()),
        DefaultPhotosRepository(PhotoFileDataSource(context, AppDirs(context), Dispatchers.IO), db.photoDao()),
    )

fun userPreferencesRepository(context: Context): UserPreferencesRepository =
    DefaultUserPreferencesRepository(DevicePreferencesDataSource(context))

fun fxRatesRepository(
    context: Context,
    db: NexoDatabase,
    refs: ReferenceDataRepository,
    prefs: UserPreferencesRepository = userPreferencesRepository(context),
): FxRatesRepository = DefaultFxRatesRepository(db, refs, prefs, FxRatesNetworkDataSource(Dispatchers.IO))

fun smsRepository(
    context: Context,
    db: NexoDatabase,
    refs: ReferenceDataRepository,
    prefs: UserPreferencesRepository = userPreferencesRepository(context),
    fx: FxRatesRepository = fxRatesRepository(context, db, refs, prefs),
): DefaultSmsRepository = DefaultSmsRepository(context, db.smsDao(), prefs, refs, SmsSuggester(db.smsDao(), refs, fx), FakeNotifier())

/**
 * A fresh book (seeded in [currency]) in an in-memory database, with the repositories a ViewModel needs wired as Hilt
 * wires them. The seeded LKR book has accounts "1" (bank) and "11" (cash) and category "6" (Food).
 */
class TestBook(val context: Context, scope: CoroutineScope, currency: String = "LKR") : Closeable {
    val db: NexoDatabase = Room.inMemoryDatabaseBuilder(context, NexoDatabase::class.java).allowMainThreadQueries().build()

    init {
        runBlocking { BookSeeder(context, db).seedFresh(CurrencyCatalog.find(currency)!!) }
    }

    val prefs = userPreferencesRepository(context)
    val refs = referenceDataRepository(db, scope)
    val photos = DefaultPhotosRepository(PhotoFileDataSource(context, AppDirs(context), Dispatchers.IO), db.photoDao())
    val transactions = transactionsRepository(context, db, refs)
    val fx = fxRatesRepository(context, db, refs, prefs)
    val sms = smsRepository(context, db, refs, prefs, fx)
    val inbox = DefaultSmsInboxRepository(context, prefs, refs, sms, fx, transactions, SmsInboxDataSource(context, Dispatchers.IO), Dispatchers.IO)
    val accounts = DefaultAccountsRepository(db, refs)
    val memos = DefaultMemosRepository(db.memoDao())
    val repeats = DefaultRepeatRulesRepository(db, refs, prefs)
    val budgets = DefaultBudgetsRepository(db)
    val favorites = DefaultFavoritesRepository(db)
    val tags = DefaultTagsRepository(db)
    val importer = DefaultTransactionImportRepository(context, db, refs, AppDirs(context), photos, Dispatchers.IO)
    val receipts = DefaultReceiptRepository(ReceiptTextDataSource(context, Dispatchers.IO), Dispatchers.Default)
    val getFeePlan = GetSmsFeePlanUseCase(sms, refs)
    val approveSms = ApproveSmsUseCase(sms, refs, transactions, GetSmsDraftUseCase(GetSmsLedgerAmountUseCase()))

    override fun close() = db.close()
}

/** Records what would have been posted instead of showing it. */
class FakeNotifier : Notifier {
    val smsReviewCounts = mutableListOf<Int>()

    override fun postSmsReview(count: Int) {
        smsReviewCounts += count
    }
}
