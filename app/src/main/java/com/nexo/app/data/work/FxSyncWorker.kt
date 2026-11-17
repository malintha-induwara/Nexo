package com.nexo.app.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexo.app.data.repository.FxRatesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

// Fetches bank rates when online and fills in "Rate pending" estimates.
@HiltWorker
class FxSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val fx: FxRatesRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!fx.online) {
            fx.fillPending()
            return Result.success()
        }
        fx.refreshIfStale()
        fx.fillPending(allowNetwork = true)
        return Result.success()
    }

    companion object {
        private const val NAME = "fx_sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<FxSyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(
                context,
            ).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
