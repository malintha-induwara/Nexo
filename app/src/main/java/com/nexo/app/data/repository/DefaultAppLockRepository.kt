package com.nexo.app.data.repository

import android.os.SystemClock
import com.nexo.app.core.common.ApplicationScope
import com.nexo.app.core.common.Dispatcher
import com.nexo.app.core.common.NexoDispatchers
import com.nexo.app.core.model.LockTimeout
import com.nexo.app.data.lock.BiometricKeyDataSource
import com.nexo.app.data.lock.PinHasher
import java.time.Clock
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Background time is measured with the monotonic clock. */
@Singleton
class DefaultAppLockRepository @Inject constructor(
    private val prefs: UserPreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope,
    private val biometricKey: BiometricKeyDataSource,
    @Dispatcher(NexoDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
    private val clock: Clock,
) : AppLockRepository {
    private val _locked = MutableStateFlow(runBlocking { prefs.getDeviceSettings().lockEnabled })
    private val locked: StateFlow<Boolean> = _locked.asStateFlow()

    override fun getLockedStream(): StateFlow<Boolean> = locked

    private var backgroundedAt: Long? = null
    private var handoffAt: Long? = null
    private var awayOnHandoff = false

    override fun expectReturn() {
        handoffAt = SystemClock.elapsedRealtime()
    }

    override fun onBackground() {
        val now = SystemClock.elapsedRealtime()
        backgroundedAt = now
        // Only a background that follows the hand-off at once belongs to it.
        awayOnHandoff = handoffAt?.let { now - it <= HANDOFF_START } == true
        handoffAt = null
    }

    override fun onForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        val handoff = awayOnHandoff
        awayOnHandoff = false
        scope.launch {
            val s = prefs.getDeviceSettings()
            if (!s.lockEnabled) return@launch
            val limit = if (s.lockTimeout == LockTimeout.IMMEDIATELY) {
                5_000L
            } else {
                s.lockTimeout.seconds * 1000L
            }
            if (shouldLock(SystemClock.elapsedRealtime() - since, limit, handoff)) {
                _locked.value = true
            }
        }
    }

    override fun lockNow() {
        scope.launch { if (prefs.getDeviceSettings().lockEnabled) _locked.value = true }
    }

    override fun unlock() {
        _locked.value = false
    }

    override suspend fun verifyPin(pin: String): PinResult = withContext(defaultDispatcher) {
        val s = prefs.getDeviceSettings()
        val now = clock.millis()
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

    override suspend fun isCurrentPin(pin: String): Boolean = withContext(defaultDispatcher) {
        prefs.getDeviceSettings().pinHash?.let { PinHasher.verify(pin, it) } == true
    }

    override suspend fun setPin(pin: String?) {
        if (pin == null) biometricKey.deleteKey()
        prefs.setPin(pin?.let { withContext(defaultDispatcher) { PinHasher.hash(it) } })
        if (pin == null) _locked.value = false
    }

    override fun getUnlockCipher(): Cipher? = biometricKey.cipher()

    override fun createBiometricKey(): Cipher? {
        runCatching { biometricKey.createKey() }
        return biometricKey.cipher()
    }

    override suspend fun setBiometricsEnabled(enabled: Boolean) {
        if (!enabled) biometricKey.deleteKey()
        prefs.setBiometrics(enabled)
    }

    companion object {
        const val FREE_TRIES = 5
        const val MAX_WAIT = 15 * 60_000L

        /** How soon after [expectReturn] the app must go to the background for the trip to count as the hand-off. */
        const val HANDOFF_START = 10_000L

        /** How long a hand-off may last before coming back locks the app after all. */
        const val HANDOFF_GRACE = 5 * 60_000L

        /** Whether coming back after [awayMillis] locks the app. A hand-off gets [HANDOFF_GRACE] instead of the usual limit. */
        fun shouldLock(awayMillis: Long, limitMillis: Long, handoff: Boolean): Boolean =
            awayMillis > if (handoff) maxOf(limitMillis, HANDOFF_GRACE) else limitMillis

        /** From the 5th consecutive failure, 30 s doubling up to 15 min. */
        fun waitFor(failures: Int): Long = if (failures < FREE_TRIES) {
            0
        } else {
            minOf(30_000L shl (failures - FREE_TRIES).coerceAtMost(10), MAX_WAIT)
        }
    }
}
