package com.nexo.app.ui.transactions

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.nexo.app.core.model.Currency
import com.nexo.app.ui.designsystem.component.rememberHaptics
import com.nexo.app.ui.transactionlist.DayGroupList
import java.time.LocalDate

@Composable
internal fun DailyContent(
    data: HomeData,
    main: Currency,
    selection: Set<String>,
    onToggleSelect: (String) -> Unit,
    focusDate: LocalDate?,
    onFocused: () -> Unit,
    highlightId: String?,
    bottom: Dp,
    onMemoStrip: (LocalDate) -> Unit,
    onOpenEntry: (txId: String) -> Unit,
    onAddEntry: (LocalDate) -> Unit,
    onOpenRepeats: () -> Unit,
) {
    val haptics = rememberHaptics()
    val state = rememberLazyListState()
    // Scroll once this snapshot holds the day (it may arrive a frame after the period changes).
    LaunchedEffect(focusDate, data) {
        val date = focusDate ?: return@LaunchedEffect
        if (data.period?.range?.let { date in it } != true) return@LaunchedEffect
        var index = if (data.repeatPreview != null) 1 else 0
        for (g in data.days) {
            if (!g.date.isAfter(date)) break
            index += 1 + g.rows.size
        }
        runCatching { state.animateScrollToItem(index) }
        onFocused()
    }
    DayGroupList(
        groups = data.days,
        main = main,
        selection = selection,
        state = state,
        onRowClick = { row ->
            if (selection.isNotEmpty()) {
                haptics.tick()
                onToggleSelect(row.id)
            } else {
                onOpenEntry(row.id)
            }
        },
        onRowLongClick = { row ->
            haptics.longPress()
            onToggleSelect(row.id)
        },
        onDayClick = { d ->
            if (selection.isEmpty()) onAddEntry(d)
        },
        onMemoClick = onMemoStrip,
        header = data.repeatPreview?.let { p ->
            {
                item(key = "repeat_preview") {
                    RepeatPreviewRow(p, main, onOpenRepeats, Modifier.animateItem())
                }
            }
        },
        bottomPadding = bottom.value.toInt(),
        highlightId = highlightId,
    )
}
