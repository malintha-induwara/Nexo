package com.nexo.app.ui.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Visibility
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.model.Account
import com.nexo.app.core.model.AccountGroup
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.CardAccounting
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.model.RefData
import com.nexo.app.domain.Balances
import com.nexo.app.domain.Net
import com.nexo.app.ui.designsystem.component.ChoiceDialog
import com.nexo.app.ui.designsystem.component.ConfirmDialog
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.FlowCharts
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.MonthPickerDialog
import com.nexo.app.ui.designsystem.component.NexoMenu
import com.nexo.app.ui.designsystem.component.NexoMenuItem
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.PeriodNavigator
import com.nexo.app.ui.designsystem.component.SettingRow
import com.nexo.app.ui.designsystem.component.SwitchRow
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.designsystem.theme.Space
import com.nexo.app.ui.designsystem.theme.nexo
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
import java.time.YearMonth
import kotlinx.coroutines.launch

@Composable
fun cardAccountingLabel(c: CardAccounting) = stringResource(
    if (c == CardAccounting.AT_USE) {
        R.string.card_at_time
    } else {
        R.string.card_lump_sum
    },
)

@Composable
fun AccountSettingsMenuRoute(vm: AccountSettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    AccountSettingsMenuScreen(
        state = state,
        onBack = nav::back,
        onOpenGroups = { nav.navigate(AccountGroupsKey) },
        onOpenAccounts = { nav.navigate(AccountListKey()) },
        onOpenDeletedGroups = { nav.navigate(DeletedGroupsKey) },
        onOpenDeletedAccounts = { nav.navigate(DeletedAccountsKey) },
        onOpenTransferExpense = { nav.navigate(TransferExpenseKey) },
        onCardAccountingChange = { c -> vm.updateSettings { it.copy(cardAccounting = c) } },
    )
}

@Composable
internal fun AccountSettingsMenuScreen(
    state: AccountSettingsUiState,
    onBack: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenDeletedGroups: () -> Unit,
    onOpenDeletedAccounts: () -> Unit,
    onOpenTransferExpense: () -> Unit,
    onCardAccountingChange: (CardAccounting) -> Unit,
) {
    val ref = state.ref
    var timing by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.tab_accounts), onBack = onBack)
    }) { p ->
        Column(Modifier.padding(p)) {
            SettingRow(stringResource(R.string.account_group), onClick = onOpenGroups)
            SettingRow(stringResource(R.string.accounts_setting), onClick = onOpenAccounts)
            SettingRow(stringResource(R.string.deleted_groups), onClick = onOpenDeletedGroups)
            SettingRow(stringResource(R.string.deleted_accounts), onClick = onOpenDeletedAccounts)
            SettingRow(
                stringResource(R.string.transfer_expense_setting),
                onClick = onOpenTransferExpense,
            )
            SettingRow(
                stringResource(R.string.card_expense_config),
                cardAccountingLabel(ref.settings.cardAccounting),
                onClick = {
                    timing = true
                },
            )
        }
    }
    if (timing) {
        CardTimingDialog(
            ref.settings.cardAccounting,
            onSelect = onCardAccountingChange,
            onDismiss = { timing = false },
        )
    }
}

@Composable
fun CardTimingDialog(
    current: CardAccounting,
    onSelect: (CardAccounting) -> Unit,
    onDismiss: () -> Unit,
) {
    ChoiceDialog(
        stringResource(R.string.card_expense_config),
        CardAccounting.entries,
        current,
        {
            cardAccountingLabel(it)
        },
        onSelect = onSelect,
        onDismiss = onDismiss,
        description = stringResource(R.string.card_expense_config_text),
    )
}

/** Account Group list with reorder and delete. */
@Composable
fun AccountGroupsRoute(vm: AccountGroupsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    AccountGroupsScreen(
        state = state,
        onBack = nav::back,
        onAddGroup = { nav.navigate(AccountGroupFormKey()) },
        onOpenGroup = { nav.navigate(AccountGroupFormKey(it)) },
        onReorder = vm::reorderGroups,
        onDelete = { vm.setGroupStatus(it, GroupStatus.DELETED) },
    )
}

@Composable
internal fun AccountGroupsScreen(
    state: AccountGroupsUiState,
    onBack: () -> Unit,
    onAddGroup: () -> Unit,
    onOpenGroup: (groupId: String) -> Unit,
    onReorder: (groupIds: List<String>) -> Unit,
    onDelete: (groupId: String) -> Unit,
) {
    val ref = state.ref
    val groups = ref.groups.filter { it.status == GroupStatus.ACTIVE }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.account_group), onBack = onBack) {
            IconButton(onClick = onAddGroup) {
                Icon(Icons.Filled.Add, stringResource(R.string.action_add))
            }
        }
    }) { p ->
        LazyColumn(Modifier.padding(p)) {
            itemsIndexed(groups, key = { _, g -> g.id }) { i, g ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onOpenGroup(g.id)
                    }.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        Text(g.name)
                        when (g.type) {
                            GroupType.CREDIT_CARD -> Text(
                                stringResource(R.string.group_card),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )

                            GroupType.DEBIT_CARD -> Text(
                                stringResource(R.string.group_debit_card),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )

                            else -> Unit
                        }
                    }
                    IconButton(onClick = {
                        onReorder(
                            groups.map {
                                it.id
                            }.toMutableList().apply { add(i - 1, removeAt(i)) },
                        )
                    }, enabled = i > 0) {
                        Icon(Icons.Filled.ArrowUpward, stringResource(R.string.move_up))
                    }
                    IconButton(onClick = {
                        onReorder(
                            groups.map {
                                it.id
                            }.toMutableList().apply { add(i + 1, removeAt(i)) },
                        )
                    }, enabled = i < groups.size - 1) {
                        Icon(Icons.Filled.ArrowDownward, stringResource(R.string.move_down))
                    }
                    if (g.builtinId !in setOf(1, 2, 3, 11)) {
                        IconButton(onClick = {
                            onDelete(g.id)
                        }) {
                            Icon(
                                Icons.Filled.RemoveCircle,
                                stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        Spacer(Modifier.padding(24.dp))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
fun AccountGroupFormRoute(groupId: String?, vm: AccountGroupsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.groupSaved) {
        if (state.groupSaved) {
            nav.back()
            vm.onGroupSavedHandled()
        }
    }
    AccountGroupFormScreen(
        state = state,
        groupId = groupId,
        onBack = nav::back,
        onSave = { name, type -> vm.saveGroup(groupId, name, type) },
    )
}

@Composable
internal fun AccountGroupFormScreen(
    state: AccountGroupsUiState,
    groupId: String?,
    onBack: () -> Unit,
    onSave: (name: String, type: GroupType) -> Unit,
) {
    val ref = state.ref
    val g = groupId?.let { ref.groupById[it] }
    var name by rememberSaveable(g?.id) { mutableStateOf(g?.name ?: "") }
    var type by rememberSaveable(g?.id) { mutableStateOf(g?.type ?: GroupType.NORMAL) }
    var error by remember { mutableStateOf(false) }
    // A built-in group whose type still equals its id has no type selector.
    val selectable = g == null || g.builtinId == null
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.account_group), onBack = onBack)
    }) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {
            OutlinedTextField(
                name,
                {
                    name = it
                    error = false
                },
                label = { Text(stringResource(R.string.name)) },
                isError = error,
                supportingText = if (error) {
                    (
                        {
                            Text(stringResource(R.string.please_enter_contents))
                        }
                        )
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (selectable) {
                listOf(
                    GroupType.NORMAL to R.string.group_type_default,
                    GroupType.CREDIT_CARD to R.string.group_type_credit,
                    GroupType.DEBIT_CARD to R.string.group_type_debit,
                ).forEach { (t, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            type = t
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(type == t, onClick = null)
                        Text(stringResource(label), Modifier.padding(start = 8.dp))
                    }
                }
            }
            Button(onClick = {
                if (name.isBlank()) {
                    error = true
                } else {
                    onSave(name, type)
                }
            }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

/** Accounts Setting list with reorder / show-hide / delete modes. */
@Composable
fun AccountListRoute(key: AccountListKey, vm: AccountListViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    AccountListScreen(
        state = state,
        startMode = key.mode,
        onBack = nav::back,
        onAddAccount = { nav.navigate(AccountFormKey()) },
        onOpenAccount = { nav.navigate(AccountFormKey(it)) },
        onStatusChange = vm::setStatus,
        onReorder = vm::reorderAccounts,
    )
}

/** [startMode] is the mode the list opened in; back leaves a mode only when it opened in normal mode. */
@Composable
internal fun AccountListScreen(
    state: AccountListUiState,
    startMode: AccountListMode,
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onOpenAccount: (accountId: String) -> Unit,
    onStatusChange: (accountId: String, AccountStatus) -> Unit,
    onReorder: (order: List<Pair<String, String>>) -> Unit,
) {
    val ref = state.ref
    val balances = state.balances
    var mode by rememberSaveable { mutableStateOf(startMode) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val list = ref.orderedAccounts().filter { a ->
        val g = ref.groupById[a.groupId]
        g != null && g.status == GroupStatus.ACTIVE &&
            (a.status == AccountStatus.ACTIVE || a.status == AccountStatus.HIDDEN)
    }
    val back = {
        if (mode != AccountListMode.NORMAL && startMode == AccountListMode.NORMAL) {
            mode = AccountListMode.NORMAL
        } else {
            onBack()
        }
    }
    BackHandler { back() }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.accounts_setting), onBack = back) {
            if (mode == AccountListMode.NORMAL) {
                IconButton(onClick = onAddAccount) {
                    Icon(Icons.Filled.Add, stringResource(R.string.action_add))
                }
                Box {
                    IconButton(onClick = {
                        menu = true
                    }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.action_more),
                        )
                    }
                    NexoMenu(menu, { menu = false }) {
                        NexoMenuItem(stringResource(R.string.modify_orders), {
                            menu = false
                            mode = AccountListMode.REORDER
                        }, Icons.Rounded.SwapVert)
                        NexoMenuItem(stringResource(R.string.show_hide), {
                            menu = false
                            mode = AccountListMode.VISIBILITY
                        }, Icons.Rounded.Visibility)
                        NexoMenuItem(stringResource(R.string.action_delete), {
                            menu = false
                            mode = AccountListMode.DELETE
                        }, Icons.Rounded.DeleteOutline, danger = true)
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
                        Text(
                            ref.groupById[a.groupId]?.name ?: "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.nexo.primaryText,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                }
                item(key = a.id) {
                    val hidden = a.status == AccountStatus.HIDDEN
                    Row(
                        Modifier.fillMaxWidth().background(
                            if (selected == a.id) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            } else {
                                Color.Transparent
                            },
                        )
                            .clickable {
                                when (mode) {
                                    AccountListMode.NORMAL -> onOpenAccount(a.id)

                                    AccountListMode.REORDER -> selected = a.id

                                    AccountListMode.VISIBILITY -> onStatusChange(
                                        a.id,
                                        if (hidden) AccountStatus.ACTIVE else AccountStatus.HIDDEN,
                                    )

                                    AccountListMode.DELETE -> Unit
                                }
                            }.padding(start = 24.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            a.name,
                            Modifier.weight(1f).padding(vertical = 14.dp),
                            color = if (hidden) {
                                MaterialTheme.colorScheme.outline
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        when (mode) {
                            AccountListMode.VISIBILITY -> {
                                Text(
                                    AmountFormatter.format(
                                        Balances.balance(ref, a, balances),
                                        ref.accountCurrency(a.id),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Icon(
                                    if (hidden) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    null,
                                    Modifier.padding(12.dp),
                                )
                            }

                            AccountListMode.DELETE -> IconButton(onClick = {
                                onStatusChange(a.id, AccountStatus.DELETED)
                            }) {
                                Icon(
                                    Icons.Filled.RemoveCircle,
                                    stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }

                            AccountListMode.REORDER -> if (selected == a.id) {
                                IconButton(
                                    onClick = {
                                        onReorder(move(ref, list, index, -1))
                                    },
                                    enabled =
                                        index > 0 || ref.groups.indexOfFirst {
                                            it.id == a.groupId
                                        } > 0,
                                ) {
                                    Icon(Icons.Filled.ArrowUpward, stringResource(R.string.move_up))
                                }
                                IconButton(onClick = {
                                    onReorder(move(ref, list, index, 1))
                                }) {
                                    Icon(
                                        Icons.Filled.ArrowDownward,
                                        stringResource(R.string.move_down),
                                    )
                                }
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

/** Moving past a group boundary moves the account into the neighbouring group. */
private fun move(
    ref: RefData,
    list: List<Account>,
    index: Int,
    delta: Int,
): List<Pair<String, String>> {
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
        val sorted = items.withIndex().sortedWith(
            compareBy({ order[it.value.second] ?: 0 }, {
                if (it.value.first == id) {
                    (if (delta > 0) -1 else Int.MAX_VALUE)
                } else {
                    it.index
                }
            }),
        )
        return sorted.map { it.value }
    }
    return items
}

/** Deleted accounts and deleted groups. */
@Composable
fun DeletedAccountsRoute(vm: DeletedItemsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    DeletedAccountsScreen(
        state = state,
        onBack = nav::back,
        onOpenAccount = { nav.navigate(AccountDetailKey(it, deleted = true)) },
        onPurge = { vm.setStatus(it, AccountStatus.PURGED) },
    )
}

@Composable
internal fun DeletedAccountsScreen(
    state: DeletedItemsUiState,
    onBack: () -> Unit,
    onOpenAccount: (accountId: String) -> Unit,
    onPurge: (accountId: String) -> Unit,
) {
    val ref = state.ref
    val list = ref.orderedAccounts().filter {
        it.status == AccountStatus.DELETED &&
            ref.groupById[it.groupId]?.status == GroupStatus.ACTIVE
    }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.deleted_accounts), onBack = onBack)
    }) { p ->
        if (list.isEmpty()) {
            EmptyState(modifier = Modifier.padding(p))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(p)) {
            items(list, key = { it.id }) { a ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onOpenAccount(a.id)
                    }.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        Text(a.name)
                        Text(
                            ref.groupById[a.groupId]?.name ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    IconButton(onClick = {
                        onPurge(a.id)
                    }) {
                        Icon(
                            Icons.Filled.RemoveCircle,
                            stringResource(R.string.delete_permanently),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
fun DeletedGroupsRoute(vm: DeletedItemsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    DeletedGroupsScreen(
        state = state,
        onBack = nav::back,
        onRestore = { vm.setGroupStatus(it, GroupStatus.ACTIVE) },
        onPurge = { vm.setGroupStatus(it, GroupStatus.PURGED) },
    )
}

@Composable
internal fun DeletedGroupsScreen(
    state: DeletedItemsUiState,
    onBack: () -> Unit,
    onRestore: (groupId: String) -> Unit,
    onPurge: (groupId: String) -> Unit,
) {
    val ref = state.ref
    val list = ref.groups.filter { it.status == GroupStatus.DELETED }
    var restore by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.deleted_groups), onBack = onBack)
    }) { p ->
        if (list.isEmpty()) {
            EmptyState(modifier = Modifier.padding(p))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(p)) {
            items(list, key = { it.id }) { g ->
                val count = ref.accounts.count {
                    it.groupId == g.id &&
                        it.status != AccountStatus.DELETED &&
                        it.status != AccountStatus.PURGED
                }
                Row(
                    Modifier.fillMaxWidth().clickable {
                        restore = g.id
                    }.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        Text("${g.name} ($count)")
                        Text(
                            stringResource(R.string.action_restore),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.nexo.primaryText,
                        )
                    }
                    IconButton(onClick = {
                        onPurge(g.id)
                    }) {
                        Icon(
                            Icons.Filled.RemoveCircle,
                            stringResource(R.string.delete_permanently),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    restore?.let { id ->
        ConfirmDialog(stringResource(R.string.restore_question), onConfirm = {
            onRestore(id)
        }, onDismiss = {
            restore = null
        })
    }
}

@Composable
fun TransferExpenseRoute(vm: AccountSettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    TransferExpenseScreen(
        state = state,
        onBack = nav::back,
        onTransferIncomeChange = { v -> vm.updateSettings { it.copy(transferIncome = v) } },
        onTransferExpenseChange = vm::setTransferExpense,
    )
}

/** The income option is always shown, default on. */
@Composable
internal fun TransferExpenseScreen(
    state: AccountSettingsUiState,
    onBack: () -> Unit,
    onTransferIncomeChange: (Boolean) -> Unit,
    onTransferExpenseChange: (accountId: String, on: Boolean) -> Unit,
) {
    val ref = state.ref
    val eligible = ref.orderedAccounts().filter { a ->
        val g = ref.groupById[a.groupId] ?: return@filter false
        a.transferExpense ||
            (
                a.status != AccountStatus.DELETED && a.status != AccountStatus.PURGED &&
                    g.status == GroupStatus.ACTIVE &&
                    g.type == GroupType.NORMAL && g.builtinId !in setOf(1, 11)
                )
    }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.transfer_expense_setting), onBack = onBack)
    }) { p ->
        LazyColumn(Modifier.padding(p)) {
            item {
                Text(
                    stringResource(R.string.transfer_expense_text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
                SwitchRow(
                    stringResource(R.string.transfer_income_option),
                    ref.settings.transferIncome,
                    onTransferIncomeChange,
                )
                HorizontalDivider()
            }
            var lastGroup: String? = null
            eligible.forEach { a ->
                if (a.groupId != lastGroup) {
                    lastGroup = a.groupId
                    item(key = "g_${a.groupId}") {
                        Text(
                            ref.groupById[a.groupId]?.name ?: "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.nexo.primaryText,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                        )
                    }
                }
                item(key = a.id) {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            onTransferExpenseChange(a.id, !a.transferExpense)
                        }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            a.transferExpense,
                            onCheckedChange = null,
                            modifier = Modifier.padding(8.dp),
                        )
                        Text(a.name)
                    }
                }
            }
        }
    }
}

@Composable
fun TotalStatsRoute(vm: TotalStatsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ui by vm.uiState.collectAsStateWithLifecycle()
    TotalStatsScreen(ui = ui, onBack = nav::back, onStep = vm::step, onPickMonth = vm::pickMonth)
}

@Composable
internal fun TotalStatsScreen(
    ui: TotalStatsUiState,
    onBack: () -> Unit,
    onStep: (forward: Boolean) -> Unit,
    onPickMonth: (YearMonth) -> Unit,
) {
    val ref = ui.ref
    var picker by rememberSaveable { mutableStateOf(false) }
    val main = ref.mainCurrency
    val labels = ui.periods.map { Formats.monthShort(it.label.monthValue) }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.total_stats), onBack = onBack)
    }) { p ->
        Column(Modifier.fillMaxSize().padding(p)) {
            ui.periods.lastOrNull()?.let { last ->
                PeriodNavigator(Formats.monthYear(last.label), {
                    onStep(false)
                }, { onStep(true) }, {
                    picker = true
                }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
            }
            if (ui.periods.isNotEmpty()) {
                FlowCharts(
                    labels, ui.balances, ui.income, ui.expense, main,
                    flowTitle =
                        stringResource(R.string.income) + " / " + stringResource(R.string.expenses),
                    inflowLabel = stringResource(R.string.income),
                    outflowLabel = stringResource(R.string.expenses),
                    contentPadding = PaddingValues(top = Space.xs, bottom = Space.xl),
                )
            }
        }
    }
    if (picker) {
        ui.periods.lastOrNull()?.let { last ->
            MonthPickerDialog(last.label, onPick = onPickMonth, onDismiss = { picker = false })
        }
    }
}

/** The preview book plus a savings group, a deleted account and a deleted group. */
internal val previewSettingsRef = PreviewData.ref.copy(
    groups = PreviewData.groups +
        listOf(
            AccountGroup("4", "Savings", GroupType.NORMAL, 4, 3, updatedAt = 0),
            AccountGroup(
                "trip",
                "Trip fund",
                GroupType.NORMAL,
                null,
                4,
                GroupStatus.DELETED,
                updatedAt = 0,
            ),
        ),
    accounts = PreviewData.accounts +
        listOf(
            Account(
                "fd",
                "Fixed deposit",
                "4",
                "LKR",
                3,
                transferExpense = true,
                createdAt = 0,
                updatedAt = 0,
            ),
            Account(
                "old",
                "Old wallet",
                "11",
                "LKR",
                4,
                status = AccountStatus.DELETED,
                createdAt = 0,
                updatedAt = 0,
            ),
        ),
)

internal val previewTotalStatsState: TotalStatsUiState = run {
    val periods = PreviewData.ref.settings.periods
    TotalStatsUiState(
        ref = PreviewData.ref,
        anchor = PreviewData.today,
        periods = generateSequence(periods.monthPeriodContaining(PreviewData.today)) {
            periods.previous(it)
        }.take(6).toList().reversed(),
        balances = listOf(180_000.0, 192_500.0, 188_000.0, 205_250.0, 221_000.0, 239_250.0),
        income = listOf(185_000.0, 185_000.0, 190_000.0, 185_000.0, 195_000.0, 185_000.0),
        expense = listOf(162_000.0, 172_500.0, 194_500.0, 167_750.0, 179_250.0, 166_750.0),
    )
}

@Preview
@Composable
private fun AccountSettingsMenuScreenPreview() = NexoPreview {
    AccountSettingsMenuScreen(
        state = AccountSettingsUiState(PreviewData.ref),
        onBack = {},
        onOpenGroups = {},
        onOpenAccounts = {},
        onOpenDeletedGroups = {},
        onOpenDeletedAccounts = {},
        onOpenTransferExpense = {},
        onCardAccountingChange = {},
    )
}

@Preview
@Composable
private fun AccountGroupsScreenPreview() = NexoPreview {
    AccountGroupsScreen(
        state = AccountGroupsUiState(previewSettingsRef),
        onBack = {},
        onAddGroup = {},
        onOpenGroup = {},
        onReorder = {},
        onDelete = {},
    )
}

@Preview
@Composable
private fun AccountGroupFormScreenPreview() = NexoPreview {
    AccountGroupFormScreen(
        state = AccountGroupsUiState(previewSettingsRef),
        groupId = null,
        onBack = {},
        onSave = { _, _ -> },
    )
}

@Preview
@Composable
private fun AccountListScreenPreview() = NexoPreview {
    AccountListScreen(
        state = AccountListUiState(
            ref = previewSettingsRef,
            balances = mapOf(
                "cash" to Net(12_500.0, 12_500.0),
                "bank" to Net(245_000.0, 245_000.0),
                "card" to Net(-18_250.0, -18_250.0),
                "fd" to Net(500_000.0, 500_000.0),
            ),
        ),
        startMode = AccountListMode.VISIBILITY,
        onBack = {},
        onAddAccount = {},
        onOpenAccount = {},
        onStatusChange = { _, _ -> },
        onReorder = {},
    )
}

@Preview
@Composable
private fun DeletedAccountsScreenPreview() = NexoPreview {
    DeletedAccountsScreen(
        state = DeletedItemsUiState(previewSettingsRef),
        onBack = {},
        onOpenAccount = {},
        onPurge = {},
    )
}

@Preview
@Composable
private fun DeletedGroupsScreenPreview() = NexoPreview {
    DeletedGroupsScreen(
        state = DeletedItemsUiState(previewSettingsRef),
        onBack = {},
        onRestore = {},
        onPurge = {},
    )
}

@Preview
@Composable
private fun TransferExpenseScreenPreview() = NexoPreview {
    TransferExpenseScreen(
        state = AccountSettingsUiState(previewSettingsRef),
        onBack = {},
        onTransferIncomeChange = {},
        onTransferExpenseChange = { _, _ -> },
    )
}

@Preview
@Composable
private fun TotalStatsScreenPreview() = NexoPreview {
    TotalStatsScreen(ui = previewTotalStatsState, onBack = {}, onStep = {}, onPickMonth = {})
}
