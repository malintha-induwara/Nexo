package com.nexo.app.ui.entry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.RepeatReflect
import com.nexo.app.core.model.RepeatRule
import com.nexo.app.data.repository.BookSettingsRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.RepeatRulesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RepeatListUiState(
    val ref: RefData = RefData(),
    val rules: List<RepeatRule> = emptyList(),
)

@HiltViewModel
class RepeatListViewModel @Inject constructor(
    private val repo: RepeatRulesRepository,
    private val refs: ReferenceDataRepository,
    private val bookSettings: BookSettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<RepeatListUiState> = combine(
        refs.getRefDataStream(),
        repo.getRulesStream(),
        ::RepeatListUiState,
    )
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RepeatListUiState())
    fun delete(ids: List<String>) = viewModelScope.launch { repo.delete(ids) }
    fun setReflect(r: RepeatReflect) = viewModelScope.launch {
        bookSettings.updateBookSettings { it.copy(repeatReflect = r) }
        repo.generate()
    }
}
