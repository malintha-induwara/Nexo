package com.nexo.app.ui.backup

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.DateOrder
import com.nexo.app.core.model.ImportError
import com.nexo.app.core.model.ImportErrorType
import com.nexo.app.core.model.ImportPreview
import com.nexo.app.core.model.ImportRow
import com.nexo.app.ui.designsystem.LocalClock
import com.nexo.app.ui.designsystem.component.ConfirmDialog
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.lock.expectReturn
import com.nexo.app.ui.navigation.AccountFormKey
import com.nexo.app.ui.navigation.AccountListKey
import com.nexo.app.ui.navigation.CategoryFormKey
import com.nexo.app.ui.navigation.CategoryListKey
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.navigation.HelpPageKey
import com.nexo.app.ui.navigation.ImportListKey
import com.nexo.app.ui.navigation.ImportPreviewKey
import com.nexo.app.ui.navigation.LocalNavigator
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ImportListRoute(vm: ImportViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshFiles() }
    LaunchedEffect(state.result) {
        val r = state.result as? BackupResult.ImportAdded ?: return@LaunchedEffect
        if (!r.ok) Toast.makeText(context, R.string.not_excel_file, Toast.LENGTH_SHORT).show()
        vm.onResultHandled()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::addImport)
    }
    ImportListScreen(
        state = state,
        onBack = nav::back,
        onHelp = { nav.navigate(HelpPageKey("import")) },
        onPickFile = {
            context.expectReturn()
            picker.launch(
                arrayOf("text/*", "application/octet-stream", "text/tab-separated-values"),
            )
        },
        onOpen = { name -> nav.navigate(ImportPreviewKey(name)) },
        onDelete = vm::deleteImports,
    )
}

@Composable
internal fun ImportListScreen(
    state: ImportUiState,
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onPickFile: () -> Unit,
    onOpen: (fileName: String) -> Unit,
    onDelete: (fileNames: Set<String>) -> Unit,
) {
    val clock = LocalClock.current
    val files = state.imports
    var editing by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.import_excel), onBack = onBack) {
            IconButton(onClick = onHelp) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.help))
            }
            if (editing) {
                TextButton(onClick = {
                    if (checked.isNotEmpty()) confirm = true
                }) { Text(stringResource(R.string.action_delete)) }
            } else if (files.isNotEmpty()) {
                IconButton(onClick = {
                    editing = true
                }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
            }
        }
    }) { p ->
        Column(Modifier.padding(p)) {
            Text(
                stringResource(R.string.import_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            Button(
                onClick = onPickFile,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.import_excel))
            }
            if (files.isEmpty()) EmptyState()
            LazyColumn {
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
                                onOpen(f.name)
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
                        Column {
                            Text(f.name)
                            Text(
                                Formats.dateTime(
                                    Instant.ofEpochMilli(
                                        f.lastModified(),
                                    ).atZone(clock.zone).toLocalDateTime(),
                                ) +
                                    " · ${f.length() / 1000} KB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(stringResource(R.string.confirm_delete), onConfirm = {
            onDelete(checked)
            checked = emptySet()
            editing = false
        }, onDismiss = { confirm = false })
    }
}

@Composable
private fun errorText(row: ImportRow): String? {
    val clock = LocalClock.current
    val e = row.error ?: return null
    return when (e.type) {
        ImportErrorType.DATE -> stringResource(
            R.string.import_error_date,
            LocalDate.now(clock).toString(),
        )

        ImportErrorType.TYPE -> stringResource(R.string.import_error_type)

        ImportErrorType.AMOUNT -> stringResource(R.string.import_error_amount)

        ImportErrorType.INCOME_CATEGORY, ImportErrorType.EXPENSE_CATEGORY -> stringResource(
            R.string.import_error_category,
            e.name ?: "",
        )

        ImportErrorType.INCOME_SUB, ImportErrorType.EXPENSE_SUB -> stringResource(
            R.string.import_error_subcategory,
            e.name ?: "",
            e.parent ?: "",
        )

        ImportErrorType.ACCOUNT -> stringResource(R.string.import_error_account, e.name ?: "")
    }
}

/** What the import preview can do; each action defaults to nothing. */
class ImportPreviewActions(
    val onBack: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onRemoveLine: (line: Int) -> Unit = {},
    val onEditLine: (line: Int) -> Unit = {},
    val onDateOrder: (DateOrder) -> Unit = {},
    val onCategories: (CategoryKind) -> Unit = {},
    val onAddCategory: (CategoryKind, name: String?) -> Unit = { _, _ -> },
    val onAddSubcategory: (CategoryKind, parentId: String?, name: String?) -> Unit = { _, _, _ -> },
    val onAccounts: () -> Unit = {},
    val onAddAccount: (name: String?) -> Unit = {},
)

@Composable
fun ImportPreviewRoute(key: ImportPreviewKey, vm: ImportViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    var order by rememberSaveable { mutableStateOf<DateOrder?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(order, reload) { vm.loadPreview(key.fileName, order) }
    LaunchedEffect(state.result) {
        when (val r = state.result) {
            BackupResult.LineRemoved -> reload++

            is BackupResult.ImportSaved -> if (r.failed == 0) {
                Toast.makeText(context, R.string.saved, Toast.LENGTH_SHORT).show()
                nav.backTo { it is ImportListKey }
            } else {
                Toast.makeText(
                    context,
                    resources.getString(R.string.import_partial, r.saved, r.failed),
                    Toast.LENGTH_LONG,
                ).show()
                reload++
            }

            else -> return@LaunchedEffect
        }
        vm.onResultHandled()
    }
    LifecycleResumeEffect(Unit) {
        reload++
        onPauseOrDispose {}
    }
    ImportPreviewScreen(
        state = state,
        actions = ImportPreviewActions(
            onBack = nav::back,
            onSave = { vm.saveImport(key.fileName, order) },
            onRemoveLine = { line -> vm.removeLine(key.fileName, line) },
            onEditLine = { line ->
                nav.navigate(
                    EntryKey(
                        mode = EntryMode.IMPORT_FIX,
                        importFile = key.fileName,
                        importLine = line,
                        importOrder = order?.name,
                    ),
                )
            },
            onDateOrder = { order = it },
            onCategories = { kind -> nav.navigate(CategoryListKey(kind)) },
            onAddCategory = { kind, name ->
                nav.navigate(CategoryFormKey(kind, presetName = name))
            },
            onAddSubcategory = { kind, parentId, name ->
                nav.navigate(
                    CategoryFormKey(kind, parentId = parentId, sub = true, presetName = name),
                )
            },
            onAccounts = { nav.navigate(AccountListKey()) },
            onAddAccount = { name -> nav.navigate(AccountFormKey(presetName = name)) },
        ),
    )
}

/** Import preview with row fixing. */
@Composable
internal fun ImportPreviewScreen(state: ImportUiState, actions: ImportPreviewActions) {
    val clock = LocalClock.current
    val ref = state.ref
    val busy = state.busy
    val preview = state.preview
    var shown by rememberSaveable { mutableIntStateOf(50) }
    var fix by remember { mutableStateOf<ImportRow?>(null) }
    var confirmSave by remember { mutableStateOf(false) }
    var deleteRow by remember { mutableStateOf<ImportRow?>(null) }
    val rows = preview?.rows.orEmpty()
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.import_input_n, rows.size), onBack = actions.onBack) {
            TextButton(onClick = {
                confirmSave = true
            }, enabled = rows.isNotEmpty()) { Text(stringResource(R.string.action_save)) }
        }
    }) { p ->
        LazyColumn(Modifier.padding(p)) {
            if (preview != null && rows.isEmpty()) item { EmptyState() }
            items(rows.take(shown), key = { "${it.index}_${it.raw.hashCode()}" }) { row ->
                val err = errorText(row)
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (row.error != null) {
                            fix = row
                        } else {
                            actions.onEditLine(row.index)
                        }
                    }.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(
                            row.raw.replace('\t', ' '),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        err?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    IconButton(onClick = {
                        deleteRow = row
                    }) {
                        Icon(
                            Icons.Filled.RemoveCircle,
                            stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (rows.size > shown) {
                item {
                    TextButton(onClick = {
                        shown += 50
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_more))
                    }
                }
            }
        }
    }
    BusyOverlay(busy, null)
    deleteRow?.let { r ->
        ConfirmDialog(stringResource(R.string.confirm_delete), onConfirm = {
            actions.onRemoveLine(r.index)
        }, onDismiss = {
            deleteRow = null
        })
    }
    if (confirmSave) {
        ConfirmDialog(
            stringResource(R.string.save_question),
            onConfirm = actions.onSave,
            onDismiss = { confirmSave = false },
        )
    }
    fix?.let { row ->
        val e = row.error!!
        val edit = { actions.onEditLine(row.index) }
        val kind = if (e.type == ImportErrorType.INCOME_CATEGORY ||
            e.type == ImportErrorType.INCOME_SUB
        ) {
            CategoryKind.INCOME
        } else {
            CategoryKind.EXPENSE
        }
        AlertDialog(
            onDismissRequest = { fix = null },
            title = { Text(errorText(row) ?: "") },
            text = {
                Column {
                    TextButton(onClick = {
                        fix = null
                        edit()
                    }) { Text(stringResource(R.string.action_edit)) }
                    when (e.type) {
                        ImportErrorType.DATE -> DateOrder.entries.forEach { o ->
                            TextButton(onClick = {
                                fix = null
                                actions.onDateOrder(o)
                            }) {
                                val sample = LocalDate.now(clock).format(
                                    DateTimeFormatter.ofPattern(o.pattern),
                                )
                                Text("${o.pattern}  ($sample)")
                            }
                        }

                        ImportErrorType.INCOME_CATEGORY, ImportErrorType.EXPENSE_CATEGORY -> {
                            TextButton(onClick = {
                                fix = null
                                actions.onCategories(kind)
                            }) {
                                Text(
                                    stringResource(
                                        if (kind == CategoryKind.INCOME) {
                                            R.string.income_category_setting
                                        } else {
                                            R.string.expenses_category_setting
                                        },
                                    ),
                                )
                            }
                            TextButton(onClick = {
                                fix = null
                                actions.onAddCategory(kind, e.name)
                            }) {
                                Text(stringResource(R.string.add_named, e.name ?: ""))
                            }
                        }

                        ImportErrorType.INCOME_SUB, ImportErrorType.EXPENSE_SUB -> {
                            TextButton(onClick = {
                                fix = null
                                actions.onCategories(kind)
                            }) {
                                Text(
                                    stringResource(
                                        if (kind == CategoryKind.INCOME) {
                                            R.string.income_category_setting
                                        } else {
                                            R.string.expenses_category_setting
                                        },
                                    ),
                                )
                            }
                            val parentId = ref.mainCategories(kind).firstOrNull {
                                it.name ==
                                    e.parent
                            }?.id
                            TextButton(onClick = {
                                fix = null
                                actions.onAddSubcategory(kind, parentId, e.name)
                            }) {
                                Text(stringResource(R.string.add_named, "${e.name} → ${e.parent}"))
                            }
                        }

                        ImportErrorType.ACCOUNT -> {
                            TextButton(onClick = {
                                fix = null
                                actions.onAccounts()
                            }) { Text(stringResource(R.string.tab_accounts)) }
                            TextButton(onClick = {
                                fix = null
                                actions.onAddAccount(e.name)
                            }) {
                                Text(stringResource(R.string.add_named, e.name ?: ""))
                            }
                        }

                        else -> Unit
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    fix = null
                }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun previewImportRow(index: Int, raw: String, error: ImportError? = null) =
    ImportRow(index, raw, raw.split('\t'), error, parsed = null)

private val previewImport = ImportPreview(
    rows = listOf(
        previewImportRow(1, "2026-11-12\tExpense\tWallet\tFood\tGroceries\t2,450.00"),
        previewImportRow(
            2,
            "2026-11-13\tExpense\tWallet\tPets\t\t1,200.00",
            ImportError(ImportErrorType.EXPENSE_CATEGORY, name = "Pets"),
        ),
        previewImportRow(3, "2026-11-13\tIncome\tSavings\tSalary\t\t180,000.00"),
    ),
    wide = false,
)

@Preview
@Composable
private fun ImportListScreenPreview() = NexoPreview {
    ImportListScreen(
        state = ImportUiState(
            imports = listOf(
                previewFile("november.tsv", Instant.parse("2026-11-14T05:10:00Z"), 12_000),
            ),
        ),
        onBack = {},
        onHelp = {},
        onPickFile = {},
        onOpen = {},
        onDelete = {},
    )
}

@Preview
@Composable
private fun ImportPreviewScreenPreview() = NexoPreview {
    ImportPreviewScreen(
        state = ImportUiState(ref = PreviewData.ref, preview = previewImport),
        actions = ImportPreviewActions(),
    )
}
