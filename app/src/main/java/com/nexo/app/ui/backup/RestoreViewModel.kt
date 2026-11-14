package com.nexo.app.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.RestoreResult
import com.nexo.app.data.repository.BackupRepository
import com.nexo.app.data.repository.RepeatRulesRepository
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
    val backups: List<File> = emptyList(),
    /** Handled once, then cleared with [RestoreViewModel.onResultHandled]. */
    val result: BackupResult? = null,
)

/** Writing a backup to a file, and restoring from a file or an automatic backup. */
@HiltViewModel
class RestoreViewModel @Inject constructor(
    private val backups: BackupRepository,
    private val repeats: RepeatRulesRepository,
) : ViewModel() {
    private val state = MutableStateFlow(RestoreUiState())
    val uiState: StateFlow<RestoreUiState> = state.asStateFlow()

    fun refreshFiles() = state.update { it.copy(backups = backups.autoBackups()) }

    fun backupFileName(): String = backups.backupFileName()

    fun isBackupName(name: String): Boolean = backups.isBackupName(name)

    fun onResultHandled() = state.update { it.copy(result = null) }

    private fun work(block: suspend () -> BackupResult?) = viewModelScope.launch {
        state.update { it.copy(busy = true) }
        val result = try {
            block()
        } finally {
            state.update { it.copy(busy = false) }
            refreshFiles()
        }
        if (result != null) state.update { it.copy(result = result) }
    }

    fun writeBackup(uri: Uri) = work { BackupResult.BackupWritten(backups.writeBackup(uri)) }

    fun restore(uri: Uri) = work {
        BackupResult.Restored(backups.restore(uri).also { afterRestore(it) })
    }

    fun restoreFile(file: File) = work {
        BackupResult.Restored(backups.restoreFile(file).also { afterRestore(it) })
    }

    private suspend fun afterRestore(r: RestoreResult) {
        if (r == RestoreResult.Success) {
            repeats.generateIfNewDay(force = true)
        }
    }

    fun deleteBackups(files: Collection<File>) = work {
        files.forEach { it.delete() }
        null
    }
}
