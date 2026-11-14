package com.nexo.app.data.repository

import com.nexo.app.core.common.Dispatcher
import com.nexo.app.core.common.NexoDispatchers
import com.nexo.app.core.common.money.CatalogCurrency
import com.nexo.app.core.common.money.CurrencyCatalog
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.Seeder
import com.nexo.app.data.file.AppDirs
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class DataReset @Inject constructor(
    private val db: NexoDatabase,
    private val seeder: Seeder,
    private val dirs: AppDirs,
    private val prefs: UserPreferencesRepository,
    private val lock: AppLockRepository,
    @Dispatcher(NexoDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun photoFileCount(): Int = withContext(ioDispatcher) { dirs.photos.listFiles()?.size ?: 0 }

    /** Complete reset: every table emptied and re-seeded. Device preferences and the PIN stay. */
    suspend fun completeReset(deletePhotos: Boolean, main: CatalogCurrency = CurrencyCatalog.forLocale()) = withContext(ioDispatcher) {
        db.clearAllTables()
        vacuum()
        seeder.seedFresh(main)
        prefs.setCollapsedGroups(emptySet())
        prefs.setRepeatDay("")
        if (deletePhotos) dirs.photos.listFiles()?.forEach { it.delete() }
    }

    /** Reset contents only: transactions, photos, tag links and the SMS review queue that points at them. */
    suspend fun contentsReset(deletePhotos: Boolean) = withContext(ioDispatcher) {
        db.transactionDao().deleteAll()
        db.photoDao().deleteAll()
        db.tagDao().deleteAllLinks()
        db.smsDao().deleteAllItems()
        db.smsDao().deleteAllHints()
        vacuum()
        if (deletePhotos) dirs.photos.listFiles()?.forEach { it.delete() }
    }

    /** Forgotten PIN: everything on the device is erased and the app returns to the welcome screen. */
    suspend fun eraseAll() = withContext(ioDispatcher) {
        db.clearAllTables()
        vacuum()
        listOf(dirs.photos, dirs.backups, dirs.imports, dirs.temp).forEach { d -> d.listFiles()?.forEach { it.deleteRecursively() } }
        lock.setPin(null)
        prefs.setBiometrics(false)
        prefs.setSetupDone(false)
        prefs.setLastAutoBackup(0)
        prefs.setRepeatDay("")
        lock.unlock()
    }

    private fun vacuum() {
        runCatching { db.openHelper.writableDatabase.execSQL("VACUUM") }
    }
}
