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
 * Inbox check every six hours while capture is on. Android releases the messages it kept back without telling
 * anyone, so this is what brings them into the queue while the app is closed.
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

        /**
         * Two runs can be almost two periods apart, and a released message has to be read within the re-read window
         * of its arrival, so this stays well under half of that window.
         */
        private const val EVERY_HOURS = 6L

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<SmsCatchUpWorker>(EVERY_HOURS, TimeUnit.HOURS).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
