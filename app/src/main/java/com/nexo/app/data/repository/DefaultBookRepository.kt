package com.nexo.app.data.repository

import com.nexo.app.core.common.ApplicationScope
import com.nexo.app.core.common.Dispatcher
import com.nexo.app.core.common.NexoDispatchers
import com.nexo.app.core.common.money.CatalogCurrency
import com.nexo.app.core.common.runToCompletion
import com.nexo.app.data.database.BookSeeder
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.file.AppDirs
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

class DefaultBookRepository @Inject constructor(
    private val db: NexoDatabase,
    private val seeder: BookSeeder,
    private val dirs: AppDirs,
    private val prefs: UserPreferencesRepository,
    private val lock: AppLockRepository,
    @Dispatcher(NexoDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
    private val clock: Clock,
) : BookRepository {
    override suspend fun seedFresh(main: CatalogCurrency) {
        seeder.seedFresh(main)
    }

    override suspend fun photoFileCount(): Int = withContext(ioDispatcher) {
        dirs.photos.listFiles()?.size
            ?: 0
    }

    /** Complete reset: every table emptied and re-seeded. Device preferences and the PIN stay. */
    override suspend fun completeReset(deletePhotos: Boolean, main: CatalogCurrency) =
        scope.runToCompletion {
            withContext(ioDispatcher) {
                db.clearAllTables()
                restartSmsCapture()
                vacuum()
                seeder.seedFresh(main)
                prefs.setCollapsedGroups(emptySet())
                prefs.setRepeatDay("")
                if (deletePhotos) dirs.photos.listFiles()?.forEach { it.delete() }
            }
        }

    /** Reset contents only: transactions, photos, tag links and the SMS review queue that points at them. */
    override suspend fun contentsReset(deletePhotos: Boolean) = scope.runToCompletion {
        withContext(ioDispatcher) {
            db.transactionDao().deleteAll()
            db.photoDao().deleteAll()
            db.tagDao().deleteAllLinks()
            db.smsDao().deleteAllItems()
            db.smsDao().deleteAllHints()
            vacuum()
            if (deletePhotos) dirs.photos.listFiles()?.forEach { it.delete() }
        }
    }

    /** Forgotten PIN: everything on the device is erased and the app returns to the welcome screen. */
    override suspend fun eraseAll() = scope.runToCompletion {
        withContext(ioDispatcher) {
            db.clearAllTables()
            restartSmsCapture()
            vacuum()
            listOf(dirs.photos, dirs.backups, dirs.imports, dirs.temp).forEach { d ->
                d.listFiles()?.forEach { it.deleteRecursively() }
            }
            lock.setPin(null)
            prefs.setBiometrics(false)
            prefs.setSetupDone(false)
            prefs.setLastAutoBackup(0)
            prefs.setRepeatDay("")
            lock.unlock()
        }
    }

    /** The record of messages already taken in is gone, so capture starts over rather than refill the queue. */
    private suspend fun restartSmsCapture() {
        if (prefs.getDeviceSettings().smsCaptureStartedAt != 0L) {
            prefs.markSmsCaptureStarted(clock.millis())
        }
    }

    private fun vacuum() {
        runCatching { db.openHelper.writableDatabase.execSQL("VACUUM") }
    }
}
