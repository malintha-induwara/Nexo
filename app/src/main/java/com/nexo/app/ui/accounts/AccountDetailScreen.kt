package com.nexo.app.ui.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.database.entity.CurrencyEntity
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.money.AmountFormatter
import com.nexo.app.ui.components.BarLineChart
import com.nexo.app.ui.components.ConfirmDialog
import com.nexo.app.ui.components.Formats
import com.nexo.app.ui.components.Legend
import com.nexo.app.ui.components.MessageDialog
import com.nexo.app.ui.components.MonthPickerDialog
import com.nexo.app.ui.components.NexoTopBar
import com.nexo.app.ui.components.PeriodNavigator
import com.nexo.app.ui.navigation.AccountDetailKey
import com.nexo.app.ui.navigation.AccountFormKey
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.theme.LocalMoneyColors
import com.nexo.app.ui.transactions.BulkAction
import com.nexo.app.ui.transactions.BulkDialogs
import com.nexo.app.ui.transactions.DayGroupList
import com.nexo.app.ui.transactions.SelectionBar
import com.nexo.app.ui.transactions.TxRow
import com.nexo.app.ui.transactions.selectedNet

@Composable
fun AccountDetailScreen(key: AccountDetailKey) {
    val vm = hiltViewModel<AccountDetailViewModel, AccountDetailViewModel.Factory>(creationCallback = { it.create(key) })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ref by vm.ref.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val charts by vm.charts.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val anchor by vm.anchor.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val money = LocalMoneyColors.current
    var help by rememberSaveable { mutableStateOf(false) }
    var monthPicker by rememberSaveable { mutableStateOf(false) }
    var restore by rememberSaveable { mutableStateOf(false) }
    var bulkOpen by remember { mutableStateOf(false) }
    var bulkAction by remember { mutableStateOf<BulkAction?>(null) }
    val account = ui.account
    val cur = account?.let { ref.accountCurrency(it.id) } ?: ref.mainCurrency
    val isCard = ui.type == GroupType.CREDIT_CARD
    val isNone = key.accountId == null
    BackHandler(enabled = selection.isNotEmpty()) { vm.setSelection(emptySet()) }

    Scaffold(
        topBar = {
            val rows = ui.days.flatMap { it.rows }
            if (selection.isNotEmpty()) {
                SelectionBar(selection.size, rows.size, selectedNet(rows, selection), cur, onToggleAll = {
                    vm.setSelection(if (selection.size >= rows.size) emptySet() else rows.map { it.id }.toSet())
                }, onClose = { vm.setSelection(emptySet()) }, onAction = { bulkAction = it; bulkOpen = true })
            } else {
                Column {
                    NexoTopBar(account?.name ?: stringResource(R.string.none), onBack = { nav.back() }) {
                        if (key.deleted) IconButton(onClick = { restore = true }) { Icon(Icons.Outlined.Restore, stringResource(R.string.action_restore)) }
                    }
                    if (!isNone) {
                        PeriodNavigator(
                            ui.title, { vm.step(false) }, { vm.step(true) },
                            if (tab == DetailTab.DAILY) ({ monthPicker = true }) else null,
                            modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface,
                        )
                        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                            DetailTab.entries.forEach { t ->
                                Tab(tab == t, onClick = { vm.setTab(t) }, text = {
                                    Text(stringResource(when (t) { DetailTab.DAILY -> R.string.daily; DetailTab.MONTHLY -> R.string.monthly; DetailTab.ANNUALLY -> R.string.annually }))
                                })
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (selection.isEmpty() && !charts && !key.deleted) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isCard && account != null) {
                        ExtendedFloatingActionButton(onClick = { nav.navigate(EntryKey(mode = EntryMode.PAY_CARD, accountId = account.id)) }, icon = { Icon(Icons.Outlined.Payments, null) }, text = { Text(stringResource(R.string.pay)) })
                    }
                    FloatingActionButton(onClick = { nav.navigate(EntryKey(accountId = account?.id)) }, containerColor = MaterialTheme.colorScheme.primary) {
                        Icon(Icons.Filled.Add, stringResource(R.string.add_transaction), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!isNone) {
                SummaryBlock(ui, cur, tab, charts, onToggleCharts = vm::toggleCharts, onEdit = { account?.let { nav.navigate(AccountFormKey(it.id)) } }, onHelp = { help = true })
            }
            if (charts && ui.chart != null) {
                val c = ui.chart!!
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    item {
                        Text(stringResource(R.string.balance), style = MaterialTheme.typography.titleSmall)
                        BarLineChart(c.labels, emptyList(), emptyList(), line = c.balances, lineColor = MaterialTheme.colorScheme.primary, showValueOnSelect = { "${c.labels[it]}: ${AmountFormatter.format(c.balances[it], cur)}" })
                        Spacer(Modifier.padding(8.dp))
                        Text(stringResource(R.string.inflow_outflow), style = MaterialTheme.typography.titleSmall)
                        BarLineChart(c.labels, listOf(c.inflow, c.outflow), listOf(money.income, money.expense))
                        Legend(listOf(stringResource(R.string.inflow) to money.income, stringResource(R.string.outflow) to money.expense))
                    }
                    items(c.labels.indices.toList()) { i ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(c.labels[i], Modifier.width(56.dp))
                            Text(AmountFormatter.format(c.balances[i], cur), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(AmountFormatter.format(c.inflow[i], cur), Modifier.weight(1f), color = money.income, style = MaterialTheme.typography.bodySmall)
                            Text(AmountFormatter.format(c.outflow[i], cur), Modifier.weight(1f), color = money.expense, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else if (tab == DetailTab.DAILY || isNone) {
                DayGroupList(
                    groups = ui.days,
                    main = cur,
                    selection = selection,
                    onRowClick = { if (selection.isNotEmpty()) vm.toggleSelect(it.id) else nav.navigate(EntryKey(mode = EntryMode.EDIT, txId = it.id)) },
                    onRowLongClick = { vm.toggleSelect(it.id) },
                    onDayClick = if (key.deleted) null else ({ d -> nav.navigate(EntryKey(date = d.toString(), accountId = account?.id)) }),
                    onMemoClick = { d -> nav.navigate(com.nexo.app.ui.navigation.MemoListKey(d.toString())) },
                    rowContent = { row ->
                        Column {
                            TxRow(row, row.id in selection, onClick = {
                                if (selection.isNotEmpty()) vm.toggleSelect(row.id) else nav.navigate(EntryKey(mode = EntryMode.EDIT, txId = row.id))
                            }, onLongClick = { vm.toggleSelect(row.id) })
                            ui.running[row.id]?.let { b ->
                                Text("(${AmountFormatter.format(b, cur)})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(end = 16.dp, bottom = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            }
                        }
                    },
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(ui.periods, key = { it.label + it.range.start }) { p ->
                        Row(
                            Modifier.fillMaxWidth().background(if (p.current) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
                                .clickable { p.month?.let(vm::openMonth) ?: p.year?.let(vm::openYear) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.width(96.dp)) {
                                Text(p.label, fontWeight = FontWeight.SemiBold)
                                Text(Formats.range(p.range.start, p.range.end), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(AmountFormatter.format(p.deposit, cur), color = money.income, style = MaterialTheme.typography.bodySmall)
                                Text(AmountFormatter.format(p.withdrawal, cur), color = money.expense, style = MaterialTheme.typography.bodySmall)
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(AmountFormatter.format(p.deposit - p.withdrawal, cur), style = MaterialTheme.typography.bodySmall)
                                Text("(${AmountFormatter.format(p.balance, cur)})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
    if (help) MessageDialog(stringResource(R.string.balance_help), onDismiss = { help = false })
    if (monthPicker) {
        val e = vm.engineFor(ref, account)
        MonthPickerDialog(e.monthPeriodContaining(anchor).label, onPick = { vm.setAnchor(e.monthPeriodFor(it).end) }, onDismiss = { monthPicker = false })
    }
    if (restore) ConfirmDialog(stringResource(R.string.restore_question), onConfirm = { vm.restore { nav.back() } }, onDismiss = { restore = false })
    BulkDialogs(bulkAction, bulkOpen, selection.size, ref, vm.writer, selection, onDone = { vm.setSelection(emptySet()) }, onDismiss = { bulkOpen = false })
}

@Composable
private fun SummaryBlock(ui: AccountDetailUi, cur: CurrencyEntity, tab: DetailTab, charts: Boolean, onToggleCharts: () -> Unit, onEdit: () -> Unit, onHelp: () -> Unit) {
    val money = LocalMoneyColors.current
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.statement) + if (ui.range != com.nexo.app.core.period.DateRange.ALL) " " + Formats.rangeFull(ui.range.start, ui.range.end) else "",
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleCharts) { Icon(if (charts) Icons.AutoMirrored.Outlined.List else Icons.Outlined.BarChart, stringResource(R.string.toggle_charts)) }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
            }
            Row {
                Figure(stringResource(R.string.deposit), AmountFormatter.format(ui.deposit, cur), money.income, Modifier.weight(1f))
                Figure(stringResource(R.string.withdrawal), AmountFormatter.format(ui.withdrawal, cur), money.expense, Modifier.weight(1f))
                Figure(stringResource(R.string.total), AmountFormatter.format(ui.deposit - ui.withdrawal, cur), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
            if (tab != DetailTab.ANNUALLY) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.balance), style = MaterialTheme.typography.labelMedium)
                    if (ui.showHelp) IconButton(onClick = onHelp) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, null) }
                    Spacer(Modifier.weight(1f))
                    val b = ui.balance
                    Text(if (b == null) "-" else AmountFormatter.format(kotlin.math.abs(b), cur), color = b?.let { money.signed(it) } ?: MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                }
            }
            ui.card?.let { c ->
                Text(
                    stringResource(R.string.balance_payable) + " " + AmountFormatter.format(kotlin.math.abs(c.payable), cur) + "   " +
                        stringResource(R.string.outstanding_balance) + " " + AmountFormatter.format(kotlin.math.abs(c.outstanding), cur),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(value, color = color, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}
