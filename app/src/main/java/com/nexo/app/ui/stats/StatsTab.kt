package com.nexo.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.model.SwipeMode
import com.nexo.app.core.money.AmountFormatter
import com.nexo.app.ui.components.ChoiceDialog
import com.nexo.app.ui.components.DateRangeChips
import com.nexo.app.ui.components.EmptyState
import com.nexo.app.ui.components.Formats
import com.nexo.app.ui.components.MonthPickerDialog
import com.nexo.app.ui.components.NexoDatePickerDialog
import com.nexo.app.ui.components.PeriodNavigator
import com.nexo.app.ui.components.PieChart
import com.nexo.app.ui.components.PieSlice
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.StatsDetailKey
import com.nexo.app.ui.navigation.StatsScope
import com.nexo.app.ui.theme.LocalHeaderColors
import com.nexo.app.ui.theme.NexoPalette
import java.math.RoundingMode
import kotlin.math.abs

@Composable
fun scopeLabel(s: StatsScope): String = stringResource(
    when (s) {
        StatsScope.WEEKLY -> R.string.weekly
        StatsScope.MONTHLY -> R.string.monthly
        StatsScope.ANNUALLY -> R.string.annually
        StatsScope.PERIOD -> R.string.period
    },
)

/** Stats tab (ST-001…ST-007). */
@Composable
fun StatsTab(todaySignal: Int, vm: StatsViewModel = hiltViewModel()) {
    val ref by vm.ref.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val income by vm.income.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val header = LocalHeaderColors.current
    var scopePicker by rememberSaveable { mutableStateOf(false) }
    var titlePicker by rememberSaveable { mutableStateOf(false) }
    var selectedSlice by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(todaySignal) { if (todaySignal > 0) vm.goToday() }
    val main = ref.mainCurrency
    val rows = if (income) data.income else data.expense

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(header.container).statusBarsPadding().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (q.scope == StatsScope.PERIOD) {
                DateRangeChips(q.from, q.to, onChange = vm::setRange, color = header.content)
            } else {
                val label = when (q.scope) {
                    StatsScope.WEEKLY -> Formats.range(data.range.start, data.range.end)
                    StatsScope.MONTHLY -> Formats.monthYear(ref.settings.periods.monthPeriodContaining(q.anchor).label)
                    else -> ref.settings.periods.yearOf(q.anchor).toString()
                }
                PeriodNavigator(label, { vm.step(false) }, { vm.step(true) }, if (q.scope == StatsScope.ANNUALLY) null else ({ titlePicker = true }))
            }
            Spacer(Modifier.weight(1f))
            AssistChip(onClick = { scopePicker = true }, label = { Text(scopeLabel(q.scope), color = header.content) }, trailingIcon = { Icon(Icons.Filled.ExpandMore, null, tint = header.content) }, modifier = Modifier.padding(end = 8.dp))
        }
        PrimaryTabRow(selectedTabIndex = if (income) 0 else 1) {
            Tab(income, onClick = { vm.setIncome(true); selectedSlice = -1 }, text = {
                Text(stringResource(R.string.income) + if (data.income.isNotEmpty()) "  " + AmountFormatter.format(data.incomeTotal, main) else "", maxLines = 1)
            })
            Tab(!income, onClick = { vm.setIncome(false); selectedSlice = -1 }, text = {
                Text(stringResource(R.string.expenses) + if (data.expense.isNotEmpty()) "  " + AmountFormatter.format(data.expenseTotal, main) else "", maxLines = 1)
            })
        }
        val threshold = with(LocalDensity.current) { 90.dp.toPx() }
        val swipe = ref.settings.swipeMode
        val swipeModifier = if (swipe == SwipeMode.NONE || q.scope == StatsScope.PERIOD) Modifier else Modifier.pointerInput(swipe, income) {
            var total = 0f
            detectHorizontalDragGestures(onDragStart = { total = 0f }, onHorizontalDrag = { _, dx -> total += dx }, onDragEnd = {
                if (abs(total) > threshold) {
                    if (swipe == SwipeMode.CHANGE_DATE) vm.step(total < 0) else vm.setIncome(total > 0)
                }
            })
        }
        LazyColumn(Modifier.fillMaxSize().then(swipeModifier), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                PieChart(
                    rows.map { PieSlice(it.name, it.percent, NexoPalette.Stats[it.colorIndex]) },
                    onSlice = { selectedSlice = it }, selected = selectedSlice.takeIf { it >= 0 },
                )
                rows.getOrNull(selectedSlice)?.let { r ->
                    Text(
                        "${r.name}: ${AmountFormatter.format(r.amount, main)} (${r.percent.toBigDecimal().setScale(1, RoundingMode.HALF_UP)}%)",
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (rows.isEmpty()) item { EmptyState() }
            items(rows, key = { "${it.kind}_${it.key}" }) { r ->
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable {
                        nav.navigate(StatsDetailKey(r.key, r.kind, q.scope, data.range.startText, data.range.endText, r.colorIndex))
                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(52.dp).background(NexoPalette.Stats[r.colorIndex], RoundedCornerShape(6.dp)).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                        Text("${r.percent.toBigDecimal().setScale(0, RoundingMode.HALF_UP)}%", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(r.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(AmountFormatter.format(r.amount, main))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    if (scopePicker) ChoiceDialog(stringResource(R.string.period), StatsScope.entries, q.scope, { scopeLabel(it) }, onSelect = vm::setScope, onDismiss = { scopePicker = false })
    if (titlePicker) {
        if (q.scope == StatsScope.MONTHLY) MonthPickerDialog(ref.settings.periods.monthPeriodContaining(q.anchor).label, onPick = vm::setMonth, onDismiss = { titlePicker = false })
        else NexoDatePickerDialog(q.anchor, onPick = vm::setAnchor, onDismiss = { titlePicker = false })
    }
}
