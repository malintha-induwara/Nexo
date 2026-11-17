package com.nexo.app.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.nexo.app.core.model.GroupType
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.SwitchRow
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.theme.nexo
import com.nexo.app.ui.navigation.LocalNavigator

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

// The income option is always shown, default on.
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
