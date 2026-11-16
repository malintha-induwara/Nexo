package com.nexo.app.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.common.money.Money
import com.nexo.app.core.common.period.DateRange
import com.nexo.app.core.model.CashFlow
import com.nexo.app.core.model.Ledger
import com.nexo.app.core.model.Memo
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.Transaction
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.repository.MemosRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.domain.Classifier
import com.nexo.app.ui.common.savedStateFlow
import com.nexo.app.ui.designsystem.UiText
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.navigation.StatsDetailKey
import com.nexo.app.ui.navigation.StatsDetailKind
import com.nexo.app.ui.navigation.StatsScope
import com.nexo.app.ui.transactionlist.BulkEditActions
import com.nexo.app.ui.transactionlist.DayGroup
import com.nexo.app.ui.transactionlist.TransactionRowMapper
import com.nexo.app.ui.transactionlist.bulkEditActions
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
import kotlinx.coroutines.flow.update

data class Breakdown(val key: String, val name: UiText, val amount: Double, val percent: Int?)

data class StatsDetailUiState(
    val ref: RefData = RefData(),
    val query: StatsQuery,
    val selected: String = "all",
    val selection: Set<String> = emptySet(),
    val title: UiText = UiText.Empty,
    val range: DateRange,
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
    @Assisted private val key: StatsDetailKey,
    refs: ReferenceDataRepository,
    private val transactions: TransactionsRepository,
    private val memos: MemosRepository,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: StatsDetailKey): StatsDetailViewModel
    }

    /**
     * Kept for event handlers that need the reference data synchronously. It feeds [uiState], so it
     * is current whenever the screen is there to call them.
     */
    private val ref: StateFlow<RefData> = refs.getRefDataStream().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RefData(),
    )
    private val query =
        savedState.savedStateFlow(
            "query",
            StatsQuery(
                key.scope,
                LocalDate.parse(key.end),
                LocalDate.parse(key.start),
                LocalDate.parse(key.end),
            ),
        )
    private val selected = savedState.savedStateFlow("selected", "all")
    private val selection = savedState.savedStateFlow("selection", emptySet<String>())
    val bulkActions: BulkEditActions = bulkEditActions(transactions, viewModelScope, selection)

    private val isTransfer =
        key.kind == StatsDetailKind.TRANSFER_EXPENSE_GROUP ||
            key.kind == StatsDetailKind.TRANSFER_INCOME_GROUP

    private val content = combine(ref, query, selected) { r, q, s ->
        Triple(r, q, s)
    }.flatMapLatest { (r, q, sel) ->
        val e = r.settings.periods
        val range = scopeRange(e, q)
        val buckets: List<Pair<String, DateRange>> = when (q.scope) {
            StatsScope.WEEKLY -> {
                val week = e.weekContaining(range.start)
                (7 downTo 0).map { i ->
                    week.start.minusDays(7L * i).let { s ->
                        "${s.monthValue}/${s.dayOfMonth}" to
                            DateRange(s, s.plusDays(6))
                    }
                }
            }

            StatsScope.ANNUALLY -> e.yearPeriods(e.yearOf(q.anchor)).map {
                Formats.monthShort(it.label.monthValue) to
                    it.range
            }

            else -> {
                var p = e.monthPeriodContaining(range.end)
                val list = ArrayList<Pair<String, DateRange>>()
                repeat(8) {
                    list.add(0, Formats.monthShort(p.label.monthValue) to p.range)
                    p = e.previous(p)
                }
                list
            }
        }
        val loadRange =
            DateRange(
                minOf(range.start, buckets.first().second.start),
                maxOf(range.end, buckets.last().second.end),
            )
        combine(
            transactions.getLedgerStream(loadRange),
            memos.getMemosStream(range.startText, range.endText),
        ) {
                l,
                memos,
            ->
            build(r, range, buckets, l, sel, memos)
        }
    }

    val uiState: StateFlow<StatsDetailUiState> = combine(content, ref, query, selected, selection) {
            c,
            r,
            q,
            sel,
            ids,
        ->
        c.copy(ref = r, query = q, selected = sel, selection = ids)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StatsDetailUiState(
            query = query.value,
            range = DateRange(LocalDate.parse(key.start), LocalDate.parse(key.end)),
            color = key.colorIndex,
        ),
    )

    private fun matches(r: RefData, c: Classifier, t: Transaction): Boolean = when (key.kind) {
        StatsDetailKind.INCOME, StatsDetailKind.EXPENSE -> {
            val kind = if (key.kind == StatsDetailKind.INCOME) TxKind.INCOME else TxKind.EXPENSE
            t.kind == kind && when (key.targetId) {
                KEY_MODIFIED -> t.isBalanceAdjustment
                KEY_NONE -> !t.isBalanceAdjustment && r.mainCategoryId(t.categoryId) == null
                else -> !t.isBalanceAdjustment && r.mainCategoryId(t.categoryId) == key.targetId
            }
        }

        StatsDetailKind.TRANSFER_EXPENSE_GROUP ->
            t.kind == TxKind.TRANSFER_OUT &&
                c.transferStatsFlow(t.accountId, t.counterpartAccountId) == CashFlow.EXPENSE &&
                r.accountById[t.counterpartAccountId]?.groupId == key.targetId

        StatsDetailKind.TRANSFER_INCOME_GROUP ->
            t.kind == TxKind.TRANSFER_OUT &&
                c.transferStatsFlow(t.accountId, t.counterpartAccountId) == CashFlow.INCOME &&
                r.accountById[t.accountId]?.groupId == key.targetId
    }

    /** Breakdown key of a row: subcategory / "other", or the account for transfer groups. */
    private fun subKey(r: RefData, t: Transaction): String = when (key.kind) {
        StatsDetailKind.TRANSFER_EXPENSE_GROUP -> t.counterpartAccountId ?: ""
        StatsDetailKind.TRANSFER_INCOME_GROUP -> t.accountId ?: ""
        else -> r.subCategoryId(t.categoryId) ?: "other"
    }

    private fun build(
        r: RefData,
        range: DateRange,
        buckets: List<Pair<String, DateRange>>,
        l: Ledger,
        sel: String,
        memos: List<Memo>,
    ): StatsDetailUiState {
        val c = Classifier(r)
        val all = l.rows.filter { matches(r, c, it) }
        val inRange = all.filter { LocalDate.parse(it.date) in range }
        val showBreakdown = isTransfer || r.settings.subcategories
        val groups = inRange.groupBy {
            subKey(r, it)
        }.mapValues { (_, v) -> v.sumOf { it.amountMain } }
        val base = if (isTransfer) groups.values.sum() else groups.values.filter { it >= 0 }.sum()
        fun pct(a: Double) = if (base == 0.0) null else Math.round(a * 100 / base).toInt()
        val subs = groups.filterKeys {
            it != "other"
        }.toList().sortedByDescending { it.second }.map { (k, v) ->
            val name = if (isTransfer) r.accountById[k]?.name ?: "" else r.categoryName(k) ?: ""
            Breakdown(k, UiText.Raw(name), v, pct(v))
        }
        val other = groups["other"]?.takeIf {
            it != 0.0
        }?.let { Breakdown("other", UiText.of(R.string.other), it, pct(it)) }
        val breakdown = if (showBreakdown) {
            listOf(Breakdown("all", UiText.of(R.string.all), groups.values.sum(), null)) +
                subs +
                listOfNotNull(other)
        } else {
            emptyList()
        }
        val visibleBreakdown = if (!isTransfer && breakdown.size < 2) emptyList() else breakdown
        val filtered = { list: List<Transaction> ->
            if (sel == "all") {
                list
            } else {
                list.filter { subKey(r, it) == sel }
            }
        }
        val trend = buckets.map { (_, br) ->
            Money.round(
                filtered(all).filter {
                    LocalDate.parse(it.date) in
                        br
                }.sumOf { it.amountMain },
                r.mainCurrency.decimals,
            )
        }
        val listRows = filtered(inRange)
        val title = when {
            sel != "all" -> visibleBreakdown.firstOrNull { it.key == sel }?.name ?: UiText.Empty
            isTransfer -> UiText.Raw(r.groupById[key.targetId]?.name ?: "")
            key.targetId == KEY_NONE -> UiText.of(R.string.none)
            key.targetId == KEY_MODIFIED -> UiText.of(R.string.modified_balance)
            else -> UiText.Raw(r.categoryName(key.targetId) ?: "")
        }
        val color = if (sel != "all" && visibleBreakdown.size > 2) {
            (visibleBreakdown.indexOfFirst { it.key == sel } - 1).coerceAtLeast(0) %
                11
        } else {
            key.colorIndex
        }
        return StatsDetailUiState(
            query = query.value,
            title = title,
            range = range,
            total = listRows.sumOf {
                it.amountMain
            },
            breakdown = visibleBreakdown,
            trendLabels = buckets.map { it.first },
            trend = trend,
            color = color,
            days = TransactionRowMapper(r, clock.zone).dayGroups(
                listRows,
                l,
                memos = memos.filter {
                    LocalDate.parse(it.date) in
                        range
                }.groupBy { LocalDate.parse(it.date) },
            ),
        )
    }

    fun step(forward: Boolean) = query.update {
        it.copy(anchor = stepAnchor(ref.value.settings.periods, it.scope, it.anchor, forward))
    }
    fun setRange(f: LocalDate, t: LocalDate) = query.update { it.copy(from = f, to = t) }
    fun select(k: String) {
        selected.value = k
    }

    fun setMonth(month: YearMonth) = query.update {
        it.copy(anchor = ref.value.settings.periods.monthPeriodFor(month).end)
    }

    fun toggleSelect(id: String) = selection.update { if (id in it) it - id else it + id }

    fun setSelection(ids: Set<String>) {
        selection.value = ids
    }
}
