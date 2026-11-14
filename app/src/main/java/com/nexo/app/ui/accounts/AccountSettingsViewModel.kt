package com.nexo.app.ui.accounts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.RefData
import com.nexo.app.data.repository.AccountsRepository
import com.nexo.app.data.repository.BookSettingsRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.domain.Balances
import com.nexo.app.ui.designsystem.theme.nexo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    val repo: AccountsRepository,
    private val refs: ReferenceDataRepository,
    private val bookSettings: BookSettingsRepository,
    private val transactions: TransactionsRepository,
) : ViewModel() {
    val ref: StateFlow<RefData> = refs.getRefDataStream().stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    val balances: StateFlow<Map<String?, com.nexo.app.domain.Net>> = transactions.getAggregatesUpToStream("9999-12-31")
        .mapLatest { Balances.netByAccount(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
    fun updateSettings(transform: (com.nexo.app.core.model.BookSettings) -> com.nexo.app.core.model.BookSettings) = viewModelScope.launch { bookSettings.updateBookSettings(transform) }
}
