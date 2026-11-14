package com.nexo.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.ui.shell.LocalShell
import com.nexo.app.core.database.entity.CurrencyEntity
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.money.AmountFormatter
import com.nexo.app.domain.AccountFigure
import com.nexo.app.domain.Balances
import com.nexo.app.ui.components.ChoiceDialog
import com.nexo.app.ui.components.DateRangeChips
import com.nexo.app.ui.components.EmptyState
import com.nexo.app.ui.components.Formats
import com.nexo.app.ui.components.MonthPickerDialog
import com.nexo.app.ui.components.NexoDatePickerDialog
import com.nexo.app.ui.components.PeriodNavigator
import com.nexo.app.ui.components.TotalsBar
import com.nexo.app.ui.navigation.AccountDetailKey
import com.nexo.app.ui.navigation.AccountFormKey
import com.nexo.app.ui.navigation.AccountListKey
import com.nexo.app.ui.navigation.AccountListMode
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.TotalStatsKey
import com.nexo.app.ui.theme.LocalHeaderColors
import com.nexo.app.ui.theme.LocalMoneyColors
import java.time.LocalDate

@Composable
fun accountsModeLabel(m: AccountsMode): String = stringResource(
    when (m) {
        AccountsMode.ALL -> R.string.all
        AccountsMode.WEEKLY -> R.string.weekly
        AccountsMode.MONTHLY -> R.string.monthly
        AccountsMode.ANNUALLY -> R.string.annually
        AccountsMode.PERIOD -> R.string.period
    },
)

@Composable
fun AccountsTab(todaySignal: Int, vm: AccountsViewModel = hiltViewModel()) {
    val ref by vm.ref.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val header = LocalHeaderColors.current
    val money = LocalMoneyColors.current
    var menu by rememberSaveable { mutableStateOf(false) }
    var modePicker by rememberSaveable { mutableStateOf(false) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(todaySignal) { if (todaySignal > 0) vm.goToday() }
    val main = ref.mainCurrency

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(header.container).statusBarsPadding().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            when (q.mode) {
                AccountsMode.ALL -> Text(stringResource(R.string.tab_accounts), color = header.content, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp))
                AccountsMode.PERIOD -> DateRangeChips(q.from, q.to, onChange = vm::setRange, color = header.content)
                else -> {
                    val r = data.range
                    val label = when (q.mode) {
                        AccountsMode.WEEKLY -> r?.let { Formats.range(it.start, it.end) } ?: ""
                        AccountsMode.MONTHLY -> Formats.monthYear(ref.settings.periods.monthPeriodContaining(q.anchor).label)
                        else -> ref.settings.periods.yearOf(q.anchor).toString()
                    }
                    PeriodNavigator(label, { vm.step(false) }, { vm.step(true) }, if (q.mode == AccountsMode.ANNUALLY) null else ({ datePicker = true }))
                }
            }
            Spacer(Modifier.weight(1f))
            AssistChip(onClick = { modePicker = true }, label = { Text(accountsModeLabel(q.mode), color = header.content) }, trailingIcon = { Icon(Icons.Filled.ExpandMore, null, tint = header.content) })
            IconButton(onClick = { nav.navigate(TotalStatsKey) }) { Icon(Icons.Outlined.BarChart, stringResource(R.string.total_stats), tint = header.content) }
            androidx.compose.foundation.layout.Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more), tint = header.content) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_add)) }, onClick = { menu = false; nav.navigate(AccountFormKey()) })
                DropdownMenuItem(text = { Text(stringResource(R.string.show_hide)) }, onClick = { menu = false; nav.navigate(AccountListKey(AccountListMode.VISIBILITY)) })
                DropdownMenuItem(text = { Text(stringResource(R.string.modify_orders)) }, onClick = { menu = false; nav.navigate(AccountListKey(AccountListMode.REORDER)) })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; nav.navigate(AccountListKey(AccountListMode.DELETE)) })
            }
            }
        }
        TotalsBar(
            data.summary.assets, data.summary.liabilities, main, money.income, money.expense,
            labels = Triple(stringResource(R.string.assets), stringResource(R.string.liabilities), stringResource(R.string.total)),
            totalValue = data.summary.total,
        )
        HorizontalDivider()
        val collapsed = device?.collapsedGroups.orEmpty()
        val visibleGroups = ref.groups.filter { it.status == GroupStatus.ACTIVE }
        val figuresByGroup = data.figures.groupBy { it.account.groupId }
        if (data.loaded && data.figures.none { it.account.status == AccountStatus.ACTIVE } && data.unassigned == null) {
            EmptyState()
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = LocalShell.current.bottomInset)) {
            data.unassigned?.let { value ->
                item(key = "none") {
                    GroupHeader(stringResource(R.string.none), value, null, main, money.signed(value), collapsed = false, onToggle = {})
                    AccountRow(stringResource(R.string.none), value, main, color = money.signed(value)) { nav.navigate(AccountDetailKey(null)) }
                }
            }
            visibleGroups.forEach { g ->
                val figures = figuresByGroup[g.id].orEmpty().filter { it.account.status == AccountStatus.ACTIVE || it.account.status == AccountStatus.HIDDEN }
                if (figures.isEmpty()) return@forEach
                val gf = data.summary.groups[g.id]
                item(key = "g_${g.id}") {
                    val name = if (g.type == GroupType.DEBIT_CARD && data.debitMonthLabel != null) "${g.name}(${Formats.monthShort(data.debitMonthLabel!!.monthValue)})" else g.name
                    val sub = gf?.subtotal ?: 0.0
                    GroupHeader(
                        name, sub, if (g.type == GroupType.CREDIT_CARD && data.range == null) gf?.payableSubtotal ?: 0.0 else null, main,
                        if (g.type == GroupType.DEBIT_CARD) MaterialTheme.colorScheme.onSurface else money.signed(sub),
                        collapsed = g.id in collapsed, onToggle = { vm.toggleCollapsed(g.id) },
                        debit = g.type == GroupType.DEBIT_CARD,
                    )
                }
                if (g.id !in collapsed) {
                    figures.filter { it.account.status == AccountStatus.ACTIVE }.forEach { f ->
                        item(key = f.account.id) {
                            FigureRow(f, ref.accountCurrency(f.account.id), data.changes[f.account.id], data.range != null) {
                                nav.navigate(AccountDetailKey(f.account.id, date = data.range?.end?.toString()))
                            }
                        }
                    }
                }
            }
        }
    }
    if (modePicker) {
        ChoiceDialog(stringResource(R.string.period), AccountsMode.entries, q.mode, { accountsModeLabel(it) }, onSelect = vm::setMode, onDismiss = { modePicker = false })
    }
    if (datePicker) {
        if (q.mode == AccountsMode.MONTHLY) {
            MonthPickerDialog(ref.settings.periods.monthPeriodContaining(q.anchor).label, onPick = { vm.setAnchor(ref.settings.periods.monthPeriodFor(it).end) }, onDismiss = { datePicker = false })
        } else {
            NexoDatePickerDialog(q.anchor, onPick = vm::setAnchor, onDismiss = { datePicker = false })
        }
    }
}

@Composable
private fun GroupHeader(
    name: String,
    subtotal: Double,
    payable: Double?,
    main: CurrencyEntity,
    color: androidx.compose.ui.graphics.Color,
    collapsed: Boolean,
    onToggle: () -> Unit,
    debit: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().background(color.copy(alpha = 0.07f)).clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (payable != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.balance_payable) + " " + AmountFormatter.format(kotlin.math.abs(payable), main), style = MaterialTheme.typography.bodySmall, color = color)
                Text(stringResource(R.string.outstanding_balance) + " " + AmountFormatter.format(kotlin.math.abs(subtotal), main), style = MaterialTheme.typography.bodySmall, color = color)
            }
        } else {
            Text(AmountFormatter.format(if (debit) subtotal else kotlin.math.abs(subtotal), main), color = color, style = MaterialTheme.typography.bodyMedium)
        }
        Icon(if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun AccountRow(name: String, value: Double, currency: CurrencyEntity, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp)) {
        Text(name, modifier = Modifier.weight(1f))
        Text(AmountFormatter.format(kotlin.math.abs(value), currency), color = color)
    }
}

@Composable
private fun FigureRow(f: AccountFigure, cur: CurrencyEntity, change: Double?, periodMode: Boolean, onClick: () -> Unit) {
    val money = LocalMoneyColors.current
    val muted = MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(f.account.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (f.excluded && f.groupType != GroupType.DEBIT_CARD) muted else MaterialTheme.colorScheme.onSurface)
            val card = f.card
            when {
                card != null && !periodMode -> Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.balance_payable) + " " + AmountFormatter.format(kotlin.math.abs(card.payable), cur), color = money.signed(card.payable), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.outstanding_balance) + " " + AmountFormatter.format(kotlin.math.abs(card.outstanding), cur), color = money.signed(card.outstanding), style = MaterialTheme.typography.bodySmall)
                    if (card.other < 0) Text(AmountFormatter.format(kotlin.math.abs(card.other), cur), color = money.expense, style = MaterialTheme.typography.labelSmall)
                }
                f.groupType == GroupType.DEBIT_CARD && !periodMode -> Text(AmountFormatter.format(f.balance, cur), color = MaterialTheme.colorScheme.onSurface)
                f.excluded -> Text(AmountFormatter.format(f.balance, cur), color = muted)
                else -> Text(AmountFormatter.format(kotlin.math.abs(f.balance), cur), color = money.signed(f.balance))
            }
        }
        if (periodMode && change != null) {
            val arrow = when {
                change > 0 -> "↑"
                change < 0 -> "↓"
                else -> "-"
            }
            Text("${AmountFormatter.format(kotlin.math.abs(change), cur)} $arrow", style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.align(Alignment.End))
        }
    }
}
