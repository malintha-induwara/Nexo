package com.nexo.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.common.period.DateRange
import com.nexo.app.core.common.period.toIsoText
import com.nexo.app.core.model.CashFlow
import com.nexo.app.core.model.Memo
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.repository.MemosRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.domain.Classifier
import com.nexo.app.ui.entry.NoteSuggestion
import com.nexo.app.ui.transactions.BulkEditActions
import com.nexo.app.ui.transactions.FilterCriteria
import com.nexo.app.ui.transactions.TransactionRowMapper
import com.nexo.app.ui.transactions.TxRowUi
import com.nexo.app.ui.transactions.bulkEditActions
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class SearchPeriod { TOTAL, WEEKLY, MONTHLY, ANNUALLY, PERIOD }

data class SearchCriteria(
    val query: String = "",
    val period: SearchPeriod = SearchPeriod.TOTAL,
    val anchor: LocalDate = LocalDate.now(),
    val from: LocalDate = LocalDate.now().withDayOfMonth(1),
    val to: LocalDate = LocalDate.now(),
    val categories: FilterCriteria = FilterCriteria(),
    val accounts: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val min: Double? = null,
    val max: Double? = null,
)

data class SearchResult(
    val loaded: Boolean = false,
    val rows: List<Pair<LocalDate, TxRowUi>> = emptyList(),
    val memos: List<Memo> = emptyList(),
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val transfer: Double = 0.0,
    val range: DateRange = DateRange.ALL,
)

data class SearchUiState(
    val ref: RefData = RefData(),
    val criteria: SearchCriteria = SearchCriteria(),
    val result: SearchResult = SearchResult(),
    val selection: Set<String> = emptySet(),
    val suggestions: List<NoteSuggestion> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val memos: MemosRepository,
    private val refs: ReferenceDataRepository,
    private val transactions: TransactionsRepository,
) : ViewModel() {
    /** Kept for event handlers that need the reference data synchronously. */
    private val ref: StateFlow<RefData> = refs.getRefDataStream().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        RefData(),
    )
    private val criteria = MutableStateFlow(SearchCriteria())
    private val executed = MutableStateFlow(SearchCriteria())
    private val selection = MutableStateFlow<Set<String>>(emptySet())

    val bulkActions: BulkEditActions = bulkEditActions(transactions, viewModelScope, selection)

    private val result: Flow<SearchResult> = combine(
        ref,
        executed,
        transactions.getChangeStampStream(),
    ) {
            r,
            c,
            _,
        ->
        r to
            c
    }
        .mapLatest { (r, c) -> run(r, c) }

    /** Note autocomplete, read once the screen starts collecting. */
    private val suggestions: Flow<List<NoteSuggestion>> = flow { emit(loadSuggestions()) }

    val uiState: StateFlow<SearchUiState> = combine(ref, criteria, result, selection, suggestions) {
            r,
            c,
            res,
            selection,
            notes,
        ->
        SearchUiState(r, c, res, selection, notes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    private suspend fun loadSuggestions(): List<NoteSuggestion> {
        val r = refs.getRefData()
        if (!r.settings.autocomplete) return emptyList()
        val cutoff =
            maxOf(
                System.currentTimeMillis() - 456L * 24 * 3600 * 1000,
                r.settings.autocompleteResetAt,
            )
        val cutDate = Instant.ofEpochMilli(
            cutoff,
        ).atZone(ZoneId.systemDefault()).toLocalDate().toIsoText()
        return transactions.getNoteUsage(cutDate, cutoff).map {
            NoteSuggestion(it.note, it.count, it.lastDate)
        }
    }

    fun range(r: RefData, c: SearchCriteria): DateRange {
        val e = r.settings.periods
        return when (c.period) {
            SearchPeriod.TOTAL -> DateRange(LocalDate.of(1972, 1, 1), LocalDate.of(2099, 1, 1))
            SearchPeriod.WEEKLY -> e.weekContaining(c.anchor)
            SearchPeriod.MONTHLY -> e.monthPeriodContaining(c.anchor).range
            SearchPeriod.ANNUALLY -> e.yearRange(e.yearOf(c.anchor))
            SearchPeriod.PERIOD -> DateRange(c.from, c.to)
        }
    }

    private suspend fun run(r: RefData, c: SearchCriteria): SearchResult {
        val range = range(r, c)
        val text = c.query.trim()
        val pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        val rows = transactions.search(
            range.startText,
            range.endText,
            pattern,
            includeSource = true,
        )
        val classifier = Classifier(r)
        val min = c.min
        val max = c.max
        val lo = if (min != null && max != null) minOf(min, max) else min
        val hi = if (min != null && max != null) maxOf(min, max) else max
        val tagLinks = if (c.tags.isEmpty()) {
            emptyMap()
        } else {
            transactions.getLedgerFor(rows).tagsByTx
        }
        val filtered = rows.filter { t ->
            !t.kind.isBalance &&
                (!c.categories.hasCategoryPart || c.categories.matches(t, r, classifier)) &&
                (
                    c.accounts.isEmpty() || t.accountId in c.accounts ||
                        (t.kind == TxKind.TRANSFER_OUT && t.counterpartAccountId in c.accounts)
                    ) &&
                (c.tags.isEmpty() || tagLinks[t.id].orEmpty().any { it in c.tags }) &&
                (lo == null || t.amountMain >= lo) && (hi == null || t.amountMain <= hi)
        }
        val ledger = transactions.getLedgerFor(filtered)
        val factory = TransactionRowMapper(r)
        val sorted = filtered.groupBy {
            it.date
        }.toSortedMap(compareByDescending { it }).flatMap { (_, day) ->
            TransactionRowMapper.sortDay(day, r.settings.timeDisplay)
        }
        var inc = BigDecimal.ZERO
        var exp = BigDecimal.ZERO
        var tr = BigDecimal.ZERO
        filtered.forEach { t ->
            when {
                t.kind == TxKind.INCOME -> inc += BigDecimal.valueOf(t.amountMain)
                t.kind == TxKind.EXPENSE -> exp += BigDecimal.valueOf(t.amountMain)
                t.kind == TxKind.TRANSFER_OUT -> tr += BigDecimal.valueOf(t.amountMain)
            }
            if (classifier.classify(t.kind, t.accountId, t.counterpartAccountId) ==
                CashFlow.NEUTRAL
            ) {
                Unit
            }
        }
        val memos = if (text.isEmpty()) {
            emptyList()
        } else {
            memos.search(range.startText, range.endText, pattern)
                .sortedWith(
                    compareByDescending<Memo> {
                        it.pinned
                    }.thenByDescending { it.dateTime }.thenByDescending { it.orderSeq },
                )
        }
        return SearchResult(
            loaded = true,
            rows = sorted.map { LocalDate.parse(it.date) to factory.row(it, ledger) },
            memos = memos,
            income = inc.toDouble(),
            expense = exp.toDouble(),
            transfer = tr.toDouble(),
            range = range,
        )
    }

    fun setQuery(q: String) = criteria.update { it.copy(query = q) }
    fun execute() {
        executed.value = criteria.value
    }

    /** Criteria changes search immediately. */
    fun update(transform: (SearchCriteria) -> SearchCriteria) {
        criteria.update(transform)
        execute()
    }

    fun step(forward: Boolean) = update { c ->
        val e = ref.value.settings.periods
        val d = c.anchor
        c.copy(
            anchor = when (c.period) {
                SearchPeriod.WEEKLY -> d.plusDays(if (forward) 7 else -7)

                SearchPeriod.MONTHLY -> e.monthPeriodContaining(d).let {
                    if (forward) e.next(it).end else e.previous(it).end
                }

                SearchPeriod.ANNUALLY -> e.yearRange(
                    e.yearOf(d) + if (forward) 1 else -1,
                ).start.plusDays(15)

                else -> d
            },
        )
    }

    fun toggleSelect(id: String) {
        selection.value = if (id in selection.value) selection.value - id else selection.value + id
    }

    fun setSelection(ids: Set<String>) {
        selection.value = ids
    }
}
