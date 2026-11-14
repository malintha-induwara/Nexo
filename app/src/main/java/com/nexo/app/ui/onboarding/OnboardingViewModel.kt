package com.nexo.app.ui.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.repository.BackupManager
import com.nexo.app.data.repository.RestoreResult
import com.nexo.app.data.database.Seeder
import com.nexo.app.core.common.money.CatalogCurrency
import com.nexo.app.core.common.money.CurrencyCatalog
import com.nexo.app.data.datastore.DevicePrefs
import com.nexo.app.data.repository.RepeatEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val step: Int = 0,
    val currency: CatalogCurrency = CurrencyCatalog.forLocale(),
    val busy: Boolean = false,
    val error: RestoreResult? = null,
    val done: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val seeder: Seeder,
    private val backups: BackupManager,
    private val prefs: DevicePrefs,
    private val repeats: RepeatEngine,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state = _state.asStateFlow()

    fun next() = _state.update { it.copy(step = it.step + 1) }
    fun back() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }
    fun setCurrency(c: CatalogCurrency) = _state.update { it.copy(currency = c) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun startFresh() = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        seeder.seedFresh(_state.value.currency)
        finish()
    }

    fun restore(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        when (val r = backups.restore(uri)) {
            RestoreResult.Success -> {
                // Fill anything the backup lacks (e.g. no currency row).
                seeder.seedFresh(_state.value.currency)
                finish()
            }
            else -> _state.update { it.copy(busy = false, error = r) }
        }
    }

    private suspend fun finish() {
        repeats.runIfNewDay(force = true)
        _state.update { it.copy(busy = false, done = true) }
    }

    /** Leaves onboarding; the root switches to the main screen when the flag flips. */
    fun complete() = viewModelScope.launch {
        prefs.setPermissionIntroShown()
        prefs.setSetupDone(true)
    }
}
