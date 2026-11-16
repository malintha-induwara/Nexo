package com.nexo.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import com.nexo.app.core.common.ApplicationScope
import com.nexo.app.core.common.Dispatcher
import com.nexo.app.core.common.NexoDispatchers
import com.nexo.app.core.common.runToCompletion
import com.nexo.app.core.model.BackupFiles
import com.nexo.app.core.model.BackupFormat
import com.nexo.app.core.model.RestoreResult
import com.nexo.app.core.model.StoredFile
import com.nexo.app.data.backup.LegacyImporter
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.file.AppDirs
import com.nexo.app.data.file.PickedFileDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

/** Backup / restore of the finance database. Backups are plain copies of the database file. */
class DefaultBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: NexoDatabase,
    private val dirs: AppDirs,
    private val prefs: UserPreferencesRepository,
    private val legacy: LegacyImporter,
    private val repeats: RepeatRulesRepository,
    private val picked: PickedFileDataSource,
    @ApplicationScope private val scope: CoroutineScope,
    @Dispatcher(NexoDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock,
) : BackupRepository {
    /** Flushes the WAL so the main file is a consistent copy. */
    private fun checkpoint() {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use {
            it.moveToFirst()
        }
    }

    /** A half-written file would look like a backup, so the copy is not cut short. */
    override suspend fun writeBackup(target: Uri): Boolean = scope.runToCompletion {
        withContext(ioDispatcher) {
            runCatching {
                checkpoint()
                context.contentResolver.openOutputStream(target, "wt")?.use { out ->
                    dirs.database.inputStream().use { it.copyTo(out) }
                }
                    ?: error("no stream")
                true
            }.getOrDefault(false)
        }
    }

    override suspend fun writeBackup(target: File): Boolean = withContext(ioDispatcher) {
        runCatching {
            checkpoint()
            target.parentFile?.mkdirs()
            dirs.database.copyTo(target, overwrite = true)
            true
        }.getOrDefault(false)
    }

    /** Copy in the temp folder for the share sheet. */
    override suspend fun shareCopy(): File? = withContext(ioDispatcher) {
        val f = File(dirs.temp, "nexo_backup.${BackupFiles.EXTENSION}")
        if (writeBackup(f)) f else null
    }

    /** Automatic daily backup: keeps the 14 newest files. */
    override suspend fun autoBackup(): Boolean = withContext(ioDispatcher) {
        val ok = writeBackup(File(dirs.backups, BackupFiles.name(LocalDateTime.now(clock))))
        if (ok) {
            prefs.setLastAutoBackup(clock.millis())
            listAutoBackups().drop(KEEP).forEach { it.file.delete() }
        }
        ok
    }

    override suspend fun getAutoBackups(): List<StoredFile> =
        withContext(ioDispatcher) { listAutoBackups() }

    private fun listAutoBackups(): List<StoredFile> = dirs.backups.listFiles()?.filter {
        BackupFiles.isBackupName(it.name) && !it.name.endsWith("journal")
    }?.map(StoredFile::of)?.sortedByDescending { it.modifiedAt }.orEmpty()

    override suspend fun deleteAutoBackups(names: Collection<String>) = withContext(ioDispatcher) {
        names.forEach { File(dirs.backups, File(it).name).delete() }
    }

    override suspend fun isBackup(uri: Uri): Boolean =
        picked.displayName(uri)?.let(BackupFiles::isBackupName) ?: true

    override suspend fun restore(uri: Uri): RestoreResult = scope.runToCompletion {
        withContext(ioDispatcher) {
            val temp = File(dirs.temp, "restore_candidate.db")
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { input.copyTo(it) }
                } != null
            }.getOrElse { return@withContext RestoreResult.Failed(it.message) }
            if (!copied) return@withContext RestoreResult.Failed(null)
            restoreNow(temp).also { temp.delete() }
        }
    }

    override suspend fun restoreFile(file: File): RestoreResult =
        scope.runToCompletion { restoreNow(file) }

    private suspend fun restoreNow(file: File): RestoreResult = withContext(ioDispatcher) {
        val result = when (detect(file)) {
            BackupFormat.NEXO -> runCatching {
                copyNexo(file)
                RestoreResult.Success
            }.getOrElse { RestoreResult.Failed(it.message) }

            BackupFormat.LEGACY_ANDROID -> legacy.import(file)

            BackupFormat.LEGACY_IPHONE -> RestoreResult.Unsupported

            BackupFormat.UNKNOWN -> RestoreResult.NotABackup
        }
        // The restored book's repeats are due from its own last day, not from this device's.
        if (result == RestoreResult.Success) repeats.generateIfNewDay(force = true)
        result
    }

    fun detect(file: File): BackupFormat = runCatching {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { src ->
            val tables = src.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table'",
                null,
            ).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            when {
                NexoDatabase.IDENTITY_TABLE in tables && "transactions" in tables &&
                    "account_groups" in tables -> BackupFormat.NEXO

                "INOUTCOME" in tables && ("sqlite_sequence" in tables || "ASSETS" in tables) -> {
                    BackupFormat.LEGACY_ANDROID
                }

                "ZASSET" in tables && "ZINOUTCOME" in tables -> BackupFormat.LEGACY_IPHONE

                else -> BackupFormat.UNKNOWN
            }
        }
    }.getOrDefault(BackupFormat.UNKNOWN)

    /** Replaces every Nexo table with the backup's rows (same schema; columns are intersected for safety). */
    private suspend fun copyNexo(file: File) {
        val src = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        src.use { source ->
            require(source.version <= DB_VERSION) { "Backup was made by a newer version of Nexo." }
            db.withTransaction {
                val target = db.openHelper.writableDatabase
                for (table in TABLES) {
                    target.execSQL("DELETE FROM `$table`")
                    val targetCols = columns(target.query("PRAGMA table_info(`$table`)"))
                    val sourceCols = runCatching {
                        columns(source.rawQuery("PRAGMA table_info(`$table`)", null))
                    }.getOrDefault(emptySet())
                    val cols = targetCols.intersect(sourceCols)
                    if (cols.isEmpty()) continue
                    source.rawQuery(
                        "SELECT ${cols.joinToString {
                            "`$it`"
                        }} FROM `$table`",
                        null,
                    ).use { c ->
                        while (c.moveToNext()) {
                            val v = ContentValues()
                            cols.forEachIndexed { i, col ->
                                when (c.getType(i)) {
                                    Cursor.FIELD_TYPE_NULL -> v.putNull(col)
                                    Cursor.FIELD_TYPE_INTEGER -> v.put(col, c.getLong(i))
                                    Cursor.FIELD_TYPE_FLOAT -> v.put(col, c.getDouble(i))
                                    Cursor.FIELD_TYPE_BLOB -> v.put(col, c.getBlob(i))
                                    else -> v.put(col, c.getString(i))
                                }
                            }
                            target.insert(table, SQLiteDatabase.CONFLICT_REPLACE, v)
                        }
                    }
                }
            }
        }
    }

    private fun columns(c: Cursor): Set<String> = c.use {
        val idx = it.getColumnIndex("name")
        buildSet { while (it.moveToNext()) add(it.getString(idx)) }
    }

    companion object {
        const val KEEP = 14
        const val DB_VERSION = NexoDatabase.VERSION
        val TABLES = listOf(
            "account_groups",
            "accounts",
            "categories",
            "currencies",
            "transactions",
            "transaction_tags",
            "tags",
            "photos",
            "repeat_rules", "favorites", "budgets", "budget_amounts", "memos", "settings",
            // Bank SMS capture. OTP hints and the record of messages already taken in are transient and stay out.
            "sms_items", "sms_sources", "sms_merchant_rules", "fee_presets", "fx_snapshots",
        )
    }
}
