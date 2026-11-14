package com.nexo.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.nexo.app.data.repository.LockManager
import com.nexo.app.data.datastore.DevicePrefs
import com.nexo.app.data.repository.RepeatEngine
import com.nexo.app.di.ApplicationScope
import com.nexo.app.data.work.AutoBackupWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NexoApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var lock: LockManager
    @Inject lateinit var prefs: DevicePrefs
    @Inject lateinit var repeats: RepeatEngine
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    @Inject lateinit var refs: com.nexo.app.data.repository.RefRepository
    @Inject lateinit var smsCapture: com.nexo.app.data.repository.SmsCapture

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                lock.onForeground()
                scope.launch {
                    if (prefs.current().setupDone) {
                        repeats.runIfNewDay()
                        // The quick-add notification is refreshed on every resume.
                        com.nexo.app.core.notifications.Notifications.updateQuickAdd(this@NexoApplication, refs.snapshot().settings.quickAddNotification)
                    }
                }
                // SMS catch-up and expiry run at app start and on every foreground; rates refresh at most every 6 hours.
                scope.launch {
                    smsCapture.refresh()
                    val s = prefs.current()
                    if (s.setupDone && s.smsCaptureEnabled) com.nexo.app.data.work.FxSyncWorker.enqueue(this@NexoApplication)
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                lock.onBackground()
                scope.launch {
                    val s = prefs.current()
                    if (s.setupDone && System.currentTimeMillis() - s.lastAutoBackup > DAY_MS) AutoBackupWorker.enqueue(this@NexoApplication)
                }
            }
        })
        // Repeats are generated at every cold start, then once per calendar day.
        scope.launch { if (prefs.current().setupDone) repeats.runIfNewDay(force = true) }
    }

    companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
