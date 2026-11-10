package com.nexo.app.ui.accounts

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.database.entity.AccountEntity
import com.nexo.app.core.database.entity.TransactionEntity
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.money.AmountFormatter
import com.nexo.app.core.money.Money
import com.nexo.app.data.AccountRepository
import com.nexo.app.data.RefRepository
import com.nexo.app.data.TransactionWriter
import com.nexo.app.domain.Balances
import com.nexo.app.domain.CardStatement
import com.nexo.app.domain.Conversion
import com.nexo.app.domain.RefData
import com.nexo.app.ui.components.AmountSheet
import com.nexo.app.ui.components.ChoiceDialog
import com.nexo.app.ui.components.ConfirmDialog
import com.nexo.app.ui.components.NexoTopBar
import com.nexo.app.ui.components.SettingRow
import com.nexo.app.ui.components.SwitchRow
import com.nexo.app.ui.navigation.AccountFormKey
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.MainKey
import com.nexo.app.ui.navigation.SubCurrenciesKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class AccountFormUi(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val id: String = UUID.randomUUID().toString(),
    val groupId: String? = null,
    val name: String = "",
    val amount: Double = 0.0,
    val balance: Double = 0.0,
    val currencyId: String = "",
    val originalCurrencyId: String = "",
    val linkedAccountId: String? = null,
    val description: String = "",
    val settlementDay: Int = 1,
    val paymentDay: Int = 1,
    val includeInTotals: Boolean = true,
    val hidden: Boolean = false,
    val pickGroup: Boolean = false,
    val difference: Double? = null,
    val confirmCurrency: Boolean = false,
)

sealed interface AccountFormEvent {
    data class Message(val text: Int) : AccountFormEvent
    data class Close(val toMain: Boolean) : AccountFormEvent
}

@HiltViewModel(assistedFactory = AccountFormViewModel.Factory::class)
class AccountFormViewModel @AssistedInject constructor(
    @Assisted private val key: AccountFormKey,
    private val db: NexoDatabase,
    refs: RefRepository,
    private val repo: AccountRepository,
    private val writer: TransactionWriter,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: AccountFormKey): AccountFormViewModel
    }

    val ref = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    private val _ui = MutableStateFlow(AccountFormUi())
    val ui = _ui.asStateFlow()
    private val _events = Channel<AccountFormEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var rows: List<TransactionEntity> = emptyList()
    private var original: AccountEntity? = null

    init {
        viewModelScope.launch {
            val r = refs.snapshot()
            val a = key.accountId?.let { r.accountById[it] }
            original = a
            if (a == null) {
                val g = key.groupId ?: r.groups.firstOrNull { it.status == GroupStatus.ACTIVE }?.id
                _ui.value = AccountFormUi(loaded = true, groupId = g, name = key.presetName ?: "", currencyId = r.mainCurrency.id, originalCurrencyId = r.mainCurrency.id, pickGroup = key.groupId == null)
            } else {
                rows = db.transactionDao().getAllForAccounts(listOf(a.id) + r.linkedDebitCards(a.id).map { it.id })
                val bal = Money.round(balanceIn(r, a.currencyId, a), r.currency(a.currencyId).decimals)
                _ui.value = AccountFormUi(
                    loaded = true, isNew = false, id = a.id, groupId = a.groupId, name = a.name, amount = bal, balance = bal,
                    currencyId = a.currencyId, originalCurrencyId = a.currencyId, linkedAccountId = a.linkedAccountId, description = a.description,
                    settlementDay = a.settlementDay, paymentDay = a.paymentDay, includeInTotals = a.includeInTotals, hidden = a.status == AccountStatus.HIDDEN,
                )
            }
        }
    }

    /** Balance of the account if its rows were expressed in [currencyId] (AS-033 live recompute). */
    private fun balanceIn(r: RefData, currencyId: String, a: AccountEntity): Double {
        val target = r.currency(currencyId)
        return Money.sum(
            rows.map { t ->
                val v = if (target.id == r.mainCurrency.id) t.amountMain
                else if (currencyId == a.currencyId) t.amountAccount
                else Conversion.accountAmount(t.amount, t.amountMain, r.currency(t.currencyId), target, r.mainCurrency)
                if (t.kind.isInflow) v else -v
            },
        )
    }

    fun groupType(): GroupType = ref.value.groupById[_ui.value.groupId]?.type ?: GroupType.NORMAL

    fun setGroup(id: String) = _ui.update { it.copy(groupId = id, pickGroup = false) }
    fun dismissGroupPicker() = _ui.update { it.copy(pickGroup = false) }
    fun reopenGroupPicker() = _ui.update { it.copy(pickGroup = true) }
    fun setName(v: String) = _ui.update { it.copy(name = v) }
    fun setAmount(v: Double?) = _ui.update { it.copy(amount = v ?: 0.0) }
    fun setDescription(v: String) = _ui.update { it.copy(description = v) }
    fun setSettlement(v: Int) = _ui.update { it.copy(settlementDay = if (v in 1..31) v else 1) }
    fun setPayment(v: Int) = _ui.update { it.copy(paymentDay = if (v in 1..31) v else 1) }
    fun setInclude(v: Boolean) = _ui.update { it.copy(includeInTotals = v) }
    fun toggleHidden() = _ui.update { it.copy(hidden = !it.hidden) }

    fun setCurrency(id: String) {
        val r = ref.value
        val a = original
        _ui.update { s ->
            if (a == null) s.copy(currencyId = id)
            else {
                val bal = Money.round(balanceIn(r, id, a), r.currency(id).decimals)
                s.copy(currencyId = id, amount = bal, balance = bal)
            }
        }
    }

    fun setLinked(id: String?) {
        val r = ref.value
        _ui.update { s ->
            var n = s.copy(linkedAccountId = id)
            // AS-023: a debit card takes its payment account's currency when that is not the main currency.
            if (groupType() == GroupType.DEBIT_CARD && id != null) {
                val c = r.accountCurrency(id)
                if (c.id != r.mainCurrency.id) n = n.copy(currencyId = c.id)
            }
            n
        }
    }

    fun save(currencyConfirmed: Boolean = false) {
        val s = _ui.value
        val r = ref.value
        if (s.name.isBlank()) {
            _events.trySend(AccountFormEvent.Message(R.string.no_title))
            return
        }
        val type = groupType()
        if (type == GroupType.DEBIT_CARD && s.linkedAccountId != null) {
            val pay = r.accountCurrency(s.linkedAccountId)
            if (pay.id != r.mainCurrency.id && pay.id != s.currencyId) {
                _events.trySend(AccountFormEvent.Message(R.string.debit_currency_mismatch))
                return
            }
        }
        if (!s.isNew && s.currencyId != s.originalCurrencyId && !currencyConfirmed) {
            _ui.update { it.copy(confirmCurrency = true) }
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val o = original
            val account = AccountEntity(
                id = s.id,
                name = s.name.trim(),
                groupId = s.groupId ?: r.groups.first().id,
                currencyId = s.currencyId,
                orderSeq = o?.orderSeq ?: repo.nextAccountOrder(),
                description = s.description,
                status = if (s.hidden) AccountStatus.HIDDEN else AccountStatus.ACTIVE,
                includeInTotals = if (type == GroupType.DEBIT_CARD) true else s.includeInTotals,
                linkedAccountId = if (type == GroupType.NORMAL) null else s.linkedAccountId,
                settlementDay = if (type == GroupType.CREDIT_CARD) s.settlementDay else 1,
                paymentDay = if (type == GroupType.CREDIT_CARD) s.paymentDay else 1,
                transferExpense = o?.transferExpense ?: false,
                createdAt = o?.createdAt ?: now,
                updatedAt = now,
            )
            repo.saveAccount(account)
            val diff = if (type == GroupType.DEBIT_CARD) 0.0 else Money.round(s.amount - (if (s.isNew) 0.0 else s.balance), r.currency(s.currencyId).decimals)
            if (diff != 0.0) _ui.update { it.copy(difference = diff, confirmCurrency = false) }
            else _events.send(AccountFormEvent.Close(false))
        }
    }

    fun recordDifference(asIncomeExpense: Boolean) {
        val s = _ui.value
        val diff = s.difference ?: return
        viewModelScope.launch {
            writer.recordBalanceDifference(s.id, diff, asIncomeExpense, s.description)
            _ui.update { it.copy(difference = null) }
            _events.send(AccountFormEvent.Close(false))
        }
    }

    fun dismissCurrency() = _ui.update { it.copy(confirmCurrency = false) }

    fun delete() = viewModelScope.launch {
        repo.setStatus(_ui.value.id, AccountStatus.DELETED)
        _events.send(AccountFormEvent.Close(true))
    }
}

@Composable
fun AccountFormScreen(key: AccountFormKey) {
    val vm = hiltViewModel<AccountFormViewModel, AccountFormViewModel.Factory>(creationCallback = { it.create(key) })
    val s by vm.ui.collectAsStateWithLifecycle()
    val ref by vm.ref.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val payLabel = stringResource(R.string.pay)
    var editAmount by rememberSaveable { mutableStateOf(false) }
    var editDay by rememberSaveable { mutableStateOf(0) }
    var pickLinked by rememberSaveable { mutableStateOf(false) }
    var showCurrencies by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is AccountFormEvent.Message -> Toast.makeText(context, e.text, Toast.LENGTH_SHORT).show()
                is AccountFormEvent.Close -> if (e.toMain) nav.backTo { it == MainKey } else nav.back()
            }
        }
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
                val e = com.nexo.app.core.period.PeriodEngine(s.settlementDay)
                val current = e.monthPeriodContaining(LocalDate.now())
                val prev = e.previous(current)
                fun line(p: com.nexo.app.core.period.MonthPeriod): String {
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
        val options = listOf<AccountEntity?>(null) + ref.pickerAccounts.filter { it.id != s.id && ref.groupType(it.id) != GroupType.DEBIT_CARD }
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
private fun androidx.compose.foundation.layout.RowScope.SettingRowWeighted(title: String, value: String, onClick: () -> Unit) {
    Column(Modifier.weight(1f).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
