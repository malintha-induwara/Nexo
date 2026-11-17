package com.nexo.app.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.SmsStatus
import com.nexo.app.core.model.needsAttention
import com.nexo.app.data.repository.SmsRepository
import com.nexo.app.ui.sms.QueueBadge
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MainShellUiState(val smsBadge: QueueBadge = QueueBadge())

@HiltViewModel
class MainShellViewModel @Inject constructor(sms: SmsRepository) : ViewModel() {
    val uiState: StateFlow<MainShellUiState> = sms.getItemsStream(listOf(SmsStatus.PENDING))
        .map { list -> MainShellUiState(QueueBadge(list.size, list.any { it.needsAttention })) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainShellUiState())
}
