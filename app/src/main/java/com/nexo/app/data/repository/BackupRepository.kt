package com.nexo.app.data.repository

import android.net.Uri
import com.nexo.app.core.model.RestoreResult
import java.io.File

/** Backups of the whole book (the database file), automatic daily copies, and restoring Nexo or legacy backups. */
interface BackupRepository {
    fun backupFileName(prefix: String = "Nexo"): String

    suspend fun writeBackup(target: Uri): Boolean

    suspend fun writeBackup(target: File): Boolean

    /** A copy in the app's temp folder, for sharing. */
    suspend fun shareCopy(): File?

    /** Writes today's automatic backup and prunes old ones. */
    suspend fun autoBackup(): Boolean

    fun autoBackups(): List<File>

    fun isBackupName(name: String): Boolean

    suspend fun restore(uri: Uri): RestoreResult

    suspend fun restoreFile(file: File): RestoreResult

    companion object {
        const val EXTENSION = "nexobak"
        const val MIME = "application/vnd.nexo.backup"
    }
}
