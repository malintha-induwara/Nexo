package com.nexo.app.ui.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.database.entity.TransactionEntity
import com.nexo.app.core.model.CashFlow
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.period.DateRange
import com.nexo.app.core.period.PeriodEngine
import com.nexo.app.data.LedgerRepository
import com.nexo.app.data.RefRepository
import com.nexo.app.domain.Classifier
import com.nexo.app.domain.RefData
import com.nexo.app.ui.navigation.StatsDetailKind
import com.nexo.app.ui.navigation.StatsScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class StatsRow(val key: String?, val name: String, val amount: Double, val percent: Double, val colorIndex: Int, val kind: StatsDetailKind)

data class StatsData(
    val range: DateRange = DateRange(LocalDate.now(), LocalDate.now()),
    val income: List<StatsRow> = emptyList(),
    val expense: List<StatsRow> = emptyList(),
) {
    val incomeTotal: Double get() = income.sumOf { it.amount }
    val expenseTotal: Double get() = expense.sumOf { it.amount }
}

data class StatsQuery(
    val scope: StatsScope = StatsScope.MONTHLY,
    val anchor: LocalDate = LocalDate.now(),
    val from: LocalDate = LocalDate.now(),
    val to: LocalDate = LocalDate.now(),
)

const val KEY_NONE = "__none__"
const val KEY_MODIFIED = "__modified__"

fun scopeRange(e: PeriodEngine, q: StatsQuery): DateRange = when (q.scope) {
    StatsScope.WEEKLY -> e.weekContaining(q.anchor)
    StatsScope.MONTHLY -> e.monthPeriodContaining(q.anchor).range
    StatsScope.ANNUALLY -> e.yearRange(e.yearOf(q.anchor))
    StatsScope.PERIOD -> DateRange(q.from, q.to)
}

fun stepAnchor(e: PeriodEngine, scope: StatsScope, d: LocalDate, forward: Boolean): LocalDate = when (scope) {
    StatsScope.WEEKLY -> d.plusDays(if (forward) 7 else -7)
    StatsScope.MONTHLY -> e.monthPeriodContaining(d).let { if (forward) e.next(it).end else e.previous(it).end }
    StatsScope.ANNUALLY -> e.yearRange(e.yearOf(d) + if (forward) 1 else -1).start.plusDays(15)
    StatsScope.PERIOD -> d
}

fun statsRows(context: Context, ref: RefData, rows: List<TransactionEntity>, income: Boolean): List<StatsRow> {
    val kind = if (income) TxKind.INCOME else TxKind.EXPENSE
    val cats = HashMap<String, BigDecimal>()
    rows.filter { it.kind == kind }.forEach { t ->
        val key = if (t.isBalanceAdjustment) KEY_MODIFIED else ref.mainCategoryId(t.categoryId) ?: KEY_NONE
        cats[key] = (cats[key] ?: BigDecimal.ZERO) + BigDecimal.valueOf(t.amountMain)
    }
    val c = Classifier(ref)
    val te = HashMap<String, BigDecimal>()
    rows.filter { it.kind == TxKind.TRANSFER_OUT }.forEach { t ->
        val flow = c.transferStatsFlow(t.accountId, t.counterpartAccountId)
        val group = when {
            income && flow == CashFlow.INCOME -> r(ref, t.accountId)
            !income && flow == CashFlow.EXPENSE -> r(ref, t.counterpartAccountId)
            else -> null
        } ?: return@forEach
        te[group] = (te[group] ?: BigDecimal.ZERO) + BigDecimal.valueOf(t.amountMain)
    }
    val positiveBase = cats.values.filter { it.signum() > 0 }.fold(BigDecimal.ZERO, BigDecimal::add) + te.values.fold(BigDecimal.ZERO, BigDecimal::add)
    fun pct(a: BigDecimal) = if (a.signum() > 0 && positiveBase.signum() != 0) a.multiply(BigDecimal(100)).divide(positiveBase, 6, RoundingMode.HALF_UP).toDouble() else 0.0
    val list = cats.map { (k, v) ->
        val name = when (k) {
            KEY_NONE -> context.getString(R.string.none)
            KEY_MODIFIED -> context.getString(R.string.modified_balance)
            else -> ref.categoryName(k) ?: context.getString(R.string.none)
        }
        StatsRow(k, name, v.toDouble(), pct(v), 0, if (income) StatsDetailKind.INCOME else StatsDetailKind.EXPENSE)
    } + te.filter { it.value.signum() != 0 }.map { (g, v) ->
        StatsRow(g, ref.groupById[g]?.name ?: "", v.toDouble(), pct(v), 0, if (income) StatsDetailKind.TRANSFER_INCOME_GROUP else StatsDetailKind.TRANSFER_EXPENSE_GROUP)
    }
    return list.sortedByDescending { it.amount }.mapIndexed { i, row -> row.copy(colorIndex = i % 11) }
}

private fun r(ref: RefData, accountId: String?): String? = ref.accountById[accountId]?.groupId

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    refs: RefRepository,
    ledgers: LedgerRepository,
) : ViewModel() {
    val ref: StateFlow<RefData> = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    private val _query = MutableStateFlow(StatsQuery())
    val query = _query.asStateFlow()
    private val _income = MutableStateFlow(false)
    val income = _income.asStateFlow()

    val data: StateFlow<StatsData> = combine(ref, _query) { r, q -> r to q }.flatMapLatest { (r, q) ->
        val range = scopeRange(r.settings.periods, q)
        ledgers.observe(range).map { l -> StatsData(range, statsRows(context, r, l.rows, true), statsRows(context, r, l.rows, false)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsData())

    fun setIncome(v: Boolean) {
        _income.value = v
    }

    fun setScope(s: StatsScope) = _query.update { q ->
        val e = ref.value.settings.periods
        val cur = scopeRange(e, q)
        when (s) {
            StatsScope.PERIOD -> q.copy(scope = s, from = cur.start, to = cur.end)
            StatsScope.WEEKLY -> q.copy(scope = s, anchor = if (LocalDate.now() in cur) LocalDate.now() else q.anchor)
            else -> q.copy(scope = s)
        }
    }

    fun step(forward: Boolean) = _query.update { it.copy(anchor = stepAnchor(ref.value.settings.periods, it.scope, it.anchor, forward)) }
    fun setAnchor(d: LocalDate) = _query.update { it.copy(anchor = d) }
    fun setMonth(m: YearMonth) = _query.update { it.copy(anchor = ref.value.settings.periods.monthPeriodFor(m).end) }
    fun setRange(f: LocalDate, t: LocalDate) = _query.update { it.copy(from = f, to = t) }
    fun goToday() = _query.update { it.copy(anchor = LocalDate.now()) }
}
