package com.nexo.app.testing

import android.content.Context
import com.nexo.app.core.notifications.Notifier
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.datastore.DevicePreferencesDataSource
import com.nexo.app.data.file.AppDirs
import com.nexo.app.data.file.PhotoFileDataSource
import com.nexo.app.data.network.FxRatesNetworkDataSource
import com.nexo.app.data.repository.DefaultBookSettingsRepository
import com.nexo.app.data.repository.DefaultFxRatesRepository
import com.nexo.app.data.repository.DefaultPhotosRepository
import com.nexo.app.data.repository.DefaultReferenceDataRepository
import com.nexo.app.data.repository.DefaultSmsRepository
import com.nexo.app.data.repository.DefaultTransactionsRepository
import com.nexo.app.data.repository.DefaultUserPreferencesRepository
import com.nexo.app.data.repository.FxRatesRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import com.nexo.app.data.sms.SmsSuggester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

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

/** Records what would have been posted instead of showing it. */
class FakeNotifier : Notifier {
    val smsReviewCounts = mutableListOf<Int>()

    override fun postSmsReview(count: Int) {
        smsReviewCounts += count
    }
}
