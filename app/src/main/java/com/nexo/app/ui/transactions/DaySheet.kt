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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.money.AmountFormatter
import com.nexo.app.domain.RefData
import com.nexo.app.ui.components.EmptyState
import com.nexo.app.ui.components.Formats
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.MemoEditorKey
import com.nexo.app.ui.theme.LocalIsDark
import com.nexo.app.ui.theme.LocalMoneyColors
import com.nexo.app.ui.theme.NexoPalette
import java.time.LocalDate

/** Calendar day sheet (UI-012). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(
    date: LocalDate,
    data: HomeData,
    ref: RefData,
    showMemos: Boolean,
    onShowMemos: (Boolean) -> Unit,
    onDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val money = LocalMoneyColors.current
    val dark = LocalIsDark.current
    val factory = RowFactory(context, ref)
    val rows = data.gridRows.filter { it.date == date.toString() }
    val carry = if (data.period?.start == date && data.carryOver != 0.0) date to data.carryOver else null
    val group = factory.dayGroups(rows, data.ledger, carryOver = carry).firstOrNull()
    val memos = data.memos.filter { it.pinned }.sortedByDescending { it.pinnedAt } + data.memos.filter { !it.pinned && it.date == date.toString() }
    val panel = showMemos || (group == null && memos.any { !it.pinned })
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onDate(date.minusDays(1)) }) { Icon(Icons.Filled.ChevronLeft, stringResource(R.string.action_previous)) }
                    Text("%02d".format(date.dayOfMonth), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.background(weekdayColor(date.dayOfWeek), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(Formats.weekdayShort(date), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(Formats.yearMonthDots(date), style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { onDate(date.plusDays(1)) }) { Icon(Icons.Filled.ChevronRight, stringResource(R.string.action_next)) }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onShowMemos(!panel) }) {
                        Icon(Icons.AutoMirrored.Outlined.StickyNote2, stringResource(R.string.memos), tint = if (panel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                    Text(AmountFormatter.format(group?.income ?: 0.0, ref.mainCurrency), color = money.income)
                    Spacer(Modifier.width(16.dp))
                    Text(AmountFormatter.format(group?.expense ?: 0.0, ref.mainCurrency), color = money.expense)
                }
                if (panel) {
                    if (memos.isEmpty()) EmptyState(stringResource(R.string.no_memos))
                    LazyColumn(Modifier.padding(12.dp)) {
                        items(memos, key = { it.id }) { m ->
                            Card(
                                onClick = { nav.navigate(MemoEditorKey(memoId = m.id)) },
                                colors = CardDefaults.cardColors(containerColor = NexoPalette.memo(m.color, dark)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    if (m.title.isNotBlank()) Text(m.title, fontWeight = FontWeight.Bold)
                                    if (m.content.isNotBlank()) Text(m.content.trimEnd(), maxLines = 4, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                } else if (group == null) {
                    EmptyState(modifier = Modifier.clickable { onShowMemos(true) })
                } else {
                    LazyColumn {
                        items(group.rows, key = { it.id }) { row ->
                            TxRow(row, selected = false, onClick = { if (!row.isCarryOver) nav.navigate(EntryKey(mode = EntryMode.EDIT, txId = row.id)) }, onLongClick = null)
                        }
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(onClick = { onShowMemos(true); nav.navigate(MemoEditorKey(date = date.toString())) }) {
                    Icon(Icons.AutoMirrored.Outlined.StickyNote2, stringResource(R.string.new_memo))
                }
                Spacer(Modifier.padding(6.dp))
                FloatingActionButton(onClick = { onShowMemos(false); nav.navigate(EntryKey(date = date.toString())) }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Filled.Add, stringResource(R.string.add_transaction), tint = Color.White)
                }
            }
        }
    }
}
