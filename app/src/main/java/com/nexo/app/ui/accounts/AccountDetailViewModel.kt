package com.nexo.app.ui.accounts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.common.period.DateRange
import com.nexo.app.core.common.period.PeriodEngine
import com.nexo.app.core.model.Account
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.Transaction
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.repository.AccountsRepository
import com.nexo.app.data.repository.MemosRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.domain.CardFigures
import com.nexo.app.domain.CardStatement
import com.nexo.app.ui.navigation.AccountDetailKey
import com.nexo.app.ui.transactions.DayGroup
import com.nexo.app.ui.transactions.RowFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DetailTab { DAILY, MONTHLY, ANNUALLY }

data class PeriodRowUi(val label: String, val range: DateRange, val deposit: Double, val withdrawal: Double, val balance: Double, val current: Boolean, val month: YearMonth? = null, val year: Int? = null)

data class ChartData(val labels: List<String>, val balances: List<Double>, val inflow: List<Double>, val outflow: List<Double>)

data class AccountDetailUi(
    val loaded: Boolean = false,
    val account: Account? = null,
    val type: GroupType = GroupType.NORMAL,
    val range: DateRange = DateRange.ALL,
    val title: String = "",
    val days: List<DayGroup> = emptyList(),
    val running: Map<String, Double> = emptyMap(),
    val deposit: Double = 0.0,
    val withdrawal: Double = 0.0,
    val balance: Double? = null,
    val showHelp: Boolean = false,
    val periods: List<PeriodRowUi> = emptyList(),
    val chart: ChartData? = null,
    val card: CardFigures? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = AccountDetailViewModel.Factory::class)
class AccountDetailViewModel @AssistedInject constructor(
    @Assisted val key: AccountDetailKey,
    @ApplicationContext private val context: Context,
    private val memos: MemosRepository,
    refs: ReferenceDataRepository,
    val transactions: TransactionsRepository,
    private val accounts: AccountsRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: AccountDetailKey): AccountDetailViewModel
    }

    val ref: StateFlow<RefData> = refs.getRefDataStream().stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    private val _tab = MutableStateFlow(DetailTab.DAILY)
    val tab = _tab.asStateFlow()
    private val _anchor = MutableStateFlow(key.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now())
    val anchor = _anchor.asStateFlow()
    private val _charts = MutableStateFlow(false)
    val charts = _charts.asStateFlow()
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection = _selection.asStateFlow()

    val ui: StateFlow<AccountDetailUi> = combine(ref, _tab, _anchor, _charts, transactions.getChangeStampStream()) { r, t, a, c, _ -> Quad(r, t, a, c) }
        .mapLatest { compute(it.r, it.t, it.a, it.c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountDetailUi())

    private data class Quad(val r: RefData, val t: DetailTab, val a: LocalDate, val c: Boolean)

    fun engineFor(r: RefData, a: Account?): PeriodEngine =
        if (a != null && r.groupType(a.id) == GroupType.CREDIT_CARD) PeriodEngine(a.settlementDay, weekStart = r.settings.weekStartDay) else r.settings.periods

    private suspend fun compute(r: RefData, tab: DetailTab, anchor: LocalDate, charts: Boolean): AccountDetailUi {
        val account = key.accountId?.let { r.accountById[it] }
        val factory = RowFactory(context, r)
        if (account == null) {
            // "None" pseudo-account: all unassigned rows, no balances.
            val rows = transactions.getWithoutAccount()
            val ledger = transactions.getLedgerFor(rows)
            return AccountDetailUi(loaded = true, title = context.getString(com.nexo.app.R.string.none), days = factory.dayGroups(rows, ledger))
        }
        val type = r.groupType(account.id) ?: GroupType.NORMAL
        val ids = listOf(account.id) + r.linkedDebitCards(account.id).map { it.id }
        val all = transactions.getAllForAccounts(ids).sortedWith(compareBy({ it.date }, { it.dateTime }, { it.createdAt }))
        val engine = engineFor(r, account)
        val measure = { t: Transaction -> com.nexo.app.domain.Balances.rowMeasure(r, t, account) }
        val signed = { t: Transaction -> if (t.kind.isInflow) measure(t) else -measure(t) }
        fun net(rows: List<Transaction>) = rows.fold(BigDecimal.ZERO) { acc, t -> acc + BigDecimal.valueOf(signed(t)) }.toDouble()
        fun inflow(rows: List<Transaction>) = rows.filter { it.kind.isInflow }.fold(BigDecimal.ZERO) { a, t -> a + BigDecimal.valueOf(measure(t)) }.toDouble()
        fun outflow(rows: List<Transaction>) = rows.filter { !it.kind.isInflow }.fold(BigDecimal.ZERO) { a, t -> a + BigDecimal.valueOf(measure(t)) }.toDouble()
        fun inRange(range: DateRange) = all.filter { LocalDate.parse(it.date) in range }
        fun before(d: LocalDate) = all.filter { LocalDate.parse(it.date).isBefore(d) }
        fun upTo(d: LocalDate) = all.filter { !LocalDate.parse(it.date).isAfter(d) }
        val isDebit = type == GroupType.DEBIT_CARD
        val card = if (type == GroupType.CREDIT_CARD) CardStatement.compute(r, account, all, LocalDate.now()) else null
        val cur = r.accountCurrency(account.id)

        when (tab) {
            DetailTab.DAILY -> {
                val period = engine.monthPeriodContaining(anchor)
                val range = period.range
                val rows = inRange(range)
                val start = if (isDebit) 0.0 else net(before(range.start))
                var running = BigDecimal.valueOf(start)
                val runningMap = HashMap<String, Double>()
                // Chronological order inside the display order of the day.
                rows.groupBy { it.date }.toSortedMap().forEach { (_, day) ->
                    val ordered = RowFactory.sortDay(day, r.settings.timeDisplay).let { if (r.settings.timeDisplay.descending) it.reversed() else it }
                    ordered.forEach { t ->
                        running += BigDecimal.valueOf(signed(t))
                        runningMap[t.id] = running.toDouble()
                    }
                }
                val ledger = transactions.getLedgerFor(rows)
                val memos = memos.getMemos().filter { LocalDate.parse(it.date) in range }.groupBy { LocalDate.parse(it.date) }
                val days = factory.dayGroups(rows, ledger, memos = memos, includeBalance = true) { t ->
                    val row = factory.row(t, ledger)
                    val shown = when {
                        account.currencyId == r.mainCurrency.id -> t.amountMain
                        t.currencyId == account.currencyId -> t.amount
                        else -> t.amountAccount
                    }
                    val flow = if (t.kind.isInflow) com.nexo.app.core.model.CashFlow.INCOME else com.nexo.app.core.model.CashFlow.EXPENSE
                    val title = if (t.kind == TxKind.TRANSFER_IN) "${factory.accountName(t.counterpartAccountId)} → ${factory.accountName(t.accountId)}" else row.title
                    row.copy(amountText = AmountFormatter.format(shown, cur), flow = flow, amountMain = shown, title = if (row.titleIsNote) row.title else title, accountText = if (t.kind == TxKind.TRANSFER_IN) title else row.accountText)
                }
                val endBalance = if (isDebit) net(rows) else net(upTo(range.end))
                val help = !isDebit && LocalDate.now() in range && all.any { val d = LocalDate.parse(it.date); d.isAfter(range.end) && d.isBefore(LocalDate.of(2050, 1, 1)) }
                val chart = if (charts) {
                    var p = period
                    val list = ArrayList<com.nexo.app.core.common.period.MonthPeriod>()
                    repeat(6) { list.add(0, p); p = engine.previous(p) }
                    val nonBalance = { l: List<Transaction> -> l.filter { !it.kind.isBalance } }
                    ChartData(
                        list.map { com.nexo.app.ui.designsystem.component.Formats.monthShort(it.label.monthValue) },
                        list.map { net(upTo(it.end)) },
                        list.map { inflow(nonBalance(inRange(it.range))) },
                        list.map { outflow(nonBalance(inRange(it.range))) },
                    )
                } else null
                return AccountDetailUi(
                    loaded = true, account = account, type = type, range = range,
                    title = com.nexo.app.ui.designsystem.component.Formats.monthYear(period.label),
                    days = days, running = runningMap, deposit = inflow(rows), withdrawal = outflow(rows),
                    balance = if (isDebit) null else endBalance, showHelp = help, chart = chart, card = card,
                )
            }
            DetailTab.MONTHLY -> {
                val year = engine.yearOf(anchor)
                val periods = engine.yearPeriods(year)
                var bal = BigDecimal.valueOf(net(before(periods.first().start)))
                val today = LocalDate.now()
                val rowsUi = periods.map { p ->
                    val rows = inRange(p.range)
                    bal += BigDecimal.valueOf(net(rows))
                    PeriodRowUi(com.nexo.app.ui.designsystem.component.Formats.monthShort(p.label.monthValue), p.range, inflow(rows), outflow(rows), bal.toDouble(), today in p.range, month = p.label)
                }.reversed()
                val yr = DateRange(periods.first().start, periods.last().end)
                val rows = inRange(yr)
                return AccountDetailUi(
                    loaded = true, account = account, type = type, range = yr, title = year.toString(), periods = rowsUi,
                    deposit = inflow(rows), withdrawal = outflow(rows), balance = if (isDebit) null else net(upTo(yr.end)),
                    chart = if (charts) yearChart(engine, year, ::inRange, ::upTo, ::net, ::inflow, ::outflow) else null, card = card,
                )
            }
            DetailTab.ANNUALLY -> {
                val dates = all.map { LocalDate.parse(it.date) }
                val thisYear = engine.yearOf(LocalDate.now())
                val first = dates.minOrNull()?.let { engine.yearOf(it) } ?: thisYear
                var last = dates.maxOrNull()?.let { engine.yearOf(it) } ?: thisYear
                if (dates.distinct().size <= 1) last = maxOf(last, thisYear)
                val lo = minOf(first, last)
                var bal = BigDecimal.ZERO
                val rowsUi = (lo..last).map { y ->
                    val yr = engine.yearRange(y)
                    val rows = inRange(yr)
                    bal += BigDecimal.valueOf(net(rows))
                    PeriodRowUi(y.toString(), yr, inflow(rows), outflow(rows), bal.toDouble(), y == thisYear, year = y)
                }.reversed()
                val chartYear = engine.yearOf(anchor)
                return AccountDetailUi(
                    loaded = true, account = account, type = type, range = DateRange.ALL, title = "$lo ~ $last", periods = rowsUi,
                    deposit = rowsUi.sumOf { it.deposit }, withdrawal = rowsUi.sumOf { it.withdrawal }, balance = null,
                    chart = if (charts) yearChart(engine, chartYear, ::inRange, ::upTo, ::net, ::inflow, ::outflow) else null, card = card,
                )
            }
        }
    }

    private fun yearChart(
        engine: PeriodEngine,
        year: Int,
        inRange: (DateRange) -> List<Transaction>,
        upTo: (LocalDate) -> List<Transaction>,
        net: (List<Transaction>) -> Double,
        inflow: (List<Transaction>) -> Double,
        outflow: (List<Transaction>) -> Double,
    ): ChartData {
        val years = (year - 5..year).toList()
        return ChartData(
            years.map { it.toString() },
            years.map { net(upTo(engine.yearRange(it).end)) },
            years.map { inflow(inRange(engine.yearRange(it)).filter { t -> !t.kind.isBalance }) },
            years.map { outflow(inRange(engine.yearRange(it)).filter { t -> !t.kind.isBalance }) },
        )
    }

    fun setTab(t: DetailTab) {
        _tab.value = t
    }

    fun openMonth(m: YearMonth) {
        val a = ref.value.accountById[key.accountId]
        _anchor.value = engineFor(ref.value, a).monthPeriodFor(m).end
        _tab.value = DetailTab.DAILY
    }

    fun openYear(y: Int) {
        val a = ref.value.accountById[key.accountId]
        _anchor.value = engineFor(ref.value, a).yearRange(y).start.plusDays(15)
        _tab.value = DetailTab.MONTHLY
    }

    fun setAnchor(d: LocalDate) {
        _anchor.value = d
    }

    fun step(forward: Boolean) {
        val r = ref.value
        val e = engineFor(r, r.accountById[key.accountId])
        _anchor.update { d ->
            when (_tab.value) {
                DetailTab.DAILY -> e.monthPeriodContaining(d).let { if (forward) e.next(it).end else e.previous(it).end }
                else -> e.yearRange(e.yearOf(d) + if (forward) 1 else -1).start.plusDays(15)
            }
        }
    }

    fun toggleCharts() = _charts.update { !it }
    fun toggleSelect(id: String) = _selection.update { if (id in it) it - id else it + id }
    fun setSelection(ids: Set<String>) {
        _selection.value = ids
    }

    fun restore(onDone: () -> Unit) = viewModelScope.launch {
        key.accountId?.let { accounts.setStatus(it, AccountStatus.ACTIVE) }
        onDone()
    }
}
