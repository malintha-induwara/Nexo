package com.nexo.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.common.money.Money
import com.nexo.app.core.common.period.DateRange
import com.nexo.app.core.common.period.MonthPeriod
import com.nexo.app.core.model.CashFlow
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.model.Ledger
import com.nexo.app.core.model.Memo
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.RepeatRule
import com.nexo.app.core.model.RepeatSchedule
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.StartScreen
import com.nexo.app.core.model.Transaction
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.repository.BudgetsRepository
import com.nexo.app.data.repository.MemosRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.RepeatRulesRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.domain.BudgetBlockData
import com.nexo.app.domain.BudgetCalc
import com.nexo.app.domain.Classifier
import com.nexo.app.domain.InOut
import com.nexo.app.domain.Totals
import com.nexo.app.ui.HomeState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class Params(
    val ref: RefData,
    val date: LocalDate,
    val tab: SubTab,
    val filter: FilterCriteria,
)

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val refs: ReferenceDataRepository,
    private val memos: MemosRepository,
    private val repeats: RepeatRulesRepository,
    private val transactions: TransactionsRepository,
    private val budgets: BudgetsRepository,
    private val home: HomeState,
) : ViewModel() {
    /** Kept for event handlers that need the reference data synchronously. */
    private val ref: StateFlow<RefData> = refs.getRefDataStream().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        RefData(),
    )

    /** The sub-tab the user picked; null means the book's start screen. */
    private val _tab = MutableStateFlow<SubTab?>(null)

    /** Waits for the real reference data, so the start screen never flashes the wrong sub-tab. */
    private val tab: Flow<SubTab> = combine(_tab, refs.getRefDataStream()) { t, r ->
        t
            ?: startTab(r)
    }.distinctUntilChanged()

    private val filter = MutableStateFlow(FilterCriteria())
    private val filterOpen = MutableStateFlow(false)
    private val selection = MutableStateFlow<Set<String>>(emptySet())

    val bulkActions: BulkEditActions = bulkEditActions(transactions, viewModelScope, selection)

    private val data: StateFlow<HomeData> = combine(ref, home.selectedDate, tab, filter) {
            r,
            d,
            t,
            f,
        ->
        Params(r, d, t, f)
    }
        .distinctUntilChanged()
        .flatMapLatest { p -> load(p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeData())

    private val budgetBlock: Flow<BudgetBlockData?> = data
        .map { d -> d.period.takeIf { d.tab == SubTab.TOTAL } }
        .distinctUntilChanged()
        .flatMapLatest { period ->
            if (period == null) {
                flowOf(null)
            } else {
                combine(
                    ref,
                    budgets.getBudgetsStream(),
                    budgets.getBudgetAmountsStream(),
                    transactions.getLedgerStream(period.range),
                ) {
                        r,
                        b,
                        a,
                        l,
                    ->
                    BudgetCalc.compute(r, b, a, l.rows, period.key)
                }
            }
        }

    val uiState: StateFlow<TransactionsUiState> = combine(
        combine(ref, tab, data, home.selectedDate, ::Quad),
        combine(filter, filterOpen, selection, ::Triple),
        combine(home.focus, home.lastSaved, home.savedMemo, ::Triple),
        budgetBlock,
    ) { (r, t, d, date), (filter, filterOpen, selection), (focus, lastSaved, savedMemo), budget ->
        TransactionsUiState(
            r, t, d, date, filter, filterOpen, selection, focus, lastSaved, savedMemo, budget,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    private fun startTab(r: RefData) = if (r.settings.startScreen == StartScreen.CALENDAR) {
        SubTab.CALENDAR
    } else {
        SubTab.DAILY
    }

    private fun currentTab(): SubTab = _tab.value ?: startTab(ref.value)

    fun clearLastSaved() = home.saved(null)

    fun onFocusHandled() = home.focusConsumed()

    private fun load(p: Params): Flow<HomeData> {
        val engine = p.ref.settings.periods
        val period = engine.monthPeriodContaining(p.date)
        val year = period.label.year
        return when (p.tab) {
            SubTab.MONTHLY -> {
                val yr = engine.yearRange(year)
                val range = DateRange(yr.start.minusDays(7), yr.end.plusDays(7))
                transactions.getLedgerStream(range).map { l -> monthly(p, period, year, l.rows) }
            }

            SubTab.NOTE -> memos.getMemosStream(
                period.range.startText,
                period.range.endText,
            ).map { memos ->
                HomeData(
                    loaded = true,
                    tab = p.tab,
                    period = period,
                    year = year,
                    memos = sortMemos(memos, period.range),
                )
            }

            SubTab.TOTAL -> {
                val prev = engine.previous(period)
                transactions.getLedgerStream(DateRange(prev.start, period.end)).map { l ->
                    total(p, period, prev, l.rows)
                }
            }

            else -> {
                val grid = gridRange(period, engine.weekStartDay)
                combine(
                    transactions.getLedgerStream(grid),
                    memos.getMemosStream(grid.startText, grid.endText),
                    repeats.getRulesStream(),
                ) {
                        l,
                        memos,
                        rules,
                    ->
                    val carry = if (p.ref.settings.carryOver && !p.filter.active) {
                        carryOverBefore(p.ref, period.start)
                    } else {
                        0.0
                    }
                    dailyAndCalendar(p, period, year, l, memos, rules, carry, grid)
                }
            }
        }
    }

    private suspend fun carryOverBefore(ref: RefData, start: LocalDate): Double {
        val t = Totals.fromAggregates(ref, transactions.getAggregatesBefore(start.toString()))
        return Money.subtract(t.income, t.expense)
    }

    private fun dailyAndCalendar(
        p: Params,
        period: MonthPeriod,
        year: Int,
        l: Ledger,
        memos: List<Memo>,
        rules: List<RepeatRule>,
        carry: Double,
        grid: DateRange,
    ): HomeData {
        val ref = p.ref
        val c = Classifier(ref)
        val factory = TransactionRowMapper(ref)
        val filtered = l.rows.filter { !p.filter.active || p.filter.matches(it, ref, c) }
        val inPeriod = filtered.filter { LocalDate.parse(it.date) in period.range }
        val memoByDay = memos.filter { !it.pinned || LocalDate.parse(it.date) in period.range }
            .filter { LocalDate.parse(it.date) in period.range }
            .groupBy { LocalDate.parse(it.date) }
            .mapValues { (_, v) ->
                v.sortedWith(
                    compareByDescending<Memo> {
                        it.dateTime
                    }.thenByDescending { it.orderSeq },
                )
            }
        val carryPair = if (carry != 0.0) period.start to carry else null
        val days = factory.dayGroups(
            inPeriod,
            l,
            if (p.filter.active) emptyMap() else memoByDay,
            carryPair,
        )
        var totals = InOut()
        days.forEach { totals += InOut(it.income, it.expense) }

        // Calendar cells
        val today = LocalDate.now()
        val byDay = filtered.filter { factory.listable(it) }.groupBy { LocalDate.parse(it.date) }
        val cells = grid.days().map { d ->
            var inc = BigDecimal.ZERO
            var exp = BigDecimal.ZERO
            val rows = byDay[d].orEmpty()
            rows.forEach { r ->
                when (c.classify(r.kind, r.accountId, r.counterpartAccountId)) {
                    CashFlow.INCOME -> inc += BigDecimal.valueOf(r.amountMain)
                    CashFlow.EXPENSE -> exp += BigDecimal.valueOf(r.amountMain)
                    CashFlow.NEUTRAL -> Unit
                }
            }
            if (carryPair != null && d == carryPair.first) {
                if (carry > 0) {
                    inc += BigDecimal.valueOf(carry)
                } else {
                    exp += BigDecimal.valueOf(-carry)
                }
            }
            CalendarCell(
                date = d,
                inPeriod = d in period.range,
                income = inc.toDouble(),
                expense = exp.toDouble(),
                hasTx = rows.isNotEmpty() || (carryPair?.first == d),
                memoColors = if (d in period.range) {
                    memoByDay[d].orEmpty().map { it.color }
                } else {
                    emptyList()
                },
                isToday = d == today,
            )
        }
        val preview = if (!p.filter.active) repeatPreview(ref, rules, period) else null
        return HomeData(
            loaded = true,
            tab = p.tab,
            period = period,
            year = year,
            days = days,
            repeatPreview = preview,
            cells = cells,
            totals = totals,
            periodRows = l.rows.filter {
                LocalDate.parse(it.date) in period.range
            },
            gridRows = filtered, carryOver = carry,
            memos = memos, ledger = l,
        )
    }

    /** Upcoming scheduled repeats from today to the period end. */
    private fun repeatPreview(
        ref: RefData,
        rules: List<RepeatRule>,
        period: MonthPeriod,
    ): RepeatPreview? {
        val today = LocalDate.now()
        val from = maxOf(today, period.start)
        if (from.isAfter(period.end)) return null
        val range = DateRange(from, period.end)
        val dates = sortedSetOf<LocalDate>()
        val names = linkedSetOf<String>()
        var total = BigDecimal.ZERO
        val c = Classifier(ref)
        for (rule in rules) {
            if (rule.accountId == null) continue
            if (!rule.kind.isTransfer && ref.categoryById[rule.categoryId]?.isDeleted != false) {
                continue
            }
            val first = runCatching { LocalDate.parse(rule.nextDate) }.getOrNull() ?: continue
            val occ = RepeatSchedule.occurrencesIn(
                first,
                rule.repeatType,
                rule.anchorDay,
                range,
                rule.endDate?.let(LocalDate::parse),
            )
            if (occ.isEmpty()) continue
            val main = BigDecimal.valueOf(
                if (ref.currency(rule.currencyId).id == ref.mainCurrency.id) {
                    rule.amount
                } else {
                    rule.amount *
                        ref.currency(rule.currencyId).rate
                },
            )
            val sign = when (rule.kind) {
                TxKind.INCOME -> 1

                TxKind.EXPENSE -> -1

                TxKind.TRANSFER_OUT -> when (
                    c.transferStatsFlow(rule.accountId, rule.toAccountId)
                ) {
                    CashFlow.INCOME -> 1
                    CashFlow.EXPENSE -> -1
                    CashFlow.NEUTRAL -> 0
                }

                else -> 0
            }
            total += main.multiply(BigDecimal(sign * occ.size))
            if (rule.repeatType == RepeatType.DAILY) dates += occ.first() else dates += occ
            names += rule.note.ifBlank {
                if (rule.kind.isTransfer) {
                    "${ref.accountById[rule.accountId]?.name} → ${ref.accountById[rule.toAccountId]?.name}"
                } else {
                    ref.categoryName(ref.mainCategoryId(rule.categoryId)) ?: ""
                }
            }
        }
        if (dates.isEmpty()) return null
        return RepeatPreview(dates.toList(), names.filter { it.isNotBlank() }, total.toDouble())
    }

    private fun monthly(
        p: Params,
        period: MonthPeriod,
        year: Int,
        rows: List<Transaction>,
    ): HomeData {
        val ref = p.ref
        val engine = ref.settings.periods
        val c = Classifier(ref)
        val filtered = rows.filter { !p.filter.active || p.filter.matches(it, ref, c) }
        fun sum(range: DateRange): InOut = Totals.fromRows(
            ref,
            filtered.filter {
                LocalDate.parse(it.date) in
                    range
            },
        )
        val today = LocalDate.now()
        val todayPeriod = engine.monthPeriodContaining(today)
        val currentYear = todayPeriod.label.year
        val periods = engine.yearPeriods(year).reversed()
        val out = ArrayList<MonthRow>()
        var shownCurrent = false
        for (mp in periods) {
            val t = sum(mp.range)
            val hasData = t.income != 0.0 || t.expense != 0.0
            val show = when {
                year < currentYear -> true

                year == currentYear -> !mp.label.isAfter(todayPeriod.label) || hasData ||
                    shownCurrent

                else -> hasData
            }
            if (!show) continue
            if (year == currentYear) shownCurrent = true
            val weeks = ArrayList<WeekRow>()
            var ws = engine.weekContaining(mp.start).start
            while (!ws.isAfter(mp.end)) {
                val wr = DateRange(ws, ws.plusDays(6))
                val wt = sum(wr)
                weeks += WeekRow(wr, wt.income, wt.expense, today in wr)
                ws = ws.plusDays(7)
            }
            out += MonthRow(mp, t.income, t.expense, weeks.reversed())
        }
        var totals = InOut()
        out.forEach { totals += InOut(it.income, it.expense) }
        return HomeData(
            loaded = true,
            tab = p.tab,
            period = period,
            year = year,
            months = out,
            totals = totals,
        )
    }

    private fun total(
        p: Params,
        period: MonthPeriod,
        prev: MonthPeriod,
        rows: List<Transaction>,
    ): HomeData {
        val ref = p.ref
        val cur = rows.filter { LocalDate.parse(it.date) in period.range }
        val before = rows.filter { LocalDate.parse(it.date) in prev.range }
        val s = summary(ref, cur, before)
        return HomeData(
            loaded = true,
            tab = p.tab,
            period = period,
            year = period.label.year,
            summary = s,
            totals = InOut(s.income, s.expense),
            periodRows = cur,
        )
    }

    /** Total-page summary. */
    private fun summary(
        ref: RefData,
        rows: List<Transaction>,
        previous: List<Transaction>,
    ): TotalSummary {
        val c = Classifier(ref)
        fun inOut(list: List<Transaction>, predicate: (Transaction) -> Boolean = { true }): InOut {
            var inc = BigDecimal.ZERO
            var exp = BigDecimal.ZERO
            list.filter(predicate).forEach { r ->
                when (c.classify(r.kind, r.accountId, r.counterpartAccountId)) {
                    CashFlow.INCOME -> inc += BigDecimal.valueOf(r.amountMain)
                    CashFlow.EXPENSE -> exp += BigDecimal.valueOf(r.amountMain)
                    else -> Unit
                }
            }
            return InOut(inc.toDouble(), exp.toDouble())
        }
        val main = ref.mainCurrency.decimals
        val now = inOut(rows)
        val prevOut = inOut(previous).expense
        val compared = if (prevOut == 0.0) {
            100
        } else {
            Math.round((now.expense - prevOut) * 100 / prevOut).toInt() + 100
        }
        fun builtin(id: String?) = ref.groupOf(id)?.builtinId
        val cashIds = setOf(1, 11)
        val cash = inOut(rows) { builtin(it.accountId) in cashIds }.expense
        val debit = inOut(rows) { ref.groupType(it.accountId) == GroupType.DEBIT_CARD }.expense
        var cardOut = BigDecimal.ZERO
        var cardIn = BigDecimal.ZERO
        var transfer = BigDecimal.ZERO
        rows.forEach { r ->
            if (ref.isCard(r.accountId)) {
                if (r.kind == TxKind.EXPENSE || r.kind == TxKind.TRANSFER_OUT) {
                    cardOut += BigDecimal.valueOf(r.amountMain)
                }
                if (r.kind == TxKind.INCOME || r.kind == TxKind.TRANSFER_IN) {
                    cardIn += BigDecimal.valueOf(r.amountMain)
                }
            }
            if (r.kind == TxKind.TRANSFER_OUT && builtin(r.accountId) in cashIds) {
                val dst = ref.groupOf(r.counterpartAccountId)
                val dstType = dst?.type
                if (dst != null && dst.builtinId !in cashIds && dstType == GroupType.NORMAL) {
                    transfer += BigDecimal.valueOf(r.amountMain)
                }
            }
        }
        val names =
            ref.groups.filter {
                it.builtinId == 11 && it.status == GroupStatus.ACTIVE
            }.map { it.name } +
                ref.groups.filter {
                    it.builtinId == 1 && it.status == GroupStatus.ACTIVE
                }.map { it.name }
        return TotalSummary(
            comparedPercent = compared,
            cashLabel = names.joinToString(", "),
            cash = Money.round(cash, main),
            debit = Money.round(debit, main),
            cardOut = cardOut.toDouble(),
            cardIn = cardIn.toDouble(),
            transfer = transfer.toDouble(),
            income = Money.round(now.income, main),
            expense = Money.round(now.expense, main),
        )
    }

    private fun sortMemos(memos: List<Memo>, range: DateRange): List<Memo> {
        val pinned = memos.filter { it.pinned }.sortedByDescending { it.pinnedAt }
        val dated = memos.filter { !it.pinned && LocalDate.parse(it.date) in range }
            .sortedWith(
                compareByDescending<Memo> {
                    it.date
                }.thenByDescending { it.dateTime }.thenByDescending { it.orderSeq },
            )
        return pinned + dated
    }

    // ---- Navigation --------------------------------------------------------------------------------------------------

    fun setTab(t: SubTab) {
        _tab.value = t
    }

    fun select(date: LocalDate) = home.select(date)

    fun consumeSavedMemo() = home.memoSaved(null)

    fun goToday() {
        viewModelScope.launch {
            _tab.value = startTab(refs.getRefData())
            home.focusOn(LocalDate.now())
        }
    }

    fun step(forward: Boolean) {
        val r = ref.value
        val engine = r.settings.periods
        val d = data.value
        if (currentTab() == SubTab.MONTHLY) {
            val y = d.year + if (forward) 1 else -1
            home.select(engine.yearRange(y).start)
        } else {
            val p = engine.monthPeriodContaining(home.selectedDate.value)
            val n = if (forward) engine.next(p) else engine.previous(p)
            home.select(n.end)
        }
    }

    fun pickMonth(m: YearMonth) {
        val p = ref.value.settings.periods.monthPeriodFor(m)
        val today = LocalDate.now()
        home.select(if (today in p.range) today else p.end)
    }

    fun openMonth(period: MonthPeriod) {
        home.select(period.end)
        _tab.value = SubTab.DAILY
    }

    // ---- Filter ------------------------------------------------------------------------------------------------------

    fun setFilterOpen(open: Boolean) {
        filterOpen.value = open
        if (!open) filter.value = FilterCriteria()
        if (open && (currentTab() == SubTab.TOTAL || currentTab() == SubTab.NOTE)) {
            _tab.value = startTab(ref.value)
        }
    }

    fun setFilter(f: FilterCriteria) {
        filter.value = f
    }

    // ---- Multi-select ------------------------------------------------------------------------------------------------

    fun toggleSelect(id: String) {
        selection.value = if (id in selection.value) selection.value - id else selection.value + id
    }

    fun setSelection(ids: Set<String>) {
        selection.value = ids
    }

    fun clearSelection() {
        selection.value = emptySet()
    }

    companion object {
        fun gridRange(period: MonthPeriod, weekStart: DayOfWeek): DateRange {
            val start = period.start.with(TemporalAdjusters.previousOrSame(weekStart))
            var end = period.end.with(TemporalAdjusters.nextOrSame(weekStart.minus(1)))
            if (ChronoUnit.DAYS.between(period.end, end) <= 4) end = end.plusDays(7)
            return DateRange(start, end)
        }
    }
}
