package com.nexo.app.ui.stats

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.database.entity.TransactionEntity
import com.nexo.app.core.model.CashFlow
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.money.AmountFormatter
import com.nexo.app.core.money.Money
import com.nexo.app.core.period.DateRange
import com.nexo.app.data.LedgerRepository
import com.nexo.app.data.RefRepository
import com.nexo.app.data.TransactionWriter
import com.nexo.app.domain.Classifier
import com.nexo.app.domain.RefData
import com.nexo.app.ui.components.DateRangeChips
import com.nexo.app.ui.components.Formats
import com.nexo.app.ui.components.LineChart
import com.nexo.app.ui.components.MonthPickerDialog
import com.nexo.app.ui.components.NexoTopBar
import com.nexo.app.ui.components.PeriodNavigator
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.StatsDetailKey
import com.nexo.app.ui.navigation.StatsDetailKind
import com.nexo.app.ui.navigation.StatsScope
import com.nexo.app.ui.theme.NexoPalette
import com.nexo.app.ui.transactions.BulkAction
import com.nexo.app.ui.transactions.BulkDialogs
import com.nexo.app.ui.transactions.DayGroup
import com.nexo.app.ui.transactions.DayGroupList
import com.nexo.app.ui.transactions.RowFactory
import com.nexo.app.ui.transactions.SelectionBar
import com.nexo.app.ui.transactions.selectedNet
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

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
    refs: RefRepository,
    private val ledgers: LedgerRepository,
    val writer: TransactionWriter,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: StatsDetailKey): StatsDetailViewModel
    }

    val ref: StateFlow<RefData> = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
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
        combine(ledgers.observe(loadRange), ledgers.observeMemos(range)) { l, memos -> build(r, range, buckets, l, sel, memos) }
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

    private fun build(r: RefData, range: DateRange, buckets: List<Pair<String, DateRange>>, l: com.nexo.app.data.Ledger, sel: String, memos: List<com.nexo.app.core.database.entity.MemoEntity>): StatsDetailUi {
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

@Composable
fun StatsDetailScreen(key: StatsDetailKey) {
    val vm = hiltViewModel<StatsDetailViewModel, StatsDetailViewModel.Factory>(creationCallback = { it.create(key) })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ref by vm.ref.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val sel by vm.selected.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    var picker by rememberSaveable { mutableStateOf(false) }
    var bulkOpen by remember { mutableStateOf(false) }
    var bulkAction by remember { mutableStateOf<BulkAction?>(null) }
    val main = ref.mainCurrency
    BackHandler(enabled = selection.isNotEmpty()) { vm.selection.value = emptySet() }
    val periodLabel = when (q.scope) {
        StatsScope.WEEKLY -> Formats.range(ui.range.start, ui.range.end)
        StatsScope.MONTHLY -> Formats.monthYear(ref.settings.periods.monthPeriodContaining(q.anchor).label)
        StatsScope.ANNUALLY -> ref.settings.periods.yearOf(q.anchor).toString()
        StatsScope.PERIOD -> ""
    }
    val summaryLabel = stringResource(
        when (q.scope) {
            StatsScope.MONTHLY -> R.string.month_total
            StatsScope.WEEKLY -> R.string.weekly_total
            StatsScope.ANNUALLY -> if (key.kind == StatsDetailKind.INCOME || key.kind == StatsDetailKind.TRANSFER_INCOME_GROUP) R.string.annual_income else R.string.annual_expenses
            StatsScope.PERIOD -> R.string.total
        },
    )
    Scaffold(topBar = {
        val rows = ui.days.flatMap { it.rows }
        if (selection.isNotEmpty()) SelectionBar(selection.size, rows.size, selectedNet(rows, selection), main, onToggleAll = {
            vm.selection.value = if (selection.size >= rows.size) emptySet() else rows.map { it.id }.toSet()
        }, onClose = { vm.selection.value = emptySet() }, onAction = { bulkAction = it; bulkOpen = true })
        else NexoTopBar(ui.title, onBack = { nav.back() })
    }) { p ->
        Column(Modifier.fillMaxSize().padding(p)) {
            if (q.scope == StatsScope.PERIOD) DateRangeChips(q.from, q.to, vm::setRange)
            else PeriodNavigator(periodLabel, { vm.step(false) }, { vm.step(true) }, if (q.scope == StatsScope.MONTHLY) ({ picker = true }) else null, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
            DayGroupList(
                groups = ui.days, main = main, selection = selection,
                onRowClick = { row -> if (selection.isNotEmpty()) vm.selection.update { if (row.id in it) it - row.id else it + row.id } else nav.navigate(EntryKey(mode = EntryMode.EDIT, txId = row.id)) },
                onRowLongClick = { row -> vm.selection.update { if (row.id in it) it - row.id else it + row.id } },
                onMemoClick = { d -> nav.navigate(com.nexo.app.ui.navigation.MemoListKey(d.toString())) },
                header = {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(summaryLabel, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(AmountFormatter.format(if (q.scope == StatsScope.ANNUALLY) ui.trend.sum() else ui.total, main), fontWeight = FontWeight.SemiBold)
                        }
                        ui.breakdown.forEach { b ->
                            Row(
                                Modifier.fillMaxWidth().background(if (b.key == sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface)
                                    .clickable { vm.select(b.key) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(b.name, Modifier.weight(1f))
                                Text(if (b.key == "all") "" else b.percent?.takeIf { it > 0 }?.let { "$it%" } ?: "-", Modifier.width(56.dp), style = MaterialTheme.typography.bodySmall)
                                Text(AmountFormatter.format(b.amount, main))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        LineChart(ui.trendLabels, ui.trend, NexoPalette.Stats[ui.color % 11], modifier = Modifier.padding(12.dp), format = { AmountFormatter.format(it, main) })
                    }
                },
            )
        }
    }
    if (picker) MonthPickerDialog(ref.settings.periods.monthPeriodContaining(q.anchor).label, onPick = { m -> vm.query.update { it.copy(anchor = ref.settings.periods.monthPeriodFor(m).end) } }, onDismiss = { picker = false })
    BulkDialogs(bulkAction, bulkOpen, selection.size, ref, vm.writer, selection, onDone = { vm.selection.value = emptySet() }, onDismiss = { bulkOpen = false })
}

