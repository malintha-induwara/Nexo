package com.nexo.app.data.repository

import javax.crypto.Cipher
import kotlinx.coroutines.flow.StateFlow

sealed interface PinResult {
    data object Success : PinResult
    data class Wrong(val triesBeforeWait: Int) : PinResult
    data class Waiting(val untilMillis: Long) : PinResult
}

/**
 * Single-user app lock: when a PIN is set the app locks on every cold start and after the background timeout.
 * Biometric unlock is an optional shortcut to the PIN.
 */
interface AppLockRepository {
    fun getLockedStream(): StateFlow<Boolean>

    /**
     * Call just before the app sends the user to another app and expects them straight back: the camera for a
     * receipt, a file picker. That trip is not treated as leaving the app.
     */
    fun expectReturn()

    fun onBackground()

    fun onForeground()

    fun lockNow()

    fun unlock()

    suspend fun verifyPin(pin: String): PinResult

    /** Whether [pin] is the PIN now set. Unlike [verifyPin], a wrong answer does not count as a failure. */
    suspend fun isCurrentPin(pin: String): Boolean

    /** Sets or changes the PIN; null removes it together with biometric unlock. */
    suspend fun setPin(pin: String?)

    /** Cipher for the unlock prompt, or null when the biometric key is missing or was invalidated. */
    fun getUnlockCipher(): Cipher?

    /** Creates a fresh biometric key and returns the cipher to confirm it with, or null if that failed. */
    fun createBiometricKey(): Cipher?

    /** Turning biometric unlock off also deletes its key. */
    suspend fun setBiometricsEnabled(enabled: Boolean)
}
