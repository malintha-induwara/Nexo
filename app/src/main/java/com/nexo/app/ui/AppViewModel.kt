package com.nexo.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
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
import kotlinx.coroutines.launch

data class AppUiState(
    /** Null until the device preferences load; the splash screen stays up until then. */
    val device: DeviceSettings? = null,
    val book: BookSettings = BookSettings(),
    val locked: Boolean = false,
    /** Screens to open once the app is unlocked (widgets, shortcuts, notifications). */
    val pending: List<NavKey> = emptyList(),
)

/** Activity-scoped state: device settings, book settings for colours, the app lock and pending deep-link targets. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    refs: ReferenceDataRepository,
    private val lock: AppLockRepository,
    private val book: BookRepository,
) : ViewModel() {
    private val pending = MutableStateFlow<List<NavKey>>(emptyList())

    val uiState: StateFlow<AppUiState> = combine(
        prefs.getDeviceSettingsStream(),
        refs.getRefDataStream().map { it.settings },
        lock.getLockedStream(),
        pending,
        ::AppUiState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    fun setPending(keys: List<NavKey>) {
        pending.value = keys
    }

    fun consumePending(): List<NavKey> = pending.value.also { pending.value = emptyList() }

    fun markWhatsNewSeen() {
        viewModelScope.launch { prefs.setWhatsNewVersion(com.nexo.app.BuildConfig.VERSION_CODE) }
    }

    /** Cipher for the biometric unlock prompt, or null when the key is missing or was invalidated. */
    fun unlockCipher(): Cipher? = lock.getUnlockCipher()

    fun unlock() = lock.unlock()

    suspend fun verifyPin(pin: String): PinResult = lock.verifyPin(pin)

    /** Forgotten PIN: erases everything on the device. */
    fun eraseAll() {
        viewModelScope.launch { book.eraseAll() }
    }

    /** The biometric key was invalidated by an enrolment change. */
    fun disableBiometrics() {
        viewModelScope.launch { lock.setBiometricsEnabled(false) }
    }
}
