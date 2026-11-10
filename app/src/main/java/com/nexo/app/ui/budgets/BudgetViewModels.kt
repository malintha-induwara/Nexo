package com.nexo.app.ui.budgets

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.database.entity.BudgetAmountEntity
import com.nexo.app.core.database.entity.BudgetEntity
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.period.DateRange
import com.nexo.app.core.period.MonthPeriod
import com.nexo.app.data.BudgetRepository
import com.nexo.app.data.LedgerRepository
import com.nexo.app.data.RefRepository
import com.nexo.app.data.TransactionWriter
import com.nexo.app.domain.BudgetBlockData
import com.nexo.app.domain.BudgetCalc
import com.nexo.app.domain.BudgetMath
import com.nexo.app.domain.RefData
import com.nexo.app.ui.navigation.BudgetDetailKey
import com.nexo.app.ui.transactions.DayGroup
import com.nexo.app.ui.transactions.RowFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** Budget block of the Total page for one month period (ST-041). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetBlockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    refs: RefRepository,
    repo: BudgetRepository,
    private val ledgers: LedgerRepository,
) : ViewModel() {
    val ref: StateFlow<RefData> = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    private val period = MutableStateFlow<MonthPeriod?>(null)

    fun setPeriod(p: MonthPeriod) {
        period.value = p
    }

    val data: StateFlow<BudgetBlockData?> = period.filterNotNull().flatMapLatest { p ->
        combine(ref, repo.budgets, repo.amounts, ledgers.observe(p.range)) { r, b, a, l ->
            BudgetCalc.compute(r, b, a, l.rows, p.key, context.getString(R.string.total_budget))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

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

/** Budget Detail (ST-046). [BudgetDetailKey.budgetId] may be "virtual_<categoryId>" for a virtual parent. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = BudgetDetailViewModel.Factory::class)
class BudgetDetailViewModel @AssistedInject constructor(
    @Assisted key: BudgetDetailKey,
    @ApplicationContext private val context: Context,
    refs: RefRepository,
    repo: BudgetRepository,
    private val db: NexoDatabase,
    private val ledgers: LedgerRepository,
    val writer: TransactionWriter,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: BudgetDetailKey): BudgetDetailViewModel
    }

    val ref: StateFlow<RefData> = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    val budgetId = MutableStateFlow(key.budgetId)
    val month = MutableStateFlow(YearMonth.parse(key.month))

    val ui: StateFlow<BudgetDetailUi> = combine(ref, budgetId, month, repo.budgets, repo.amounts) { r, id, m, b, a -> Inputs(r, id, m, b, a) }
        .flatMapLatest { inp ->
            val e = inp.r.settings.periods
            val year = e.yearPeriods(inp.m.year)
            val range = DateRange(year.first().start, year.last().end)
            combine(ledgers.observe(range), db.transactionDao().observeStamp()) { l, _ -> l }.map { l -> build(inp, year, l) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetDetailUi())

    private data class Inputs(val r: RefData, val id: String, val m: YearMonth, val budgets: List<BudgetEntity>, val amounts: List<BudgetAmountEntity>)

    private fun build(inp: Inputs, year: List<MonthPeriod>, l: com.nexo.app.data.Ledger): BudgetDetailUi {
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
        fun matches(t: com.nexo.app.core.database.entity.TransactionEntity) =
            if (isSub) t.categoryId == categoryId else r.mainCategoryId(t.categoryId) == categoryId
        val months = year.map { p ->
            val used = expense.filter { LocalDate.parse(it.date) in p.range && matches(it) }.sumOf { it.amountMain }
            BudgetMonth(p, budgetFor(p.key), com.nexo.app.core.money.Money.round(used, r.mainCurrency.decimals))
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

/** Shared by Budget Settings, Add Budget and the monthly editor. */
@HiltViewModel
class BudgetSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val refs: RefRepository,
    val repo: BudgetRepository,
) : ViewModel() {
    val ref: StateFlow<RefData> = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    val budgets = repo.budgets.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val amounts = repo.amounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentKey: Int get() = ref.value.settings.periods.monthPeriodContaining(LocalDate.now()).key

    val list: StateFlow<BudgetBlockData?> = combine(ref, repo.budgets, repo.amounts) { r, b, a ->
        BudgetCalc.compute(r, b, a, emptyList(), r.settings.periods.monthPeriodContaining(LocalDate.now()).key, context.getString(R.string.total_budget), forSettings = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun add(categoryId: String?, amount: Double, onDone: () -> Unit) = viewModelScope.launch {
        repo.add(categoryId, amount, currentKey)
        onDone()
    }

    fun setMonth(budgetId: String, key: Int, amount: Double) = viewModelScope.launch { repo.setMonth(budgetId, key, amount) }
    fun setDefault(budgetId: String, amount: Double) = viewModelScope.launch { repo.setDefault(budgetId, currentKey, amount) }
    fun delete(budgetId: String, onDone: () -> Unit = {}) = viewModelScope.launch { repo.delete(budgetId); onDone() }
}
