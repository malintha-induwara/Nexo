package com.nexo.app.ui.budgets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.common.money.Money
import com.nexo.app.core.common.period.DateRange
import com.nexo.app.core.common.period.MonthPeriod
import com.nexo.app.core.model.Budget
import com.nexo.app.core.model.BudgetAmount
import com.nexo.app.core.model.BudgetMath
import com.nexo.app.core.model.Ledger
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.Transaction
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.repository.BudgetsRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.ui.common.savedStateFlow
import com.nexo.app.ui.navigation.BudgetDetailKey
import com.nexo.app.ui.transactionlist.DayGroup
import com.nexo.app.ui.transactionlist.TransactionRowMapper
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BudgetMonth(val period: MonthPeriod, val budget: Double, val used: Double)

data class BudgetDetailUiState(
    val loaded: Boolean = false,
    val ref: RefData = RefData(),
    val month: YearMonth,
    val budgetId: String = "",
    val name: String = "",
    val categoryId: String? = null,
    val isVirtual: Boolean = false,
    val siblings: List<Pair<String, String>> = emptyList(),
    val months: List<BudgetMonth> = emptyList(),
    val selected: Int = 0,
    val days: List<DayGroup> = emptyList(),
)

// [BudgetDetailKey.budgetId] may be "virtual_<categoryId>" for a virtual parent.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = BudgetDetailViewModel.Factory::class)
class BudgetDetailViewModel @AssistedInject constructor(
    @Assisted key: BudgetDetailKey,
    refs: ReferenceDataRepository,
    repo: BudgetsRepository,
    private val transactions: TransactionsRepository,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: BudgetDetailKey): BudgetDetailViewModel
    }

    private val ref: StateFlow<RefData> = refs.getRefDataStream().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RefData(),
    )
    private val budgetId = savedState.savedStateFlow("budget", key.budgetId)
    private val month = savedState.savedStateFlow("month", YearMonth.parse(key.month))

    val uiState: StateFlow<BudgetDetailUiState> = combine(
        ref,
        budgetId,
        month,
        repo.getBudgetsStream(),
        repo.getBudgetAmountsStream(),
    ) {
            r,
            id,
            m,
            b,
            a,
        ->
        Inputs(r, id, m, b, a)
    }
        .flatMapLatest { inp ->
            val e = inp.r.settings.periods
            val year = e.yearPeriods(inp.m.year)
            val range = DateRange(year.first().start, year.last().end)
            combine(transactions.getLedgerStream(range), transactions.getChangeStampStream()) {
                    l,
                    _,
                ->
                l
            }.map { l -> build(inp, year, l) }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BudgetDetailUiState(month = month.value),
        )

    private data class Inputs(
        val r: RefData,
        val id: String,
        val m: YearMonth,
        val budgets: List<Budget>,
        val amounts: List<BudgetAmount>,
    )

    private fun build(inp: Inputs, year: List<MonthPeriod>, l: Ledger): BudgetDetailUiState {
        val r = inp.r
        val budget = inp.budgets.firstOrNull { it.id == inp.id }
        val categoryId =
            budget?.categoryId
                ?: inp.id.removePrefix("virtual_").takeIf { inp.id.startsWith("virtual_") }
        val cat = categoryId?.let { r.categoryById[it] }
        val isSub = cat?.parentId != null
        val byBudget = inp.amounts.groupBy { it.budgetId }
        val subBudgets = if (cat != null && !isSub) {
            inp.budgets.filter { b ->
                !b.isDeleted &&
                    b.categoryId?.let { r.categoryById[it]?.parentId } == cat.id
            }
        } else {
            emptyList()
        }
        fun budgetFor(key: Int): Double {
            val own = budget?.let { BudgetMath.effective(byBudget[it.id].orEmpty(), key) } ?: 0.0
            return if (own == 0.0 && !isSub) {
                subBudgets.sumOf { BudgetMath.effective(byBudget[it.id].orEmpty(), key) }
            } else {
                own
            }
        }
        val expense = l.rows.filter { it.kind == TxKind.EXPENSE && !it.isBalanceAdjustment }
        fun matches(t: Transaction) =
            if (isSub) t.categoryId == categoryId else r.mainCategoryId(t.categoryId) == categoryId
        val months = year.map { p ->
            val used = expense.filter {
                LocalDate.parse(it.date) in p.range && matches(it)
            }.sumOf { it.amountMain }
            BudgetMonth(p, budgetFor(p.key), Money.round(used, r.mainCurrency.decimals))
        }
        val selected = months.indexOfFirst { it.period.label == inp.m }.coerceAtLeast(0)
        val sel = months.getOrNull(selected)
        val factory = TransactionRowMapper(r, clock.zone)
        val dayRows = expense.filter {
            sel != null && LocalDate.parse(it.date) in sel.period.range &&
                matches(it)
        }
        val parentId = if (isSub) cat?.parentId else categoryId
        val siblings = if (r.settings.subcategories && parentId != null) {
            val mainB = inp.budgets.firstOrNull { !it.isDeleted && it.categoryId == parentId }
            val subs = inp.budgets.filter { b ->
                !b.isDeleted &&
                    b.categoryId?.let { r.categoryById[it]?.parentId } == parentId
            }
            if (subs.isEmpty()) {
                emptyList()
            } else {
                listOf((mainB?.id ?: "virtual_$parentId") to (r.categoryName(parentId) ?: "")) +
                    subs.map { it.id to (r.categoryName(it.categoryId) ?: "") }
            }
        } else {
            emptyList()
        }
        return BudgetDetailUiState(
            loaded = true,
            ref = r,
            month = inp.m,
            budgetId = inp.id,
            name = cat?.name ?: "",
            categoryId = categoryId,
            isVirtual =
                budget == null,
            siblings = siblings, months = months, selected = selected,
            days = factory.dayGroups(dayRows, l),
        )
    }

    fun selectBudget(id: String) {
        budgetId.value = id
    }

    fun setMonth(m: YearMonth) {
        month.value = m
    }

    fun selectIndex(i: Int) {
        uiState.value.months.getOrNull(i)?.let { month.value = it.period.label }
    }

    fun step(forward: Boolean) {
        month.value = month.value.plusMonths(if (forward) 1 else -1)
    }
}
