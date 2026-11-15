package com.nexo.app.system

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.nexo.app.core.common.ApplicationScope
import com.nexo.app.data.repository.AppLockRepository
import com.nexo.app.data.repository.RepeatRulesRepository
import com.nexo.app.data.repository.SmsInboxRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import com.nexo.app.data.work.AutoBackupWorker
import com.nexo.app.data.work.FxSyncWorker
import com.nexo.app.data.work.SmsCatchUpWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * What the app does as its process starts, comes to the foreground and goes to the background.
 * It observes the whole process, so moving between screens doesn't count as leaving.
 */
@Singleton
class AppLifecycleTasks @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lock: AppLockRepository,
    private val prefs: UserPreferencesRepository,
    private val repeats: RepeatRulesRepository,
    private val smsInbox: SmsInboxRepository,
    private val notifications: StandingNotifications,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    /** Once per process: repeats are generated at every cold start, then once per calendar day. */
    override fun onCreate(owner: LifecycleOwner) {
        scope.launch {
            if (setupDone()) repeats.generateIfNewDay(force = true)
        }
        // The hourly inbox check runs exactly while capture is active, whatever switched it on or off.
        scope.launch {
            var scheduled = false
            prefs.getDeviceSettingsStream()
                .map { it.setupDone && it.smsCaptureStartedAt != 0L }
                .distinctUntilChanged()
                .collect { active ->
                    if (active) {
                        SmsCatchUpWorker.schedule(context)
                    } else if (scheduled) {
                        SmsCatchUpWorker.cancel(context)
                    }
                    scheduled = active
                }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        lock.onForeground()
        scope.launch {
            if (setupDone()) repeats.generateIfNewDay()
            notifications.refreshQuickAdd()
        }
        // SMS catch-up and expiry run at app start and on every foreground; rates refresh at most
        // every 6 hours.
        scope.launch {
            smsInbox.refresh()
            val s = prefs.getDeviceSettings()
            if (s.setupDone && s.smsCaptureEnabled) FxSyncWorker.enqueue(context)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        lock.onBackground()
        scope.launch {
            val s = prefs.getDeviceSettings()
            if (s.setupDone && clock.millis() - s.lastAutoBackup > AUTO_BACKUP_EVERY_MS) {
                AutoBackupWorker.enqueue(context)
            }
        }
    }

    private suspend fun setupDone() = prefs.getDeviceSettings().setupDone

    private companion object {
        const val AUTO_BACKUP_EVERY_MS = 24 * 60 * 60 * 1000L
    }
}
