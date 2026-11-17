package com.nexo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.BookSettings
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.model.RefData
import com.nexo.app.core.notifications.Notifier
import com.nexo.app.data.repository.BookSettingsRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConfigurationUiState(val ref: RefData = RefData(), val device: DeviceSettings? = null)

@HiltViewModel
class ConfigurationViewModel @Inject constructor(
    refs: ReferenceDataRepository,
    private val bookSettings: BookSettingsRepository,
    private val prefs: UserPreferencesRepository,
    private val notifier: Notifier,
    private val clock: Clock,
) : ViewModel() {
    val uiState: StateFlow<ConfigurationUiState> = combine(
        refs.getRefDataStream(),
        prefs.getDeviceSettingsStream(),
        ::ConfigurationUiState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConfigurationUiState())

    fun update(transform: (BookSettings) -> BookSettings) = viewModelScope.launch {
        bookSettings.updateBookSettings(transform)
    }

    fun setNumberPadDescending(descending: Boolean) = viewModelScope.launch {
        prefs.setNumberPadDescending(descending)
    }

    fun setPrivacyButton(on: Boolean) = viewModelScope.launch { prefs.setPrivacyButton(on) }

    // Suggestions only learn from notes written after now.
    fun resetAutocomplete() = update { it.copy(autocompleteResetAt = clock.millis()) }

    fun setQuickAddNotification(on: Boolean) = viewModelScope.launch {
        bookSettings.updateBookSettings { it.copy(quickAddNotification = on) }
        notifier.updateQuickAdd(on)
    }
}
