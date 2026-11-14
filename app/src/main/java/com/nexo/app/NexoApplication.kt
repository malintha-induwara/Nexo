package com.nexo.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.nexo.app.core.common.ApplicationScope
import com.nexo.app.core.notifications.Notifications
import com.nexo.app.data.repository.AppLockRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.RepeatRulesRepository
import com.nexo.app.data.repository.SmsInboxRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import com.nexo.app.data.work.AutoBackupWorker
import com.nexo.app.data.work.FxSyncWorker
import dagger.hilt.android.HiltAndroidApp
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class NexoApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var lock: AppLockRepository

    @Inject lateinit var prefs: UserPreferencesRepository

    @Inject lateinit var repeats: RepeatRulesRepository

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    @Inject lateinit var refs: ReferenceDataRepository

    @Inject lateinit var smsInbox: SmsInboxRepository

    @Inject lateinit var clock: Clock

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                lock.onForeground()
                scope.launch {
                    if (prefs.getDeviceSettings().setupDone) {
                        repeats.generateIfNewDay()
                        // The quick-add notification is refreshed on every resume.
                        Notifications.updateQuickAdd(
                            this@NexoApplication,
                            refs.getRefData().settings.quickAddNotification,
                        )
                    }
                }
                // SMS catch-up and expiry run at app start and on every foreground; rates refresh at most every 6 hours.
                scope.launch {
                    smsInbox.refresh()
                    val s = prefs.getDeviceSettings()
                    if (s.setupDone && s.smsCaptureEnabled) {
                        FxSyncWorker.enqueue(this@NexoApplication)
                    }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                lock.onBackground()
                scope.launch {
                    val s = prefs.getDeviceSettings()
                    if (s.setupDone && clock.millis() - s.lastAutoBackup > DAY_MS) {
                        AutoBackupWorker.enqueue(this@NexoApplication)
                    }
                }
            }
        })
        // Repeats are generated at every cold start, then once per calendar day.
        scope.launch {
            if (prefs.getDeviceSettings().setupDone) repeats.generateIfNewDay(force = true)
        }
    }

    companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
