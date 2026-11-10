package com.nexo.app.ui.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.database.entity.CurrencyEntity
import com.nexo.app.core.database.entity.MemoEntity
import com.nexo.app.core.model.SwipeMode
import com.nexo.app.core.money.AmountFormatter
import com.nexo.app.ui.components.Formats
import com.nexo.app.ui.components.MonthPickerDialog
import com.nexo.app.ui.components.PeriodNavigator
import com.nexo.app.ui.components.TotalsBar
import com.nexo.app.ui.navigation.BookmarksKey
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.MemoEditorKey
import com.nexo.app.ui.navigation.RepeatListKey
import com.nexo.app.ui.navigation.SearchKey
import com.nexo.app.ui.shell.LocalShell
import com.nexo.app.ui.theme.LocalHeaderColors
import com.nexo.app.ui.theme.LocalIsDark
import com.nexo.app.ui.theme.LocalMoneyColors
import com.nexo.app.ui.theme.NexoPalette
import java.time.LocalDate
import kotlin.math.abs

@Composable
fun TransactionsTab(todaySignal: Int, vm: TransactionsViewModel = hiltViewModel()) {
    val ref by vm.ref.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val filterOpen by vm.filterOpen.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val selectedDate by vm.selectedDate.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val shell = LocalShell.current
    val header = LocalHeaderColors.current
    val money = LocalMoneyColors.current
    val main = ref.mainCurrency
    val currentTab = tab ?: return

    var monthPicker by rememberSaveable { mutableStateOf(false) }
    var daySheet by rememberSaveable { mutableStateOf<String?>(null) }
    var daySheetMemos by rememberSaveable { mutableStateOf(false) }
    var bulkOpen by remember { mutableStateOf(false) }
    var bulkAction by remember { mutableStateOf<BulkAction?>(null) }
    var filterCollapsed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(todaySignal) { if (todaySignal > 0) vm.goToday() }
    val selecting = selection.isNotEmpty()
    DisposableEffect(filterOpen, selecting) {
        shell.bottomBarHidden = filterOpen || selecting
        onDispose { shell.bottomBarHidden = false }
    }
    BackHandler(enabled = monthPicker || selecting || filterOpen) {
        when {
            monthPicker -> monthPicker = false
            selecting -> vm.clearSelection()
            filterOpen -> vm.setFilterOpen(false)
        }
    }

    val tabs = if (ref.settings.startScreen == com.nexo.app.core.model.StartScreen.CALENDAR)
        listOf(SubTab.CALENDAR, SubTab.DAILY, SubTab.MONTHLY, SubTab.TOTAL, SubTab.NOTE)
    else listOf(SubTab.DAILY, SubTab.CALENDAR, SubTab.MONTHLY, SubTab.TOTAL, SubTab.NOTE)
    val visibleTabs = if (filterOpen) tabs.filter { it != SubTab.TOTAL && it != SubTab.NOTE } else tabs

    val title = if (currentTab == SubTab.MONTHLY) data.year.toString() else data.period?.let { Formats.monthYear(it.label) } ?: ""

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val dailyRows = data.days.flatMap { it.rows }
        if (selecting) {
            Box(Modifier.statusBarsPadding()) {
                SelectionBar(
                    count = selection.size,
                    total = dailyRows.count { !it.isCarryOver },
                    net = selectedNet(dailyRows, selection),
                    main = main,
                    onToggleAll = {
                        val all = dailyRows.filter { !it.isCarryOver }.map { it.id }.toSet()
                        vm.setSelection(if (selection.size >= all.size) emptySet() else all)
                    },
                    onClose = vm::clearSelection,
                    onAction = { bulkAction = it; bulkOpen = true },
                )
            }
        } else {
            Row(
                Modifier.fillMaxWidth().background(if (filterOpen) MaterialTheme.colorScheme.secondaryContainer else header.container).statusBarsPadding().height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PeriodNavigator(
                    title = title,
                    onPrevious = { vm.step(false) },
                    onNext = { vm.step(true) },
                    onTitleClick = if (currentTab == SubTab.MONTHLY) null else ({ monthPicker = true }),
                    color = if (filterOpen) MaterialTheme.colorScheme.onSecondaryContainer else header.content,
                )
                Spacer(Modifier.weight(1f))
                val tint = if (filterOpen) MaterialTheme.colorScheme.onSecondaryContainer else header.content
                if (!filterOpen) {
                    IconButton(onClick = { nav.navigate(BookmarksKey()) }) { Icon(Icons.Outlined.StarOutline, stringResource(R.string.bookmarks), tint = tint) }
                    IconButton(onClick = { nav.navigate(SearchKey()) }) { Icon(Icons.Outlined.Search, stringResource(R.string.search), tint = tint) }
                }
                IconButton(onClick = { vm.setFilterOpen(!filterOpen); filterCollapsed = false }) {
                    Icon(if (filterOpen) Icons.Filled.Close else Icons.Outlined.FilterList, stringResource(R.string.filter), tint = tint)
                }
            }
            androidx.compose.material3.PrimaryTabRow(
                selectedTabIndex = visibleTabs.indexOf(currentTab).coerceAtLeast(0),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                visibleTabs.forEach { t ->
                    Tab(selected = t == currentTab, onClick = { vm.setTab(t) }, text = { Text(stringResource(subTabLabel(t)), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium) }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (currentTab != SubTab.NOTE) {
            TotalsBar(data.totals.income, data.totals.expense, main, money.income, money.expense)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val swipe = if (selecting) SwipeMode.NONE else ref.settings.swipeMode
            val threshold = with(LocalDensity.current) { 90.dp.toPx() }
            val swipeModifier = if (swipe == SwipeMode.NONE) Modifier else Modifier.pointerInput(swipe, currentTab, visibleTabs) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onHorizontalDrag = { _, dx -> total += dx },
                    onDragEnd = {
                        if (abs(total) > threshold) {
                            val forward = total < 0
                            if (swipe == SwipeMode.CHANGE_DATE) vm.step(forward)
                            else {
                                val i = visibleTabs.indexOf(currentTab) + if (forward) 1 else -1
                                visibleTabs.getOrNull(i)?.let(vm::setTab)
                            }
                        }
                    },
                )
            }
            Box(Modifier.fillMaxSize().then(swipeModifier)) {
                when (currentTab) {
                    SubTab.DAILY -> DailyContent(data, main, selection, vm, onMemoStrip = { d -> daySheet = d.toString(); daySheetMemos = true })
                    SubTab.CALENDAR -> CalendarContent(data, main, ref.settings.periods.weekStartDay) { d -> daySheet = d.toString(); daySheetMemos = false }
                    SubTab.MONTHLY -> MonthlyContent(data, main, selectedDate) { vm.openMonth(it) }
                    SubTab.TOTAL -> TotalContent(data, main)
                    SubTab.NOTE -> NoteContent(data.memos, vm)
                }
            }
            if (!selecting) {
                Column(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val memoFab = currentTab == SubTab.NOTE || (ref.settings.memoButton && (currentTab == SubTab.DAILY || currentTab == SubTab.CALENDAR))
                    if (memoFab && currentTab != SubTab.NOTE) {
                        SmallFloatingActionButton(onClick = { nav.navigate(MemoEditorKey(date = selectedDate.toString())) }) {
                            Icon(Icons.AutoMirrored.Outlined.StickyNote2, stringResource(R.string.new_memo))
                        }
                    }
                    if (currentTab == SubTab.NOTE) {
                        FloatingActionButton(onClick = { nav.navigate(MemoEditorKey(date = selectedDate.toString())) }, containerColor = MaterialTheme.colorScheme.primary) {
                            Icon(Icons.AutoMirrored.Outlined.StickyNote2, stringResource(R.string.new_memo), tint = Color.White)
                        }
                    } else {
                        FloatingActionButton(onClick = { nav.navigate(EntryKey()) }, containerColor = MaterialTheme.colorScheme.primary) {
                            Icon(Icons.Filled.Add, stringResource(R.string.add_transaction), tint = Color.White)
                        }
                    }
                }
            }
            if (filterOpen) {
                FilterPanel(
                    ref = ref,
                    rows = data.periodRows,
                    criteria = filter,
                    collapsed = filterCollapsed,
                    onCollapsedChange = { filterCollapsed = it },
                    onChange = vm::setFilter,
                )
            }
        }
    }

    if (monthPicker) data.period?.let { p -> MonthPickerDialog(p.label, onPick = vm::pickMonth, onDismiss = { monthPicker = false }) }
    BulkDialogs(bulkAction, bulkOpen, selection.size, ref, vm.writer, selection, onDone = vm::clearSelection, onDismiss = { bulkOpen = false })
    daySheet?.let { d ->
        DaySheet(
            date = LocalDate.parse(d),
            data = data,
            ref = ref,
            showMemos = daySheetMemos,
            onShowMemos = { daySheetMemos = it },
            onDate = { daySheet = it.toString(); vm.select(it) },
            onDismiss = { vm.select(LocalDate.parse(d)); daySheet = null },
        )
    }
}

fun subTabLabel(t: SubTab): Int = when (t) {
    SubTab.DAILY -> R.string.daily
    SubTab.CALENDAR -> R.string.calendar
    SubTab.MONTHLY -> R.string.monthly
    SubTab.TOTAL -> R.string.total
    SubTab.NOTE -> R.string.note
}

@Composable
private fun DailyContent(data: HomeData, main: CurrencyEntity, selection: Set<String>, vm: TransactionsViewModel, onMemoStrip: (LocalDate) -> Unit) {
    val nav = LocalNavigator.current
    val state = rememberLazyListState()
    LaunchedEffect(Unit) {
        vm.focusEvents.collect { date ->
            var index = if (data.repeatPreview != null) 1 else 0
            for (g in vm.data.value.days) {
                if (!g.date.isAfter(date)) break
                index += 1 + g.rows.size
            }
            runCatching { state.scrollToItem(index) }
        }
    }
    DayGroupList(
        groups = data.days,
        main = main,
        selection = selection,
        state = state,
        onRowClick = { row ->
            if (selection.isNotEmpty()) vm.toggleSelect(row.id) else nav.navigate(EntryKey(mode = EntryMode.EDIT, txId = row.id))
        },
        onRowLongClick = { row -> vm.toggleSelect(row.id) },
        onDayClick = { d -> if (selection.isEmpty()) nav.navigate(EntryKey(date = d.toString())) },
        onMemoClick = onMemoStrip,
        header = data.repeatPreview?.let { p -> { item(key = "repeat_preview") { RepeatPreviewRow(p, main) { nav.navigate(RepeatListKey) } } } },
    )
}

@Composable
private fun CalendarContent(data: HomeData, main: CurrencyEntity, weekStart: java.time.DayOfWeek, onDay: (LocalDate) -> Unit) {
    val money = LocalMoneyColors.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 4.dp)) {
            (0 until 7).forEach { i ->
                val d = weekStart.plus(i.toLong())
                Text(Formats.weekdayShort(d), Modifier.weight(1f), color = weekdayColor(d), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        val weeks = data.cells.chunked(7)
        Column(Modifier.fillMaxSize().padding(bottom = 80.dp)) {
            weeks.forEach { week ->
                Row(Modifier.fillMaxWidth().weight(1f).heightIn(min = 64.dp)) {
                    week.forEach { cell ->
                        val longest = listOf(cell.income, cell.expense, cell.income - cell.expense).maxOf { AmountFormatter.number(it, main.decimals).length }
                        val size = if (longest >= 9) ((27 - longest) / 2f).coerceAtLeast(6f) else 9.5f
                        Column(
                            Modifier.weight(1f).fillMaxSize()
                                .background(if (cell.inPeriod) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                .clickable { onDay(cell.date) }
                                .padding(3.dp),
                        ) {
                            val label = if (cell.date.dayOfMonth == 1) Formats.monthDay(cell.date) else cell.date.dayOfMonth.toString()
                            Box(
                                Modifier.then(if (cell.isToday) Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)) else Modifier).padding(horizontal = 3.dp),
                            ) {
                                Text(
                                    label, fontSize = 11.sp,
                                    color = when {
                                        cell.isToday -> Color.White
                                        cell.date.dayOfWeek == java.time.DayOfWeek.SUNDAY && cell.inPeriod -> NexoPalette.Sunday
                                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = if (cell.inPeriod) 1f else 0.5f)
                                    },
                                )
                            }
                            if (cell.memoColors.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    cell.memoColors.take(4).forEach { MemoDot(it, 6) }
                                    if (cell.memoColors.size > 4) Text("+${cell.memoColors.size - 4}", fontSize = 8.sp)
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            val alpha = if (cell.inPeriod) 1f else 0.5f
                            if (cell.income != 0.0) Text(AmountFormatter.number(cell.income, main.decimals), fontSize = size.sp, color = money.income.copy(alpha = alpha), maxLines = 1, modifier = Modifier.align(Alignment.End))
                            if (cell.expense != 0.0) Text(AmountFormatter.number(cell.expense, main.decimals), fontSize = size.sp, color = money.expense.copy(alpha = alpha), maxLines = 1, modifier = Modifier.align(Alignment.End))
                            if ((cell.income != 0.0 && cell.expense != 0.0) || (cell.hasTx && cell.income == 0.0 && cell.expense == 0.0)) {
                                Text(AmountFormatter.number(cell.income - cell.expense, main.decimals), fontSize = size.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha), maxLines = 1, modifier = Modifier.align(Alignment.End))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyContent(data: HomeData, main: CurrencyEntity, selected: LocalDate, onWeek: (com.nexo.app.core.period.MonthPeriod) -> Unit) {
    val money = LocalMoneyColors.current
    var expanded by rememberSaveable(data.year) { mutableStateOf(data.months.firstOrNull { selected in it.period.range }?.period?.key ?: -1) }
    if (data.months.isEmpty()) {
        com.nexo.app.ui.components.EmptyState()
        return
    }
    val today = LocalDate.now()
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        items(data.months, key = { it.period.key }) { m ->
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                Row(
                    Modifier.fillMaxWidth().clickable { expanded = if (expanded == m.period.key) -1 else m.period.key }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.width(96.dp)) {
                        Text(Formats.monthShort(m.period.label.monthValue), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (today in m.period.range || m.period.label.monthValue == 12) {
                            Text(Formats.range(m.period.start, m.period.end), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(AmountFormatter.format(m.income, main), color = money.income, style = MaterialTheme.typography.bodyMedium)
                        Text(AmountFormatter.format(m.expense, main), color = money.expense, style = MaterialTheme.typography.bodyMedium)
                        Text(AmountFormatter.format(m.income - m.expense, main), style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(if (expanded == m.period.key) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.outline)
                }
                if (expanded == m.period.key) {
                    m.weeks.forEach { w ->
                        Row(
                            Modifier.fillMaxWidth().background(if (w.containsToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerLow)
                                .clickable { onWeek(m.period) }.padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(Formats.range(w.range.start, w.range.end), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(110.dp))
                            Text(AmountFormatter.format(w.income, main), color = money.income, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(AmountFormatter.format(w.expense, main), color = money.expense, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(
                                (if (w.containsToday) stringResource(R.string.total) + " " else "") + AmountFormatter.format(w.income - w.expense, main),
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1,
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteContent(memos: List<MemoEntity>, vm: TransactionsViewModel) {
    val nav = LocalNavigator.current
    val saved by vm.savedMemo.collectAsStateWithLifecycle()
    val dark = LocalIsDark.current
    if (memos.isEmpty()) {
        com.nexo.app.ui.components.EmptyState(stringResource(R.string.no_memos))
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var lastDate: String? = null
        memos.forEach { m ->
            if (!m.pinned && m.date != lastDate) {
                lastDate = m.date
                val d = LocalDate.parse(m.date)
                item(key = "d_${m.date}") {
                    Text("${Formats.monthDay(d)} (${Formats.weekdayShort(d)})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                }
            }
            item(key = m.id) {
                val flash = remember { Animatable(0f) }
                LaunchedEffect(saved) {
                    if (saved == m.id) {
                        flash.animateTo(1f)
                        flash.animateTo(0f)
                        vm.consumeSavedMemo()
                    }
                }
                Card(
                    onClick = { nav.navigate(MemoEditorKey(memoId = m.id)) },
                    colors = CardDefaults.cardColors(containerColor = NexoPalette.memo(m.color, dark)),
                    modifier = Modifier.fillMaxWidth().border(if (flash.value > 0f) 2.dp else 0.dp, MaterialTheme.colorScheme.primary.copy(alpha = flash.value), CardDefaults.shape),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (m.pinned) Icon(Icons.Outlined.PushPin, null, Modifier.size(16.dp).padding(end = 4.dp))
                            if (m.title.isNotBlank()) Text(m.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (m.content.isNotBlank()) Text(m.content.trimEnd('\n'), style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalContent(data: HomeData, main: CurrencyEntity) {
    val nav = LocalNavigator.current
    LazyColumn(contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { data.period?.let { com.nexo.app.ui.budgets.BudgetBlock(it) } }
        item {
            val s = data.summary ?: return@item
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.tab_accounts), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        data.period?.let { Text(Formats.range(it.start, it.end), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                    }
                    Spacer(Modifier.height(8.dp))
                    SummaryLine(stringResource(R.string.compared_expenses), "${s.comparedPercent}%")
                    SummaryLine(stringResource(R.string.expenses_of, s.cashLabel), AmountFormatter.format(s.cash, main))
                    if (s.debit != 0.0) SummaryLine(stringResource(R.string.expenses_debit_card), AmountFormatter.format(s.debit, main))
                    if (s.cardIn != 0.0) SummaryLine(stringResource(R.string.expenses_card_pay), "${AmountFormatter.format(s.cardOut, main)}(${AmountFormatter.format(s.cardIn, main)})")
                    else SummaryLine(stringResource(R.string.expenses_card), AmountFormatter.format(s.cardOut, main))
                    SummaryLine(stringResource(R.string.transfer_of, s.cashLabel), AmountFormatter.format(s.transfer, main))
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { data.period?.let { nav.navigate(com.nexo.app.ui.navigation.ExcelExportKey(it.start.toString(), it.end.toString())) } }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.export_to_excel))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
