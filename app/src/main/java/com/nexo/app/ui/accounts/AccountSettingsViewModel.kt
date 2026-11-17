package com.nexo.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.BookSettings
import com.nexo.app.core.model.RefData
import com.nexo.app.data.repository.AccountsRepository
import com.nexo.app.data.repository.BookSettingsRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountSettingsUiState(val ref: RefData = RefData())

@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    private val repo: AccountsRepository,
    refs: ReferenceDataRepository,
    private val bookSettings: BookSettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<AccountSettingsUiState> = refs.getRefDataStream()
        .map(::AccountSettingsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountSettingsUiState())

    fun updateSettings(transform: (BookSettings) -> BookSettings) = viewModelScope.launch {
        bookSettings.updateBookSettings(transform)
    }

    fun setTransferExpense(id: String, on: Boolean) = viewModelScope.launch {
        repo.setTransferExpense(id, on)
    }
}
