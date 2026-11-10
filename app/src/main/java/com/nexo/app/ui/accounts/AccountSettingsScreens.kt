package com.nexo.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.database.entity.AccountEntity
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.CardAccounting
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.money.AmountFormatter
import com.nexo.app.core.period.MonthPeriod
import com.nexo.app.data.AccountRepository
import com.nexo.app.data.RefRepository
import com.nexo.app.domain.Balances
import com.nexo.app.domain.RefData
import com.nexo.app.ui.components.BarLineChart
import com.nexo.app.ui.components.ChoiceDialog
import com.nexo.app.ui.components.ConfirmDialog
import com.nexo.app.ui.components.EmptyState
import com.nexo.app.ui.components.Formats
import com.nexo.app.ui.components.Legend
import com.nexo.app.ui.components.MonthPickerDialog
import com.nexo.app.ui.components.NexoTopBar
import com.nexo.app.ui.components.PeriodNavigator
import com.nexo.app.ui.components.SettingRow
import com.nexo.app.ui.components.SwitchRow
import com.nexo.app.ui.navigation.AccountDetailKey
import com.nexo.app.ui.navigation.AccountFormKey
import com.nexo.app.ui.navigation.AccountGroupFormKey
import com.nexo.app.ui.navigation.AccountGroupsKey
import com.nexo.app.ui.navigation.AccountListKey
import com.nexo.app.ui.navigation.AccountListMode
import com.nexo.app.ui.navigation.DeletedAccountsKey
import com.nexo.app.ui.navigation.DeletedGroupsKey
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.TransferExpenseKey
import com.nexo.app.ui.theme.LocalMoneyColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    val repo: AccountRepository,
    private val refs: RefRepository,
    private val db: NexoDatabase,
) : ViewModel() {
    val ref: StateFlow<RefData> = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    val balances: StateFlow<Map<String?, com.nexo.app.domain.Net>> = db.transactionDao().observeAggregatesUpTo("9999-12-31")
        .mapLatest { Balances.netByAccount(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
    fun updateSettings(transform: (com.nexo.app.core.settings.BookSettings) -> com.nexo.app.core.settings.BookSettings) = viewModelScope.launch { refs.updateSettings(transform) }
}

@Composable
fun cardAccountingLabel(c: CardAccounting) = stringResource(if (c == CardAccounting.AT_USE) R.string.card_at_time else R.string.card_lump_sum)

/** Accounts settings menu (AS-010). */
@Composable
fun AccountSettingsMenu(vm: AccountSettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    var timing by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.tab_accounts), onBack = { nav.back() }) }) { p ->
        Column(Modifier.padding(p)) {
            SettingRow(stringResource(R.string.account_group), onClick = { nav.navigate(AccountGroupsKey) })
            SettingRow(stringResource(R.string.accounts_setting), onClick = { nav.navigate(AccountListKey()) })
            SettingRow(stringResource(R.string.deleted_groups), onClick = { nav.navigate(DeletedGroupsKey) })
            SettingRow(stringResource(R.string.deleted_accounts), onClick = { nav.navigate(DeletedAccountsKey) })
            SettingRow(stringResource(R.string.transfer_expense_setting), onClick = { nav.navigate(TransferExpenseKey) })
            SettingRow(stringResource(R.string.card_expense_config), cardAccountingLabel(ref.settings.cardAccounting), onClick = { timing = true })
        }
    }
    if (timing) CardTimingDialog(ref.settings.cardAccounting, onSelect = { c -> vm.updateSettings { it.copy(cardAccounting = c) } }, onDismiss = { timing = false })
}

@Composable
fun CardTimingDialog(current: CardAccounting, onSelect: (CardAccounting) -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(
        stringResource(R.string.card_expense_config), CardAccounting.entries, current, { cardAccountingLabel(it) },
        onSelect = onSelect, onDismiss = onDismiss, description = stringResource(R.string.card_expense_config_text),
    )
}

/** Account Group list (AS-010a) with reorder and delete. */
@Composable
fun AccountGroupsScreen(vm: AccountSettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    val groups = ref.groups.filter { it.status == GroupStatus.ACTIVE }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.account_group), onBack = { nav.back() }) {
            IconButton(onClick = { nav.navigate(AccountGroupFormKey()) }) { Icon(Icons.Filled.Add, stringResource(R.string.action_add)) }
        }
    }) { p ->
        LazyColumn(Modifier.padding(p)) {
            itemsIndexed(groups, key = { _, g -> g.id }) { i, g ->
                Row(Modifier.fillMaxWidth().clickable { nav.navigate(AccountGroupFormKey(g.id)) }.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        Text(g.name)
                        when (g.type) {
                            GroupType.CREDIT_CARD -> Text(stringResource(R.string.group_card), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            GroupType.DEBIT_CARD -> Text(stringResource(R.string.group_debit_card), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            else -> Unit
                        }
                    }
                    IconButton(onClick = { vm.launch { vm.repo.reorderGroups(groups.map { it.id }.toMutableList().apply { add(i - 1, removeAt(i)) }) } }, enabled = i > 0) { Icon(Icons.Filled.ArrowUpward, stringResource(R.string.move_up)) }
                    IconButton(onClick = { vm.launch { vm.repo.reorderGroups(groups.map { it.id }.toMutableList().apply { add(i + 1, removeAt(i)) }) } }, enabled = i < groups.size - 1) { Icon(Icons.Filled.ArrowDownward, stringResource(R.string.move_down)) }
                    if (g.builtinId !in setOf(1, 2, 3, 11)) {
                        IconButton(onClick = { vm.launch { vm.repo.setGroupStatus(g.id, GroupStatus.DELETED) } }) { Icon(Icons.Filled.RemoveCircle, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) }
                    } else androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** Add / edit group (AS-011). */
@Composable
fun AccountGroupFormScreen(groupId: String?, vm: AccountSettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    val g = groupId?.let { ref.groupById[it] }
    var name by rememberSaveable(g?.id) { mutableStateOf(g?.name ?: "") }
    var type by rememberSaveable(g?.id) { mutableStateOf(g?.type ?: GroupType.NORMAL) }
    var error by remember { mutableStateOf(false) }
    // A built-in group whose type still equals its id has no type selector (group rules, section 04).
    val selectable = g == null || g.builtinId == null
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.account_group), onBack = { nav.back() }) }) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {
            OutlinedTextField(name, { name = it; error = false }, label = { Text(stringResource(R.string.name)) }, isError = error,
                supportingText = if (error) ({ Text(stringResource(R.string.please_enter_contents)) }) else null, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (selectable) {
                listOf(GroupType.NORMAL to R.string.group_type_default, GroupType.CREDIT_CARD to R.string.group_type_credit, GroupType.DEBIT_CARD to R.string.group_type_debit).forEach { (t, label) ->
                    Row(Modifier.fillMaxWidth().clickable { type = t }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(type == t, onClick = null)
                        Text(stringResource(label), Modifier.padding(start = 8.dp))
                    }
                }
            }
            Button(onClick = {
                if (name.isBlank()) error = true
                else vm.launch { vm.repo.saveGroup(groupId, name, type); nav.back() }
            }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text(stringResource(R.string.action_save)) }
        }
    }
}

/** Accounts Setting list with reorder / show-hide / delete modes (AS-040…AS-043). */
@Composable
fun AccountListScreen(key: AccountListKey, vm: AccountSettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    val balances by vm.balances.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(key.mode) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val list = ref.orderedAccounts().filter { a ->
        val g = ref.groupById[a.groupId]
        g != null && g.status == GroupStatus.ACTIVE && (a.status == AccountStatus.ACTIVE || a.status == AccountStatus.HIDDEN)
    }
    val back = { if (mode != AccountListMode.NORMAL && key.mode == AccountListMode.NORMAL) mode = AccountListMode.NORMAL else nav.back() }
    androidx.activity.compose.BackHandler { back() }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.accounts_setting), onBack = back) {
            if (mode == AccountListMode.NORMAL) {
                IconButton(onClick = { nav.navigate(AccountFormKey()) }) { Icon(Icons.Filled.Add, stringResource(R.string.action_add)) }
                androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menu = true }) { Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more)) }
                androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                    androidx.compose.material3.DropdownMenuItem({ Text(stringResource(R.string.modify_orders)) }, { menu = false; mode = AccountListMode.REORDER })
                    androidx.compose.material3.DropdownMenuItem({ Text(stringResource(R.string.show_hide)) }, { menu = false; mode = AccountListMode.VISIBILITY })
                    androidx.compose.material3.DropdownMenuItem({ Text(stringResource(R.string.action_delete)) }, { menu = false; mode = AccountListMode.DELETE })
                }
                }
            }
        }
    }) { p ->
        LazyColumn(Modifier.padding(p), contentPadding = PaddingValues(bottom = 32.dp)) {
            var lastGroup: String? = null
            list.forEachIndexed { index, a ->
                if (a.groupId != lastGroup) {
                    lastGroup = a.groupId
                    item(key = "g_${a.groupId}") {
                        Text(ref.groupById[a.groupId]?.name ?: "", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                    }
                }
                item(key = a.id) {
                    val hidden = a.status == AccountStatus.HIDDEN
                    Row(
                        Modifier.fillMaxWidth().background(if (selected == a.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable {
                                when (mode) {
                                    AccountListMode.NORMAL -> nav.navigate(AccountFormKey(a.id))
                                    AccountListMode.REORDER -> selected = a.id
                                    AccountListMode.VISIBILITY -> vm.launch { vm.repo.setStatus(a.id, if (hidden) AccountStatus.ACTIVE else AccountStatus.HIDDEN) }
                                    AccountListMode.DELETE -> Unit
                                }
                            }.padding(start = 24.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(a.name, Modifier.weight(1f).padding(vertical = 14.dp), color = if (hidden) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                        when (mode) {
                            AccountListMode.VISIBILITY -> {
                                Text(AmountFormatter.format(Balances.balance(ref, a, balances), ref.accountCurrency(a.id)), style = MaterialTheme.typography.bodySmall)
                                Icon(if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null, Modifier.padding(12.dp))
                            }
                            AccountListMode.DELETE -> IconButton(onClick = { vm.launch { vm.repo.setStatus(a.id, AccountStatus.DELETED) } }) {
                                Icon(Icons.Filled.RemoveCircle, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                            }
                            AccountListMode.REORDER -> if (selected == a.id) {
                                IconButton(onClick = { vm.launch { vm.repo.reorderAccounts(move(ref, list, index, -1)) } }, enabled = index > 0 || ref.groups.indexOfFirst { it.id == a.groupId } > 0) { Icon(Icons.Filled.ArrowUpward, stringResource(R.string.move_up)) }
                                IconButton(onClick = { vm.launch { vm.repo.reorderAccounts(move(ref, list, index, 1)) } }) { Icon(Icons.Filled.ArrowDownward, stringResource(R.string.move_down)) }
                            }
                            AccountListMode.NORMAL -> Unit
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** AS-041: moving past a group boundary moves the account into the neighbouring group. */
private fun move(ref: RefData, list: List<AccountEntity>, index: Int, delta: Int): List<Pair<String, String>> {
    val items = list.map { it.id to it.groupId }.toMutableList()
    val (id, group) = items[index]
    val target = index + delta
    val activeGroups = ref.groups.filter { it.status == GroupStatus.ACTIVE }.map { it.id }
    if (target in items.indices && items[target].second == group) {
        items.removeAt(index)
        items.add(target, id to group)
    } else {
        val gi = activeGroups.indexOf(group) + delta
        val newGroup = activeGroups.getOrNull(gi) ?: return items
        items[index] = id to newGroup
        // Re-sort by group order, keeping relative order.
        val order = activeGroups.withIndex().associate { it.value to it.index }
        val sorted = items.withIndex().sortedWith(compareBy({ order[it.value.second] ?: 0 }, { if (it.value.first == id) (if (delta > 0) -1 else Int.MAX_VALUE) else it.index }))
        return sorted.map { it.value }
    }
    return items
}

/** Deleted accounts (AS-044) and deleted groups (AS-013). */
@Composable
fun DeletedAccountsScreen(vm: AccountSettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    val list = ref.orderedAccounts().filter { it.status == AccountStatus.DELETED && ref.groupById[it.groupId]?.status == GroupStatus.ACTIVE }
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.deleted_accounts), onBack = { nav.back() }) }) { p ->
        if (list.isEmpty()) {
            EmptyState(modifier = Modifier.padding(p))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(p)) {
            items(list, key = { it.id }) { a ->
                Row(Modifier.fillMaxWidth().clickable { nav.navigate(AccountDetailKey(a.id, deleted = true)) }.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        Text(a.name)
                        Text(ref.groupById[a.groupId]?.name ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    IconButton(onClick = { vm.launch { vm.repo.setStatus(a.id, AccountStatus.PURGED) } }) { Icon(Icons.Filled.RemoveCircle, stringResource(R.string.delete_permanently), tint = MaterialTheme.colorScheme.error) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
fun DeletedGroupsScreen(vm: AccountSettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    val list = ref.groups.filter { it.status == GroupStatus.DELETED }
    var restore by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.deleted_groups), onBack = { nav.back() }) }) { p ->
        if (list.isEmpty()) {
            EmptyState(modifier = Modifier.padding(p))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(p)) {
            items(list, key = { it.id }) { g ->
                val count = ref.accounts.count { it.groupId == g.id && it.status != AccountStatus.DELETED && it.status != AccountStatus.PURGED }
                Row(Modifier.fillMaxWidth().clickable { restore = g.id }.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        Text("${g.name} ($count)")
                        Text(stringResource(R.string.action_restore), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { vm.launch { vm.repo.setGroupStatus(g.id, GroupStatus.PURGED) } }) { Icon(Icons.Filled.RemoveCircle, stringResource(R.string.delete_permanently), tint = MaterialTheme.colorScheme.error) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    restore?.let { id -> ConfirmDialog(stringResource(R.string.restore_question), onConfirm = { vm.launch { vm.repo.setGroupStatus(id, GroupStatus.ACTIVE) } }, onDismiss = { restore = null }) }
}

/** Transfer-Expense setting (AS-086). The income option is always shown, default on. */
@Composable
fun TransferExpenseScreen(vm: AccountSettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    val eligible = ref.orderedAccounts().filter { a ->
        val g = ref.groupById[a.groupId] ?: return@filter false
        a.transferExpense || (a.status != AccountStatus.DELETED && a.status != AccountStatus.PURGED && g.status == GroupStatus.ACTIVE &&
            g.type == GroupType.NORMAL && g.builtinId !in setOf(1, 11))
    }
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.transfer_expense_setting), onBack = { nav.back() }) }) { p ->
        LazyColumn(Modifier.padding(p)) {
            item {
                Text(stringResource(R.string.transfer_expense_text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                SwitchRow(stringResource(R.string.transfer_income_option), ref.settings.transferIncome, { v -> vm.updateSettings { it.copy(transferIncome = v) } })
                HorizontalDivider()
            }
            var lastGroup: String? = null
            eligible.forEach { a ->
                if (a.groupId != lastGroup) {
                    lastGroup = a.groupId
                    item(key = "g_${a.groupId}") { Text(ref.groupById[a.groupId]?.name ?: "", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp)) }
                }
                item(key = a.id) {
                    Row(Modifier.fillMaxWidth().clickable { vm.launch { vm.repo.setTransferExpense(a.id, !a.transferExpense) } }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(a.transferExpense, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                        Text(a.name)
                    }
                }
            }
        }
    }
}

data class TotalStatsUi(val periods: List<MonthPeriod> = emptyList(), val balances: List<Double> = emptyList(), val income: List<Double> = emptyList(), val expense: List<Double> = emptyList())

@HiltViewModel
class TotalStatsViewModel @Inject constructor(refs: RefRepository, private val db: NexoDatabase) : ViewModel() {
    val ref = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    val anchor = MutableStateFlow(LocalDate.now())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val ui = combine(ref, anchor, db.transactionDao().observeStamp()) { r, a, _ -> r to a }.mapLatest { (r, a) ->
        val e = r.settings.periods
        var p = e.monthPeriodContaining(a)
        val periods = ArrayList<MonthPeriod>()
        repeat(6) { periods.add(0, p); p = e.previous(p) }
        // B10: included accounts (non-debit, not deleted, included in totals) + their debit cards + unassigned rows.
        val included = r.accounts.filter { acc ->
            val g = r.groupById[acc.groupId] ?: return@filter false
            g.type != GroupType.DEBIT_CARD && acc.status != AccountStatus.DELETED && acc.status != AccountStatus.PURGED && acc.includeInTotals
        }.map { it.id }.toSet()
        val withDebits = included + r.accounts.filter { it.linkedAccountId in included && r.groupType(it.id) == GroupType.DEBIT_CARD }.map { it.id }
        val balances = periods.map { per ->
            db.transactionDao().aggregatesUpTo(per.range.endText).filter { it.accountId == null || it.accountId in withDebits }
                .fold(BigDecimal.ZERO) { acc, x -> acc + BigDecimal.valueOf(if (x.kind.isInflow) x.main else -x.main) }.toDouble()
        }
        val sums = periods.map { per -> db.transactionDao().aggregatesBetween(per.range.startText, per.range.endText) }
        TotalStatsUi(
            periods, balances,
            sums.map { s -> s.filter { it.kind == TxKind.INCOME }.sumOf { it.main } },
            sums.map { s -> s.filter { it.kind == TxKind.EXPENSE }.sumOf { it.main } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TotalStatsUi())

    fun step(forward: Boolean) {
        val e = ref.value.settings.periods
        val p = e.monthPeriodContaining(anchor.value)
        anchor.value = (if (forward) e.next(p) else e.previous(p)).end
    }
}

/** Total Stats (AS-075). */
@Composable
fun TotalStatsScreen(vm: TotalStatsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ref by vm.ref.collectAsStateWithLifecycle()
    val money = LocalMoneyColors.current
    var picker by rememberSaveable { mutableStateOf(false) }
    val main = ref.mainCurrency
    val labels = ui.periods.map { Formats.monthShort(it.label.monthValue) }
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.total_stats), onBack = { nav.back() }) }) { p ->
        Column(Modifier.padding(p).verticalScroll(rememberScrollState()).padding(16.dp)) {
            ui.periods.lastOrNull()?.let { last ->
                PeriodNavigator(Formats.monthYear(last.label), { vm.step(false) }, { vm.step(true) }, { picker = true }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
            }
            Text(stringResource(R.string.balance), style = MaterialTheme.typography.labelLarge)
            Text(AmountFormatter.format(ui.balances.lastOrNull() ?: 0.0, main), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            BarLineChart(labels, emptyList(), emptyList(), line = ui.balances, lineColor = MaterialTheme.colorScheme.primary, showValueOnSelect = { "${labels[it]}: ${AmountFormatter.format(ui.balances[it], main)}" })
            BarLineChart(labels, listOf(ui.income, ui.expense), listOf(money.income, money.expense), modifier = Modifier.padding(top = 16.dp))
            Legend(listOf(stringResource(R.string.income) to money.income, stringResource(R.string.expense) to money.expense))
            ui.periods.indices.reversed().forEach { i ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(labels[i], Modifier.weight(0.6f))
                    Text(AmountFormatter.format(ui.balances[i], main), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(AmountFormatter.format(ui.income[i], main), Modifier.weight(1f), color = money.income, style = MaterialTheme.typography.bodySmall)
                    Text(AmountFormatter.format(ui.expense[i], main), Modifier.weight(1f), color = money.expense, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (picker) ui.periods.lastOrNull()?.let { last ->
        MonthPickerDialog(last.label, onPick = { vm.anchor.value = ref.settings.periods.monthPeriodFor(it).end }, onDismiss = { picker = false })
    }
}
