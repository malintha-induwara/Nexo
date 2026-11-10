package com.nexo.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexo.app.backup.BackupManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Daily on-device backup job (CFG-031). */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backups: BackupManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (backups.autoBackup()) Result.success() else Result.retry()

    companion object {
        private const val NAME = "auto_backup"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
        }
    }
}
