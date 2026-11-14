package com.nexo.app.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.common.period.DateRange
import com.nexo.app.core.model.DateOrder
import com.nexo.app.core.model.ImportPreview
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.RestoreResult
import com.nexo.app.data.repository.BackupRepository
import com.nexo.app.data.repository.BookRepository
import com.nexo.app.data.repository.ExcelExportRepository
import com.nexo.app.data.repository.PhotoBackupRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.RepeatRulesRepository
import com.nexo.app.data.repository.TransactionImportRepository
import com.nexo.app.ui.HomeState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    val ref: RefData = RefData(),
    val busy: Boolean = false,
    val progress: Progress? = null,
    val backups: List<File> = emptyList(),
    val imports: List<File> = emptyList(),
    val preview: ImportPreview? = null,
    /** Handled once, then cleared with [BackupViewModel.onResultHandled]. */
    val result: BackupResult? = null,
)

/** Shared by the backup, restore, Excel export, import and reset screens. */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backups: BackupRepository,
    private val excel: ExcelExportRepository,
    private val importer: TransactionImportRepository,
    private val photos: PhotoBackupRepository,
    private val book: BookRepository,
    private val repeats: RepeatRulesRepository,
    refs: ReferenceDataRepository,
    private val home: HomeState,
    private val clock: Clock,
) : ViewModel() {
    private val state = MutableStateFlow(BackupUiState())

    val uiState: StateFlow<BackupUiState> = combine(refs.getRefDataStream(), state) { r, s ->
        s.copy(ref = r)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    fun refreshFiles() = state.update {
        it.copy(backups = backups.autoBackups(), imports = importer.files())
    }

    fun backupFileName(): String = backups.backupFileName()

    fun isBackupName(name: String): Boolean = backups.isBackupName(name)

    fun localPhotoCount(): Int = photos.localCount()

    fun onResultHandled() = state.update { it.copy(result = null) }

    private fun work(block: suspend () -> BackupResult?) = viewModelScope.launch {
        state.update { it.copy(busy = true) }
        val result = try {
            block()
        } finally {
            state.update { it.copy(busy = false, progress = null) }
            refreshFiles()
        }
        if (result != null) state.update { it.copy(result = result) }
    }

    fun writeBackup(uri: Uri) = work { BackupResult.BackupWritten(backups.writeBackup(uri)) }

    fun shareBackup() = work { backups.shareCopy()?.let(BackupResult::ShareReady) }

    fun restore(uri: Uri) = work {
        BackupResult.Restored(backups.restore(uri).also { afterRestore(it) })
    }

    fun restoreFile(file: File) = work {
        BackupResult.Restored(backups.restoreFile(file).also { afterRestore(it) })
    }

    private suspend fun afterRestore(r: RestoreResult) {
        if (r == RestoreResult.Success) {
            repeats.generateIfNewDay(force = true)
            home.focusOn(LocalDate.now(clock))
        }
    }

    fun deleteBackups(files: Collection<File>) = work {
        files.forEach { it.delete() }
        null
    }

    fun exportExcel(range: DateRange, total: Boolean) = work {
        BackupResult.ExcelReady(excel.export(range, total), range, total)
    }

    fun addImport(uri: Uri) = work { BackupResult.ImportAdded(importer.add(uri) != null) }

    fun deleteImports(names: Collection<String>) = work {
        importer.delete(names)
        null
    }

    /** Reads the import file into [BackupUiState.preview]. */
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

    fun exportPhotos(tree: Uri) = work {
        BackupResult.PhotosCopied(
            photos.export(tree) { d, t ->
                state.update { it.copy(progress = Progress(d, t)) }
            },
        )
    }

    fun importPhotos(tree: Uri) = work {
        BackupResult.PhotosCopied(
            photos.import(tree) { d, t ->
                state.update { it.copy(progress = Progress(d, t)) }
            },
        )
    }

    fun completeReset(deletePhotos: Boolean) = work {
        book.completeReset(deletePhotos)
        home.focusOn(LocalDate.now(clock))
        BackupResult.ResetDone
    }

    fun contentsReset(deletePhotos: Boolean) = work {
        book.contentsReset(deletePhotos)
        BackupResult.ResetDone
    }
}
