package com.nexo.app.core.lock

import android.os.SystemClock
import com.nexo.app.core.settings.DevicePrefs
import com.nexo.app.core.settings.LockTimeout
import com.nexo.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PinResult {
    data object Success : PinResult
    data class Wrong(val triesBeforeWait: Int) : PinResult
    data class Waiting(val untilMillis: Long) : PinResult
}

/**
 * Single-user lock (adapted from 08 LU-015/LU-017): when a PIN is set the app locks on every cold start and
 * after the background timeout. Background time is measured with the monotonic clock.
 */
@Singleton
class LockManager @Inject constructor(
    private val prefs: DevicePrefs,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _locked = MutableStateFlow(runBlocking { prefs.current().lockEnabled })
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long? = null

    fun onBackground() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun onForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        scope.launch {
            val s = prefs.current()
            if (!s.lockEnabled) return@launch
            val limit = if (s.lockTimeout == LockTimeout.IMMEDIATELY) 5_000L else s.lockTimeout.seconds * 1000L
            if (SystemClock.elapsedRealtime() - since > limit) _locked.value = true
        }
    }

    fun lockNow() {
        scope.launch { if (prefs.current().lockEnabled) _locked.value = true }
    }

    fun unlock() {
        _locked.value = false
    }

    suspend fun verifyPin(pin: String): PinResult = withContext(Dispatchers.Default) {
        val s = prefs.current()
        val now = System.currentTimeMillis()
        var lockoutUntil = s.pinLockoutUntil
        // A wait far in the future means the clock moved backwards: restart it from now.
        if (lockoutUntil - now > MAX_WAIT) lockoutUntil = now + waitFor(s.pinFailures)
        if (lockoutUntil > now) return@withContext PinResult.Waiting(lockoutUntil)
        val record = s.pinHash ?: return@withContext PinResult.Success
        if (PinHasher.verify(pin, record)) {
            prefs.setPinFailures(0, 0)
            unlock()
            PinResult.Success
        } else {
            val failures = s.pinFailures + 1
            val wait = waitFor(failures)
            prefs.setPinFailures(failures, if (wait > 0) now + wait else 0)
            if (wait > 0) PinResult.Waiting(now + wait) else PinResult.Wrong(FREE_TRIES - failures)
        }
    }

    suspend fun setPin(pin: String?) {
        prefs.setPin(pin?.let { withContext(Dispatchers.Default) { PinHasher.hash(it) } })
        if (pin == null) _locked.value = false
    }

    companion object {
        const val FREE_TRIES = 5
        const val MAX_WAIT = 15 * 60_000L

        /** BR-6: from the 5th consecutive failure, 30 s doubling up to 15 min. */
        fun waitFor(failures: Int): Long = if (failures < FREE_TRIES) 0 else minOf(30_000L shl (failures - FREE_TRIES).coerceAtMost(10), MAX_WAIT)
    }
}
