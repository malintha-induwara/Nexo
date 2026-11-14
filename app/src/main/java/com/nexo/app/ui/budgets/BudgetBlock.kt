package com.nexo.app.ui.budgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.common.period.MonthPeriod
import com.nexo.app.core.model.Currency
import com.nexo.app.core.model.RefData
import com.nexo.app.domain.BudgetBlockData
import com.nexo.app.domain.BudgetRow
import com.nexo.app.ui.designsystem.LocalClock
import com.nexo.app.ui.designsystem.theme.LocalMoneyColors
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.nexo
import com.nexo.app.ui.navigation.BudgetAddKey
import com.nexo.app.ui.navigation.BudgetDetailKey
import com.nexo.app.ui.navigation.BudgetEditorKey
import com.nexo.app.ui.navigation.BudgetSettingsKey
import com.nexo.app.ui.navigation.LocalNavigator
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Proportional "Today" marker. */
fun todayFraction(period: MonthPeriod, now: LocalDateTime): Float? {
    val start = period.start.atStartOfDay()
    val end = period.end.plusDays(1).atStartOfDay()
    if (!now.isAfter(start) || !now.isBefore(end)) return null
    val total = ChronoUnit.MINUTES.between(start, end).toFloat()
    return (ChronoUnit.MINUTES.between(start, now) / total).coerceIn(0f, 1f)
}

@Composable
fun BudgetBlock(period: MonthPeriod, data: BudgetBlockData?, ref: RefData) {
    val nav = LocalNavigator.current
    var expandedAll by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    val today = todayFraction(period, LocalDateTime.now(LocalClock.current))
    val d = data
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Savings, null, tint = MaterialTheme.nexo.primaryText)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.budget),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { nav.navigate(BudgetSettingsKey) }) {
                    Text(stringResource(R.string.budget_setting))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
            if (d?.total == null) {
                Text(
                    stringResource(R.string.budget_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                return@Column
            }
            if (today != null) {
                Text(
                    stringResource(R.string.today),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.nexo.primaryText,
                )
            }
            val anyChildren = d.rows.any { it.children.isNotEmpty() }
            BudgetRowView(d.total, ref.mainCurrency, today, onClick = {
                if (d.total.budgetId != null) {
                    nav.navigate(BudgetEditorKey(d.total.budgetId))
                } else {
                    nav.navigate(BudgetAddKey(presetTotal = true, preset = true))
                }
            }, expandable = anyChildren, expanded = expandedAll, onExpand = {
                expandedAll = !expandedAll
                expanded =
                    if (expandedAll) d.rows.mapNotNull { it.categoryId }.toSet() else emptySet()
            })
            d.rows.forEach { row ->
                val open = row.categoryId in expanded
                BudgetRowView(row, ref.mainCurrency, today, onClick = {
                    nav.navigate(
                        BudgetDetailKey(
                            row.budgetId ?: "virtual_${row.categoryId}",
                            period.label.toString(),
                        ),
                    )
                }, expandable = row.children.isNotEmpty(), expanded = open, onExpand = {
                    expanded =
                        if (open) expanded - row.categoryId!! else expanded + row.categoryId!!
                })
                if (open) {
                    row.children.forEach { child ->
                        BudgetRowView(child, ref.mainCurrency, today, small = true, onClick = {
                            child.budgetId?.let {
                                nav.navigate(BudgetDetailKey(it, period.label.toString()))
                            }
                        })
                    }
                }
            }
        }
    }
}

/** One budget row: progress bar with rate, today line, used and remaining / excess. */
@Composable
fun BudgetRowView(
    row: BudgetRow,
    main: Currency,
    today: Float?,
    onClick: () -> Unit,
    small: Boolean = false,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onExpand: () -> Unit = {},
) {
    val over = row.used > row.amount
    val colors = LocalNexoColors.current
    val money = LocalMoneyColors.current
    val barColor = if (over) money.expenseFill else money.incomeFill
    val usedColor = if (over) money.expense else money.income
    Column(
        Modifier.fillMaxWidth().clickable(
            onClick = onClick,
        ).padding(start = if (small) 16.dp else 0.dp, top = 10.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (row.isTotal) stringResource(R.string.total_budget) else row.name,
                style = if (small) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                fontWeight = if (row.isTotal) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                AmountFormatter.format(row.amount, main),
                style = MaterialTheme.typography.bodySmall,
            )
            if (expandable) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null,
                    Modifier.clickable(onClick = onExpand).padding(start = 4.dp),
                )
            }
        }
        // A thin meter: the fill is what has been used, the tick is where today falls in the month.
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(if (small) 6.dp else 8.dp)) {
            Box(Modifier.matchParentSize().clip(CircleShape).background(colors.raised))
            if (row.fill > 0f) {
                Box(
                    Modifier.fillMaxWidth(
                        row.fill,
                    ).fillMaxHeight().clip(CircleShape).background(barColor),
                )
            }
            if (today != null) {
                Canvas(Modifier.matchParentSize()) {
                    val x = size.width * today
                    val reach = 4.dp.toPx()
                    drawLine(
                        colors.surface,
                        Offset(x, -reach),
                        Offset(x, size.height + reach),
                        strokeWidth = 5.dp.toPx(),
                    )
                    drawLine(
                        colors.text,
                        Offset(x, -reach),
                        Offset(x, size.height + reach),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
        }
        Row {
            Text(
                if (row.used < 0) {
                    "(${AmountFormatter.format(-row.used, main)})"
                } else {
                    AmountFormatter.format(row.used, main)
                },
                color = usedColor,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                " · " + (
                    row.rate?.let {
                        "$it%"
                    } ?: "-"
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (over) {
                    stringResource(R.string.excess) + " " +
                        AmountFormatter.format(row.remaining, main)
                } else {
                    AmountFormatter.format(row.remaining, main)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (over) money.expense else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
