package com.nexo.app.ui.budgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.database.entity.CurrencyEntity
import com.nexo.app.core.money.AmountFormatter
import com.nexo.app.core.period.MonthPeriod
import com.nexo.app.domain.BudgetRow
import com.nexo.app.ui.navigation.BudgetAddKey
import com.nexo.app.ui.navigation.BudgetDetailKey
import com.nexo.app.ui.navigation.BudgetEditorKey
import com.nexo.app.ui.navigation.BudgetSettingsKey
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.theme.NexoPalette
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Proportional "Today" marker (ST-043 with the README §8 fix). */
fun todayFraction(period: MonthPeriod, now: LocalDateTime = LocalDateTime.now()): Float? {
    val start = period.start.atStartOfDay()
    val end = period.end.plusDays(1).atStartOfDay()
    if (!now.isAfter(start) || !now.isBefore(end)) return null
    val total = ChronoUnit.MINUTES.between(start, end).toFloat()
    return (ChronoUnit.MINUTES.between(start, now) / total).coerceIn(0f, 1f)
}

@Composable
fun BudgetBlock(period: MonthPeriod, vm: BudgetBlockViewModel = hiltViewModel(key = "budget_block")) {
    LaunchedEffect(period) { vm.setPeriod(period) }
    val data by vm.data.collectAsStateWithLifecycle()
    val ref by vm.ref.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    var expandedAll by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    val today = todayFraction(period)
    val d = data
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Savings, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.budget), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { nav.navigate(BudgetSettingsKey) }) {
                    Text(stringResource(R.string.budget_setting))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
            if (d?.total == null) {
                Text(stringResource(R.string.budget_empty_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                return@Column
            }
            if (today != null) Text(stringResource(R.string.today), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            val anyChildren = d.rows.any { it.children.isNotEmpty() }
            BudgetRowView(d.total, ref.mainCurrency, today, onClick = {
                if (d.total.budgetId != null) nav.navigate(BudgetEditorKey(d.total.budgetId)) else nav.navigate(BudgetAddKey(presetTotal = true, preset = true))
            }, expandable = anyChildren, expanded = expandedAll, onExpand = {
                expandedAll = !expandedAll
                expanded = if (expandedAll) d.rows.mapNotNull { it.categoryId }.toSet() else emptySet()
            })
            d.rows.forEach { row ->
                val open = row.categoryId in expanded
                BudgetRowView(row, ref.mainCurrency, today, onClick = {
                    nav.navigate(BudgetDetailKey(row.budgetId ?: "virtual_${row.categoryId}", period.label.toString()))
                }, expandable = row.children.isNotEmpty(), expanded = open, onExpand = {
                    expanded = if (open) expanded - row.categoryId!! else expanded + row.categoryId!!
                })
                if (open) row.children.forEach { child ->
                    BudgetRowView(child, ref.mainCurrency, today, small = true, onClick = {
                        child.budgetId?.let { nav.navigate(BudgetDetailKey(it, period.label.toString())) }
                    })
                }
            }
        }
    }
}

/** One budget row (ST-042): progress bar with rate, today line, used and remaining / excess. */
@Composable
fun BudgetRowView(
    row: BudgetRow,
    main: CurrencyEntity,
    today: Float?,
    onClick: () -> Unit,
    small: Boolean = false,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onExpand: () -> Unit = {},
) {
    val over = row.used > row.amount
    val full = row.used == row.amount && row.amount > 0
    val barColor = if (over) NexoPalette.ExpenseRed else NexoPalette.IncomeBlue
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = if (small) 16.dp else 0.dp, top = 10.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.name, style = if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge, fontWeight = if (row.isTotal) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(AmountFormatter.format(row.amount, main), style = MaterialTheme.typography.bodySmall)
            if (expandable) Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.clickable(onClick = onExpand).padding(start = 4.dp))
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(if (small) 14.dp else 18.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
            Box(Modifier.fillMaxWidth(row.fill).height(if (small) 14.dp else 18.dp).background(barColor.copy(alpha = if (full) 1f else 0.85f), RoundedCornerShape(4.dp)))
            val rate = row.rate
            Text(if (rate == null) "-" else "$rate%", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp))
            if (today != null) {
                val line = MaterialTheme.colorScheme.onSurface
                Canvas(Modifier.matchParentSize()) {
                    val x = size.width * today
                    drawLine(line, Offset(x, -4f), Offset(x, size.height + 4f), strokeWidth = 2f)
                }
            }
        }
        Row {
            Text(
                if (row.used < 0) "(${AmountFormatter.format(-row.used, main)})" else AmountFormatter.format(row.used, main),
                color = barColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
            )
            Text(
                if (over) stringResource(R.string.excess) + " " + AmountFormatter.format(row.remaining, main) else AmountFormatter.format(row.remaining, main),
                style = MaterialTheme.typography.bodySmall, color = if (over) NexoPalette.ExpenseRed else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

