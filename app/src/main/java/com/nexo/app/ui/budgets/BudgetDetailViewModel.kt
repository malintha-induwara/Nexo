package com.nexo.app.ui.budgets

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.common.period.DateRange
import com.nexo.app.core.common.period.MonthPeriod
import com.nexo.app.core.model.BudgetMath
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.database.entity.BudgetAmountEntity
import com.nexo.app.data.database.entity.BudgetEntity
import com.nexo.app.data.repository.BudgetsRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.ui.navigation.BudgetDetailKey
import com.nexo.app.ui.transactions.DayGroup
import com.nexo.app.ui.transactions.RowFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BudgetMonth(val period: MonthPeriod, val budget: Double, val used: Double)

data class BudgetDetailUi(
    val loaded: Boolean = false,
    val budgetId: String = "",
    val name: String = "",
    val categoryId: String? = null,
    val isVirtual: Boolean = false,
    val siblings: List<Pair<String, String>> = emptyList(),
    val months: List<BudgetMonth> = emptyList(),
    val selected: Int = 0,
    val days: List<DayGroup> = emptyList(),
)

/** [BudgetDetailKey.budgetId] may be "virtual_<categoryId>" for a virtual parent. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = BudgetDetailViewModel.Factory::class)
class BudgetDetailViewModel @AssistedInject constructor(
    @Assisted key: BudgetDetailKey,
    @ApplicationContext private val context: Context,
    refs: ReferenceDataRepository,
    repo: BudgetsRepository,
    val transactions: TransactionsRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: BudgetDetailKey): BudgetDetailViewModel
    }

    val ref: StateFlow<RefData> = refs.getRefDataStream().stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    val budgetId = MutableStateFlow(key.budgetId)
    val month = MutableStateFlow(YearMonth.parse(key.month))

    val ui: StateFlow<BudgetDetailUi> = combine(ref, budgetId, month, repo.getBudgetsStream(), repo.getBudgetAmountsStream()) { r, id, m, b, a -> Inputs(r, id, m, b, a) }
        .flatMapLatest { inp ->
            val e = inp.r.settings.periods
            val year = e.yearPeriods(inp.m.year)
            val range = DateRange(year.first().start, year.last().end)
            combine(transactions.getLedgerStream(range), transactions.getChangeStampStream()) { l, _ -> l }.map { l -> build(inp, year, l) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetDetailUi())

    private data class Inputs(val r: RefData, val id: String, val m: YearMonth, val budgets: List<BudgetEntity>, val amounts: List<BudgetAmountEntity>)

    private fun build(inp: Inputs, year: List<MonthPeriod>, l: com.nexo.app.core.model.Ledger): BudgetDetailUi {
        val r = inp.r
        val budget = inp.budgets.firstOrNull { it.id == inp.id }
        val categoryId = budget?.categoryId ?: inp.id.removePrefix("virtual_").takeIf { inp.id.startsWith("virtual_") }
        val cat = categoryId?.let { r.categoryById[it] }
        val isSub = cat?.parentId != null
        val byBudget = inp.amounts.groupBy { it.budgetId }
        val subBudgets = if (cat != null && !isSub) inp.budgets.filter { b -> !b.isDeleted && b.categoryId?.let { r.categoryById[it]?.parentId } == cat.id } else emptyList()
        fun budgetFor(key: Int): Double {
            val own = budget?.let { BudgetMath.effective(byBudget[it.id].orEmpty(), key) } ?: 0.0
            return if (own == 0.0 && !isSub) subBudgets.sumOf { BudgetMath.effective(byBudget[it.id].orEmpty(), key) } else own
        }
        val expense = l.rows.filter { it.kind == TxKind.EXPENSE && !it.isBalanceAdjustment }
        fun matches(t: com.nexo.app.data.database.entity.TransactionEntity) =
            if (isSub) t.categoryId == categoryId else r.mainCategoryId(t.categoryId) == categoryId
        val months = year.map { p ->
            val used = expense.filter { LocalDate.parse(it.date) in p.range && matches(it) }.sumOf { it.amountMain }
            BudgetMonth(p, budgetFor(p.key), com.nexo.app.core.common.money.Money.round(used, r.mainCurrency.decimals))
        }
        val selected = months.indexOfFirst { it.period.label == inp.m }.coerceAtLeast(0)
        val sel = months.getOrNull(selected)
        val factory = RowFactory(context, r)
        val dayRows = expense.filter { sel != null && LocalDate.parse(it.date) in sel.period.range && matches(it) }
        val parentId = if (isSub) cat?.parentId else categoryId
        val siblings = if (r.settings.subcategories && parentId != null) {
            val mainB = inp.budgets.firstOrNull { !it.isDeleted && it.categoryId == parentId }
            val subs = inp.budgets.filter { b -> !b.isDeleted && b.categoryId?.let { r.categoryById[it]?.parentId } == parentId }
            if (subs.isEmpty()) emptyList()
            else listOf((mainB?.id ?: "virtual_$parentId") to (r.categoryName(parentId) ?: "")) + subs.map { it.id to (r.categoryName(it.categoryId) ?: "") }
        } else emptyList()
        return BudgetDetailUi(
            loaded = true, budgetId = inp.id, name = cat?.name ?: "", categoryId = categoryId, isVirtual = budget == null,
            siblings = siblings, months = months, selected = selected,
            days = factory.dayGroups(dayRows, l),
        )
    }

    fun selectIndex(i: Int) {
        ui.value.months.getOrNull(i)?.let { month.value = it.period.label }
    }

    fun step(forward: Boolean) {
        month.value = month.value.plusMonths(if (forward) 1 else -1)
    }
}
