package com.nexo.app.data.repository

import javax.crypto.Cipher
import kotlinx.coroutines.flow.Flow

sealed interface PinResult {
    data object Success : PinResult
    data class Wrong(val triesBeforeWait: Int) : PinResult
    data class Waiting(val untilMillis: Long) : PinResult
}

// While a PIN is set, locks on every cold start and after the background timeout.
// Biometric unlock is an optional shortcut to the PIN.
interface AppLockRepository {
    // Emits once the lock state is known, so nothing draws before the lock screen.
    fun getLockedStream(): Flow<Boolean>

    // Call before sending the user to another app they come straight back from (camera,
    // file picker); that trip doesn't count as leaving.
    fun expectReturn()

    fun onBackground()

    fun onForeground()

    fun lockNow()

    fun unlock()

    suspend fun verifyPin(pin: String): PinResult

    // Unlike [verifyPin], a wrong answer does not count as a failure.
    suspend fun isCurrentPin(pin: String): Boolean

    // Null removes the PIN together with biometric unlock.
    suspend fun setPin(pin: String?)

    // Null when the biometric key is missing or was invalidated.
    suspend fun getUnlockCipher(): Cipher?

    // Creates a fresh biometric key; null if that failed.
    suspend fun createBiometricKey(): Cipher?

    // Turning it off also deletes the key.
    suspend fun setBiometricsEnabled(enabled: Boolean)
}
