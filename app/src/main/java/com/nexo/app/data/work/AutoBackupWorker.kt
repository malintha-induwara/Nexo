package com.nexo.app.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexo.app.data.repository.BackupRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Daily on-device backup job. */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backups: BackupRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (backups.autoBackup()) Result.success() else Result.retry()

    companion object {
        private const val NAME = "auto_backup"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
        }
    }
}
