package com.nexo.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.model.RefData
import com.nexo.app.data.repository.AccountsRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountGroupsUiState(val ref: RefData = RefData(), val groupSaved: Boolean = false)

@HiltViewModel
class AccountGroupsViewModel @Inject constructor(
    private val repo: AccountsRepository,
    refs: ReferenceDataRepository,
) : ViewModel() {
    private val groupSaved = MutableStateFlow(false)

    val uiState: StateFlow<AccountGroupsUiState> =
        combine(refs.getRefDataStream(), groupSaved, ::AccountGroupsUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountGroupsUiState())

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
}
