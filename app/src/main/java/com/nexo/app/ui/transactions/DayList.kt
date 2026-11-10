package com.nexo.app.ui.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.database.entity.CurrencyEntity
import com.nexo.app.core.database.entity.MemoEntity
import com.nexo.app.core.model.MemoColor
import com.nexo.app.core.money.AmountFormatter
import com.nexo.app.ui.components.EmptyState
import com.nexo.app.ui.components.Formats
import com.nexo.app.ui.theme.LocalMoneyColors
import com.nexo.app.ui.theme.NexoPalette
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun weekdayColor(d: DayOfWeek): Color = when (d) {
    DayOfWeek.SUNDAY -> NexoPalette.Sunday
    DayOfWeek.SATURDAY -> NexoPalette.Saturday
    else -> MaterialTheme.colorScheme.outline
}

@Composable
fun DayHeader(group: DayGroup, main: CurrencyEntity, onClick: (() -> Unit)?) {
    val money = LocalMoneyColors.current
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("%02d".format(group.date.dayOfMonth), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.background(weekdayColor(group.date.dayOfWeek), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(Formats.weekdayShort(group.date), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(8.dp))
        Text(Formats.yearMonthDots(group.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(AmountFormatter.format(group.income, main), color = money.income, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Text(AmountFormatter.format(group.expense, main), color = money.expense, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TxRow(row: TxRowUi, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val money = LocalMoneyColors.current
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.width(84.dp)) {
            Text(row.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            row.subCategory?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (row.titleIsNote) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val second = buildList {
                row.time?.let { add(it) }
                if (row.titleIsNote && row.accountText.isNotEmpty()) add(row.accountText)
                row.tags?.let { add(it) }
            }.joinToString(", ")
            if (second.isNotEmpty()) Text(second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            row.descriptionLine?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (row.hasPhoto) Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp).padding(end = 2.dp))
        Text(row.amountText, color = money.of(row.flow), style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.widthIn(max = 160.dp))
    }
}

@Composable
fun MemoDot(color: MemoColor, size: Int = 8) {
    val c = NexoPalette.memoDot(color)
    Box(
        Modifier.size(size.dp).background(c, CircleShape)
            .then(if (color == MemoColor.WHITE) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape) else Modifier),
    )
}

@Composable
fun MemoStrip(memos: List<MemoEntity>, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) {
        memos.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                MemoDot(m.color)
                Spacer(Modifier.width(8.dp))
                Text(
                    m.title.ifBlank { m.content.replace('\n', ' ').trim() },
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun RepeatPreviewRow(p: RepeatPreview, main: CurrencyEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Repeat, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.repeat), style = MaterialTheme.typography.bodyLarge)
            Text(p.dates.joinToString(", ") { "${it.monthValue}/${it.dayOfMonth}" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p.names.isNotEmpty()) Text(p.names.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(AmountFormatter.format(p.amount, main), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Day-grouped list used on every screen that lists transactions. */
@Composable
fun DayGroupList(
    groups: List<DayGroup>,
    main: CurrencyEntity,
    selection: Set<String>,
    onRowClick: (TxRowUi) -> Unit,
    onRowLongClick: ((TxRowUi) -> Unit)?,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    onDayClick: ((LocalDate) -> Unit)? = null,
    onMemoClick: ((LocalDate) -> Unit)? = null,
    header: (LazyListScope.() -> Unit)? = null,
    rowContent: (@Composable (TxRowUi) -> Unit)? = null,
    emptyText: String? = null,
    bottomPadding: Int = 96,
) {
    LazyColumn(modifier, state = state, contentPadding = PaddingValues(bottom = bottomPadding.dp)) {
        header?.invoke(this)
        if (groups.isEmpty()) {
            item { EmptyState(emptyText ?: stringResource(R.string.no_data)) }
        }
        groups.forEach { g ->
            item(key = "h_${g.date}") {
                Column {
                    Spacer(Modifier.height(8.dp))
                    DayHeader(g, main, onDayClick?.let { { it(g.date) } })
                    if (g.memos.isNotEmpty()) MemoStrip(g.memos) { onMemoClick?.invoke(g.date) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            items(g.rows, key = { "r_${g.date}_${it.id}" }) { row ->
                if (rowContent != null) rowContent(row)
                else TxRow(
                    row, selected = row.id in selection,
                    onClick = { if (!row.isCarryOver) onRowClick(row) },
                    onLongClick = if (row.isCarryOver || onRowLongClick == null) null else ({ onRowLongClick(row) }),
                )
            }
        }
    }
}
