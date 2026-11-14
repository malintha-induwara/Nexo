package com.nexo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.repository.LockManager
import com.nexo.app.core.model.BookSettings
import com.nexo.app.data.datastore.DevicePrefs
import com.nexo.app.data.repository.RefRepository
import com.nexo.app.data.repository.RepeatEngine
import com.nexo.app.core.model.RefData
import com.nexo.app.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val refs: RefRepository,
    val prefs: DevicePrefs,
    val lock: LockManager,
    private val repeats: RepeatEngine,
    private val widgets: WidgetRefresher,
) : ViewModel() {
    val ref = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    val device = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun update(transform: (BookSettings) -> BookSettings) = viewModelScope.launch {
        refs.updateSettings(transform)
        widgets.refresh()
    }

    fun updateAndRunRepeats(transform: (BookSettings) -> BookSettings) = viewModelScope.launch {
        refs.updateSettings(transform)
        repeats.run()
    }

    fun device(block: suspend DevicePrefs.() -> Unit) = viewModelScope.launch {
        prefs.block()
        widgets.refresh()
    }

    fun lockNow() = lock.lockNow()
}
