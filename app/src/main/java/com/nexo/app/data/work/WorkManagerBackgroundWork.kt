package com.nexo.app.data.work

import android.content.Context
import com.nexo.app.core.work.BackgroundWork
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class WorkManagerBackgroundWork @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackgroundWork {
    override fun scheduleSmsCatchUp() = SmsCatchUpWorker.schedule(context)

    override fun cancelSmsCatchUp() = SmsCatchUpWorker.cancel(context)

    override fun requestFxSync() = FxSyncWorker.enqueue(context)

    override fun requestAutoBackup() = AutoBackupWorker.enqueue(context)
}
