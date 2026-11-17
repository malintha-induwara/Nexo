package com.nexo.app.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.common.period.DateRange
import com.nexo.app.core.model.RefData
import com.nexo.app.data.repository.ExcelExportRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExcelExportUiState(
    val ref: RefData = RefData(),
    val busy: Boolean = false,
    val result: BackupResult? = null,
)

@HiltViewModel
class ExcelExportViewModel @Inject constructor(
    private val excel: ExcelExportRepository,
    refs: ReferenceDataRepository,
) : ViewModel() {
    private val state = MutableStateFlow(ExcelExportUiState())

    val uiState: StateFlow<ExcelExportUiState> = combine(refs.getRefDataStream(), state) { r, s ->
        s.copy(ref = r)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExcelExportUiState())

    fun onResultHandled() = state.update { it.copy(result = null) }

    fun exportExcel(range: DateRange, total: Boolean) = viewModelScope.launch {
        state.update { it.copy(busy = true) }
        val result = try {
            BackupResult.ExcelReady(excel.export(range, total), range, total)
        } finally {
            state.update { it.copy(busy = false) }
        }
        state.update { it.copy(result = result) }
    }
}
