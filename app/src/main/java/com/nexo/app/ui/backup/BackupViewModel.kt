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
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class Progress(val done: Int, val total: Int)

@HiltViewModel
class BackupViewModel @Inject constructor(
    val backups: BackupRepository,
    val excel: ExcelExportRepository,
    val importer: TransactionImportRepository,
    val photos: PhotoBackupRepository,
    val reset: BookRepository,
    private val repeats: RepeatRulesRepository,
    refs: ReferenceDataRepository,
    private val home: HomeState,
) : ViewModel() {
    val ref = refs.getRefDataStream().stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _progress = MutableStateFlow<Progress?>(null)
    val progress = _progress.asStateFlow()
    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files = _files.asStateFlow()
    private val _imports = MutableStateFlow<List<File>>(emptyList())
    val imports = _imports.asStateFlow()

    fun refreshFiles() {
        _files.value = backups.autoBackups()
        _imports.value = importer.files()
    }

    private fun work(block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try {
            block()
        } finally {
            _busy.value = false
            _progress.value = null
            refreshFiles()
        }
    }

    fun writeBackup(uri: Uri, done: (Boolean) -> Unit) = work { done(backups.writeBackup(uri)) }
    fun shareBackup(done: (File?) -> Unit) = work { done(backups.shareCopy()) }

    fun restore(uri: Uri, done: (RestoreResult) -> Unit) = work {
        val r = backups.restore(uri)
        afterRestore(r)
        done(r)
    }

    fun restoreFile(file: File, done: (RestoreResult) -> Unit) = work {
        val r = backups.restoreFile(file)
        afterRestore(r)
        done(r)
    }

    private suspend fun afterRestore(r: RestoreResult) {
        if (r == RestoreResult.Success) {
            repeats.generateIfNewDay(force = true)
            home.focusOn(LocalDate.now())
        }
    }

    fun deleteBackups(files: Collection<File>) = work { files.forEach { it.delete() } }

    fun exportExcel(range: DateRange, total: Boolean, done: (File) -> Unit) = work { done(excel.export(range, total)) }

    fun addImport(uri: Uri, done: (Boolean) -> Unit) = work { done(importer.add(uri) != null) }
    fun deleteImports(names: Collection<String>) = work { importer.delete(names) }

    suspend fun preview(name: String, order: DateOrder?): ImportPreview = importer.load(name, order)
    fun removeLine(name: String, index: Int, done: () -> Unit) = work { importer.removeLine(name, index); done() }
    fun saveImport(name: String, order: DateOrder?, done: (Int, Int) -> Unit) = work {
        val (ok, failed) = importer.saveAll(name, order)
        done(ok, failed)
    }

    fun exportPhotos(tree: Uri, done: (Int) -> Unit) = work { done(photos.export(tree) { d, t -> _progress.value = Progress(d, t) }) }
    fun importPhotos(tree: Uri, done: (Int) -> Unit) = work { done(photos.import(tree) { d, t -> _progress.value = Progress(d, t) }) }

    fun completeReset(deletePhotos: Boolean, done: () -> Unit) = work { reset.completeReset(deletePhotos); home.focusOn(LocalDate.now()); done() }
    fun contentsReset(deletePhotos: Boolean, done: () -> Unit) = work { reset.contentsReset(deletePhotos); done() }
}
