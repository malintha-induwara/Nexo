package com.nexo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.BookSettings
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.model.LockTimeout
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.ThemeMode
import com.nexo.app.data.repository.AppLockRepository
import com.nexo.app.data.repository.BookSettingsRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.crypto.Cipher
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(val ref: RefData = RefData(), val device: DeviceSettings? = null)

/** Shared by the More tab and the configuration, appearance, reminder and lock screens. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    refs: ReferenceDataRepository,
    private val bookSettings: BookSettingsRepository,
    private val prefs: UserPreferencesRepository,
    private val lock: AppLockRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        refs.getRefDataStream(),
        prefs.getDeviceSettingsStream(),
        ::SettingsUiState,
    )
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun update(transform: (BookSettings) -> BookSettings) = viewModelScope.launch {
        bookSettings.updateBookSettings(transform)
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }

    fun setNumberPadDescending(descending: Boolean) = viewModelScope.launch {
        prefs.setNumberPadDescending(descending)
    }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int, days: Set<Int>) =
        viewModelScope.launch {
            prefs.setReminder(enabled, hour, minute, days)
        }

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
