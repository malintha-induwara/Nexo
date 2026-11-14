package com.nexo.app.ui.transactions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountParts
import com.nexo.app.core.model.CashFlow
import com.nexo.app.core.model.Currency
import com.nexo.app.core.model.Memo
import com.nexo.app.core.model.MemoColor
import com.nexo.app.ui.designsystem.LocalClock
import com.nexo.app.ui.designsystem.asString
import com.nexo.app.ui.designsystem.component.AmountText
import com.nexo.app.ui.designsystem.component.CategoryTile
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.IconTile
import com.nexo.app.ui.designsystem.theme.CategoryHues
import com.nexo.app.ui.designsystem.theme.LocalMoneyColors
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.NexoPalette
import com.nexo.app.ui.designsystem.theme.NexoShapes
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Sizes
import com.nexo.app.ui.designsystem.theme.Space
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

@Composable
fun weekdayColor(d: DayOfWeek): Color = when (d) {
    DayOfWeek.SUNDAY -> NexoPalette.Sunday
    DayOfWeek.SATURDAY -> NexoPalette.Saturday
    else -> MaterialTheme.colorScheme.outline
}

/** "Today · Tue, Nov 10", "Yesterday · …" or "Fri, Nov 6". */
@Composable
fun dayLabel(date: LocalDate): String {
    val clock = LocalClock.current
    val today = LocalDate.now(clock)
    val base = Formats.weekdayMonthDay(date)
    return when (date) {
        today -> stringResource(R.string.today) + " · " + base
        today.minusDays(1) -> stringResource(R.string.yesterday) + " · " + base
        else -> base
    }
}

/** Upper-case day label with the day's income and expenses on the right. Tapping it adds an entry on that day. */
@Composable
fun DayHeader(
    group: DayGroup,
    main: Currency,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val clock = LocalClock.current
    val money = LocalMoneyColors.current
    val colors = LocalNexoColors.current
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = Space.gutter, end = Space.gutter, top = Space.xl, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            dayLabel(group.date).uppercase(Locale.getDefault()),
            style = NexoType.Caps,
            color = if (group.date == LocalDate.now(clock)) colors.text else colors.textTertiary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (group.income != 0.0) {
            AmountText(
                group.income,
                main,
                style = NexoType.AmountSmall,
                color = money.income,
                sign = AmountParts.Sign.ALWAYS,
            )
        }
        if (group.income != 0.0 && group.expense != 0.0) Spacer(Modifier.width(Space.m))
        if (group.expense != 0.0) {
            AmountText(-group.expense, main, style = NexoType.AmountSmall, color = money.expense)
        }
    }
}

/** The tile at the start of a transaction row. */
@Composable
fun RowTile(row: TxRowUi) {
    val colors = LocalNexoColors.current
    val v = row.visual
    val kind = row.tx?.kind
    when {
        row.isCarryOver -> IconTile(Icons.Rounded.History, colors.textSecondary)

        v != null -> CategoryTile(v.emoji, v.monogram, CategoryHues.of(v.colorIndex, colors.isDark))

        kind != null && kind.isTransfer -> IconTile(Icons.Rounded.SwapHoriz, colors.textSecondary)

        kind != null && (kind.isBalance || row.tx?.isBalanceAdjustment == true) -> IconTile(
            Icons.Rounded.Tune,
            colors.textSecondary,
        )

        else -> CategoryTile(null, "?", colors.textSecondary)
    }
}

/**
 * One transaction: tile, title (the note, or the category when there is none), a meta line
 * (category, account, time, tags) and the amount, signed and coloured by direction.
 * [highlight] briefly washes the row, used when it has just been saved.
 * [runningBalance] is the account's balance after this transaction, shown in brackets under the amount.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TxRow(
    row: TxRowUi,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    runningBalance: String? = null,
) {
    val money = LocalMoneyColors.current
    val colors = LocalNexoColors.current
    val background by animateColorAsState(
        if (selected) colors.raised else Color.Transparent,
        NexoMotion.standard(),
        label = "rowSelected",
    )
    val flash = remember { Animatable(0f) }
    LaunchedEffect(highlight) {
        if (highlight) {
            flash.animateTo(1f, NexoMotion.enter(NexoMotion.SHORT))
            flash.animateTo(0f, tween(900, delayMillis = 350))
        }
    }
    val category = row.category.asString()
    val accountText = row.accountText.asString()
    val title = when {
        row.isCarryOver || row.titleIsNote -> row.title.asString()
        else -> row.subCategory ?: category
    }
    val meta = buildList {
        if (!row.isCarryOver) {
            when {
                row.titleIsNote -> add(row.subCategory?.let { "$category › $it" } ?: category)
                row.subCategory != null -> add(category)
            }
            if (accountText.isNotEmpty()) add(accountText)
        }
        row.time?.let { add(it) }
        row.tags?.let { t -> add(t.split(", ").joinToString(" ") { "#$it" }) }
    }.joinToString(" · ")

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.row)
            .drawBehind {
                drawRect(background)
                if (flash.value > 0f) drawRect(colors.primary.copy(alpha = 0.10f * flash.value))
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Space.gutter, vertical = Space.s + Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                (
                    scaleIn(NexoMotion.snappy(), initialScale = 0.6f) +
                        fadeIn(NexoMotion.enter(NexoMotion.SHORT))
                    ) togetherWith
                    (scaleOut(NexoMotion.exit(), targetScale = 0.6f) + fadeOut(NexoMotion.exit()))
            },
            label = "rowTile",
        ) { on ->
            if (on) {
                Box(
                    Modifier.size(Sizes.tile).clip(NexoShapes.tile).background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                RowTile(row)
            }
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = NexoType.RowTitle,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = NexoType.Meta,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            row.descriptionLine?.let {
                Text(
                    it,
                    style = NexoType.Meta,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(Space.m))
        if (row.hasPhoto) {
            Icon(
                Icons.Outlined.Image,
                null,
                tint = colors.textTertiary,
                modifier = Modifier.padding(end = Space.s).size(16.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            when (row.flow) {
                CashFlow.INCOME -> AmountText(
                    row.amount,
                    row.currency,
                    color = money.income,
                    sign = AmountParts.Sign.ALWAYS,
                )

                CashFlow.EXPENSE -> AmountText(-row.amount, row.currency, color = money.expense)

                CashFlow.NEUTRAL -> AmountText(
                    row.amount,
                    row.currency,
                    color = colors.text,
                    sign = AmountParts.Sign.NONE,
                )
            }
            if (runningBalance != null) {
                Text(
                    "($runningBalance)",
                    style = NexoType.Meta,
                    color = colors.textTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun MemoDot(color: MemoColor, size: Int = 8) {
    val c = NexoPalette.memoDot(color)
    Box(
        Modifier.size(size.dp).background(c, CircleShape)
            .then(
                if (color == MemoColor.WHITE) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                } else {
                    Modifier
                },
            ),
    )
}

@Composable
fun MemoStrip(memos: List<Memo>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalNexoColors.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.l)
            .clip(NexoShapes.control)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.xs, vertical = Space.xs),
    ) {
        memos.forEach { m ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 3.dp),
            ) {
                MemoDot(m.color)
                Spacer(Modifier.width(Space.s))
                Text(
                    m.title.ifBlank { m.content.replace('\n', ' ').trim() },
                    style = NexoType.Meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
fun RepeatPreviewRow(
    p: RepeatPreview,
    main: Currency,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNexoColors.current
    Row(
        modifier.fillMaxWidth().heightIn(
            min = Sizes.row,
        ).clickable(onClick = onClick).padding(horizontal = Space.gutter, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(Icons.Rounded.Repeat, colors.textSecondary)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.repeat), style = NexoType.RowTitle, color = colors.text)
            val detail = buildList {
                add(p.dates.joinToString(", ") { "${it.monthValue}/${it.dayOfMonth}" })
                if (p.names.isNotEmpty()) add(p.names.joinToString(", "))
            }.joinToString(" · ")
            Text(
                detail,
                style = NexoType.Meta,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.m))
        AmountText(p.amount, main, color = colors.textSecondary, sign = AmountParts.Sign.NONE)
    }
}

/** Day-grouped list used on every screen that lists transactions. */
@Composable
fun DayGroupList(
    groups: List<DayGroup>,
    main: Currency,
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
    highlightId: String? = null,
) {
    LazyColumn(
        modifier,
        state = state,
        contentPadding = PaddingValues(bottom = bottomPadding.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        header?.invoke(this)
        if (groups.isEmpty()) {
            item(key = "empty") {
                EmptyState(emptyText ?: stringResource(R.string.no_data), Modifier.animateItem())
            }
        }
        groups.forEach { g ->
            item(key = "h_${g.date}", contentType = "day") {
                Column(Modifier.animateItem()) {
                    DayHeader(g, main, onDayClick?.let { { it(g.date) } })
                    if (g.memos.isNotEmpty()) {
                        MemoStrip(g.memos, onClick = {
                            onMemoClick?.invoke(g.date)
                        })
                    }
                }
            }
            items(g.rows, key = { "r_${g.date}_${it.id}" }, contentType = { "row" }) { row ->
                Box(Modifier.animateItem()) {
                    if (rowContent != null) {
                        rowContent(row)
                    } else {
                        TxRow(
                            row,
                            selected = row.id in selection,
                            onClick = { if (!row.isCarryOver) onRowClick(row) },
                            onLongClick = if (row.isCarryOver || onRowLongClick == null) {
                                null
                            } else {
                                ({ onRowLongClick(row) })
                            },
                            highlight = row.id == highlightId,
                        )
                    }
                }
            }
        }
        item(key = "end") { Spacer(Modifier.height(Space.s)) }
    }
}
