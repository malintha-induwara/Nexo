package com.nexo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.notifications.Notifier
import com.nexo.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReminderUiState(
    val device: DeviceSettings? = null,
    // Android 13+ asks the user.
    val canPostNotifications: Boolean = true,
    // Android 12+ asks the user.
    val canScheduleExactAlarms: Boolean = true,
)

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val notifier: Notifier,
) : ViewModel() {
    private val permissions = MutableStateFlow(readPermissions())

    val uiState: StateFlow<ReminderUiState> = combine(
        prefs.getDeviceSettingsStream(),
        permissions,
    ) { device, (post, exact) -> ReminderUiState(device, post, exact) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            permissions.value.let { (post, exact) -> ReminderUiState(null, post, exact) },
        )

    fun setReminder(enabled: Boolean, hour: Int, minute: Int, days: Set<Int>) =
        viewModelScope.launch {
            prefs.setReminder(enabled, hour, minute, days)
            notifier.scheduleReminder(enabled, hour, minute)
        }

    // The user may have changed a permission in system settings while away.
    fun refreshPermissions() {
        permissions.value = readPermissions()
    }

    private fun readPermissions() = notifier.canPost() to notifier.canScheduleExactAlarms()
}
