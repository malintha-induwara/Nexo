package com.nexo.app.testing

import android.content.Context
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.file.AppDirs
import com.nexo.app.data.file.PhotoFileDataSource
import com.nexo.app.data.repository.DefaultBookSettingsRepository
import com.nexo.app.data.repository.DefaultPhotosRepository
import com.nexo.app.data.repository.DefaultReferenceDataRepository
import com.nexo.app.data.repository.DefaultTransactionsRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.TransactionsRepository
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
