package com.nexo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.BookSettings
import com.nexo.app.core.model.RefData
import com.nexo.app.data.repository.AppLockRepository
import com.nexo.app.data.repository.BookSettingsRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.RepeatRulesRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val refs: ReferenceDataRepository,
    private val bookSettings: BookSettingsRepository,
    val prefs: UserPreferencesRepository,
    val lock: AppLockRepository,
    private val repeats: RepeatRulesRepository,
) : ViewModel() {
    val ref = refs.getRefDataStream().stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    val device = prefs.getDeviceSettingsStream().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun update(transform: (BookSettings) -> BookSettings) = viewModelScope.launch {
        bookSettings.updateBookSettings(transform)
    }

    fun updateAndRunRepeats(transform: (BookSettings) -> BookSettings) = viewModelScope.launch {
        bookSettings.updateBookSettings(transform)
        repeats.generate()
    }

    fun device(block: suspend UserPreferencesRepository.() -> Unit) = viewModelScope.launch {
        prefs.block()
    }

    fun lockNow() = lock.lockNow()

    fun setBiometricsEnabled(enabled: Boolean) = viewModelScope.launch { lock.setBiometricsEnabled(enabled) }
}
