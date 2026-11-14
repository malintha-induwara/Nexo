package com.nexo.app.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.common.period.DateRange
import com.nexo.app.core.model.RestoreResult
import com.nexo.app.data.repository.BackupRepository
import com.nexo.app.data.repository.BookRepository
import com.nexo.app.data.repository.PhotoBackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Progress(val done: Int, val total: Int)

/** What a finished backup operation reports to the screen that started it. */
sealed interface BackupResult {
    data class BackupWritten(val ok: Boolean) : BackupResult

    data class ShareReady(val file: File) : BackupResult

    data class Restored(val result: RestoreResult) : BackupResult

    data class ExcelReady(val file: File, val range: DateRange, val total: Boolean) : BackupResult

    data class ImportAdded(val ok: Boolean) : BackupResult

    data object LineRemoved : BackupResult

    data class ImportSaved(val saved: Int, val failed: Int) : BackupResult

    data class PhotosCopied(val count: Int) : BackupResult

    data object ResetDone : BackupResult
}

data class BackupUiState(
    val busy: Boolean = false,
    val progress: Progress? = null,
    /** Handled once, then cleared with [BackupViewModel.onResultHandled]. */
    val result: BackupResult? = null,
)

/** The backup hub: sharing a copy, moving photos, and resetting the book. */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backups: BackupRepository,
    private val photos: PhotoBackupRepository,
    private val book: BookRepository,
) : ViewModel() {
    private val state = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = state.asStateFlow()

    fun localPhotoCount(): Int = photos.localCount()

    fun onResultHandled() = state.update { it.copy(result = null) }

    private fun work(block: suspend () -> BackupResult?) = viewModelScope.launch {
        state.update { it.copy(busy = true) }
        val result = try {
            block()
        } finally {
            state.update { it.copy(busy = false, progress = null) }
        }
        if (result != null) state.update { it.copy(result = result) }
    }

    fun shareBackup() = work { backups.shareCopy()?.let(BackupResult::ShareReady) }

    fun exportPhotos(tree: Uri) = work {
        BackupResult.PhotosCopied(
            photos.export(tree) { d, t -> state.update { it.copy(progress = Progress(d, t)) } },
        )
    }

    fun importPhotos(tree: Uri) = work {
        BackupResult.PhotosCopied(
            photos.import(tree) { d, t -> state.update { it.copy(progress = Progress(d, t)) } },
        )
    }

    fun completeReset(deletePhotos: Boolean) = work {
        book.completeReset(deletePhotos)
        BackupResult.ResetDone
    }

    fun contentsReset(deletePhotos: Boolean) = work {
        book.contentsReset(deletePhotos)
        BackupResult.ResetDone
    }
}
