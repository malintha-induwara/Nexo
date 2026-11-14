package com.nexo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.notifications.Notifier
import com.nexo.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReminderUiState(val device: DeviceSettings? = null)

/** The daily reminder: its days and time, and the alarm behind it. */
@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val notifier: Notifier,
) : ViewModel() {
    val uiState: StateFlow<ReminderUiState> = prefs.getDeviceSettingsStream()
        .map(::ReminderUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderUiState())

    fun setReminder(enabled: Boolean, hour: Int, minute: Int, days: Set<Int>) =
        viewModelScope.launch {
            prefs.setReminder(enabled, hour, minute, days)
            notifier.scheduleReminder(enabled, hour, minute)
        }

    fun canPostNotifications(): Boolean = notifier.canPost()

    fun canScheduleExactAlarms(): Boolean = notifier.canScheduleExactAlarms()
}
