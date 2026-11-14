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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.backup.BackupManager
import com.nexo.app.backup.RestoreResult
import com.nexo.app.core.period.DateRange
import com.nexo.app.ui.components.ConfirmDialog
import com.nexo.app.ui.components.EmptyState
import com.nexo.app.ui.components.Formats
import com.nexo.app.ui.components.MessageDialog
import com.nexo.app.ui.components.NexoTopBar
import com.nexo.app.ui.components.SectionHeader
import com.nexo.app.ui.components.SettingRow
import com.nexo.app.ui.navigation.AutoBackupHistoryKey
import com.nexo.app.ui.navigation.BackupDeviceKey
import com.nexo.app.ui.navigation.ExcelExportKey
import com.nexo.app.ui.navigation.HelpPageKey
import com.nexo.app.ui.navigation.ImportListKey
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.MainKey
import com.nexo.app.ui.navigation.RestoreFileKey
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
                LinearProgressIndicator({ if (progress.total == 0) 0f else progress.done / progress.total.toFloat() }, Modifier.fillMaxWidth().padding(top = 8.dp))
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text(stringResource(R.string.please_wait), Modifier.padding(start = 16.dp))
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

@Composable
fun BackupScreen(vm: BackupViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    var resetFlow by rememberSaveable { mutableStateOf<String?>(null) }
    var resetStep by rememberSaveable { mutableStateOf(0) }
    var deletePhotos by rememberSaveable { mutableStateOf(false) }
    var photoResult by remember { mutableStateOf<Int?>(null) }
    val exportTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { vm.exportPhotos(it) { n -> photoResult = n } }
    }
    val importTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { vm.importPhotos(it) { n -> photoResult = n } }
    }
    val subject = stringResource(R.string.backup_mail_subject)
    val body = stringResource(R.string.backup_mail_body)
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.backup), onBack = { nav.back() }) }) { p ->
        Column(Modifier.padding(p).verticalScroll(rememberScrollState())) {
            SettingRow(stringResource(R.string.backup_on_device), onClick = { nav.navigate(BackupDeviceKey) })
            SettingRow(stringResource(R.string.share_backup), onClick = {
                vm.shareBackup { f -> f?.let { shareFile(context, it, BackupManager.MIME, subject, body) } }
            })
            SettingRow(stringResource(R.string.export_to_excel), onClick = { nav.navigate(ExcelExportKey()) })
            SettingRow(stringResource(R.string.import_excel), onClick = { nav.navigate(ImportListKey) })
            SettingRow(stringResource(R.string.export_photos), stringResource(R.string.export_photos_text), onClick = { exportTree.launch(null) })
            SettingRow(stringResource(R.string.import_photos), stringResource(R.string.import_photos_text), onClick = { importTree.launch(null) })
            SettingRow(stringResource(R.string.help_backup), onClick = { nav.navigate(HelpPageKey("backup")) })
            SectionHeader(stringResource(R.string.action_reset))
            SettingRow(stringResource(R.string.complete_reset), onClick = { resetFlow = "all"; resetStep = 1; deletePhotos = false })
            SettingRow(stringResource(R.string.contents_reset), onClick = { resetFlow = "contents"; resetStep = 1; deletePhotos = false })
        }
    }
    BusyOverlay(busy, progress)
    photoResult?.let { MessageDialog(stringResource(R.string.photos_copied, it), onDismiss = { photoResult = null }) }
    val flow = resetFlow
    if (flow != null) {
        val photoCount = vm.photos.localCount()
        val finish = {
            resetFlow = null
            val done = { Toast.makeText(context, R.string.reset_complete, Toast.LENGTH_SHORT).show(); nav.resetTo(MainKey) }
            if (flow == "all") vm.completeReset(deletePhotos) { done() } else vm.contentsReset(deletePhotos) { done() }
        }
        when (resetStep) {
            1 -> ConfirmDialog(
                stringResource(if (flow == "all") R.string.reset_question else R.string.contents_reset_text),
                onConfirm = { resetStep = if (photoCount > 0) 2 else 3 }, onDismiss = { if (resetStep == 1) resetFlow = null },
            )
            2 -> AlertDialog(
                onDismissRequest = { resetFlow = null },
                text = { Text(stringResource(R.string.reset_photos_question)) },
                confirmButton = { TextButton(onClick = { deletePhotos = true; resetStep = 3 }) { Text(stringResource(R.string.action_yes)) } },
                dismissButton = { TextButton(onClick = { deletePhotos = false; resetStep = 3 }) { Text(stringResource(R.string.action_no)) } },
            )
            3 -> ConfirmDialog(
                stringResource(if (flow == "all") R.string.all_data_deleted_question else R.string.reset_question),
                onConfirm = { finish() }, onDismiss = { if (resetStep == 3) resetFlow = null },
            )
        }
    }
}

/** Backup / restore on device. */
@Composable
fun BackupDeviceScreen(vm: BackupViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val files by vm.files.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<Int?>(null) }
    var pending by remember { mutableStateOf<Uri?>(null) }
    var result by remember { mutableStateOf<RestoreResult?>(null) }
    LaunchedEffect(Unit) { vm.refreshFiles() }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { vm.writeBackup(it) { ok -> message = if (ok) R.string.backup_completed else R.string.backup_failed } }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = displayName(context, uri)
            if (name != null && !vm.backups.isBackupName(name)) message = R.string.restore_not_backup else pending = uri
        }
    }
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.backup_on_device), onBack = { nav.back() }) }) { p ->
        Column(Modifier.padding(p)) {
            Text(stringResource(R.string.backup_device_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            SettingRow(stringResource(R.string.data_backup), stringResource(R.string.data_backup_text), onClick = { create.launch(vm.backups.backupFileName()) })
            SettingRow(stringResource(R.string.data_recovery), stringResource(R.string.data_recovery_text), onClick = { open.launch(arrayOf("*/*")) })
            files.firstOrNull()?.let { newest ->
                SettingRow(stringResource(R.string.auto_backup_history), Formats.dateTime(Instant.ofEpochMilli(newest.lastModified()).atZone(ZoneId.systemDefault()).toLocalDateTime()), onClick = { nav.navigate(AutoBackupHistoryKey) })
            }
        }
    }
    BusyOverlay(busy, null)
    message?.let { MessageDialog(stringResource(it), onDismiss = { message = null }) }
    pending?.let { uri ->
        ConfirmDialog(stringResource(R.string.restore_selected_question), onConfirm = { vm.restore(uri) { result = it } }, onDismiss = { pending = null })
    }
    result?.let { r -> MessageDialog(restoreMessage(r), onDismiss = { result = null; if (r == RestoreResult.Success) nav.resetTo(MainKey) }) }
}

fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull() ?: uri.lastPathSegment

/** Restore requested by opening a backup file with Nexo. */
@Composable
fun RestoreFileScreen(key: RestoreFileKey, vm: BackupViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    var result by remember { mutableStateOf<RestoreResult?>(null) }
    var asked by remember { mutableStateOf(true) }
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.data_recovery), onBack = { nav.back() }) }) { p -> Column(Modifier.padding(p)) {} }
    if (asked) ConfirmDialog(stringResource(R.string.restore_selected_question), onConfirm = { asked = false; vm.restore(Uri.parse(key.uri)) { result = it } }, onDismiss = { asked = false; nav.back() })
    BusyOverlay(busy, null)
    result?.let { r -> MessageDialog(restoreMessage(r), onDismiss = { result = null; nav.resetTo(MainKey) }) }
}

@Composable
fun AutoBackupHistoryScreen(vm: BackupViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val files by vm.files.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var restore by remember { mutableStateOf<File?>(null) }
    var result by remember { mutableStateOf<RestoreResult?>(null) }
    LaunchedEffect(Unit) { vm.refreshFiles() }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.auto_backup_history), onBack = { if (editing) editing = false else nav.back() }) {
            if (editing) TextButton(onClick = { if (checked.isNotEmpty()) confirmDelete = true }) { Text(stringResource(R.string.action_delete)) }
            else if (files.isNotEmpty()) IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
        }
    }) { p ->
        if (files.isEmpty()) {
            EmptyState(modifier = Modifier.padding(p))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(p)) {
            items(files, key = { it.name }) { f ->
                Row(Modifier.fillMaxWidth().clickable { if (editing) checked = if (f.name in checked) checked - f.name else checked + f.name else restore = f }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (editing) Checkbox(f.name in checked, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Formats.dateTime(Instant.ofEpochMilli(f.lastModified()).atZone(ZoneId.systemDefault()).toLocalDateTime()))
                        Text("${java.text.NumberFormat.getIntegerInstance().format(f.length() / 1000)} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    BusyOverlay(busy, null)
    if (confirmDelete) ConfirmDialog(stringResource(R.string.confirm_delete), onConfirm = { vm.deleteBackups(files.filter { it.name in checked }); checked = emptySet(); editing = false }, onDismiss = { confirmDelete = false })
    restore?.let { f -> ConfirmDialog(stringResource(R.string.restore_selected_question), onConfirm = { vm.restoreFile(f) { result = it } }, onDismiss = { restore = null }) }
    result?.let { r -> MessageDialog(restoreMessage(r), onDismiss = { result = null; if (r == RestoreResult.Success) nav.resetTo(MainKey) }) }
}

/** Excel period dialog. */
@Composable
fun ExcelExportScreen(key: ExcelExportKey, vm: BackupViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val e = ref.settings.periods
    val today = LocalDate.now()
    val month = key.start?.let { DateRange(LocalDate.parse(it), LocalDate.parse(key.end)) } ?: e.monthPeriodContaining(today).range
    val lastMonth = e.monthPeriodContaining(month.start.minusDays(1)).range
    val year = e.yearRange(e.yearOf(today))
    val lastYear = e.yearRange(e.yearOf(today) - 1)
    val options = listOf(
        Triple(R.string.monthly, month, false), Triple(R.string.last_month, lastMonth, false),
        Triple(R.string.annually, year, false), Triple(R.string.last_year, lastYear, false),
        Triple(R.string.total, DateRange(LocalDate.of(1970, 1, 1), LocalDate.of(9999, 12, 31)), true),
    )
    val noBackup = stringResource(R.string.excel_not_backup)
    val subjectBase = stringResource(R.string.excel_subject)
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.export_to_excel), onBack = { nav.back() }) }) { p ->
        Column(Modifier.padding(p)) {
            options.forEach { (label, range, total) ->
                SettingRow(
                    stringResource(label),
                    if (total) null else "${Formats.shortDate(range.start)} ~ ${Formats.shortDate(range.end)}",
                    onClick = {
                        vm.exportExcel(range, total) { f ->
                            val subject = if (total) subjectBase else "$subjectBase ${Formats.shortDate(range.start)} ~ ${Formats.shortDate(range.end)}"
                            shareFile(context, f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", subject, noBackup)
                        }
                    },
                )
            }
        }
    }
    BusyOverlay(busy, null)
}
