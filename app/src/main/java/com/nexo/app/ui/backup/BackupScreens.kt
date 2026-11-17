package com.nexo.app.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.common.period.DateRange
import com.nexo.app.core.model.BackupFiles
import com.nexo.app.core.model.RestoreResult
import com.nexo.app.core.model.StoredFile
import com.nexo.app.ui.designsystem.LocalClock
import com.nexo.app.ui.designsystem.component.ConfirmDialog
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.MessageDialog
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.SectionHeader
import com.nexo.app.ui.designsystem.component.SettingRow
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.lock.LocalExpectReturn
import com.nexo.app.ui.navigation.AutoBackupHistoryKey
import com.nexo.app.ui.navigation.BackupDeviceKey
import com.nexo.app.ui.navigation.ExcelExportKey
import com.nexo.app.ui.navigation.HelpPageKey
import com.nexo.app.ui.navigation.ImportListKey
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.MainKey
import com.nexo.app.ui.navigation.NavResult
import com.nexo.app.ui.navigation.Navigator
import com.nexo.app.ui.navigation.RestoreFileKey
import java.io.File
import java.text.NumberFormat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

fun shareFile(context: Context, file: File, mime: String, subject: String, body: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, subject))
}

@Composable
fun BusyOverlay(busy: Boolean, progress: Progress?) {
    if (!busy) return
    AlertDialog(onDismissRequest = {}, confirmButton = {}, text = {
        Column {
            if (progress != null) {
                Text("${progress.done}/${progress.total}")
                LinearProgressIndicator({
                    if (progress.total == 0) {
                        0f
                    } else {
                        progress.done / progress.total.toFloat()
                    }
                }, Modifier.fillMaxWidth().padding(top = 8.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.please_wait), Modifier.padding(start = 16.dp))
                }
            }
        }
    })
}

@Composable
private fun restoreMessage(r: RestoreResult) = stringResource(
    when (r) {
        RestoreResult.Success -> R.string.restore_completed
        RestoreResult.NotABackup -> R.string.restore_not_backup
        RestoreResult.Unsupported -> R.string.restore_unsupported
        is RestoreResult.Failed -> R.string.restore_failed
    },
)

class BackupActions(
    val onBack: () -> Unit = {},
    val onBackupDevice: () -> Unit = {},
    val onShareBackup: () -> Unit = {},
    val onExcelExport: () -> Unit = {},
    val onImportExcel: () -> Unit = {},
    val onExportPhotos: () -> Unit = {},
    val onImportPhotos: () -> Unit = {},
    val onHelp: () -> Unit = {},
    val onCompleteReset: (deletePhotos: Boolean) -> Unit = {},
    val onContentsReset: (deletePhotos: Boolean) -> Unit = {},
    val onPhotosCopiedShown: () -> Unit = {},
)

@Composable
fun BackupRoute(vm: BackupViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val clock = LocalClock.current
    val context = LocalContext.current
    val expectReturn = LocalExpectReturn.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    var photoResult by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { vm.refreshPhotoCount() }
    val subject = stringResource(R.string.backup_mail_subject)
    val body = stringResource(R.string.backup_mail_body)
    LaunchedEffect(state.result) {
        when (val r = state.result) {
            is BackupResult.ShareReady -> shareFile(
                context,
                r.file,
                BackupFiles.MIME,
                subject,
                body,
            )

            is BackupResult.PhotosCopied -> photoResult = r.count

            BackupResult.ResetDone -> {
                Toast.makeText(context, R.string.reset_complete, Toast.LENGTH_SHORT).show()
                nav.toMainOnToday(clock)
            }

            else -> return@LaunchedEffect
        }
        vm.onResultHandled()
    }
    val exportTree =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let(vm::exportPhotos)
        }
    val importTree =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let(vm::importPhotos)
        }
    BackupScreen(
        state = state,
        photosCopied = photoResult,
        actions = BackupActions(
            onBack = nav::back,
            onBackupDevice = { nav.navigate(BackupDeviceKey) },
            onShareBackup = vm::shareBackup,
            onExcelExport = { nav.navigate(ExcelExportKey()) },
            onImportExcel = { nav.navigate(ImportListKey) },
            onExportPhotos = {
                expectReturn()
                exportTree.launch(null)
            },
            onImportPhotos = {
                expectReturn()
                importTree.launch(null)
            },
            onHelp = { nav.navigate(HelpPageKey("backup")) },
            onCompleteReset = vm::completeReset,
            onContentsReset = vm::contentsReset,
            onPhotosCopiedShown = { photoResult = null },
        ),
    )
}

// photosCopied: photos an export or import just copied, shown until dismissed.
@Composable
internal fun BackupScreen(state: BackupUiState, photosCopied: Int?, actions: BackupActions) {
    val busy = state.busy
    val progress = state.progress
    var resetFlow by rememberSaveable { mutableStateOf<String?>(null) }
    var resetStep by rememberSaveable { mutableStateOf(0) }
    var deletePhotos by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.backup), onBack = actions.onBack)
    }) { p ->
        Column(Modifier.padding(p).verticalScroll(rememberScrollState())) {
            SettingRow(stringResource(R.string.backup_on_device), onClick = actions.onBackupDevice)
            SettingRow(stringResource(R.string.share_backup), onClick = actions.onShareBackup)
            SettingRow(stringResource(R.string.export_to_excel), onClick = actions.onExcelExport)
            SettingRow(stringResource(R.string.import_excel), onClick = actions.onImportExcel)
            SettingRow(
                stringResource(R.string.export_photos),
                stringResource(R.string.export_photos_text),
                onClick = actions.onExportPhotos,
            )
            SettingRow(
                stringResource(R.string.import_photos),
                stringResource(R.string.import_photos_text),
                onClick = actions.onImportPhotos,
            )
            SettingRow(stringResource(R.string.help_backup), onClick = actions.onHelp)
            SectionHeader(stringResource(R.string.action_reset))
            SettingRow(stringResource(R.string.complete_reset), onClick = {
                resetFlow = "all"
                resetStep = 1
                deletePhotos = false
            })
            SettingRow(stringResource(R.string.contents_reset), onClick = {
                resetFlow = "contents"
                resetStep = 1
                deletePhotos = false
            })
        }
    }
    BusyOverlay(busy, progress)
    photosCopied?.let {
        MessageDialog(
            stringResource(R.string.photos_copied, it),
            onDismiss = actions.onPhotosCopiedShown,
        )
    }
    val flow = resetFlow
    if (flow != null) {
        val photoCount = state.photoCount
        val finish = {
            resetFlow = null
            if (flow == "all") {
                actions.onCompleteReset(deletePhotos)
            } else {
                actions.onContentsReset(deletePhotos)
            }
        }
        when (resetStep) {
            1 -> ConfirmDialog(
                stringResource(
                    if (flow == "all") {
                        R.string.reset_question
                    } else {
                        R.string.contents_reset_text
                    },
                ),
                onConfirm = {
                    resetStep = if (photoCount > 0) {
                        2
                    } else {
                        3
                    }
                },
                onDismiss = { if (resetStep == 1) resetFlow = null },
            )

            2 -> AlertDialog(
                onDismissRequest = { resetFlow = null },
                text = { Text(stringResource(R.string.reset_photos_question)) },
                confirmButton = {
                    TextButton(onClick = {
                        deletePhotos = true
                        resetStep = 3
                    }) { Text(stringResource(R.string.action_yes)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        deletePhotos = false
                        resetStep = 3
                    }) { Text(stringResource(R.string.action_no)) }
                },
            )

            3 -> ConfirmDialog(
                stringResource(
                    if (flow == "all") {
                        R.string.all_data_deleted_question
                    } else {
                        R.string.reset_question
                    },
                ),
                onConfirm = { finish() },
                onDismiss = { if (resetStep == 3) resetFlow = null },
            )
        }
    }
}

@Composable
fun BackupDeviceRoute(vm: RestoreViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val clock = LocalClock.current
    val expectReturn = LocalExpectReturn.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<Int?>(null) }
    var pending by remember { mutableStateOf<Uri?>(null) }
    var result by remember { mutableStateOf<RestoreResult?>(null) }
    LaunchedEffect(Unit) { vm.refreshFiles() }
    LaunchedEffect(state.result) {
        when (val r = state.result) {
            is BackupResult.BackupWritten ->
                message =
                    if (r.ok) R.string.backup_completed else R.string.backup_failed

            is BackupResult.Restored -> result = r.result

            is BackupResult.RestorePicked -> pending = r.uri

            BackupResult.NotABackup -> message = R.string.restore_not_backup

            else -> return@LaunchedEffect
        }
        vm.onResultHandled()
    }
    val create =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri ->
            uri?.let(vm::writeBackup)
        }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::pickRestoreFile)
    }
    BackupDeviceScreen(
        state = state,
        message = message,
        pendingRestore = pending,
        restoreResult = result,
        onBack = nav::back,
        onBackup = {
            expectReturn()
            create.launch(BackupFiles.name(LocalDateTime.now(clock)))
        },
        onPickBackupFile = {
            expectReturn()
            open.launch(arrayOf("*/*"))
        },
        onAutoBackupHistory = { nav.navigate(AutoBackupHistoryKey) },
        onMessageShown = { message = null },
        onRestore = vm::restore,
        onRestoreDismissed = { pending = null },
        onResultShown = {
            val r = result
            result = null
            if (r == RestoreResult.Success) {
                nav.toMainOnToday(clock)
            }
        },
    )
}

// message is a string resource; pendingRestore is a picked file awaiting confirmation.
@Composable
internal fun BackupDeviceScreen(
    state: RestoreUiState,
    message: Int?,
    pendingRestore: Uri?,
    restoreResult: RestoreResult?,
    onBack: () -> Unit,
    onBackup: () -> Unit,
    onPickBackupFile: () -> Unit,
    onAutoBackupHistory: () -> Unit,
    onMessageShown: () -> Unit,
    onRestore: (Uri) -> Unit,
    onRestoreDismissed: () -> Unit,
    onResultShown: () -> Unit,
) {
    val clock = LocalClock.current
    val busy = state.busy
    val files = state.backups
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.backup_on_device), onBack = onBack)
    }) { p ->
        Column(Modifier.padding(p)) {
            Text(
                stringResource(R.string.backup_device_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            SettingRow(
                stringResource(R.string.data_backup),
                stringResource(R.string.data_backup_text),
                onClick = onBackup,
            )
            SettingRow(
                stringResource(R.string.data_recovery),
                stringResource(R.string.data_recovery_text),
                onClick = onPickBackupFile,
            )
            files.firstOrNull()?.let { newest ->
                SettingRow(
                    stringResource(R.string.auto_backup_history),
                    Formats.dateTime(
                        Instant.ofEpochMilli(
                            newest.modifiedAt,
                        ).atZone(clock.zone).toLocalDateTime(),
                    ),
                    onClick = onAutoBackupHistory,
                )
            }
        }
    }
    BusyOverlay(busy, null)
    message?.let { MessageDialog(stringResource(it), onDismiss = onMessageShown) }
    pendingRestore?.let { uri ->
        ConfirmDialog(stringResource(R.string.restore_selected_question), onConfirm = {
            onRestore(uri)
        }, onDismiss = onRestoreDismissed)
    }
    restoreResult?.let { r ->
        MessageDialog(restoreMessage(r), onDismiss = onResultShown)
    }
}

@Composable
fun RestoreFileRoute(key: RestoreFileKey, vm: RestoreViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val clock = LocalClock.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    var result by remember { mutableStateOf<RestoreResult?>(null) }
    LaunchedEffect(state.result) {
        val r = state.result as? BackupResult.Restored ?: return@LaunchedEffect
        result = r.result
        vm.onResultHandled()
    }
    RestoreFileScreen(
        state = state,
        restoreResult = result,
        onBack = nav::back,
        onRestore = { vm.restore(Uri.parse(key.uri)) },
        onResultShown = {
            val r = result
            result = null
            if (r == RestoreResult.Success) nav.toMainOnToday(clock) else nav.resetTo(MainKey)
        },
    )
}

// Shown when a backup file is opened with Nexo.
@Composable
internal fun RestoreFileScreen(
    state: RestoreUiState,
    restoreResult: RestoreResult?,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onResultShown: () -> Unit,
) {
    val busy = state.busy
    var asked by remember { mutableStateOf(true) }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.data_recovery), onBack = onBack)
    }) { p -> Column(Modifier.padding(p)) {} }
    if (asked) {
        ConfirmDialog(stringResource(R.string.restore_selected_question), onConfirm = {
            asked = false
            onRestore()
        }, onDismiss = {
            asked = false
            onBack()
        })
    }
    BusyOverlay(busy, null)
    restoreResult?.let { r ->
        MessageDialog(restoreMessage(r), onDismiss = onResultShown)
    }
}

@Composable
fun AutoBackupHistoryRoute(vm: RestoreViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val clock = LocalClock.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    var result by remember { mutableStateOf<RestoreResult?>(null) }
    LaunchedEffect(Unit) { vm.refreshFiles() }
    LaunchedEffect(state.result) {
        val r = state.result as? BackupResult.Restored ?: return@LaunchedEffect
        result = r.result
        vm.onResultHandled()
    }
    AutoBackupHistoryScreen(
        state = state,
        restoreResult = result,
        onBack = nav::back,
        onDelete = vm::deleteBackups,
        onRestore = { vm.restoreFile(it.file) },
        onResultShown = {
            val r = result
            result = null
            if (r == RestoreResult.Success) {
                nav.toMainOnToday(clock)
            }
        },
    )
}

@Composable
internal fun AutoBackupHistoryScreen(
    state: RestoreUiState,
    restoreResult: RestoreResult?,
    onBack: () -> Unit,
    onDelete: (names: Set<String>) -> Unit,
    onRestore: (StoredFile) -> Unit,
    onResultShown: () -> Unit,
) {
    val clock = LocalClock.current
    val files = state.backups
    val busy = state.busy
    var editing by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var restore by remember { mutableStateOf<StoredFile?>(null) }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.auto_backup_history), onBack = {
            if (editing) {
                editing = false
            } else {
                onBack()
            }
        }) {
            if (editing) {
                TextButton(onClick = {
                    if (checked.isNotEmpty()) confirmDelete = true
                }) { Text(stringResource(R.string.action_delete)) }
            } else if (files.isNotEmpty()) {
                IconButton(onClick = {
                    editing = true
                }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
            }
        }
    }) { p ->
        if (files.isEmpty()) {
            EmptyState(modifier = Modifier.padding(p))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(p)) {
            items(files, key = { it.name }) { f ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (editing) {
                            checked =
                                if (f.name in checked) {
                                    checked - f.name
                                } else {
                                    checked + f.name
                                }
                        } else {
                            restore = f
                        }
                    }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (editing) {
                        Checkbox(
                            f.name in checked,
                            onCheckedChange = null,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            Formats.dateTime(
                                Instant.ofEpochMilli(
                                    f.modifiedAt,
                                ).atZone(clock.zone).toLocalDateTime(),
                            ),
                        )
                        Text(
                            "${NumberFormat.getIntegerInstance().format(f.sizeBytes / 1000)} KB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    BusyOverlay(busy, null)
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.confirm_delete), onConfirm = {
            onDelete(checked)
            checked = emptySet()
            editing = false
        }, onDismiss = { confirmDelete = false })
    }
    restore?.let { f ->
        ConfirmDialog(stringResource(R.string.restore_selected_question), onConfirm = {
            onRestore(f)
        }, onDismiss = {
            restore = null
        })
    }
    restoreResult?.let { r ->
        MessageDialog(restoreMessage(r), onDismiss = onResultShown)
    }
}

@Composable
fun ExcelExportRoute(key: ExcelExportKey, vm: ExcelExportViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    val noBackup = stringResource(R.string.excel_not_backup)
    val subjectBase = stringResource(R.string.excel_subject)
    LaunchedEffect(state.result) {
        val r = state.result as? BackupResult.ExcelReady ?: return@LaunchedEffect
        val subject = if (r.total) {
            subjectBase
        } else {
            "$subjectBase ${Formats.shortDate(r.range.start)} ~ ${Formats.shortDate(r.range.end)}"
        }
        shareFile(
            context,
            r.file,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            subject,
            noBackup,
        )
        vm.onResultHandled()
    }
    ExcelExportScreen(key = key, state = state, onBack = nav::back, onExport = vm::exportExcel)
}

@Composable
internal fun ExcelExportScreen(
    key: ExcelExportKey,
    state: ExcelExportUiState,
    onBack: () -> Unit,
    onExport: (DateRange, total: Boolean) -> Unit,
) {
    val clock = LocalClock.current
    val ref = state.ref
    val busy = state.busy
    val e = ref.settings.periods
    val today = LocalDate.now(clock)
    val month =
        key.start?.let { DateRange(LocalDate.parse(it), LocalDate.parse(key.end)) }
            ?: e.monthPeriodContaining(today).range
    val lastMonth = e.monthPeriodContaining(month.start.minusDays(1)).range
    val year = e.yearRange(e.yearOf(today))
    val lastYear = e.yearRange(e.yearOf(today) - 1)
    val options = listOf(
        Triple(R.string.monthly, month, false),
        Triple(R.string.last_month, lastMonth, false),
        Triple(R.string.annually, year, false),
        Triple(R.string.last_year, lastYear, false),
        Triple(
            R.string.total,
            DateRange(LocalDate.of(1970, 1, 1), LocalDate.of(9999, 12, 31)),
            true,
        ),
    )
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.export_to_excel), onBack = onBack)
    }) { p ->
        Column(Modifier.padding(p)) {
            options.forEach { (label, range, total) ->
                SettingRow(
                    stringResource(label),
                    if (total) {
                        null
                    } else {
                        "${Formats.shortDate(range.start)} ~ ${Formats.shortDate(range.end)}"
                    },
                    onClick = {
                        onExport(range, total)
                    },
                )
            }
        }
    }
    BusyOverlay(busy, null)
}

// For previews; nothing on disk.
internal fun previewFile(name: String, modified: Instant, bytes: Long): StoredFile =
    StoredFile(File(name), modified.toEpochMilli(), bytes)

private val previewBackups = listOf(
    previewFile("nexo_auto_2.nexobak", Instant.parse("2026-11-14T03:00:00Z"), 184_000),
    previewFile("nexo_auto_1.nexobak", Instant.parse("2026-11-13T03:00:00Z"), 181_000),
)

@Preview
@Composable
private fun BackupScreenPreview() = NexoPreview {
    BackupScreen(state = BackupUiState(), photosCopied = null, actions = BackupActions())
}

@Preview
@Composable
private fun BackupDeviceScreenPreview() = NexoPreview {
    BackupDeviceScreen(
        state = RestoreUiState(backups = previewBackups),
        message = null,
        pendingRestore = null,
        restoreResult = null,
        onBack = {},
        onBackup = {},
        onPickBackupFile = {},
        onAutoBackupHistory = {},
        onMessageShown = {},
        onRestore = {},
        onRestoreDismissed = {},
        onResultShown = {},
    )
}

@Preview
@Composable
private fun RestoreFileScreenPreview() = NexoPreview {
    RestoreFileScreen(
        state = RestoreUiState(),
        restoreResult = null,
        onBack = {},
        onRestore = {},
        onResultShown = {},
    )
}

@Preview
@Composable
private fun AutoBackupHistoryScreenPreview() = NexoPreview {
    AutoBackupHistoryScreen(
        state = RestoreUiState(backups = previewBackups),
        restoreResult = null,
        onBack = {},
        onDelete = {},
        onRestore = {},
        onResultShown = {},
    )
}

@Preview
@Composable
private fun ExcelExportScreenPreview() = NexoPreview {
    ExcelExportScreen(
        key = ExcelExportKey(),
        state = ExcelExportUiState(ref = PreviewData.ref),
        onBack = {},
        onExport = { _, _ -> },
    )
}

private fun Navigator.toMainOnToday(clock: Clock) {
    results.post(NavResult.Focus(LocalDate.now(clock)))
    resetTo(MainKey)
}
