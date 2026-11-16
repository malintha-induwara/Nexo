package com.nexo.app.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.DateOrder
import com.nexo.app.core.model.ImportPreview
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.StoredFile
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.TransactionImportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportUiState(
    val ref: RefData = RefData(),
    val busy: Boolean = false,
    /** Import files added to the app, waiting to be reviewed. */
    val imports: List<StoredFile> = emptyList(),
    val preview: ImportPreview? = null,
    /** Handled once, then cleared with [ImportViewModel.onResultHandled]. */
    val result: BackupResult? = null,
)

/** Spreadsheet imports: the list of added files and the preview of one. */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importer: TransactionImportRepository,
    refs: ReferenceDataRepository,
) : ViewModel() {
    private val state = MutableStateFlow(ImportUiState())

    val uiState: StateFlow<ImportUiState> = combine(refs.getRefDataStream(), state) { r, s ->
        s.copy(ref = r)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportUiState())

    fun refreshFiles() = viewModelScope.launch { loadFiles() }

    private suspend fun loadFiles() {
        val files = importer.getFiles()
        state.update { it.copy(imports = files) }
    }

    fun onResultHandled() = state.update { it.copy(result = null) }

    private fun work(block: suspend () -> BackupResult?) = viewModelScope.launch {
        state.update { it.copy(busy = true) }
        val result = try {
            block()
        } finally {
            state.update { it.copy(busy = false) }
        }
        loadFiles()
        if (result != null) state.update { it.copy(result = result) }
    }

    fun addImport(uri: Uri) = work { BackupResult.ImportAdded(importer.add(uri) != null) }

    fun deleteImports(names: Collection<String>) = work {
        importer.delete(names)
        null
    }

    /** Reads the import file into [ImportUiState.preview]. */
    fun loadPreview(name: String, order: DateOrder?) = viewModelScope.launch {
        val preview = importer.load(name, order)
        state.update { it.copy(preview = preview) }
    }

    fun removeLine(name: String, index: Int) = work {
        importer.removeLine(name, index)
        BackupResult.LineRemoved
    }

    fun saveImport(name: String, order: DateOrder?) = work {
        val (saved, failed) = importer.saveAll(name, order)
        BackupResult.ImportSaved(saved, failed)
    }
}
