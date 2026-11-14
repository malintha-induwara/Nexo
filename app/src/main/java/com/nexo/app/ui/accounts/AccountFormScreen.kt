package com.nexo.app.ui.accounts

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.common.period.MonthPeriod
import com.nexo.app.core.common.period.PeriodEngine
import com.nexo.app.core.model.Account
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.domain.CardStatement
import com.nexo.app.ui.designsystem.asString
import com.nexo.app.ui.designsystem.component.AmountSheet
import com.nexo.app.ui.designsystem.component.ChoiceDialog
import com.nexo.app.ui.designsystem.component.ConfirmDialog
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.SettingRow
import com.nexo.app.ui.designsystem.component.SwitchRow
import com.nexo.app.ui.navigation.AccountFormKey
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.MainKey
import com.nexo.app.ui.navigation.SubCurrenciesKey
import java.time.LocalDate

@Composable
fun AccountFormScreen(key: AccountFormKey) {
    val vm = hiltViewModel<AccountFormViewModel, AccountFormViewModel.Factory>(creationCallback = { it.create(key) })
    val s by vm.uiState.collectAsStateWithLifecycle()
    val ref = s.ref
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val payLabel = stringResource(R.string.pay)
    var editAmount by rememberSaveable { mutableStateOf(false) }
    var editDay by rememberSaveable { mutableStateOf(0) }
    var pickLinked by rememberSaveable { mutableStateOf(false) }
    var showCurrencies by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.initialize() }
    s.userMessage?.let { message ->
        val text = message.text.asString()
        LaunchedEffect(message.id) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            vm.onUserMessageShown(message.id)
        }
    }
    LaunchedEffect(s.close) {
        when (s.close) {
            AccountFormClose.TO_MAIN -> nav.backTo { it == MainKey }
            AccountFormClose.BACK -> nav.back()
            null -> return@LaunchedEffect
        }
        vm.onCloseHandled()
    }
    if (!s.loaded) return
    val group = ref.groupById[s.groupId]
    val type = group?.type ?: GroupType.NORMAL
    val cur = ref.currency(s.currencyId)
    Scaffold(topBar = {
        NexoTopBar(stringResource(if (s.isNew) R.string.add_account else R.string.account_info), onBack = { nav.back() }) {
            if (!s.isNew) {
                IconButton(onClick = vm::toggleHidden) { Icon(if (s.hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, stringResource(R.string.show_hide)) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())) {
            SettingRow(stringResource(R.string.group), group?.name ?: "", onClick = vm::reopenGroupPicker)
            OutlinedTextField(s.name, vm::setName, label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            if (type != GroupType.DEBIT_CARD) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SettingRowWeighted(stringResource(R.string.amount), AmountFormatter.format(s.amount, cur)) { editAmount = true }
                    TextButton(onClick = { showCurrencies = !showCurrencies }) { Text(cur.symbol) }
                }
            }
            if (showCurrencies || (type == GroupType.DEBIT_CARD && ref.activeCurrencies.size > 1)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ref.activeCurrencies.forEach { c -> FilterChip(c.id == s.currencyId, onClick = { vm.setCurrency(c.id) }, label = { Text(c.isoCode ?: c.symbol) }) }
                    FilterChip(false, onClick = { nav.navigate(SubCurrenciesKey) }, label = { Text("+") })
                }
            }
            if (type == GroupType.CREDIT_CARD || type == GroupType.DEBIT_CARD) {
                SettingRow(stringResource(R.string.payment_account), s.linkedAccountId?.let { ref.accountById[it]?.name } ?: stringResource(R.string.none), onClick = { pickLinked = true })
            }
            OutlinedTextField(s.description, vm::setDescription, label = { Text(stringResource(R.string.description)) }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            if (type == GroupType.CREDIT_CARD) {
                SettingRow(stringResource(R.string.settlement_date), stringResource(R.string.every_n, s.settlementDay), onClick = { editDay = 1 })
                SettingRow(stringResource(R.string.payment_date), stringResource(R.string.every_n, s.paymentDay), onClick = { editDay = 2 })
                val e = PeriodEngine(s.settlementDay)
                val current = e.monthPeriodContaining(LocalDate.now())
                val prev = e.previous(current)
                fun line(p: MonthPeriod): String {
                    val pay = CardStatement.paymentDate(p.end, s.paymentDay)
                    return "%02d-%02d ~ %02d-%02d (%s %02d-%02d)".format(p.start.monthValue, p.start.dayOfMonth, p.end.monthValue, p.end.dayOfMonth, payLabel, pay.monthValue, pay.dayOfMonth)
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(stringResource(R.string.balance_payable) + ": " + line(prev), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.outstanding_balance) + ": " + line(current), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!s.isNew && type != GroupType.DEBIT_CARD) {
                SwitchRow(stringResource(R.string.include_in_totals), s.includeInTotals, vm::setInclude)
            }
            val help = when {
                group?.builtinId == 5 -> R.string.loan_help
                group?.builtinId == 9 -> R.string.overdraft_help
                type == GroupType.DEBIT_CARD -> R.string.debit_help
                else -> null
            }
            help?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            HorizontalDivider()
            Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text(stringResource(R.string.action_save)) }
        }
    }
    if (s.pickGroup) {
        ChoiceDialog(
            stringResource(R.string.group), ref.groups.filter { it.status == GroupStatus.ACTIVE }, group, { it.name },
            onSelect = { vm.setGroup(it.id) }, onDismiss = vm::dismissGroupPicker,
        )
    }
    if (pickLinked) {
        val options = listOf<Account?>(null) + ref.pickerAccounts.filter { it.id != s.id && ref.groupType(it.id) != GroupType.DEBIT_CARD }
        ChoiceDialog(stringResource(R.string.payment_account), options, s.linkedAccountId?.let { ref.accountById[it] }, { it?.name ?: stringResource(R.string.none) },
            onSelect = { vm.setLinked(it?.id) }, onDismiss = { pickLinked = false })
    }
    if (editAmount) AmountSheet(stringResource(R.string.amount), s.amount, cur.decimals, onConfirm = vm::setAmount, onDismiss = { editAmount = false })
    if (editDay != 0) {
        AmountSheet(
            stringResource(if (editDay == 1) R.string.settlement_date else R.string.payment_date),
            (if (editDay == 1) s.settlementDay else s.paymentDay).toDouble(), 0, allowSign = false, calculator = false, maxLength = 2,
            onConfirm = { v -> val d = (v ?: 1.0).toInt(); if (editDay == 1) vm.setSettlement(d) else vm.setPayment(d) },
            onDismiss = { editDay = 0 },
        )
    }
    if (s.confirmCurrency) ConfirmDialog(stringResource(R.string.currency_change_warning), onConfirm = { vm.save(currencyConfirmed = true) }, onDismiss = vm::dismissCurrency)
    s.difference?.let { diff ->
        AlertDialog(
            onDismissRequest = { vm.recordDifference(false) },
            text = { Text(stringResource(if (diff > 0) R.string.difference_income_question else R.string.difference_expense_question)) },
            confirmButton = { TextButton(onClick = { vm.recordDifference(true) }) { Text(stringResource(R.string.action_yes)) } },
            dismissButton = { TextButton(onClick = { vm.recordDifference(false) }) { Text(stringResource(R.string.action_no)) } },
        )
    }
    if (confirmDelete) ConfirmDialog(stringResource(R.string.confirm_delete), onConfirm = vm::delete, onDismiss = { confirmDelete = false })
}

@Composable
private fun RowScope.SettingRowWeighted(title: String, value: String, onClick: () -> Unit) {
    Column(Modifier.weight(1f).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
