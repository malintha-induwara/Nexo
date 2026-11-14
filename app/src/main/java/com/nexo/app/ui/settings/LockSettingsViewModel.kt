package com.nexo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.model.LockTimeout
import com.nexo.app.data.repository.AppLockRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.crypto.Cipher
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LockSettingsUiState(val device: DeviceSettings? = null)

/** App lock: PIN, biometric unlock and timeout. */
@HiltViewModel
class LockSettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val lock: AppLockRepository,
) : ViewModel() {
    val uiState: StateFlow<LockSettingsUiState> = prefs.getDeviceSettingsStream()
        .map(::LockSettingsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockSettingsUiState())

    fun setLockTimeout(timeout: LockTimeout) = viewModelScope.launch {
        prefs.setLockTimeout(timeout)
    }

    /** Sets or changes the PIN; null removes it together with biometric unlock. */
    fun setPin(pin: String?) = viewModelScope.launch { lock.setPin(pin) }

    suspend fun isCurrentPin(pin: String): Boolean = lock.isCurrentPin(pin)

    fun lockNow() = lock.lockNow()

    /** A fresh biometric key and the cipher to confirm it with, or null if that failed. */
    fun createBiometricKey(): Cipher? = lock.createBiometricKey()

    fun setBiometricsEnabled(enabled: Boolean) = viewModelScope.launch {
        lock.setBiometricsEnabled(enabled)
    }
}
