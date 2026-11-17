package com.nexo.app.data.repository

import android.net.Uri
import com.nexo.app.core.model.RestoreResult
import com.nexo.app.core.model.StoredFile
import java.io.File

// Whole-book backups (the database file), daily automatic copies, and restore.
interface BackupRepository {
    suspend fun writeBackup(target: Uri): Boolean

    suspend fun writeBackup(target: File): Boolean

    // Copy in the app's temp folder, for sharing.
    suspend fun shareCopy(): File?

    // Writes today's automatic backup and prunes old ones.
    suspend fun autoBackup(): Boolean

    // Newest first.
    suspend fun getAutoBackups(): List<StoredFile>

    suspend fun deleteAutoBackups(names: Collection<String>)

    // A file whose name can't be read is let through.
    suspend fun isBackup(uri: Uri): Boolean

    // Once started it finishes, even if the caller goes away.
    suspend fun restore(uri: Uri): RestoreResult

    suspend fun restoreFile(file: File): RestoreResult
}
