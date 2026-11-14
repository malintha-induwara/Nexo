package com.nexo.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import com.nexo.app.core.common.Dispatcher
import com.nexo.app.core.common.NexoDispatchers
import com.nexo.app.core.model.BackupFormat
import com.nexo.app.core.model.RestoreResult
import com.nexo.app.data.backup.LegacyImporter
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.file.AppDirs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Backup / restore of the finance database. Backups are plain copies of the database file. */
class DefaultBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: NexoDatabase,
    private val dirs: AppDirs,
    private val prefs: UserPreferencesRepository,
    private val legacy: LegacyImporter,
    @Dispatcher(NexoDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : BackupRepository {
    /** "Nexo(<date>-<HHmmss>).nexobak" with a locale-dependent date part. */
    override fun backupFileName(now: LocalDateTime, prefix: String): String {
        val date = now.toLocalDate().format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.getDefault()),
        )
            .replace(" ", "")
            .replace(Regex("[^\\p{Nd}]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
        val time = now.format(DateTimeFormatter.ofPattern("HHmmss"))
        return "$prefix($date-$time).$BackupRepository.EXTENSION"
    }

    /** Flushes the WAL so the main file is a consistent copy. */
    private fun checkpoint() {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use {
            it.moveToFirst()
        }
    }

    override suspend fun writeBackup(target: Uri): Boolean = withContext(ioDispatcher) {
        runCatching {
            checkpoint()
            context.contentResolver.openOutputStream(target, "wt")?.use { out ->
                dirs.database.inputStream().use { it.copyTo(out) }
            }
                ?: error("no stream")
            true
        }.getOrDefault(false)
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
        val f = File(dirs.temp, "nexo_backup.$BackupRepository.EXTENSION")
        if (writeBackup(f)) f else null
    }

    /** Automatic daily backup: keeps the 14 newest files. */
    override suspend fun autoBackup(): Boolean = withContext(ioDispatcher) {
        val ok = writeBackup(File(dirs.backups, backupFileName()))
        if (ok) {
            prefs.setLastAutoBackup(System.currentTimeMillis())
            autoBackups().drop(KEEP).forEach { it.delete() }
        }
        ok
    }

    override fun autoBackups(): List<File> = dirs.backups.listFiles()?.filter {
        isBackupName(it.name) &&
            !it.name.endsWith("journal")
    }
        ?.sortedByDescending { it.lastModified() }.orEmpty()

    override fun isBackupName(name: String): Boolean {
        if (name.contains("weple.sqlite")) return false
        val accepted =
            listOf(
                ".$BackupRepository.EXTENSION",
                ".mmbak",
                ".sqlite",
                ".MoneyManager",
                ".moneymanager",
                ".MoneyManager2",
                ".moneymanager2",
            )
        return accepted.any { name.endsWith(it) } || name.endsWith("sql")
    }

    override suspend fun restore(uri: Uri): RestoreResult = withContext(ioDispatcher) {
        val temp = File(dirs.temp, "restore_candidate.db")
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { input.copyTo(it) }
            }
                ?: return@withContext RestoreResult.Failed(null)
        }.onFailure { return@withContext RestoreResult.Failed(it.message) }
        restoreFile(temp).also { temp.delete() }
    }

    override suspend fun restoreFile(file: File): RestoreResult = withContext(ioDispatcher) {
        when (detect(file)) {
            BackupFormat.NEXO -> runCatching {
                copyNexo(file)
                RestoreResult.Success
            }.getOrElse { RestoreResult.Failed(it.message) }

            BackupFormat.LEGACY_ANDROID -> legacy.import(file)

            BackupFormat.LEGACY_IPHONE -> RestoreResult.Unsupported

            BackupFormat.UNKNOWN -> RestoreResult.NotABackup
        }
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
            // Bank SMS capture. OTP hints are transient and stay out of backups.
            "sms_items", "sms_sources", "sms_merchant_rules", "fee_presets", "fx_snapshots",
        )
    }
}
