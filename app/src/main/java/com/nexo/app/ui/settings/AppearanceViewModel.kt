package com.nexo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.model.ThemeMode
import com.nexo.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppearanceUiState(val device: DeviceSettings? = null)

@HiltViewModel
class AppearanceViewModel @Inject constructor(private val prefs: UserPreferencesRepository) :
    ViewModel() {
    val uiState: StateFlow<AppearanceUiState> = prefs.getDeviceSettingsStream()
        .map(::AppearanceUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceUiState())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }
}
