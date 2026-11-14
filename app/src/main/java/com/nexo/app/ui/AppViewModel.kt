package com.nexo.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.nexo.app.core.model.BookSettings
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.data.repository.AppLockRepository
import com.nexo.app.data.repository.DataReset
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Activity-scoped state: device settings, book settings for colours, lock and pending deep-link targets. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    refs: ReferenceDataRepository,
    val lock: AppLockRepository,
    private val reset: DataReset,
) : ViewModel() {
    val device: StateFlow<DeviceSettings?> = prefs.getDeviceSettingsStream().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val book: StateFlow<BookSettings> = refs.getRefDataStream().map { it.settings }.stateIn(viewModelScope, SharingStarted.Eagerly, BookSettings())
    val locked = lock.getLockedStream()

    private val _pending = MutableStateFlow<List<NavKey>>(emptyList())
    /** Screens to open once the app is unlocked (widgets, shortcuts, notifications). */
    val pending: StateFlow<List<NavKey>> = _pending.asStateFlow()

    fun setPending(keys: List<NavKey>) {
        _pending.value = keys
    }

    fun consumePending(): List<NavKey> = _pending.value.also { _pending.value = emptyList() }

    fun markWhatsNewSeen() {
        viewModelScope.launch { prefs.setWhatsNewVersion(com.nexo.app.BuildConfig.VERSION_CODE) }
    }

    /** Forgotten PIN: erases everything on the device. */
    fun eraseAll() {
        viewModelScope.launch { reset.eraseAll() }
    }

    /** The biometric key was invalidated by an enrolment change. */
    fun disableBiometrics() {
        viewModelScope.launch { lock.setBiometricsEnabled(false) }
    }
}
