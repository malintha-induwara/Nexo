package com.nexo.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.nexo.app.BuildConfig
import com.nexo.app.core.model.BookSettings
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.data.repository.AppLockRepository
import com.nexo.app.data.repository.BookRepository
import com.nexo.app.data.repository.PinResult
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.crypto.Cipher
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// A PIN that did not unlock; [id] tells two identical answers apart.
data class PinAttempt(val result: PinResult, val id: Int)

sealed interface BiometricRequest {
    data class Prompt(val cipher: Cipher) : BiometricRequest

    // The key was invalidated by an enrolment change, so biometric unlock is now off.
    data object Invalidated : BiometricRequest
}

// Each is handled once by the lock screen, then cleared.
data class LockScreenUiState(
    val pinAttempt: PinAttempt? = null,
    val biometric: BiometricRequest? = null,
)

data class AppUiState(
    // Null until the device preferences load; the splash screen stays up until then.
    val device: DeviceSettings? = null,
    val book: BookSettings = BookSettings(),
    val locked: Boolean = false,
    // Screens to open once the app is unlocked (shortcuts, notifications).
    val pending: List<NavKey> = emptyList(),
    val lock: LockScreenUiState = LockScreenUiState(),
)

// Activity-scoped: device settings, the app lock and pending deep-link targets.
@HiltViewModel
class AppViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    refs: ReferenceDataRepository,
    private val lock: AppLockRepository,
    private val book: BookRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val pending = MutableStateFlow<List<NavKey>>(emptyList())
    private val lockScreen = MutableStateFlow(LockScreenUiState())
    private var pinAttempts = 0

    val uiState: StateFlow<AppUiState> = combine(
        prefs.getDeviceSettingsStream(),
        refs.getRefDataStream().map { it.settings },
        lock.getLockedStream(),
        pending,
        lockScreen,
        ::AppUiState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    // Saved state, so a target still waiting behind the lock screen is routed again after
    // process death.
    val launchHandled: Boolean get() = savedState[LAUNCH_HANDLED] ?: false

    fun setPending(keys: List<NavKey>) {
        pending.value = keys
        if (keys.isNotEmpty()) savedState[LAUNCH_HANDLED] = false
    }

    fun onPendingHandled() {
        pending.value = emptyList()
        savedState[LAUNCH_HANDLED] = true
    }

    fun markWhatsNewSeen() {
        viewModelScope.launch { prefs.setWhatsNewVersion(BuildConfig.VERSION_CODE) }
    }

    fun setHideAmounts(v: Boolean) {
        viewModelScope.launch { prefs.setHideAmounts(v) }
    }

    fun expectReturn() = lock.expectReturn()

    fun unlock() = lock.unlock()

    fun submitPin(pin: String) {
        viewModelScope.launch {
            val result = lock.verifyPin(pin)
            if (result != PinResult.Success) {
                lockScreen.update { it.copy(pinAttempt = PinAttempt(result, ++pinAttempts)) }
            }
        }
    }

    fun onPinAttemptShown() = lockScreen.update { it.copy(pinAttempt = null) }

    // Without a usable key, biometric unlock is switched off.
    fun requestBiometricUnlock() {
        viewModelScope.launch {
            val cipher = lock.getUnlockCipher()
            if (cipher == null) lock.setBiometricsEnabled(false)
            lockScreen.update {
                it.copy(
                    biometric = cipher?.let(BiometricRequest::Prompt)
                        ?: BiometricRequest.Invalidated,
                )
            }
        }
    }

    fun onBiometricRequestHandled() = lockScreen.update { it.copy(biometric = null) }

    // Forgotten PIN: erases everything on the device.
    fun eraseAll() {
        viewModelScope.launch { book.eraseAll() }
    }

    private companion object {
        const val LAUNCH_HANDLED = "launch_handled"
    }
}
