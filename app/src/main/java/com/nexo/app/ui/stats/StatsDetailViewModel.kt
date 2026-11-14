package com.nexo.app.ui.stats

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.common.money.Money
import com.nexo.app.core.common.period.DateRange
import com.nexo.app.core.model.CashFlow
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.database.entity.TransactionEntity
import com.nexo.app.data.repository.MemosRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.domain.Classifier
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.navigation.StatsDetailKey
import com.nexo.app.ui.navigation.StatsDetailKind
import com.nexo.app.ui.navigation.StatsScope
import com.nexo.app.ui.transactions.DayGroup
import com.nexo.app.ui.transactions.RowFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class Breakdown(val key: String, val name: String, val amount: Double, val percent: Int?)

data class StatsDetailUi(
    val title: String = "",
    val range: DateRange = DateRange(LocalDate.now(), LocalDate.now()),
    val total: Double = 0.0,
    val breakdown: List<Breakdown> = emptyList(),
    val trendLabels: List<String> = emptyList(),
    val trend: List<Double> = emptyList(),
    val days: List<DayGroup> = emptyList(),
    val color: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = StatsDetailViewModel.Factory::class)
class StatsDetailViewModel @AssistedInject constructor(
    @Assisted val key: StatsDetailKey,
    @ApplicationContext private val context: Context,
    refs: ReferenceDataRepository,
    val transactions: TransactionsRepository,
    private val memos: MemosRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: StatsDetailKey): StatsDetailViewModel
    }

    val ref: StateFlow<RefData> = refs.getRefDataStream().stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    val query = MutableStateFlow(StatsQuery(key.scope, LocalDate.parse(key.end), LocalDate.parse(key.start), LocalDate.parse(key.end)))
    val selected = MutableStateFlow("all")
    val selection = MutableStateFlow<Set<String>>(emptySet())

    private val isTransfer = key.kind == StatsDetailKind.TRANSFER_EXPENSE_GROUP || key.kind == StatsDetailKind.TRANSFER_INCOME_GROUP

    val ui: StateFlow<StatsDetailUi> = combine(ref, query, selected) { r, q, s -> Triple(r, q, s) }.flatMapLatest { (r, q, sel) ->
        val e = r.settings.periods
        val range = scopeRange(e, q)
        val buckets: List<Pair<String, DateRange>> = when (q.scope) {
            StatsScope.WEEKLY -> {
                val week = e.weekContaining(range.start)
                (7 downTo 0).map { i -> week.start.minusDays(7L * i).let { s -> "${s.monthValue}/${s.dayOfMonth}" to DateRange(s, s.plusDays(6)) } }
            }
            StatsScope.ANNUALLY -> e.yearPeriods(e.yearOf(q.anchor)).map { Formats.monthShort(it.label.monthValue) to it.range }
            else -> {
                var p = e.monthPeriodContaining(range.end)
                val list = ArrayList<Pair<String, DateRange>>()
                repeat(8) { list.add(0, Formats.monthShort(p.label.monthValue) to p.range); p = e.previous(p) }
                list
            }
        }
        val loadRange = DateRange(minOf(range.start, buckets.first().second.start), maxOf(range.end, buckets.last().second.end))
        combine(transactions.getLedgerStream(loadRange), memos.getMemosStream(range.startText, range.endText)) { l, memos -> build(r, range, buckets, l, sel, memos) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsDetailUi(color = key.colorIndex))

    private fun matches(r: RefData, c: Classifier, t: TransactionEntity): Boolean = when (key.kind) {
        StatsDetailKind.INCOME, StatsDetailKind.EXPENSE -> {
            val kind = if (key.kind == StatsDetailKind.INCOME) TxKind.INCOME else TxKind.EXPENSE
            t.kind == kind && when (key.targetId) {
                KEY_MODIFIED -> t.isBalanceAdjustment
                KEY_NONE -> !t.isBalanceAdjustment && r.mainCategoryId(t.categoryId) == null
                else -> !t.isBalanceAdjustment && r.mainCategoryId(t.categoryId) == key.targetId
            }
        }
        StatsDetailKind.TRANSFER_EXPENSE_GROUP -> t.kind == TxKind.TRANSFER_OUT && c.transferStatsFlow(t.accountId, t.counterpartAccountId) == CashFlow.EXPENSE &&
            r.accountById[t.counterpartAccountId]?.groupId == key.targetId
        StatsDetailKind.TRANSFER_INCOME_GROUP -> t.kind == TxKind.TRANSFER_OUT && c.transferStatsFlow(t.accountId, t.counterpartAccountId) == CashFlow.INCOME &&
            r.accountById[t.accountId]?.groupId == key.targetId
    }

    /** Breakdown key of a row: subcategory / "other", or the account for transfer groups. */
    private fun subKey(r: RefData, t: TransactionEntity): String = when (key.kind) {
        StatsDetailKind.TRANSFER_EXPENSE_GROUP -> t.counterpartAccountId ?: ""
        StatsDetailKind.TRANSFER_INCOME_GROUP -> t.accountId ?: ""
        else -> r.subCategoryId(t.categoryId) ?: "other"
    }

    private fun build(r: RefData, range: DateRange, buckets: List<Pair<String, DateRange>>, l: com.nexo.app.core.model.Ledger, sel: String, memos: List<com.nexo.app.data.database.entity.MemoEntity>): StatsDetailUi {
        val c = Classifier(r)
        val all = l.rows.filter { matches(r, c, it) }
        val inRange = all.filter { LocalDate.parse(it.date) in range }
        val showBreakdown = isTransfer || r.settings.subcategories
        val groups = inRange.groupBy { subKey(r, it) }.mapValues { (_, v) -> v.sumOf { it.amountMain } }
        val base = if (isTransfer) groups.values.sum() else groups.values.filter { it >= 0 }.sum()
        fun pct(a: Double) = if (base == 0.0) null else Math.round(a * 100 / base).toInt()
        val subs = groups.filterKeys { it != "other" }.toList().sortedByDescending { it.second }.map { (k, v) ->
            val name = if (isTransfer) r.accountById[k]?.name ?: "" else r.categoryName(k) ?: ""
            Breakdown(k, name, v, pct(v))
        }
        val other = groups["other"]?.takeIf { it != 0.0 }?.let { Breakdown("other", context.getString(R.string.other), it, pct(it)) }
        val breakdown = if (showBreakdown) listOf(Breakdown("all", context.getString(R.string.all), groups.values.sum(), null)) + subs + listOfNotNull(other) else emptyList()
        val visibleBreakdown = if (!isTransfer && breakdown.size < 2) emptyList() else breakdown
        val filtered = { list: List<TransactionEntity> -> if (sel == "all") list else list.filter { subKey(r, it) == sel } }
        val trend = buckets.map { (_, br) -> Money.round(filtered(all).filter { LocalDate.parse(it.date) in br }.sumOf { it.amountMain }, r.mainCurrency.decimals) }
        val listRows = filtered(inRange)
        val title = when {
            sel != "all" -> visibleBreakdown.firstOrNull { it.key == sel }?.name ?: ""
            isTransfer -> r.groupById[key.targetId]?.name ?: ""
            key.targetId == KEY_NONE -> context.getString(R.string.none)
            key.targetId == KEY_MODIFIED -> context.getString(R.string.modified_balance)
            else -> r.categoryName(key.targetId) ?: ""
        }
        val color = if (sel != "all" && visibleBreakdown.size > 2) (visibleBreakdown.indexOfFirst { it.key == sel } - 1).coerceAtLeast(0) % 11 else key.colorIndex
        return StatsDetailUi(
            title = title, range = range, total = listRows.sumOf { it.amountMain }, breakdown = visibleBreakdown,
            trendLabels = buckets.map { it.first }, trend = trend, color = color,
            days = RowFactory(context, r).dayGroups(listRows, l, memos = memos.filter { LocalDate.parse(it.date) in range }.groupBy { LocalDate.parse(it.date) }),
        )
    }

    fun step(forward: Boolean) = query.update { it.copy(anchor = stepAnchor(ref.value.settings.periods, it.scope, it.anchor, forward)) }
    fun setRange(f: LocalDate, t: LocalDate) = query.update { it.copy(from = f, to = t) }
    fun select(k: String) {
        selected.value = k
    }
}
