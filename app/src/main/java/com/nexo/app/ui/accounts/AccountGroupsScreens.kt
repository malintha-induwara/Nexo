package com.nexo.app.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.model.Account
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.theme.nexo
import com.nexo.app.ui.navigation.AccountGroupFormKey
import com.nexo.app.ui.navigation.LocalNavigator

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
