package com.nexo.app.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexo.app.data.repository.SmsInboxRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Hourly inbox check while capture is on. Android releases the messages it kept back without telling anyone, so
 * this is what brings them into the queue while the app is closed.
 */
@HiltWorker
class SmsCatchUpWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val inbox: SmsInboxRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        inbox.refresh()
        return Result.success()
    }

    companion object {
        private const val NAME = "sms_catch_up"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SmsCatchUpWorker>(1, TimeUnit.HOURS).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
