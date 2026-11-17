package com.nexo.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.RefData
import com.nexo.app.data.repository.AccountsRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeletedItemsUiState(val ref: RefData = RefData())

@HiltViewModel
class DeletedItemsViewModel @Inject constructor(
    private val repo: AccountsRepository,
    refs: ReferenceDataRepository,
) : ViewModel() {
    val uiState: StateFlow<DeletedItemsUiState> = refs.getRefDataStream()
        .map(::DeletedItemsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeletedItemsUiState())

    fun setStatus(id: String, status: AccountStatus) = viewModelScope.launch {
        repo.setStatus(id, status)
    }

    fun setGroupStatus(id: String, status: GroupStatus) = viewModelScope.launch {
        repo.setGroupStatus(id, status)
    }
}
