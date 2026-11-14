package com.nexo.app.ui.transactions

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountParts
import com.nexo.app.core.model.RefData
import com.nexo.app.ui.designsystem.LocalClock
import com.nexo.app.ui.designsystem.component.AmountText
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.InlineBottomSheet
import com.nexo.app.ui.designsystem.component.NavMotion
import com.nexo.app.ui.designsystem.component.PeriodNavigator
import com.nexo.app.ui.designsystem.component.RoundIconButton
import com.nexo.app.ui.designsystem.component.pressScale
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.designsystem.theme.LocalIsDark
import com.nexo.app.ui.designsystem.theme.LocalMoneyColors
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoPalette
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Space
import java.time.LocalDate
import java.util.Locale

/**
 * Calendar day sheet. [date] null hides it. The sheet is part of the Transactions
 * screen, so opening an entry from it and coming back animates as one screen.
 */
@Composable
fun DaySheet(
    date: LocalDate?,
    data: HomeData,
    ref: RefData,
    showMemos: Boolean,
    onShowMemos: (Boolean) -> Unit,
    onDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    onOpenEntry: (txId: String) -> Unit,
    onAddEntry: (LocalDate) -> Unit,
    onOpenMemo: (memoId: String) -> Unit,
    onNewMemo: (LocalDate) -> Unit,
    highlightId: String? = null,
) {
    val clock = LocalClock.current
    val money = LocalMoneyColors.current
    val colors = LocalNexoColors.current
    val dark = LocalIsDark.current
    // Keep the last date so the content stays put while the sheet slides away.
    val last = remember { arrayOfNulls<LocalDate>(1) }
    if (date != null) last[0] = date

    InlineBottomSheet(visible = date != null, onDismiss = onDismiss) {
        val shown = last[0] ?: return@InlineBottomSheet
        val factory = remember(ref, clock) { TransactionRowMapper(ref, clock.zone) }
        fun groupOf(d: LocalDate): DayGroup? {
            val rows = data.gridRows.filter { it.date == d.toString() }
            val carry = if (data.period?.start == d && data.carryOver != 0.0) {
                d to data.carryOver
            } else {
                null
            }
            return factory.dayGroups(rows, data.ledger, carryOver = carry).firstOrNull()
        }
        fun memosOf(d: LocalDate) =
            data.memos.filter { it.pinned }.sortedByDescending { it.pinnedAt } +
                data.memos.filter { !it.pinned && it.date == d.toString() }
        val group = groupOf(shown)
        val panel = showMemos || (group == null && memosOf(shown).any { !it.pinned })

        Row(
            Modifier.fillMaxWidth().padding(start = Space.xs, end = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PeriodNavigator(
                title = Formats.weekdayMonthDay(shown),
                onPrevious = { onDate(shown.minusDays(1)) },
                onNext = { onDate(shown.plusDays(1)) },
                color = colors.text,
            )
            Spacer(Modifier.weight(1f))
            RoundIconButton(
                Icons.AutoMirrored.Outlined.StickyNote2,
                stringResource(R.string.memos),
                {
                    onShowMemos(!panel)
                },
                tint = if (panel) colors.text else colors.textSecondary,
                container = if (panel) colors.raisedHigh else Color.Transparent,
            )
            Spacer(Modifier.width(Space.xs))
            RoundIconButton(Icons.Rounded.Close, stringResource(R.string.action_close), onDismiss)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                (
                    if (shown == LocalDate.now(clock)) {
                        stringResource(R.string.today)
                    } else {
                        shown.year.toString()
                    }
                    ).uppercase(Locale.getDefault()),
                style = NexoType.Caps,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            AmountText(
                group?.income ?: 0.0,
                ref.mainCurrency,
                style = NexoType.AmountSmall,
                color = money.income,
                sign = AmountParts.Sign.ALWAYS,
            )
            Spacer(Modifier.width(Space.m))
            AmountText(
                -(group?.expense ?: 0.0),
                ref.mainCurrency,
                style = NexoType.AmountSmall,
                color = money.expense,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = shown to panel,
                transitionSpec = {
                    NavMotion.lateral(
                        this,
                        targetState.first.compareTo(initialState.first).coerceIn(-1, 1),
                    )
                },
                modifier = Modifier.fillMaxSize(),
                label = "daySheet",
            ) { (day, memoPanel) ->
                val dayGroup = groupOf(day)
                val memos = memosOf(day)
                when {
                    memoPanel -> {
                        if (memos.isEmpty()) EmptyState(stringResource(R.string.no_memos))
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = Space.l,
                                end = Space.l,
                                top = Space.s,
                                bottom = 140.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(Space.s),
                        ) {
                            items(memos, key = { it.id }) { m ->
                                Card(
                                    onClick = { onOpenMemo(m.id) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = NexoPalette.memo(m.color, dark),
                                    ),
                                    modifier = Modifier.animateItem().fillMaxWidth(),
                                ) {
                                    Column(Modifier.padding(Space.m)) {
                                        if (m.title.isNotBlank()) {
                                            Text(m.title, fontWeight = FontWeight.Bold)
                                        }
                                        if (m.content.isNotBlank()) {
                                            Text(
                                                m.content.trimEnd(),
                                                maxLines = 4,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    dayGroup == null -> EmptyState(
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onShowMemos(true) },
                    )

                    else -> LazyColumn(
                        contentPadding = PaddingValues(top = Space.xs, bottom = 140.dp),
                    ) {
                        items(dayGroup.rows, key = { it.id }) { row ->
                            TxRow(
                                row,
                                selected = false,
                                onClick = {
                                    if (!row.isCarryOver) onOpenEntry(row.id)
                                },
                                onLongClick = null,
                                modifier = Modifier.animateItem(),
                                highlight = row.id == highlightId,
                            )
                        }
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomEnd).padding(Space.gutter),
                horizontalAlignment = Alignment.End,
            ) {
                RoundIconButton(
                    Icons.AutoMirrored.Outlined.StickyNote2,
                    stringResource(R.string.new_memo),
                    onClick = {
                        onShowMemos(true)
                        onNewMemo(shown)
                    },
                    size = 44.dp,
                    container = colors.raisedHigh,
                )
                Spacer(Modifier.height(Space.m))
                val interactions = remember { MutableInteractionSource() }
                val addLabel = stringResource(R.string.add_transaction)
                Box(
                    Modifier
                        .pressScale(interactions, 0.9f)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(colors.primary)
                        .clickable(
                            interactionSource = interactions,
                            indication = null,
                            role = Role.Button,
                        ) {
                            onShowMemos(false)
                            onAddEntry(shown)
                        }
                        .semantics { contentDescription = addLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun DaySheetPreview() = NexoPreview {
    DaySheet(
        date = PreviewData.today,
        data = previewTransactionsState.data,
        ref = PreviewData.ref,
        showMemos = false,
        onShowMemos = {},
        onDate = {},
        onDismiss = {},
        onOpenEntry = {},
        onAddEntry = {},
        onOpenMemo = {},
        onNewMemo = {},
    )
}
