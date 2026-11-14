package com.nexo.app.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.ui.components.IncomeExpensePill
import com.nexo.app.core.database.entity.CurrencyEntity
import com.nexo.app.core.model.CashFlow
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.money.AmountFormatter
import com.nexo.app.core.money.Money
import com.nexo.app.data.TransactionWriter
import com.nexo.app.domain.RefData
import com.nexo.app.ui.components.ConfirmDialog
import com.nexo.app.ui.components.MessageDialog
import com.nexo.app.ui.components.NexoDatePickerDialog
import com.nexo.app.ui.components.TextFieldDialog
import com.nexo.app.ui.theme.nexo
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class BulkAction { DATE, CATEGORY, ACCOUNT, NOTE }

/** Σ selected income − Σ selected expense in main currency. */
fun selectedNet(rows: List<TxRowUi>, ids: Set<String>): Double = rows.filter { it.id in ids }.fold(0.0) { acc, r ->
    when (r.flow) {
        CashFlow.INCOME -> Money.add(acc, r.amountMain)
        CashFlow.EXPENSE -> Money.subtract(acc, r.amountMain)
        CashFlow.NEUTRAL -> acc
    }
}

/** Edit bar shown during multi-select. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionBar(
    count: Int,
    total: Int,
    net: Double,
    main: CurrencyEntity,
    onToggleAll: () -> Unit,
    onClose: () -> Unit,
    onAction: (BulkAction?) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column {
        TopAppBar(
            title = { Text(if (count == 0) stringResource(R.string.please_select_item) else stringResource(R.string.n_selected, count), style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                com.nexo.app.ui.components.RoundIconButton(
                    androidx.compose.material.icons.Icons.Rounded.Close, stringResource(R.string.action_back), onClose,
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                )
            },
            actions = {
                IconButton(onClick = { onAction(null) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    listOf(
                        BulkAction.DATE to R.string.edit_all_dates,
                        BulkAction.CATEGORY to R.string.edit_all_categories,
                        BulkAction.ACCOUNT to R.string.edit_all_assets,
                        BulkAction.NOTE to R.string.edit_all_notes,
                    ).forEach { (a, label) -> DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { menu = false; onAction(a) }) }
                }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TriStateCheckbox(
                state = when {
                    count == 0 -> ToggleableState.Off
                    count >= total -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                },
                onClick = onToggleAll,
            )
            Text(stringResource(R.string.action_select_all), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            com.nexo.app.ui.components.RollingAmount(net, main, style = com.nexo.app.ui.theme.NexoType.Amount, minorScale = 0.8f)
        }
    }
}

/** Dialogs behind the delete icon and the "Edit All …" menu. */
@Composable
fun BulkDialogs(
    action: BulkAction?,
    open: Boolean,
    count: Int,
    ref: RefData,
    writer: TransactionWriter,
    ids: Set<String>,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!open) return
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun run(block: suspend () -> Unit) {
        scope.launch {
            block()
            onDone()
        }
        onDismiss()
    }
    if (count == 0) {
        MessageDialog(stringResource(R.string.please_select_item), onDismiss = onDismiss)
        return
    }
    when (action) {
        null -> ConfirmDialog(
            message = stringResource(R.string.want_to_delete_n, count),
            onConfirm = { run { writer.delete(ids) } },
            onDismiss = onDismiss,
        )
        BulkAction.DATE -> NexoDatePickerDialog(LocalDate.now(), onPick = { d -> run { writer.setDate(ids, d) } }, onDismiss = onDismiss)
        BulkAction.NOTE -> TextFieldDialog(
            title = stringResource(R.string.edit_all_notes), initial = "", label = stringResource(R.string.note),
            onConfirm = { n -> run { writer.setNote(ids, n) } }, onDismiss = onDismiss,
        )
        BulkAction.CATEGORY -> CategoryPickerDialog(ref, onPick = { id -> run { writer.setCategory(ids, id) } }, onDismiss = onDismiss)
        BulkAction.ACCOUNT -> AccountPickerDialog(
            ref,
            onAccount = { id -> run { writer.setAccount(ids, id) } },
            onTransfer = { from, to -> run { writer.setTransfer(ids, from, to) } },
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun CategoryPickerDialog(ref: RefData, onPick: (String) -> Unit, onDismiss: () -> Unit, allowAll: Boolean = false, onAll: (() -> Unit)? = null, kinds: List<CategoryKind> = listOf(CategoryKind.INCOME, CategoryKind.EXPENSE)) {
    var tab by remember { mutableIntStateOf(if (kinds.size == 1) 0 else 1) }
    var expanded by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.category)) },
        text = {
            Column {
                if (kinds.size > 1) {
                    IncomeExpensePill(tab, onSelect = { tab = it }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                }
                val kind = if (kinds.size == 1) kinds[0] else if (tab == 0) CategoryKind.INCOME else CategoryKind.EXPENSE
                LazyColumn(Modifier.height(380.dp)) {
                    if (allowAll && onAll != null) item {
                        Text(stringResource(R.string.all), Modifier.fillMaxWidth().clickable { onAll() }.padding(vertical = 12.dp), fontWeight = FontWeight.SemiBold)
                    }
                    items(ref.mainCategories(kind), key = { it.id }) { c ->
                        val subs = if (ref.settings.subcategories) ref.subCategories(c.id) else emptyList()
                        Column {
                            Text(
                                c.name + if (subs.isNotEmpty()) "  ›" else "",
                                Modifier.fillMaxWidth().clickable { if (subs.isEmpty() || expanded == c.id) onPick(c.id) else expanded = c.id }.padding(vertical = 12.dp),
                            )
                            if (expanded == c.id) subs.forEach { sub ->
                                Text(sub.name, Modifier.fillMaxWidth().clickable { onPick(sub.id) }.padding(start = 24.dp, top = 10.dp, bottom = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** One account, or a From/To pair that turns the selection into transfers. */
@Composable
fun AccountPickerDialog(ref: RefData, onAccount: (String) -> Unit, onTransfer: ((String, String) -> Unit)?, onDismiss: () -> Unit) {
    var pair by remember { mutableStateOf(false) }
    var from by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (!pair) R.string.account else if (from == null) R.string.from else R.string.to), Modifier.weight(1f))
                if (onTransfer != null) {
                    Text(stringResource(R.string.transfer), style = MaterialTheme.typography.bodyMedium)
                    Switch(pair, { pair = it; from = null }, Modifier.padding(start = 8.dp))
                }
            }
        },
        text = {
            LazyColumn(Modifier.height(380.dp)) {
                var lastGroup: String? = null
                ref.pickerAccounts.forEach { a ->
                    if (a.groupId != lastGroup) {
                        lastGroup = a.groupId
                        item(key = "g_${a.groupId}") {
                            Text(ref.groupById[a.groupId]?.name ?: "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.nexo.primaryText, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                    item(key = a.id) {
                        Text(
                            a.name,
                            Modifier.fillMaxWidth().clickable {
                                when {
                                    !pair -> onAccount(a.id)
                                    from == null -> from = a.id
                                    from != a.id -> onTransfer?.invoke(from!!, a.id)
                                }
                            }.padding(vertical = 12.dp),
                            color = if (a.id == from) MaterialTheme.nexo.primaryText else Color.Unspecified,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
