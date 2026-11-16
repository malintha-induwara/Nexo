package com.nexo.app.ui.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.model.CardAccounting
import com.nexo.app.ui.designsystem.component.ChoiceDialog
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.SettingRow
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.designsystem.theme.nexo
import com.nexo.app.ui.navigation.AccountGroupsKey
import com.nexo.app.ui.navigation.AccountListKey
import com.nexo.app.ui.navigation.DeletedAccountsKey
import com.nexo.app.ui.navigation.DeletedGroupsKey
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.TransferExpenseKey

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
