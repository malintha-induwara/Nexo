package com.nexo.app.ui.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.model.GroupType
import com.nexo.app.data.database.entity.CurrencyEntity
import com.nexo.app.ui.designsystem.component.AmountText
import com.nexo.app.ui.designsystem.component.ConfirmDialog
import com.nexo.app.ui.designsystem.component.FlowAmount
import com.nexo.app.ui.designsystem.component.FlowCharts
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.GroupCard
import com.nexo.app.ui.designsystem.component.MessageDialog
import com.nexo.app.ui.designsystem.component.MonthPickerDialog
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.PeriodNavigator
import com.nexo.app.ui.designsystem.component.RoundIconButton
import com.nexo.app.ui.designsystem.component.SegmentedPill
import com.nexo.app.ui.designsystem.component.shrinkToFit
import com.nexo.app.ui.designsystem.theme.LocalMoneyColors
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Space
import com.nexo.app.ui.navigation.AccountDetailKey
import com.nexo.app.ui.navigation.AccountFormKey
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.transactions.BulkAction
import com.nexo.app.ui.transactions.BulkDialogs
import com.nexo.app.ui.transactions.DayGroupList
import com.nexo.app.ui.transactions.SelectionBar
import com.nexo.app.ui.transactions.TxRow
import com.nexo.app.ui.transactions.selectedNet
import java.util.Locale

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
                        SegmentedPill(
                            options = DetailTab.entries,
                            selected = tab,
                            onSelect = vm::setTab,
                            label = { t -> stringResource(when (t) { DetailTab.DAILY -> R.string.daily; DetailTab.MONTHLY -> R.string.monthly; DetailTab.ANNUALLY -> R.string.annually }) },
                            textStyle = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
                        )
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
                    FloatingActionButton(onClick = { nav.navigate(EntryKey(accountId = account?.id, fromAdd = true)) }, containerColor = MaterialTheme.colorScheme.primary) {
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
                FlowCharts(c.labels, c.balances, c.inflow, c.outflow, cur, stringResource(R.string.inflow_outflow), stringResource(R.string.inflow), stringResource(R.string.outflow))
            } else if (tab == DetailTab.DAILY || isNone) {
                DayGroupList(
                    groups = ui.days,
                    main = cur,
                    selection = selection,
                    onRowClick = { if (selection.isNotEmpty()) vm.toggleSelect(it.id) else nav.navigate(EntryKey(mode = EntryMode.EDIT, txId = it.id)) },
                    onRowLongClick = { vm.toggleSelect(it.id) },
                    onDayClick = if (key.deleted) null else ({ d -> nav.navigate(EntryKey(date = d.toString(), accountId = account?.id, fromAdd = true)) }),
                    onMemoClick = { d -> nav.navigate(com.nexo.app.ui.navigation.MemoListKey(d.toString())) },
                    rowContent = { row ->
                        TxRow(
                            row, row.id in selection,
                            onClick = { if (selection.isNotEmpty()) vm.toggleSelect(row.id) else nav.navigate(EntryKey(mode = EntryMode.EDIT, txId = row.id)) },
                            onLongClick = { vm.toggleSelect(row.id) },
                            runningBalance = ui.running[row.id]?.let { AmountFormatter.format(it, cur) },
                        )
                    },
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(top = Space.xs, bottom = 96.dp)) {
                    items(ui.periods, key = { it.label + it.range.start }) { p ->
                        PeriodCard(p, cur, onClick = { p.month?.let(vm::openMonth) ?: p.year?.let(vm::openYear) }, modifier = Modifier.padding(horizontal = Space.l, vertical = Space.xs))
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
    BulkDialogs(bulkAction, bulkOpen, selection.size, ref, vm.transactions, selection, onDone = { vm.setSelection(emptySet()) }, onDismiss = { bulkOpen = false })
}

/** The statement for the period on screen: the balance on one line, with money in, out and the net beneath. */
@Composable
private fun SummaryBlock(ui: AccountDetailUi, cur: CurrencyEntity, tab: DetailTab, charts: Boolean, onToggleCharts: () -> Unit, onEdit: () -> Unit, onHelp: () -> Unit) {
    val colors = LocalNexoColors.current
    val money = LocalMoneyColors.current
    GroupCard(Modifier.padding(horizontal = Space.l, vertical = Space.xs)) {
        Column(Modifier.padding(start = Space.l, end = Space.m, top = 10.dp, bottom = Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (stringResource(R.string.statement) + if (ui.range != com.nexo.app.core.common.period.DateRange.ALL) " " + Formats.rangeFull(ui.range.start, ui.range.end) else "").uppercase(Locale.getDefault()),
                    style = NexoType.Caps, color = colors.textTertiary, maxLines = 1, modifier = Modifier.weight(1f),
                )
                RoundIconButton(if (charts) Icons.AutoMirrored.Outlined.List else Icons.Outlined.BarChart, stringResource(R.string.toggle_charts), onToggleCharts, size = 32.dp)
                Spacer(Modifier.width(Space.s))
                RoundIconButton(Icons.Outlined.Edit, stringResource(R.string.action_edit), onEdit, size = 32.dp)
            }
            if (tab != DetailTab.ANNUALLY) {
                Row(Modifier.padding(top = Space.xs, end = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.balance), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    if (ui.showHelp) {
                        Icon(
                            Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.help), tint = colors.textTertiary,
                            modifier = Modifier.padding(start = Space.xs).size(28.dp).clip(CircleShape).clickable(onClick = onHelp).padding(6.dp),
                        )
                    }
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        val b = ui.balance
                        if (b == null) Text("-", style = NexoType.AmountLarge, color = colors.text)
                        else AmountText(b, cur, Modifier.shrinkToFit(), style = NexoType.AmountLarge, color = if (b < 0) money.expense else colors.text)
                    }
                }
            }
            Spacer(Modifier.height(Space.s))
            Row(Modifier.padding(end = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Figure(stringResource(R.string.deposit), ui.deposit, cur, money.income, Modifier.weight(1f))
                Figure(stringResource(R.string.withdrawal), ui.withdrawal, cur, money.expense, Modifier.weight(1f))
                Figure(stringResource(R.string.total), ui.deposit - ui.withdrawal, cur, colors.text, Modifier.weight(1f))
            }
            ui.card?.let { c ->
                Spacer(Modifier.height(Space.s))
                Row(Modifier.padding(end = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    Figure(stringResource(R.string.payable), kotlin.math.abs(c.payable), cur, money.signed(c.payable), Modifier.weight(1f))
                    Figure(stringResource(R.string.outstanding), kotlin.math.abs(c.outstanding), cur, money.signed(c.outstanding), Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: Double, cur: CurrencyEntity, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(Locale.getDefault()), style = NexoType.Caps, color = LocalNexoColors.current.textTertiary, maxLines = 1)
        FlowAmount(value, cur, color, Modifier.shrinkToFit(), NexoType.AmountSmall)
    }
}

/** A month or year of the account: money in and out, the net, and the balance it ended on in brackets. */
@Composable
private fun PeriodCard(p: PeriodRowUi, cur: CurrencyEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalNexoColors.current
    val money = LocalMoneyColors.current
    GroupCard(modifier, highlighted = p.current, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Column(Modifier.width(92.dp)) {
                Text(p.label, style = MaterialTheme.typography.titleMedium, color = colors.text, maxLines = 1)
                Text(Formats.range(p.range.start, p.range.end), style = NexoType.Meta, color = if (p.current) colors.primaryText else colors.textTertiary, maxLines = 1)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                FlowAmount(p.deposit, cur, money.income, Modifier.shrinkToFit(), NexoType.AmountSmall)
                Spacer(Modifier.padding(top = Space.xxs))
                FlowAmount(p.withdrawal, cur, money.expense, Modifier.shrinkToFit(), NexoType.AmountSmall)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                FlowAmount(p.deposit - p.withdrawal, cur, colors.text, Modifier.shrinkToFit())
                Text("(${AmountFormatter.format(p.balance, cur)})", style = NexoType.AmountSmall, color = colors.textTertiary, maxLines = 1, modifier = Modifier.shrinkToFit())
            }
        }
    }
}
