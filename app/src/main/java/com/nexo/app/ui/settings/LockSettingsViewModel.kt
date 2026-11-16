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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Whether a PIN typed in settings is the current one. [id] tells two identical answers apart. */
data class PinCheck(val correct: Boolean, val id: Int)

data class LockSettingsUiState(
    val device: DeviceSettings? = null,
    /** Handled once by the PIN dialog, then cleared. */
    val pinCheck: PinCheck? = null,
    /** A fresh biometric key to confirm with the system prompt; handled once, then cleared. */
    val biometricSetup: Cipher? = null,
)

/** App lock: PIN, biometric unlock and timeout. */
@HiltViewModel
class LockSettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val lock: AppLockRepository,
) : ViewModel() {
    private val pinCheck = MutableStateFlow<PinCheck?>(null)
    private val biometricSetup = MutableStateFlow<Cipher?>(null)
    private var pinChecks = 0

    val uiState: StateFlow<LockSettingsUiState> = combine(
        prefs.getDeviceSettingsStream(),
        pinCheck,
        biometricSetup,
        ::LockSettingsUiState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockSettingsUiState())

    fun setLockTimeout(timeout: LockTimeout) = viewModelScope.launch {
        prefs.setLockTimeout(timeout)
    }

    /** Sets or changes the PIN; null removes it together with biometric unlock. */
    fun setPin(pin: String?) = viewModelScope.launch { lock.setPin(pin) }

    /** Unlike the lock screen, a wrong answer here does not count as a failed attempt. */
    fun checkPin(pin: String) = viewModelScope.launch {
        pinCheck.value = PinCheck(lock.isCurrentPin(pin), ++pinChecks)
    }

    fun onPinCheckHandled() {
        pinCheck.value = null
    }

    fun lockNow() = lock.lockNow()

    /** Makes a fresh biometric key; turning biometrics on waits for the prompt that confirms it. */
    fun startBiometricSetup() = viewModelScope.launch {
        biometricSetup.value = lock.createBiometricKey()
    }

    fun onBiometricSetupHandled() {
        biometricSetup.value = null
    }

    fun setBiometricsEnabled(enabled: Boolean) = viewModelScope.launch {
        lock.setBiometricsEnabled(enabled)
    }
}
