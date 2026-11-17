package com.nexo.app.ui.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexo.app.core.common.period.MonthPeriod
import com.nexo.app.core.model.Currency
import com.nexo.app.ui.designsystem.LocalClock
import com.nexo.app.ui.designsystem.component.AmountText
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.FlowAmount
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.GroupCard
import com.nexo.app.ui.designsystem.component.rememberHaptics
import com.nexo.app.ui.designsystem.component.shrinkToFit
import com.nexo.app.ui.designsystem.theme.LocalMoneyColors
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.NexoShapes
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Space
import java.time.LocalDate

// One card per month, opening to the weeks inside it.
@Composable
internal fun MonthlyContent(
    data: HomeData,
    main: Currency,
    selected: LocalDate,
    bottom: Dp,
    onWeek: (MonthPeriod) -> Unit,
) {
    val clock = LocalClock.current
    val haptics = rememberHaptics()
    var expanded by rememberSaveable(data.year) {
        mutableStateOf(data.months.firstOrNull { selected in it.period.range }?.period?.key ?: -1)
    }
    if (data.months.isEmpty()) {
        EmptyState()
        return
    }
    val today = LocalDate.now(clock)
    LazyColumn(contentPadding = PaddingValues(top = Space.xs, bottom = bottom)) {
        items(data.months, key = { it.period.key }) { m ->
            MonthCard(
                month = m,
                main = main,
                open = expanded == m.period.key,
                showRange = today in m.period.range || m.period.label.monthValue == 12,
                onToggle = {
                    haptics.tick()
                    expanded =
                        if (expanded == m.period.key) -1 else m.period.key
                },
                onWeek = { onWeek(m.period) },
                modifier = Modifier.padding(horizontal = Space.l, vertical = Space.xs),
            )
        }
    }
}

@Composable
private fun MonthCard(
    month: MonthRow,
    main: Currency,
    open: Boolean,
    showRange: Boolean,
    onToggle: () -> Unit,
    onWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNexoColors.current
    val money = LocalMoneyColors.current
    val interactions = remember { MutableInteractionSource() }
    val turn by animateFloatAsState(if (open) 180f else 0f, NexoMotion.snappy(), label = "chevron")
    GroupCard(modifier, interactions = interactions) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(interactionSource = interactions, indication = null, onClick = onToggle)
                .padding(start = Space.l, end = Space.m, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    Formats.monthShort(month.period.label.monthValue),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                )
                if (showRange) {
                    Text(
                        Formats.range(month.period.start, month.period.end),
                        style = NexoType.Meta,
                        color = colors.textTertiary,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                FlowAmount(month.income, main, money.income)
                FlowAmount(month.expense, main, money.expense)
                AmountText(
                    month.income - month.expense,
                    main,
                    style = NexoType.AmountSmall,
                    color = colors.textSecondary,
                )
            }
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                null,
                tint = colors.textTertiary,
                modifier = Modifier.padding(start = Space.xs).size(20.dp).graphicsLayer {
                    rotationZ = turn
                },
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(NexoMotion.snappy()) + fadeIn(NexoMotion.enter()),
            exit = shrinkVertically(NexoMotion.snappy()) + fadeOut(NexoMotion.exit()),
        ) {
            Column(Modifier.padding(start = Space.s, end = Space.s, bottom = Space.s)) {
                month.weeks.forEach { w -> WeekLine(w, main, onWeek) }
            }
        }
    }
}

@Composable
private fun WeekLine(week: WeekRow, main: Currency, onClick: () -> Unit) {
    val colors = LocalNexoColors.current
    val money = LocalMoneyColors.current
    val net = week.income - week.expense
    Row(
        Modifier
            .fillMaxWidth()
            .clip(NexoShapes.control)
            .background(if (week.containsToday) colors.primarySoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.s, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            Formats.range(week.range.start, week.range.end),
            style = NexoType.Meta,
            color = if (week.containsToday) colors.primaryText else colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.width(88.dp),
        )
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            FlowAmount(
                week.income,
                main,
                money.income,
                Modifier.shrinkToFit(),
                NexoType.AmountSmall,
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            FlowAmount(
                week.expense,
                main,
                money.expense,
                Modifier.shrinkToFit(),
                NexoType.AmountSmall,
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            FlowAmount(net, main, colors.text, Modifier.shrinkToFit(), NexoType.AmountSmall)
        }
    }
}
