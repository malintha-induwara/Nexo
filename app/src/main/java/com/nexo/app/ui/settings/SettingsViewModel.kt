package com.nexo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.data.repository.AppLockRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(val device: DeviceSettings? = null)

// Backs the More tab.
@HiltViewModel
class SettingsViewModel @Inject constructor(
    prefs: UserPreferencesRepository,
    private val lock: AppLockRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = prefs.getDeviceSettingsStream()
        .map(::SettingsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun lockNow() = lock.lockNow()
}
