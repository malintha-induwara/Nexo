package com.nexo.app.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.model.Currency
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.shrinkToFit
import com.nexo.app.ui.designsystem.theme.LocalMoneyColors
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.MoneyColors
import com.nexo.app.ui.designsystem.theme.NexoPalette
import com.nexo.app.ui.designsystem.theme.NexoShapes
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Space
import com.nexo.app.ui.transactionlist.MemoDot
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

// No ruled lines: only days with transactions or memos get a tile, so activity stands out.
@Composable
internal fun CalendarContent(
    data: HomeData,
    main: Currency,
    weekStart: DayOfWeek,
    bottom: Dp,
    onDay: (LocalDate) -> Unit,
) {
    val money = LocalMoneyColors.current
    val colors = LocalNexoColors.current
    Column(Modifier.fillMaxSize().padding(horizontal = Space.s)) {
        Row(Modifier.fillMaxWidth().padding(top = Space.s, bottom = Space.s)) {
            (0 until 7).forEach { i ->
                val d = weekStart.plus(i.toLong())
                Text(
                    Formats.weekdayShort(d).uppercase(Locale.getDefault()),
                    Modifier.weight(1f),
                    color = when (d) {
                        DayOfWeek.SUNDAY -> NexoPalette.Sunday
                        DayOfWeek.SATURDAY -> NexoPalette.Saturday
                        else -> colors.textTertiary
                    },
                    style = NexoType.Caps,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        val weeks = data.cells.chunked(7)
        Column(
            Modifier.fillMaxSize().padding(bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(CalendarGap),
        ) {
            weeks.forEach { week ->
                Row(
                    Modifier.fillMaxWidth().weight(1f).heightIn(min = 64.dp),
                    horizontalArrangement = Arrangement.spacedBy(CalendarGap),
                ) {
                    week.forEach { cell ->
                        CalendarDay(cell, main, money, Modifier.weight(1f).fillMaxHeight()) {
                            onDay(cell.date)
                        }
                    }
                }
            }
        }
    }
}

private val CalendarGap = 3.dp

@Composable
private fun CalendarDay(
    cell: CalendarCell,
    main: Currency,
    money: MoneyColors,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalNexoColors.current
    val active = cell.hasTx || cell.memoColors.isNotEmpty()
    val dim = if (cell.inPeriod) 1f else 0.45f
    // Long figures step the type down; shrinkToFit below catches anything that still overflows.
    val longest = listOf(cell.income, cell.expense, cell.income - cell.expense).maxOf {
        AmountFormatter.number(it, main.decimals).length
    }
    val size = if (longest >= 9) ((27 - longest) / 2f).coerceAtLeast(6f) else 9.5f
    val figure = NexoType.AmountSmall.copy(
        fontSize = size.sp,
        lineHeight = (size + 3.5f).sp,
        fontWeight = FontWeight.SemiBold,
    )
    Column(
        modifier
            .clip(NexoShapes.tile)
            .background(if (active) colors.surface.copy(alpha = dim) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val label = if (cell.date.dayOfMonth == 1) {
            Formats.monthDay(cell.date)
        } else {
            cell.date.dayOfMonth.toString()
        }
        Box(
            Modifier
                .height(24.dp)
                .widthIn(min = 24.dp)
                .clip(CircleShape)
                .then(if (cell.isToday) Modifier.background(colors.primary) else Modifier)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                color = when {
                    cell.isToday -> colors.onPrimary
                    !cell.inPeriod -> colors.textTertiary.copy(alpha = 0.7f)
                    cell.date.dayOfWeek == DayOfWeek.SUNDAY -> NexoPalette.Sunday
                    else -> colors.text
                },
            )
        }
        if (cell.memoColors.isNotEmpty()) {
            Row(
                Modifier.padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                cell.memoColors.take(4).forEach { MemoDot(it, 5) }
                if (cell.memoColors.size > 4) {
                    Text(
                        "+${cell.memoColors.size - 4}",
                        fontSize = 8.sp,
                        color = colors.textTertiary,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (cell.income != 0.0) {
            Text(
                AmountFormatter.number(cell.income, main.decimals),
                Modifier.shrinkToFit(),
                style = figure,
                color = money.income.copy(alpha = dim),
                maxLines = 1,
            )
        }
        if (cell.expense != 0.0) {
            Text(
                AmountFormatter.number(cell.expense, main.decimals),
                Modifier.shrinkToFit(),
                style = figure,
                color = money.expense.copy(alpha = dim),
                maxLines = 1,
            )
        }
        if ((cell.income != 0.0 && cell.expense != 0.0) ||
            (cell.hasTx && cell.income == 0.0 && cell.expense == 0.0)
        ) {
            Text(
                AmountFormatter.number(cell.income - cell.expense, main.decimals),
                Modifier.shrinkToFit(),
                style = figure,
                color = colors.textSecondary.copy(alpha = dim),
                maxLines = 1,
            )
        }
    }
}
