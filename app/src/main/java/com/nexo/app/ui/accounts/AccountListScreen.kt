package com.nexo.app.ui.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.model.Account
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.RefData
import com.nexo.app.domain.Balances
import com.nexo.app.domain.Net
import com.nexo.app.ui.designsystem.component.NexoMenu
import com.nexo.app.ui.designsystem.component.NexoMenuItem
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.theme.nexo
import com.nexo.app.ui.navigation.AccountFormKey
import com.nexo.app.ui.navigation.AccountListKey
import com.nexo.app.ui.navigation.AccountListMode
import com.nexo.app.ui.navigation.LocalNavigator

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

// Back leaves a mode only when the list opened in normal mode.
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

// Moving past a group boundary moves the account into the neighbouring group.
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
