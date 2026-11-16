package com.nexo.app.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveCircle
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.ui.designsystem.component.ConfirmDialog
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.theme.nexo
import com.nexo.app.ui.navigation.AccountDetailKey
import com.nexo.app.ui.navigation.LocalNavigator

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
