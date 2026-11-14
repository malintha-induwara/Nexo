package com.nexo.app.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.model.Currency
import com.nexo.app.ui.designsystem.UiText
import com.nexo.app.ui.designsystem.asString
import com.nexo.app.ui.designsystem.component.AmountText
import com.nexo.app.ui.designsystem.component.AreaChart
import com.nexo.app.ui.designsystem.component.ChartTitle
import com.nexo.app.ui.designsystem.component.DateRangeChips
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.GroupCard
import com.nexo.app.ui.designsystem.component.MonthPickerDialog
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.PeriodNavigator
import com.nexo.app.ui.designsystem.component.rememberHaptics
import com.nexo.app.ui.designsystem.component.shrinkToFit
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoPalette
import com.nexo.app.ui.designsystem.theme.NexoShapes
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Space
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.MemoListKey
import com.nexo.app.ui.navigation.StatsDetailKey
import com.nexo.app.ui.navigation.StatsDetailKind
import com.nexo.app.ui.navigation.StatsScope
import com.nexo.app.ui.transactions.BulkAction
import com.nexo.app.ui.transactions.BulkDialogs
import com.nexo.app.ui.transactions.BulkEditActions
import com.nexo.app.ui.transactions.DayGroupList
import com.nexo.app.ui.transactions.SelectionBar
import com.nexo.app.ui.transactions.selectedNet
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

@Composable
fun StatsDetailRoute(key: StatsDetailKey) {
    val vm =
        hiltViewModel<StatsDetailViewModel, StatsDetailViewModel.Factory>(creationCallback = {
            it.create(key)
        })
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    StatsDetailScreen(
        kind = key.kind,
        ui = ui,
        bulkActions = vm.bulkActions,
        onBack = nav::back,
        onStep = vm::step,
        onSetRange = vm::setRange,
        onPickMonth = vm::setMonth,
        onSelectBreakdown = vm::select,
        onToggleSelect = vm::toggleSelect,
        onSetSelection = vm::setSelection,
        onOpenTransaction = { nav.navigate(EntryKey(mode = EntryMode.EDIT, txId = it)) },
        onOpenMemos = { nav.navigate(MemoListKey(it.toString())) },
    )
}

@Composable
internal fun StatsDetailScreen(
    kind: StatsDetailKind,
    ui: StatsDetailUiState,
    bulkActions: BulkEditActions,
    onBack: () -> Unit,
    onStep: (forward: Boolean) -> Unit,
    onSetRange: (from: LocalDate, to: LocalDate) -> Unit,
    onPickMonth: (YearMonth) -> Unit,
    onSelectBreakdown: (key: String) -> Unit,
    onToggleSelect: (txId: String) -> Unit,
    onSetSelection: (Set<String>) -> Unit,
    onOpenTransaction: (txId: String) -> Unit,
    onOpenMemos: (date: LocalDate) -> Unit,
) {
    val ref = ui.ref
    val q = ui.query
    val sel = ui.selected
    val selection = ui.selection
    var picker by rememberSaveable { mutableStateOf(false) }
    var bulkOpen by remember { mutableStateOf(false) }
    var bulkAction by remember { mutableStateOf<BulkAction?>(null) }
    val main = ref.mainCurrency
    BackHandler(enabled = selection.isNotEmpty()) { onSetSelection(emptySet()) }
    val periodLabel = when (q.scope) {
        StatsScope.WEEKLY -> Formats.range(ui.range.start, ui.range.end)

        StatsScope.MONTHLY -> Formats.monthYear(
            ref.settings.periods.monthPeriodContaining(q.anchor).label,
        )

        StatsScope.ANNUALLY -> ref.settings.periods.yearOf(q.anchor).toString()

        StatsScope.PERIOD -> ""
    }
    val summaryLabel = stringResource(
        when (q.scope) {
            StatsScope.MONTHLY -> R.string.month_total

            StatsScope.WEEKLY -> R.string.weekly_total

            StatsScope.ANNUALLY -> if (kind == StatsDetailKind.INCOME ||
                kind == StatsDetailKind.TRANSFER_INCOME_GROUP
            ) {
                R.string.annual_income
            } else {
                R.string.annual_expenses
            }

            StatsScope.PERIOD -> R.string.total
        },
    )
    Scaffold(topBar = {
        val rows = ui.days.flatMap { it.rows }
        if (selection.isNotEmpty()) {
            SelectionBar(
                selection.size,
                rows.size,
                selectedNet(rows, selection),
                main,
                onToggleAll = {
                    onSetSelection(
                        if (selection.size >= rows.size) {
                            emptySet()
                        } else {
                            rows.map { it.id }.toSet()
                        },
                    )
                },
                onClose = { onSetSelection(emptySet()) },
                onAction = {
                    bulkAction = it
                    bulkOpen = true
                },
            )
        } else {
            NexoTopBar(ui.title.asString(), onBack = onBack)
        }
    }) { p ->
        Column(Modifier.fillMaxSize().padding(p)) {
            if (q.scope == StatsScope.PERIOD) {
                DateRangeChips(q.from, q.to, onSetRange)
            } else {
                PeriodNavigator(
                    periodLabel,
                    { onStep(false) },
                    { onStep(true) },
                    if (q.scope == StatsScope.MONTHLY) {
                        ({ picker = true })
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            DayGroupList(
                groups = ui.days,
                main = main,
                selection = selection,
                onRowClick = { row ->
                    if (selection.isNotEmpty()) {
                        onToggleSelect(row.id)
                    } else {
                        onOpenTransaction(row.id)
                    }
                },
                onRowLongClick = { row -> onToggleSelect(row.id) },
                onMemoClick = { d -> onOpenMemos(d) },
                header = {
                    item(key = "summary") {
                        val colors = LocalNexoColors.current
                        GroupCard(Modifier.padding(horizontal = Space.l, vertical = Space.xs)) {
                            Column(Modifier.padding(Space.s)) {
                                Row(
                                    Modifier.padding(horizontal = Space.s, vertical = Space.s),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        summaryLabel.uppercase(Locale.getDefault()),
                                        style = NexoType.Caps,
                                        color = colors.textTertiary,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f),
                                    )
                                    AmountText(
                                        if (q.scope == StatsScope.ANNUALLY) {
                                            ui.trend.sum()
                                        } else {
                                            ui.total
                                        },
                                        main,
                                        style = NexoType.Balance,
                                        color = colors.text,
                                    )
                                }
                                ui.breakdown.forEach { b ->
                                    val on = b.key == sel
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(NexoShapes.control)
                                            .background(
                                                if (on) colors.primarySoft else Color.Transparent,
                                            )
                                            .clickable { onSelectBreakdown(b.key) }
                                            .padding(horizontal = Space.s, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            b.name.asString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (on) colors.primaryText else colors.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            if (b.key == "all") {
                                                ""
                                            } else {
                                                b.percent?.takeIf { it > 0 }?.let { "$it%" }
                                                    ?: "-"
                                            },
                                            style = NexoType.Meta,
                                            color = colors.textSecondary,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.width(48.dp),
                                        )
                                        Spacer(Modifier.width(Space.m))
                                        AmountText(
                                            b.amount,
                                            main,
                                            style = NexoType.AmountSmall,
                                            color = colors.text,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (ui.trend.any { it != 0.0 } && ui.trend.size == ui.trendLabels.size) {
                        item(key = "trend") {
                            TrendCard(
                                ui.title.asString(),
                                ui.trendLabels,
                                ui.trend,
                                NexoPalette.Stats[
                                    ui.color %
                                        11,
                                ],
                                main,
                            )
                        }
                    }
                },
            )
        }
    }
    if (picker) {
        MonthPickerDialog(
            ref.settings.periods.monthPeriodContaining(q.anchor).label,
            onPick = onPickMonth,
            onDismiss = {
                picker = false
            },
        )
    }
    BulkDialogs(bulkAction, bulkOpen, selection.size, ref, bulkActions, onDismiss = {
        bulkOpen = false
    })
}

/** The category's total over the recent periods; tap or drag the chart to read another period. */
@Composable
private fun TrendCard(
    title: String,
    labels: List<String>,
    values: List<Double>,
    color: Color,
    main: Currency,
) {
    val haptics = rememberHaptics()
    var picked by rememberSaveable(labels) { mutableIntStateOf(labels.lastIndex) }
    val i = picked.coerceIn(0, labels.lastIndex)
    GroupCard(Modifier.padding(horizontal = Space.l, vertical = Space.xs)) {
        Column(Modifier.padding(start = Space.l, end = Space.l, top = Space.l, bottom = Space.m)) {
            ChartTitle(title, labels[i])
            Spacer(Modifier.height(Space.xxs))
            AmountText(
                values[i],
                main,
                Modifier.shrinkToFit(),
                style = NexoType.Balance,
                color = LocalNexoColors.current.text,
            )
            Spacer(Modifier.height(Space.s))
            AreaChart(labels, values, color, i, {
                if (it != picked) {
                    haptics.tick()
                    picked = it
                }
            })
        }
    }
}

@Preview
@Composable
private fun StatsDetailScreenPreview() = NexoPreview {
    val month = PreviewData.ref.settings.periods.monthPeriodContaining(PreviewData.today)
    StatsDetailScreen(
        kind = StatsDetailKind.EXPENSE,
        ui = StatsDetailUiState(
            ref = PreviewData.ref,
            query = StatsQuery(anchor = PreviewData.today),
            title = UiText.Raw("Food"),
            range = month.range,
            total = 42_500.0,
            breakdown = listOf(
                Breakdown("all", UiText.Raw("All"), 42_500.0, null),
                Breakdown("groceries", UiText.Raw("Groceries"), 30_000.0, 71),
            ),
            trendLabels = listOf("Jun", "Jul", "Aug", "Sep", "Oct", "Nov"),
            trend = listOf(38_000.0, 41_200.0, 36_900.0, 44_100.0, 39_800.0, 42_500.0),
        ),
        bulkActions = BulkEditActions({}, {}, {}, {}, {}, { _, _ -> }),
        onBack = {},
        onStep = {},
        onSetRange = { _, _ -> },
        onPickMonth = {},
        onSelectBreakdown = {},
        onToggleSelect = {},
        onSetSelection = {},
        onOpenTransaction = {},
        onOpenMemos = {},
    )
}
