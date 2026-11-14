package com.nexo.app.ui.accounts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.BookSettings
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.model.RefData
import com.nexo.app.data.repository.AccountsRepository
import com.nexo.app.data.repository.BookSettingsRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.domain.Balances
import com.nexo.app.domain.Net
import com.nexo.app.ui.designsystem.theme.nexo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountSettingsUiState(
    val ref: RefData = RefData(),
    /** Net per account (key null = the "None" pseudo-account). */
    val balances: Map<String?, Net> = emptyMap(),
    /** A group was saved: the form goes back, then calls [AccountSettingsViewModel.onGroupSavedHandled]. */
    val groupSaved: Boolean = false,
)

/** Shared by the account and group management screens. */
@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    private val repo: AccountsRepository,
    refs: ReferenceDataRepository,
    private val bookSettings: BookSettingsRepository,
    transactions: TransactionsRepository,
) : ViewModel() {
    private val groupSaved = MutableStateFlow(false)

    val uiState: StateFlow<AccountSettingsUiState> = combine(
        refs.getRefDataStream(),
        transactions.getAggregatesUpToStream("9999-12-31").mapLatest { Balances.netByAccount(it) },
        groupSaved,
        ::AccountSettingsUiState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountSettingsUiState())

    fun updateSettings(transform: (BookSettings) -> BookSettings) = viewModelScope.launch {
        bookSettings.updateBookSettings(transform)
    }

    fun reorderGroups(ids: List<String>) = viewModelScope.launch { repo.reorderGroups(ids) }

    fun setGroupStatus(id: String, status: GroupStatus) = viewModelScope.launch {
        repo.setGroupStatus(id, status)
    }

    fun saveGroup(id: String?, name: String, type: GroupType) = viewModelScope.launch {
        repo.saveGroup(id, name, type)
        groupSaved.value = true
    }

    fun onGroupSavedHandled() {
        groupSaved.value = false
    }

    fun setStatus(id: String, status: AccountStatus) = viewModelScope.launch {
        repo.setStatus(id, status)
    }

    /** Order = position in the whole list; the group may change. */
    fun reorderAccounts(order: List<Pair<String, String>>) =
        viewModelScope.launch { repo.reorderAccounts(order) }

    fun setTransferExpense(id: String, on: Boolean) = viewModelScope.launch {
        repo.setTransferExpense(id, on)
    }
}
