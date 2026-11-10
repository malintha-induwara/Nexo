package com.nexo.app.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.money.AmountFormatter
import com.nexo.app.domain.BudgetMath
import com.nexo.app.domain.BudgetRow
import com.nexo.app.ui.components.AmountSheet
import com.nexo.app.ui.components.BarLineChart
import com.nexo.app.ui.components.ConfirmDialog
import com.nexo.app.ui.components.EmptyState
import com.nexo.app.ui.components.Formats
import com.nexo.app.ui.components.Legend
import com.nexo.app.ui.components.MonthPickerDialog
import com.nexo.app.ui.components.NexoTopBar
import com.nexo.app.ui.components.PeriodNavigator
import com.nexo.app.ui.components.SettingRow
import com.nexo.app.ui.navigation.BudgetAddKey
import com.nexo.app.ui.navigation.BudgetDetailKey
import com.nexo.app.ui.navigation.BudgetEditorKey
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.theme.LocalMoneyColors
import com.nexo.app.ui.transactions.CategoryPickerDialog
import com.nexo.app.ui.transactions.DayGroupList
import java.time.LocalDate
import java.time.YearMonth

/** Budget Detail (ST-046). */
@Composable
fun BudgetDetailScreen(key: BudgetDetailKey) {
    val vm = hiltViewModel<BudgetDetailViewModel, BudgetDetailViewModel.Factory>(creationCallback = { it.create(key) })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ref by vm.ref.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val money = LocalMoneyColors.current
    var dropdown by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(false) }
    val main = ref.mainCurrency
    val sel = ui.months.getOrNull(ui.selected)
    Scaffold(topBar = {
        NexoTopBar(ui.name, onBack = { nav.back() }, titleContent = {
            Box {
                Row(Modifier.clickable(enabled = ui.siblings.isNotEmpty()) { dropdown = true }, verticalAlignment = Alignment.CenterVertically) {
                    Text(ui.name, fontWeight = FontWeight.SemiBold)
                    if (ui.siblings.isNotEmpty()) Icon(Icons.Filled.ArrowDropDown, null)
                }
                DropdownMenu(dropdown, { dropdown = false }) {
                    ui.siblings.forEach { (id, name) -> DropdownMenuItem({ Text(name) }, { dropdown = false; vm.budgetId.value = id }) }
                }
            }
        }) {
            IconButton(onClick = {
                if (ui.isVirtual) nav.navigate(BudgetAddKey(categoryId = ui.categoryId, preset = true)) else nav.navigate(BudgetEditorKey(ui.budgetId))
            }) { Icon(Icons.Outlined.Edit, stringResource(R.string.budget_setting)) }
        }
    }) { p ->
        Column(Modifier.fillMaxSize().padding(p)) {
            PeriodNavigator(Formats.monthYear(month), { vm.step(false) }, { vm.step(true) }, { picker = true }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
            if (sel != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Figure(stringResource(R.string.budget), AmountFormatter.format(sel.budget, main), Modifier.weight(1f))
                    Figure(stringResource(R.string.used_amount), AmountFormatter.format(sel.used, main), Modifier.weight(1f))
                    val rem = sel.budget - sel.used
                    Figure(stringResource(R.string.remaining), AmountFormatter.format(rem, main), Modifier.weight(1f), if (rem < 0) money.expense else MaterialTheme.colorScheme.onSurface)
                }
            }
            BarLineChart(
                labels = ui.months.map { Formats.monthShort(it.period.label.monthValue) },
                columns = listOf(ui.months.map { it.used }),
                columnColors = listOf(money.expense),
                line = ui.months.map { it.budget },
                lineColor = money.income,
                selected = ui.selected,
                onSelect = vm::selectIndex,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Legend(listOf(stringResource(R.string.expenses) to money.expense, stringResource(R.string.budget) to money.income))
            DayGroupList(
                groups = ui.days, main = main, selection = emptySet(),
                onRowClick = { nav.navigate(EntryKey(mode = EntryMode.EDIT, txId = it.id)) }, onRowLongClick = null,
            )
        }
    }
    if (picker) MonthPickerDialog(month, onPick = { vm.month.value = it }, onDismiss = { picker = false })
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1)
    }
}

/** Budget Settings, plain or expandable (ST-047 / ST-048). */
@Composable
fun BudgetSettingsScreen(vm: BudgetSettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val data by vm.list.collectAsStateWithLifecycle()
    val ref by vm.ref.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    val main = ref.mainCurrency
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.budget_setting), onBack = { nav.back() }) {
            IconButton(onClick = { nav.navigate(BudgetAddKey()) }) { Icon(Icons.Filled.Add, stringResource(R.string.action_add)) }
        }
    }) { p ->
        LazyColumn(Modifier.padding(p), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Text(stringResource(R.string.budget_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            val d = data
            if (d == null || (d.total == null && d.rows.isEmpty())) {
                item { EmptyState() }
                return@LazyColumn
            }
            d.total?.let { t -> item(key = "total") { SettingsBudgetRow(t, main, onClick = { t.budgetId?.let { nav.navigate(BudgetEditorKey(it)) } }, onDelete = { confirm = t.budgetId }) } }
            d.rows.forEach { row ->
                item(key = row.categoryId ?: row.name) {
                    SettingsBudgetRow(
                        row, main,
                        onClick = {
                            when {
                                row.children.isNotEmpty() && ref.settings.subcategories -> expanded = if (row.categoryId in expanded) expanded - row.categoryId!! else expanded + row.categoryId!!
                                row.budgetId == null -> nav.navigate(BudgetAddKey(categoryId = row.categoryId, preset = true))
                                else -> nav.navigate(BudgetEditorKey(row.budgetId))
                            }
                        },
                        onEdit = if (row.children.isNotEmpty()) ({
                            if (row.budgetId == null) nav.navigate(BudgetAddKey(categoryId = row.categoryId, preset = true)) else nav.navigate(BudgetEditorKey(row.budgetId))
                        }) else null,
                        onDelete = row.budgetId?.let { id -> { confirm = id } },
                        virtual = row.budgetId == null,
                    )
                }
                if (row.categoryId in expanded) row.children.forEach { c ->
                    item(key = "c_${c.categoryId}") {
                        Box(Modifier.padding(start = 24.dp)) {
                            SettingsBudgetRow(c, main, onClick = { c.budgetId?.let { nav.navigate(BudgetEditorKey(it)) } }, onDelete = c.budgetId?.let { id -> { confirm = id } })
                        }
                    }
                }
            }
        }
    }
    confirm?.let { id -> ConfirmDialog(stringResource(R.string.confirm_delete), onConfirm = { vm.delete(id) }, onDismiss = { confirm = null }) }
}

@Composable
private fun SettingsBudgetRow(row: BudgetRow, main: com.nexo.app.core.database.entity.CurrencyEntity, onClick: () -> Unit, onDelete: (() -> Unit)?, onEdit: (() -> Unit)? = null, virtual: Boolean = false) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(row.name + if (row.children.isNotEmpty()) "  ▾" else "", Modifier.weight(1f).padding(vertical = 14.dp), fontWeight = if (row.isTotal) FontWeight.SemiBold else FontWeight.Normal)
        if (!virtual) Text(AmountFormatter.format(row.amount, main), style = MaterialTheme.typography.bodyMedium)
        if (onEdit != null) IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
        if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Filled.RemoveCircle, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) }
        else Box(Modifier.padding(24.dp))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Add Budget (ST-049). */
@Composable
fun BudgetAddScreen(key: BudgetAddKey, vm: BudgetSettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    var target by rememberSaveable { mutableStateOf(if (key.presetTotal) "total" else key.categoryId) }
    var amount by rememberSaveable { mutableStateOf<Double?>(null) }
    var picker by rememberSaveable { mutableStateOf(!key.preset) }
    var pad by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf(false) }
    val label = when (target) {
        null -> ""
        "total" -> stringResource(R.string.total_budget)
        else -> {
            val c = ref.categoryById[target]
            if (c?.parentId != null) "${ref.categoryName(c.parentId)}/${c.name}" else c?.name ?: ""
        }
    }
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.add_budget), onBack = { nav.back() }) }) { p ->
        Column(Modifier.padding(p)) {
            SettingRow(stringResource(R.string.category), label.ifEmpty { stringResource(R.string.select_specific_category) }, onClick = { picker = true })
            SettingRow(stringResource(R.string.amount), amount?.let { AmountFormatter.format(it, ref.mainCurrency) } ?: "", onClick = { pad = true })
            if (error) Text(stringResource(R.string.select_specific_category), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            Button(onClick = {
                val t = target
                if (t == null) error = true else vm.add(if (t == "total") null else t, amount ?: 0.0) { nav.back() }
            }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text(stringResource(R.string.action_save)) }
        }
    }
    if (picker) {
        CategoryPickerDialog(ref, onPick = { target = it; picker = false; if (amount == null) pad = true }, onDismiss = { picker = false },
            allowAll = true, onAll = { target = "total"; picker = false; if (amount == null) pad = true }, kinds = listOf(CategoryKind.EXPENSE))
    }
    if (pad) AmountSheet(stringResource(R.string.amount), amount, ref.mainCurrency.decimals, allowSign = false, onConfirm = { amount = it?.let { v -> kotlin.math.abs(v) } }, onDismiss = { pad = false })
}

/** Monthly budget editor (ST-050). */
@Composable
fun BudgetEditorScreen(key: BudgetEditorKey, vm: BudgetSettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val amounts by vm.amounts.collectAsStateWithLifecycle()
    val budget = budgets.firstOrNull { it.id == key.budgetId }
    val records = amounts.filter { it.budgetId == key.budgetId }
    val currentLabel = ref.settings.periods.monthPeriodContaining(LocalDate.now()).label
    var year by rememberSaveable { mutableIntStateOf(currentLabel.year) }
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(budget?.isDeleted) { if (budget?.isDeleted == true) nav.back() }
    val title = if (budget?.categoryId == null) stringResource(R.string.total_budget) else ref.categoryName(budget.categoryId) ?: ""
    val main = ref.mainCurrency
    Scaffold(topBar = {
        NexoTopBar(title, onBack = { nav.back() }) {
            IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
        }
    }) { p ->
        LazyColumn(Modifier.padding(p)) {
            item {
                Text(
                    stringResource(R.string.budget_editor_help) + if (ref.settings.subcategories) "\n" + stringResource(R.string.budget_rollup_help) else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp),
                )
                SettingRow(stringResource(R.string.default_budget), AmountFormatter.format(BudgetMath.latest(records), main), onClick = { editing = 0 })
                HorizontalDivider()
                PeriodNavigator(year.toString(), { year-- }, { year++ }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
            }
            items((12 downTo 1).toList()) { m ->
                val ym = YearMonth.of(year, m)
                val k = BudgetMath.key(ym)
                Row(
                    Modifier.fillMaxWidth().background(if (ym == currentLabel) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
                        .clickable { editing = k }.padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(Formats.monthYear(ym), Modifier.weight(1f))
                    Text(AmountFormatter.format(BudgetMath.effective(records, k), main))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    editing?.let { k ->
        val label = if (k == 0) stringResource(R.string.default_budget) else Formats.monthYear(YearMonth.of(k / 100, k % 100))
        AmountSheet(
            label, if (k == 0) BudgetMath.latest(records) else BudgetMath.effective(records, k), main.decimals, allowSign = false, subtitle = title,
            onConfirm = { v -> val a = kotlin.math.abs(v ?: 0.0); if (k == 0) vm.setDefault(key.budgetId, a) else vm.setMonth(key.budgetId, k, a) },
            onDismiss = { editing = null },
        )
    }
    if (confirm) ConfirmDialog(stringResource(R.string.confirm_delete), onConfirm = { vm.delete(key.budgetId) }, onDismiss = { confirm = false })
}
