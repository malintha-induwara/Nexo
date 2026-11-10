package com.nexo.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.nexo.app.core.lock.LockManager
import com.nexo.app.core.settings.BookSettings
import com.nexo.app.core.settings.DeviceSettings
import com.nexo.app.core.settings.DevicePrefs
import com.nexo.app.data.RefRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Activity-scoped state: device settings, book settings for colours, lock and pending deep-link targets. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val prefs: DevicePrefs,
    refs: RefRepository,
    val lock: LockManager,
) : ViewModel() {
    val device: StateFlow<DeviceSettings?> = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val book: StateFlow<BookSettings> = refs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, BookSettings())
    val locked = lock.locked

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
}
