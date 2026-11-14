package com.nexo.app.ui.sms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.model.FeeMode
import com.nexo.app.core.model.FeePreset
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.SmsHistoryImport
import com.nexo.app.core.model.SmsSource
import com.nexo.app.data.repository.BookSettingsRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.SmsInboxRepository
import com.nexo.app.data.repository.SmsRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import com.nexo.app.data.sms.parser.Banks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SmsSettingsUiState(
    val ref: RefData = RefData(),
    /** Null until the preferences are read. */
    val device: DeviceSettings? = null,
    val permitted: Boolean = false,
    val pendingCount: Int = 0,
    val sources: List<SmsSource> = emptyList(),
    val presets: List<FeePreset> = emptyList(),
)

@HiltViewModel
class SmsSettingsViewModel @Inject constructor(
    private val sms: SmsRepository,
    refs: ReferenceDataRepository,
    private val bookSettings: BookSettingsRepository,
    private val prefs: UserPreferencesRepository,
    private val inbox: SmsInboxRepository,
) : ViewModel() {
    private val permitted = MutableStateFlow(inbox.hasPermission())

    val uiState: StateFlow<SmsSettingsUiState> = combine(
        refs.getRefDataStream(),
        prefs.getDeviceSettingsStream(),
        permitted,
        sms.getPendingCountStream(),
        combine(sms.getSourcesStream(), sms.getFeePresetsStream(), ::Pair),
    ) { ref, device, permitted, pending, (sources, presets) ->
        SmsSettingsUiState(ref, device, permitted, pending, sources, presets)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SmsSettingsUiState(permitted = inbox.hasPermission()),
    )

    /** Coming back from the permission dialog or App info. */
    fun refresh() = viewModelScope.launch {
        permitted.value = inbox.hasPermission()
        inbox.refresh()
    }

    fun setEnabled(on: Boolean) = viewModelScope.launch {
        inbox.setCaptureEnabled(on)
        permitted.value = inbox.hasPermission()
    }

    fun offerHistory() = viewModelScope.launch {
        prefs.setSmsHistoryImport(SmsHistoryImport.OFFERED)
    }

    fun skipHistory() = viewModelScope.launch {
        prefs.setSmsHistoryImport(SmsHistoryImport.SKIPPED)
    }

    fun addSender(raw: String) = viewModelScope.launch {
        val id = Banks.normalizeSender(raw)
        if (id.isNotEmpty()) {
            prefs.setSmsCustomSenders(prefs.getDeviceSettings().smsCustomSenders + id)
        }
    }

    fun removeSender(id: String) = viewModelScope.launch {
        prefs.setSmsCustomSenders(prefs.getDeviceSettings().smsCustomSenders - id)
    }

    fun setFullText(on: Boolean) = viewModelScope.launch { prefs.setSmsStoreFullText(on) }

    fun setCashAccount(id: String) = viewModelScope.launch {
        bookSettings.updateBookSettings { it.copy(smsCashAccountId = id) }
    }

    fun mapSource(source: SmsSource, accountId: String) = viewModelScope.launch {
        sms.mapCard(source.bank, source.last4, accountId)
    }

    fun removeSource(source: SmsSource) = viewModelScope.launch {
        sms.unmapCard(source.bank, source.last4)
    }

    fun setFeeMode(preset: FeePreset, mode: FeeMode) = viewModelScope.launch {
        sms.setFeeMode(preset.accountId, preset.route, mode)
    }
}
