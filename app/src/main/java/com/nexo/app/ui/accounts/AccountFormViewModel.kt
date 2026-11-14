package com.nexo.app.ui.accounts

import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.common.money.Conversion
import com.nexo.app.core.common.money.Money
import com.nexo.app.core.model.Account
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.Transaction
import com.nexo.app.data.repository.AccountsRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.ui.designsystem.UiText
import com.nexo.app.ui.designsystem.UserMessage
import com.nexo.app.ui.navigation.AccountFormKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountFormUiState(
    val loaded: Boolean = false,
    val ref: RefData = RefData(),
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
    /** Shown once, then cleared with [AccountFormViewModel.onUserMessageShown]. */
    val userMessage: UserMessage? = null,
    /** The form is done; handled once, then cleared with [AccountFormViewModel.onCloseHandled]. */
    val close: AccountFormClose? = null,
)

/** How the form closes: back to where it came from, or all the way to the main screen after a delete. */
enum class AccountFormClose { BACK, TO_MAIN }

@HiltViewModel(assistedFactory = AccountFormViewModel.Factory::class)
class AccountFormViewModel @AssistedInject constructor(
    @Assisted private val key: AccountFormKey,
    private val refs: ReferenceDataRepository,
    private val repo: AccountsRepository,
    private val transactions: TransactionsRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: AccountFormKey): AccountFormViewModel
    }

    /** Kept for event handlers that need the reference data synchronously. */
    private val ref = refs.getRefDataStream().stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    private val _ui = MutableStateFlow(AccountFormUiState())

    val uiState: StateFlow<AccountFormUiState> = combine(_ui, ref) { s, r -> s.copy(ref = r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountFormUiState())

    private var rows: List<Transaction> = emptyList()
    private var original: Account? = null
    private var initialized = false

    /** Loads the account once; safe to call again. */
    @MainThread
    fun initialize() {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            val r = refs.getRefData()
            val a = key.accountId?.let { r.accountById[it] }
            original = a
            if (a == null) {
                val g = key.groupId ?: r.groups.firstOrNull { it.status == GroupStatus.ACTIVE }?.id
                _ui.value = AccountFormUiState(loaded = true, groupId = g, name = key.presetName ?: "", currencyId = r.mainCurrency.id, originalCurrencyId = r.mainCurrency.id, pickGroup = key.groupId == null)
            } else {
                rows = transactions.getAllForAccounts(listOf(a.id) + r.linkedDebitCards(a.id).map { it.id })
                val bal = Money.round(balanceIn(r, a.currencyId, a), r.currency(a.currencyId).decimals)
                _ui.value = AccountFormUiState(
                    loaded = true, isNew = false, id = a.id, groupId = a.groupId, name = a.name, amount = bal, balance = bal,
                    currencyId = a.currencyId, originalCurrencyId = a.currencyId, linkedAccountId = a.linkedAccountId, description = a.description,
                    settlementDay = a.settlementDay, paymentDay = a.paymentDay, includeInTotals = a.includeInTotals, hidden = a.status == AccountStatus.HIDDEN,
                )
            }
        }
    }

    /** Balance of the account if its rows were expressed in [currencyId]. */
    private fun balanceIn(r: RefData, currencyId: String, a: Account): Double {
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
            // A debit card takes its payment account's currency when that is not the main currency.
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
            showMessage(R.string.no_title)
            return
        }
        val type = groupType()
        if (type == GroupType.DEBIT_CARD && s.linkedAccountId != null) {
            val pay = r.accountCurrency(s.linkedAccountId)
            if (pay.id != r.mainCurrency.id && pay.id != s.currencyId) {
                showMessage(R.string.debit_currency_mismatch)
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
            val account = Account(
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
            else _ui.update { it.copy(close = AccountFormClose.BACK) }
        }
    }

    fun recordDifference(asIncomeExpense: Boolean) {
        val s = _ui.value
        val diff = s.difference ?: return
        viewModelScope.launch {
            transactions.recordBalanceDifference(s.id, diff, asIncomeExpense, s.description)
            _ui.update { it.copy(difference = null, close = AccountFormClose.BACK) }
        }
    }

    fun dismissCurrency() = _ui.update { it.copy(confirmCurrency = false) }

    fun delete() = viewModelScope.launch {
        repo.setStatus(_ui.value.id, AccountStatus.DELETED)
        _ui.update { it.copy(close = AccountFormClose.TO_MAIN) }
    }

    private fun showMessage(@StringRes text: Int) = _ui.update { it.copy(userMessage = UserMessage(UiText.of(text))) }

    fun onUserMessageShown(id: Long) = _ui.update { if (it.userMessage?.id == id) it.copy(userMessage = null) else it }

    fun onCloseHandled() = _ui.update { it.copy(close = null) }
}
