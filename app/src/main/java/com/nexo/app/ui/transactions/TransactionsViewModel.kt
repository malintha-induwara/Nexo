package com.nexo.app.ui.transactions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.database.entity.MemoEntity
import com.nexo.app.core.database.entity.RepeatRuleEntity
import com.nexo.app.core.database.entity.TransactionEntity
import com.nexo.app.core.model.CashFlow
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.model.StartScreen
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.money.Money
import com.nexo.app.core.period.DateRange
import com.nexo.app.core.period.MonthPeriod
import com.nexo.app.data.LedgerRepository
import com.nexo.app.data.RefRepository
import com.nexo.app.data.TransactionWriter
import com.nexo.app.domain.Classifier
import com.nexo.app.domain.InOut
import com.nexo.app.domain.RefData
import com.nexo.app.domain.RepeatSchedule
import com.nexo.app.domain.Totals
import com.nexo.app.ui.HomeState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

private data class Params(val ref: RefData, val date: LocalDate, val tab: SubTab, val filter: FilterCriteria)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val refs: RefRepository,
    private val ledgers: LedgerRepository,
    private val db: NexoDatabase,
    val writer: TransactionWriter,
    private val home: HomeState,
) : ViewModel() {
    val ref: StateFlow<RefData> = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    val selectedDate = home.selectedDate
    val focusEvents = home.focus
    val savedMemo = home.savedMemo
    val lastSaved = home.lastSaved

    fun clearLastSaved() = home.saved(null)

    private val _tab = MutableStateFlow<SubTab?>(null)
    val tab: StateFlow<SubTab?> = _tab.asStateFlow()

    private val _filter = MutableStateFlow(FilterCriteria())
    val filter: StateFlow<FilterCriteria> = _filter.asStateFlow()

    private val _filterOpen = MutableStateFlow(false)
    val filterOpen = _filterOpen.asStateFlow()

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection = _selection.asStateFlow()

    init {
        viewModelScope.launch {
            val r = refs.snapshot()
            if (_tab.value == null) _tab.value = if (r.settings.startScreen == StartScreen.CALENDAR) SubTab.CALENDAR else SubTab.DAILY
        }
    }

    val data: StateFlow<HomeData> = combine(ref, home.selectedDate, _tab.filterNotNull(), _filter) { r, d, t, f -> Params(r, d, t, f) }
        .distinctUntilChanged()
        .flatMapLatest { p -> load(p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeData())

    private fun load(p: Params): kotlinx.coroutines.flow.Flow<HomeData> {
        val engine = p.ref.settings.periods
        val period = engine.monthPeriodContaining(p.date)
        val year = period.label.year
        return when (p.tab) {
            SubTab.MONTHLY -> {
                val yr = engine.yearRange(year)
                val range = DateRange(yr.start.minusDays(7), yr.end.plusDays(7))
                ledgers.observe(range).map { l -> monthly(p, period, year, l.rows) }
            }
            SubTab.NOTE -> ledgers.observeMemos(period.range).map { memos ->
                HomeData(loaded = true, tab = p.tab, period = period, year = year, memos = sortMemos(memos, period.range))
            }
            SubTab.TOTAL -> {
                val prev = engine.previous(period)
                ledgers.observe(DateRange(prev.start, period.end)).map { l -> total(p, period, prev, l.rows) }
            }
            else -> {
                val grid = gridRange(period, engine.weekStartDay)
                combine(ledgers.observe(grid), ledgers.observeMemos(grid), db.repeatRuleDao().observeAll()) { l, memos, rules ->
                    val carry = if (p.ref.settings.carryOver && !p.filter.active) carryOverBefore(p.ref, period.start) else 0.0
                    dailyAndCalendar(p, period, year, l, memos, rules, carry, grid)
                }
            }
        }
    }

    private suspend fun carryOverBefore(ref: RefData, start: LocalDate): Double {
        val t = Totals.fromAggregates(ref, db.transactionDao().aggregatesBefore(start.toString()))
        return Money.subtract(t.income, t.expense)
    }

    private fun dailyAndCalendar(
        p: Params,
        period: MonthPeriod,
        year: Int,
        l: com.nexo.app.data.Ledger,
        memos: List<MemoEntity>,
        rules: List<RepeatRuleEntity>,
        carry: Double,
        grid: DateRange,
    ): HomeData {
        val ref = p.ref
        val c = Classifier(ref)
        val factory = RowFactory(context, ref)
        val filtered = l.rows.filter { !p.filter.active || p.filter.matches(it, ref, c) }
        val inPeriod = filtered.filter { LocalDate.parse(it.date) in period.range }
        val memoByDay = memos.filter { !it.pinned || LocalDate.parse(it.date) in period.range }
            .filter { LocalDate.parse(it.date) in period.range }
            .groupBy { LocalDate.parse(it.date) }
            .mapValues { (_, v) -> v.sortedWith(compareByDescending<MemoEntity> { it.dateTime }.thenByDescending { it.orderSeq }) }
        val carryPair = if (carry != 0.0) period.start to carry else null
        val days = factory.dayGroups(inPeriod, l, if (p.filter.active) emptyMap() else memoByDay, carryPair)
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
                if (carry > 0) inc += BigDecimal.valueOf(carry) else exp += BigDecimal.valueOf(-carry)
            }
            CalendarCell(
                date = d,
                inPeriod = d in period.range,
                income = inc.toDouble(),
                expense = exp.toDouble(),
                hasTx = rows.isNotEmpty() || (carryPair?.first == d),
                memoColors = if (d in period.range) memoByDay[d].orEmpty().map { it.color } else emptyList(),
                isToday = d == today,
            )
        }
        val preview = if (!p.filter.active) repeatPreview(ref, rules, period) else null
        return HomeData(
            loaded = true, tab = p.tab, period = period, year = year, days = days, repeatPreview = preview, cells = cells,
            totals = totals, periodRows = l.rows.filter { LocalDate.parse(it.date) in period.range }, gridRows = filtered, carryOver = carry,
            memos = memos, ledger = l,
        )
    }

    /** Upcoming scheduled repeats from today to the period end. */
    private fun repeatPreview(ref: RefData, rules: List<RepeatRuleEntity>, period: MonthPeriod): RepeatPreview? {
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
            if (!rule.kind.isTransfer && ref.categoryById[rule.categoryId]?.isDeleted != false) continue
            val first = runCatching { LocalDate.parse(rule.nextDate) }.getOrNull() ?: continue
            val occ = RepeatSchedule.occurrencesIn(first, rule.repeatType, rule.anchorDay, range, rule.endDate?.let(LocalDate::parse))
            if (occ.isEmpty()) continue
            val main = BigDecimal.valueOf(if (ref.currency(rule.currencyId).id == ref.mainCurrency.id) rule.amount else rule.amount * ref.currency(rule.currencyId).rate)
            val sign = when (rule.kind) {
                TxKind.INCOME -> 1
                TxKind.EXPENSE -> -1
                TxKind.TRANSFER_OUT -> when (c.transferStatsFlow(rule.accountId, rule.toAccountId)) {
                    CashFlow.INCOME -> 1
                    CashFlow.EXPENSE -> -1
                    CashFlow.NEUTRAL -> 0
                }
                else -> 0
            }
            total += main.multiply(BigDecimal(sign * occ.size))
            if (rule.repeatType == com.nexo.app.core.model.RepeatType.DAILY) dates += occ.first() else dates += occ
            names += rule.note.ifBlank {
                if (rule.kind.isTransfer) "${ref.accountById[rule.accountId]?.name} → ${ref.accountById[rule.toAccountId]?.name}"
                else ref.categoryName(ref.mainCategoryId(rule.categoryId)) ?: ""
            }
        }
        if (dates.isEmpty()) return null
        return RepeatPreview(dates.toList(), names.filter { it.isNotBlank() }, total.toDouble())
    }

    private fun monthly(p: Params, period: MonthPeriod, year: Int, rows: List<TransactionEntity>): HomeData {
        val ref = p.ref
        val engine = ref.settings.periods
        val c = Classifier(ref)
        val filtered = rows.filter { !p.filter.active || p.filter.matches(it, ref, c) }
        fun sum(range: DateRange): InOut = Totals.fromRows(ref, filtered.filter { LocalDate.parse(it.date) in range })
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
                year == currentYear -> !mp.label.isAfter(todayPeriod.label) || hasData || shownCurrent
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
        return HomeData(loaded = true, tab = p.tab, period = period, year = year, months = out, totals = totals)
    }

    private fun total(p: Params, period: MonthPeriod, prev: MonthPeriod, rows: List<TransactionEntity>): HomeData {
        val ref = p.ref
        val cur = rows.filter { LocalDate.parse(it.date) in period.range }
        val before = rows.filter { LocalDate.parse(it.date) in prev.range }
        val s = summary(ref, cur, before)
        return HomeData(
            loaded = true, tab = p.tab, period = period, year = period.label.year, summary = s,
            totals = InOut(s.income, s.expense), periodRows = cur,
        )
    }

    /** Total-page summary. */
    private fun summary(ref: RefData, rows: List<TransactionEntity>, previous: List<TransactionEntity>): TotalSummary {
        val c = Classifier(ref)
        fun inOut(list: List<TransactionEntity>, predicate: (TransactionEntity) -> Boolean = { true }): InOut {
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
        val compared = if (prevOut == 0.0) 100 else Math.round((now.expense - prevOut) * 100 / prevOut).toInt() + 100
        fun builtin(id: String?) = ref.groupOf(id)?.builtinId
        val cashIds = setOf(1, 11)
        val cash = inOut(rows) { builtin(it.accountId) in cashIds }.expense
        val debit = inOut(rows) { ref.groupType(it.accountId) == GroupType.DEBIT_CARD }.expense
        var cardOut = BigDecimal.ZERO
        var cardIn = BigDecimal.ZERO
        var transfer = BigDecimal.ZERO
        rows.forEach { r ->
            if (ref.isCard(r.accountId)) {
                if (r.kind == TxKind.EXPENSE || r.kind == TxKind.TRANSFER_OUT) cardOut += BigDecimal.valueOf(r.amountMain)
                if (r.kind == TxKind.INCOME || r.kind == TxKind.TRANSFER_IN) cardIn += BigDecimal.valueOf(r.amountMain)
            }
            if (r.kind == TxKind.TRANSFER_OUT && builtin(r.accountId) in cashIds) {
                val dst = ref.groupOf(r.counterpartAccountId)
                val dstType = dst?.type
                if (dst != null && dst.builtinId !in cashIds && dstType == GroupType.NORMAL) transfer += BigDecimal.valueOf(r.amountMain)
            }
        }
        val names = ref.groups.filter { it.builtinId == 11 && it.status == com.nexo.app.core.model.GroupStatus.ACTIVE }.map { it.name } +
            ref.groups.filter { it.builtinId == 1 && it.status == com.nexo.app.core.model.GroupStatus.ACTIVE }.map { it.name }
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

    private fun sortMemos(memos: List<MemoEntity>, range: DateRange): List<MemoEntity> {
        val pinned = memos.filter { it.pinned }.sortedByDescending { it.pinnedAt }
        val dated = memos.filter { !it.pinned && LocalDate.parse(it.date) in range }
            .sortedWith(compareByDescending<MemoEntity> { it.date }.thenByDescending { it.dateTime }.thenByDescending { it.orderSeq })
        return pinned + dated
    }

    // ---- Navigation --------------------------------------------------------------------------------------------------

    fun setTab(t: SubTab) {
        _tab.value = t
    }

    fun select(date: LocalDate) = home.select(date)

    fun consumeSavedMemo() = home.memoSaved(null)

    fun rowFactory(r: RefData) = RowFactory(context, r)

    fun goToday() {
        viewModelScope.launch {
            val r = refs.snapshot()
            _tab.value = if (r.settings.startScreen == StartScreen.CALENDAR) SubTab.CALENDAR else SubTab.DAILY
            home.focusOn(LocalDate.now())
        }
    }

    fun step(forward: Boolean) {
        val r = ref.value
        val engine = r.settings.periods
        val d = data.value
        if (_tab.value == SubTab.MONTHLY) {
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
        _filterOpen.value = open
        if (!open) _filter.value = FilterCriteria()
        if (open && (_tab.value == SubTab.TOTAL || _tab.value == SubTab.NOTE)) {
            _tab.value = if (ref.value.settings.startScreen == StartScreen.CALENDAR) SubTab.CALENDAR else SubTab.DAILY
        }
    }

    fun setFilter(f: FilterCriteria) {
        _filter.value = f
    }

    // ---- Multi-select ------------------------------------------------------------------------------------------------

    fun toggleSelect(id: String) {
        _selection.value = if (id in _selection.value) _selection.value - id else _selection.value + id
    }

    fun setSelection(ids: Set<String>) {
        _selection.value = ids
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun deleteSelected() = bulk { writer.delete(it) }
    fun setDateSelected(d: LocalDate) = bulk { writer.setDate(it, d) }
    fun setCategorySelected(id: String) = bulk { writer.setCategory(it, id) }
    fun setAccountSelected(id: String) = bulk { writer.setAccount(it, id) }
    fun setTransferSelected(from: String, to: String) = bulk { writer.setTransfer(it, from, to) }
    fun setNoteSelected(note: String) = bulk { writer.setNote(it, note) }

    private fun bulk(action: suspend (Set<String>) -> Unit) {
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            action(ids)
            _selection.value = emptySet()
        }
    }

    companion object {
        fun gridRange(period: MonthPeriod, weekStart: java.time.DayOfWeek): DateRange {
            val start = period.start.with(TemporalAdjusters.previousOrSame(weekStart))
            var end = period.end.with(TemporalAdjusters.nextOrSame(weekStart.minus(1)))
            if (java.time.temporal.ChronoUnit.DAYS.between(period.end, end) <= 4) end = end.plusDays(7)
            return DateRange(start, end)
        }
    }
}
