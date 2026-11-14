package com.nexo.app.data

import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.database.Seeder
import com.nexo.app.core.files.AppDirs
import com.nexo.app.core.lock.BiometricHelper
import com.nexo.app.core.lock.LockManager
import com.nexo.app.core.money.CatalogCurrency
import com.nexo.app.core.money.CurrencyCatalog
import com.nexo.app.core.settings.DevicePrefs
import com.nexo.app.widget.WidgetRefresher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reset operations (CFG-038, DM-086). */
@Singleton
class DataReset @Inject constructor(
    private val db: NexoDatabase,
    private val seeder: Seeder,
    private val dirs: AppDirs,
    private val prefs: DevicePrefs,
    private val lock: LockManager,
    private val widgets: WidgetRefresher,
) {
    suspend fun photoFileCount(): Int = withContext(Dispatchers.IO) { dirs.photos.listFiles()?.size ?: 0 }

    /** Complete reset: every table emptied and re-seeded. Device preferences and the PIN stay. */
    suspend fun completeReset(deletePhotos: Boolean, main: CatalogCurrency = CurrencyCatalog.forLocale()) = withContext(Dispatchers.IO) {
        db.clearAllTables()
        vacuum()
        seeder.seedFresh(main)
        prefs.setCollapsedGroups(emptySet())
        prefs.setRepeatDay("")
        if (deletePhotos) dirs.photos.listFiles()?.forEach { it.delete() }
        widgets.refresh()
    }

    /** Reset contents only: transactions, photos, tag links and the SMS review queue that points at them. */
    suspend fun contentsReset(deletePhotos: Boolean) = withContext(Dispatchers.IO) {
        db.transactionDao().deleteAll()
        db.photoDao().deleteAll()
        db.tagDao().deleteAllLinks()
        db.smsDao().deleteAllItems()
        db.smsDao().deleteAllHints()
        vacuum()
        if (deletePhotos) dirs.photos.listFiles()?.forEach { it.delete() }
        widgets.refresh()
    }

    /** Forgotten PIN: everything on the device is erased and the app returns to the welcome screen. */
    suspend fun eraseAll() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        vacuum()
        listOf(dirs.photos, dirs.backups, dirs.imports, dirs.temp).forEach { d -> d.listFiles()?.forEach { it.deleteRecursively() } }
        BiometricHelper.deleteKey()
        lock.setPin(null)
        prefs.setBiometrics(false)
        prefs.setSetupDone(false)
        prefs.setLastAutoBackup(0)
        prefs.setRepeatDay("")
        lock.unlock()
        widgets.refresh()
    }

    private fun vacuum() {
        runCatching { db.openHelper.writableDatabase.execSQL("VACUUM") }
    }
}
