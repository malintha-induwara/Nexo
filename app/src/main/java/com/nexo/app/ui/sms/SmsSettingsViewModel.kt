package com.nexo.app.ui.sms

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.FeeMode
import com.nexo.app.core.model.RefData
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.entity.FeePresetEntity
import com.nexo.app.data.database.entity.SmsSourceEntity
import com.nexo.app.data.repository.BookSettingsRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.SmsCapture
import com.nexo.app.data.repository.UserPreferencesRepository
import com.nexo.app.data.sms.parser.Banks
import com.nexo.app.domain.sms.SmsApprover
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SmsSettingsViewModel @Inject constructor(
    db: NexoDatabase,
    private val refs: ReferenceDataRepository,
    private val bookSettings: BookSettingsRepository,
    private val prefs: UserPreferencesRepository,
    private val capture: SmsCapture,
    private val approver: SmsApprover,
) : ViewModel() {
    private val dao = db.smsDao()
    val ref: StateFlow<RefData> = refs.getRefDataStream().stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    val device = prefs.getDeviceSettingsStream().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val pendingCount = dao.observePendingCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val sources = dao.observeSources().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val presets = dao.observeFeePresets().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _permitted = MutableStateFlow(capture.hasPermission())
    val permitted: StateFlow<Boolean> = _permitted.asStateFlow()

    /** Coming back from the permission dialog or App info. */
    fun refresh() = viewModelScope.launch {
        _permitted.value = capture.hasPermission()
        capture.refresh()
    }

    fun setEnabled(on: Boolean) = viewModelScope.launch {
        capture.setEnabled(on)
        _permitted.value = capture.hasPermission()
    }

    fun offerHistory() = viewModelScope.launch { prefs.setSmsHistoryImport(com.nexo.app.core.model.SmsHistoryImport.OFFERED) }

    fun skipHistory() = viewModelScope.launch { prefs.setSmsHistoryImport(com.nexo.app.core.model.SmsHistoryImport.SKIPPED) }

    fun addSender(raw: String) = viewModelScope.launch {
        val id = com.nexo.app.data.sms.parser.Banks.normalizeSender(raw)
        if (id.isNotEmpty()) prefs.setSmsCustomSenders(prefs.getDeviceSettings().smsCustomSenders + id)
    }

    fun removeSender(id: String) = viewModelScope.launch { prefs.setSmsCustomSenders(prefs.getDeviceSettings().smsCustomSenders - id) }

    fun setFullText(on: Boolean) = viewModelScope.launch { prefs.setSmsStoreFullText(on) }
    fun setCashAccount(id: String) = viewModelScope.launch { bookSettings.updateBookSettings { it.copy(smsCashAccountId = id) } }
    fun mapSource(source: SmsSourceEntity, accountId: String) = viewModelScope.launch { approver.mapCard(source.bank, source.last4, accountId) }
    fun removeSource(source: SmsSourceEntity) = viewModelScope.launch { approver.unmapCard(source.bank, source.last4) }
    fun setFeeMode(preset: FeePresetEntity, mode: FeeMode) = viewModelScope.launch { approver.setFeeMode(preset.accountId, preset.route, mode) }
}
