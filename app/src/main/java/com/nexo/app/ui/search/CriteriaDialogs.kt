package com.nexo.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.RefData
import com.nexo.app.ui.designsystem.component.IncomeExpensePill
import com.nexo.app.ui.designsystem.theme.nexo
import com.nexo.app.ui.transactionlist.FilterCriteria

data class CheckItem(
    val id: String,
    val label: String,
    val header: Boolean = false,
    val indent: Boolean = false,
)

// Multi-select list with an "All" tri-state header.
@Composable
fun MultiSelectDialog(
    title: String,
    items: List<CheckItem>,
    initial: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    val ids = items.filter { !it.header }.map { it.id }.toSet()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        selected =
                            if (selected.containsAll(ids)) emptySet() else ids
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TriStateCheckbox(
                        when {
                            selected.isEmpty() -> ToggleableState.Off
                            selected.containsAll(ids) -> ToggleableState.On
                            else -> ToggleableState.Indeterminate
                        },
                        onClick = null,
                        modifier = Modifier.padding(8.dp),
                    )
                    Text(stringResource(R.string.all), fontWeight = FontWeight.SemiBold)
                }
                LazyColumn(Modifier.height(380.dp)) {
                    items.forEach { item ->
                        item(key = item.id + item.header) {
                            if (item.header) {
                                Text(
                                    item.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.nexo.primaryText,
                                    modifier = Modifier.padding(top = 10.dp, start = 8.dp),
                                )
                            } else {
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        selected =
                                            if (item.id in selected) {
                                                selected - item.id
                                            } else {
                                                selected + item.id
                                            }
                                    }
                                        .padding(start = if (item.indent) 24.dp else 0.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        item.id in selected,
                                        onCheckedChange = null,
                                        modifier = Modifier.padding(8.dp),
                                    )
                                    Text(item.label)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(selected)
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

fun accountItems(ref: RefData): List<CheckItem> {
    val out = ArrayList<CheckItem>()
    var g: String? = null
    ref.orderedAccounts().filter { ref.groupById[it.groupId] != null }.forEach { a ->
        if (a.groupId != g) {
            g = a.groupId
            out += CheckItem("g_${a.groupId}", ref.groupById[a.groupId]?.name ?: "", header = true)
        }
        out += CheckItem(a.id, a.name)
    }
    return out
}

@Composable
fun CategoryCriteriaDialog(
    ref: RefData,
    initial: FilterCriteria,
    onConfirm: (FilterCriteria) -> Unit,
    onDismiss: () -> Unit,
) {
    var f by remember { mutableStateOf(initial) }
    var tab by remember { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.category)) },
        text = {
            Column {
                IncomeExpensePill(tab, onSelect = {
                    tab = it
                }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                val kind = if (tab == 0) CategoryKind.INCOME else CategoryKind.EXPENSE
                val allOn = if (kind == CategoryKind.INCOME) f.incomeAll else f.expenseAll
                Row(
                    Modifier.fillMaxWidth().clickable {
                        f =
                            if (kind == CategoryKind.INCOME) {
                                f.copy(incomeAll = !f.incomeAll)
                            } else {
                                f.copy(expenseAll = !f.expenseAll)
                            }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(allOn, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                    Text(stringResource(R.string.all), fontWeight = FontWeight.SemiBold)
                }
                LazyColumn(Modifier.height(340.dp)) {
                    ref.mainCategories(kind).forEach { m ->
                        item(key = m.id) {
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    f = f.copy(categories = toggle(f.categories, m.id))
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    m.id in f.categories || allOn,
                                    onCheckedChange = null,
                                    modifier = Modifier.padding(8.dp),
                                )
                                Text(m.name)
                            }
                        }
                        if (ref.settings.subcategories) {
                            ref.subCategories(m.id).forEach { s ->
                                item(key = s.id) {
                                    Row(
                                        Modifier.fillMaxWidth().clickable {
                                            f = f.copy(categories = toggle(f.categories, s.id))
                                        }.padding(start = 24.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            s.id in f.categories || m.id in f.categories || allOn,
                                            onCheckedChange = null,
                                            modifier = Modifier.padding(8.dp),
                                        )
                                        Text(s.name)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(f)
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun toggle(set: Set<String>, id: String) = if (id in set) set - id else set + id
