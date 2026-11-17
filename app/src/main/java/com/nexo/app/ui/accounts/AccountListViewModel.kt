package com.nexo.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.RefData
import com.nexo.app.data.repository.AccountsRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.domain.Balances
import com.nexo.app.domain.Net
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountListUiState(
    val ref: RefData = RefData(),
    // The null key is the "None" pseudo-account.
    val balances: Map<String?, Net> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountListViewModel @Inject constructor(
    private val repo: AccountsRepository,
    refs: ReferenceDataRepository,
    transactions: TransactionsRepository,
) : ViewModel() {
    val uiState: StateFlow<AccountListUiState> = combine(
        refs.getRefDataStream(),
        transactions.getAggregatesUpToStream("9999-12-31").mapLatest { Balances.netByAccount(it) },
        ::AccountListUiState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountListUiState())

    fun setStatus(id: String, status: AccountStatus) = viewModelScope.launch {
        repo.setStatus(id, status)
    }

    // Order is the position in the whole list; an account's group may change.
    fun reorderAccounts(order: List<Pair<String, String>>) =
        viewModelScope.launch { repo.reorderAccounts(order) }
}
