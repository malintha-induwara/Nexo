package com.nexo.app.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.StoredFile
import com.nexo.app.data.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RestoreUiState(
    val busy: Boolean = false,
    /** Automatic backups on the device, newest first. */
    val backups: List<StoredFile> = emptyList(),
    /** Handled once, then cleared with [RestoreViewModel.onResultHandled]. */
    val result: BackupResult? = null,
)

/** Writing a backup to a file, and restoring from a file or an automatic backup. */
@HiltViewModel
class RestoreViewModel @Inject constructor(private val backups: BackupRepository) : ViewModel() {
    private val state = MutableStateFlow(RestoreUiState())
    val uiState: StateFlow<RestoreUiState> = state.asStateFlow()

    fun refreshFiles() = viewModelScope.launch { loadFiles() }

    private suspend fun loadFiles() {
        val files = backups.getAutoBackups()
        state.update { it.copy(backups = files) }
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

    fun writeBackup(uri: Uri) = work { BackupResult.BackupWritten(backups.writeBackup(uri)) }

    /** A picked file waits for the user's confirmation, unless its name says it is no backup. */
    fun pickRestoreFile(uri: Uri) = viewModelScope.launch {
        val result =
            if (backups.isBackup(uri)) BackupResult.RestorePicked(uri) else BackupResult.NotABackup
        state.update { it.copy(result = result) }
    }

    fun restore(uri: Uri) = work { BackupResult.Restored(backups.restore(uri)) }

    fun restoreFile(file: File) = work { BackupResult.Restored(backups.restoreFile(file)) }

    fun deleteBackups(names: Collection<String>) = work {
        backups.deleteAutoBackups(names)
        null
    }
}
