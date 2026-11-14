package com.nexo.app.ui.transactions

import androidx.activity.compose.BackHandler
import java.util.Locale
import com.nexo.app.ui.theme.NexoShapes
import com.nexo.app.ui.theme.MoneyColors
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import com.nexo.app.ui.components.NavMotion
import com.nexo.app.ui.components.RollingAmount
import com.nexo.app.ui.components.RoundIconButton
import com.nexo.app.ui.components.SegmentedPill
import com.nexo.app.ui.components.collapsingHeader
import com.nexo.app.ui.components.rememberCollapsingHeaderState
import com.nexo.app.ui.components.rememberHaptics
import com.nexo.app.ui.components.shrinkToFit
import com.nexo.app.ui.theme.LocalNexoColors
import com.nexo.app.ui.theme.NexoMotion
import com.nexo.app.ui.theme.NexoType
import com.nexo.app.ui.theme.Space
import kotlin.math.sign
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
    val lastSaved by vm.lastSaved.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val shell = LocalShell.current
    val money = LocalMoneyColors.current
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val main = ref.mainCurrency
    val currentTab = tab ?: return

    var monthPicker by rememberSaveable { mutableStateOf(false) }
    var daySheet by rememberSaveable { mutableStateOf<String?>(null) }
    var daySheetMemos by rememberSaveable { mutableStateOf(false) }
    var bulkOpen by remember { mutableStateOf(false) }
    var bulkAction by remember { mutableStateOf<BulkAction?>(null) }
    var filterCollapsed by rememberSaveable { mutableStateOf(false) }
    // A date the Daily list should scroll to once its data contains that day (after a save or "today").
    var focusDate by remember { mutableStateOf<LocalDate?>(null) }
    val header = rememberCollapsingHeaderState()

    LaunchedEffect(todaySignal) { if (todaySignal > 0) vm.goToday() }
    LaunchedEffect(Unit) { vm.focusEvents.collect { focusDate = it } }
    LaunchedEffect(currentTab, data.period?.key) { header.expand() }
    // The highlight plays once; clear it so scrolling the row away and back does not replay it.
    LaunchedEffect(lastSaved) {
        if (lastSaved != null) {
            kotlinx.coroutines.delay(2_500)
            vm.clearLastSaved()
        }
    }
    val selecting = selection.isNotEmpty()
    // Not reset on dispose: the value is saved with the screen so the bar does not flash in on return.
    val hideBar = filterOpen || selecting || daySheet != null
    LaunchedEffect(hideBar) { shell.bottomBarHidden = hideBar }
    // On the Notes view the bar's add button writes a memo instead of a transaction.
    val memoDate by rememberUpdatedState(selectedDate)
    DisposableEffect(currentTab) {
        if (currentTab == SubTab.NOTE) {
            shell.addOverride = { nav.navigate(MemoEditorKey(date = memoDate.toString())) }
            shell.addLabelOverride = R.string.new_memo
        }
        onDispose {
            shell.addOverride = null
            shell.addLabelOverride = null
        }
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(colors.background)) {
            val dailyRows = data.days.flatMap { it.rows }
            AnimatedContent(
                targetState = selecting,
                transitionSpec = { fadeIn(NexoMotion.enter(NexoMotion.MEDIUM)) togetherWith fadeOut(NexoMotion.exit()) },
                label = "header",
            ) { sel ->
                if (sel) {
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
                        Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(start = Space.xs, end = Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PeriodNavigator(
                            title = title,
                            onPrevious = { vm.step(false) },
                            onNext = { vm.step(true) },
                            onTitleClick = if (currentTab == SubTab.MONTHLY) null else ({ monthPicker = true }),
                            color = colors.text,
                        )
                        Spacer(Modifier.weight(1f))
                        AnimatedVisibility(!filterOpen, enter = fadeIn(NexoMotion.enter()) + expandHorizontally(NexoMotion.snappy()), exit = fadeOut(NexoMotion.exit()) + shrinkHorizontally(NexoMotion.snappy())) {
                            Row {
                                com.nexo.app.ui.sms.SmsInboxAction()
                                HeaderAction(Icons.Rounded.StarOutline, stringResource(R.string.bookmarks)) { nav.navigate(BookmarksKey()) }
                                HeaderAction(Icons.Rounded.Search, stringResource(R.string.search)) { nav.navigate(SearchKey()) }
                            }
                        }
                        HeaderAction(if (filterOpen) Icons.Rounded.Close else Icons.Rounded.FilterList, stringResource(R.string.filter), active = filterOpen) {
                            haptics.tick()
                            vm.setFilterOpen(!filterOpen)
                            filterCollapsed = false
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = currentTab != SubTab.NOTE && !selecting,
                enter = expandVertically(NexoMotion.snappy()) + fadeIn(NexoMotion.enter()),
                exit = shrinkVertically(NexoMotion.snappy()) + fadeOut(NexoMotion.exit()),
            ) {
                NetHero(data.totals.income, data.totals.expense, main, Modifier.collapsingHeader(header))
            }
            if (!selecting) {
                SegmentedPill(
                    options = visibleTabs,
                    selected = currentTab,
                    onSelect = vm::setTab,
                    label = { stringResource(subTabLabel(it)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
                    equalWidths = false,
                    textStyle = MaterialTheme.typography.labelMedium,
                    segmentPadding = 10.dp,
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth().nestedScroll(header.connection)) {
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
                val bottom = shell.bottomInset
                AnimatedContent(
                    targetState = data,
                    contentKey = { d -> Triple(d.tab, if (d.tab == SubTab.MONTHLY) d.year else d.period?.key, d.loaded) },
                    transitionSpec = {
                        val a = initialState
                        val b = targetState
                        val direction = when {
                            !a.loaded || !b.loaded -> 0
                            a.tab != b.tab -> (tabs.indexOf(b.tab) - tabs.indexOf(a.tab)).sign
                            b.tab == SubTab.MONTHLY -> (b.year - a.year).sign
                            else -> compareValues(b.period?.start, a.period?.start).sign
                        }
                        NavMotion.lateral(this, direction)
                    },
                    modifier = Modifier.fillMaxSize().then(swipeModifier),
                    label = "homeContent",
                ) { d ->
                    when (d.tab) {
                        SubTab.DAILY -> DailyContent(d, main, selection, vm, focusDate, { focusDate = null }, lastSaved, bottom, onMemoStrip = { day -> daySheet = day.toString(); daySheetMemos = true })
                        SubTab.CALENDAR -> CalendarContent(d, main, ref.settings.periods.weekStartDay, bottom) { day -> daySheet = day.toString(); daySheetMemos = false }
                        SubTab.MONTHLY -> MonthlyContent(d, main, selectedDate, bottom) { vm.openMonth(it) }
                        SubTab.TOTAL -> TotalContent(d, main, bottom)
                        SubTab.NOTE -> NoteContent(d.memos, vm, bottom)
                    }
                }
                val memoButton = !selecting && !filterOpen && ref.settings.memoButton && (currentTab == SubTab.DAILY || currentTab == SubTab.CALENDAR)
                androidx.compose.animation.AnimatedVisibility(
                    memoButton,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = Space.gutter, bottom = bottom),
                    enter = scaleIn(NexoMotion.snappy()) + fadeIn(),
                    exit = scaleOut(NexoMotion.exit()) + fadeOut(),
                ) {
                    RoundIconButton(
                        Icons.AutoMirrored.Outlined.StickyNote2,
                        stringResource(R.string.new_memo),
                        onClick = { nav.navigate(MemoEditorKey(date = selectedDate.toString())) },
                        size = 48.dp,
                        container = if (colors.isDark) colors.raised else colors.surface,
                    )
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
        // Part of this screen (not a dialog), so it travels with the screen during navigation.
        DaySheet(
            date = daySheet?.let(LocalDate::parse),
            data = data,
            ref = ref,
            showMemos = daySheetMemos,
            onShowMemos = { daySheetMemos = it },
            onDate = { daySheet = it.toString(); vm.select(it) },
            onDismiss = { daySheet?.let { vm.select(LocalDate.parse(it)) }; daySheet = null },
            highlightId = lastSaved,
        )
    }

    if (monthPicker) data.period?.let { p -> MonthPickerDialog(p.label, onPick = vm::pickMonth, onDismiss = { monthPicker = false }) }
    BulkDialogs(bulkAction, bulkOpen, selection.size, ref, vm.writer, selection, onDone = vm::clearSelection, onDismiss = { bulkOpen = false })
}

/** A 40dp header action; [active] fills it to show a mode is on (the filter). */
@Composable
private fun HeaderAction(icon: ImageVector, description: String, active: Boolean = false, onClick: () -> Unit) {
    val colors = LocalNexoColors.current
    val container by animateColorAsState(if (active) colors.raised else Color.Transparent, NexoMotion.standard(), label = "headerAction")
    RoundIconButton(icon, description, onClick, tint = if (active) colors.text else colors.textSecondary, container = container)
}

/** The period's net as the screen's one large number, with income and expenses beneath. */
@Composable
private fun NetHero(income: Double, expense: Double, main: CurrencyEntity, modifier: Modifier = Modifier) {
    val money = LocalMoneyColors.current
    val colors = LocalNexoColors.current
    Column(modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.gutter, top = Space.xs, bottom = Space.m)) {
        Text(stringResource(R.string.net), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        RollingAmount(income - expense, main, Modifier.shrinkToFit(), color = colors.text)
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SouthWest, null, tint = money.income, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Space.xs))
            RollingAmount(income, main, style = NexoType.Amount, color = money.income, minorScale = 0.8f)
            Spacer(Modifier.width(Space.gutter))
            Icon(Icons.Rounded.NorthEast, null, tint = money.expense, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Space.xs))
            RollingAmount(expense, main, style = NexoType.Amount, color = money.expense, minorScale = 0.8f)
        }
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
private fun DailyContent(
    data: HomeData,
    main: CurrencyEntity,
    selection: Set<String>,
    vm: TransactionsViewModel,
    focusDate: LocalDate?,
    onFocused: () -> Unit,
    highlightId: String?,
    bottom: Dp,
    onMemoStrip: (LocalDate) -> Unit,
) {
    val nav = LocalNavigator.current
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
            if (selection.isNotEmpty()) { haptics.tick(); vm.toggleSelect(row.id) } else nav.navigate(EntryKey(mode = EntryMode.EDIT, txId = row.id))
        },
        onRowLongClick = { row -> haptics.longPress(); vm.toggleSelect(row.id) },
        onDayClick = { d -> if (selection.isEmpty()) nav.navigate(EntryKey(date = d.toString(), fromAdd = true)) },
        onMemoClick = onMemoStrip,
        header = data.repeatPreview?.let { p -> { item(key = "repeat_preview") { RepeatPreviewRow(p, main, { nav.navigate(RepeatListKey) }, Modifier.animateItem()) } } },
        bottomPadding = bottom.value.toInt(),
        highlightId = highlightId,
    )
}

/**
 * Month grid (UI-011). No ruled lines: days sit on the page, and a day with transactions or memos
 * becomes a soft tile, so activity is what stands out. Today's number is an inverted pill.
 * Each cell still shows income, expenses and the day's net.
 */
@Composable
private fun CalendarContent(data: HomeData, main: CurrencyEntity, weekStart: java.time.DayOfWeek, bottom: Dp, onDay: (LocalDate) -> Unit) {
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
                        java.time.DayOfWeek.SUNDAY -> NexoPalette.Sunday
                        java.time.DayOfWeek.SATURDAY -> NexoPalette.Saturday
                        else -> colors.textTertiary
                    },
                    style = NexoType.Caps,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        val weeks = data.cells.chunked(7)
        Column(Modifier.fillMaxSize().padding(bottom = bottom), verticalArrangement = Arrangement.spacedBy(CalendarGap)) {
            weeks.forEach { week ->
                Row(Modifier.fillMaxWidth().weight(1f).heightIn(min = 64.dp), horizontalArrangement = Arrangement.spacedBy(CalendarGap)) {
                    week.forEach { cell -> CalendarDay(cell, main, money, Modifier.weight(1f).fillMaxHeight()) { onDay(cell.date) } }
                }
            }
        }
    }
}

private val CalendarGap = 3.dp

@Composable
private fun CalendarDay(cell: CalendarCell, main: CurrencyEntity, money: MoneyColors, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalNexoColors.current
    val active = cell.hasTx || cell.memoColors.isNotEmpty()
    val dim = if (cell.inPeriod) 1f else 0.45f
    // Long figures step the type down; shrinkToFit below catches anything that still overflows.
    val longest = listOf(cell.income, cell.expense, cell.income - cell.expense).maxOf { AmountFormatter.number(it, main.decimals).length }
    val size = if (longest >= 9) ((27 - longest) / 2f).coerceAtLeast(6f) else 9.5f
    val figure = NexoType.AmountSmall.copy(fontSize = size.sp, lineHeight = (size + 3.5f).sp, fontWeight = FontWeight.SemiBold)
    Column(
        modifier
            .clip(NexoShapes.tile)
            .background(if (active) colors.surface.copy(alpha = dim) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val label = if (cell.date.dayOfMonth == 1) Formats.monthDay(cell.date) else cell.date.dayOfMonth.toString()
        Box(
            Modifier
                .height(24.dp)
                .widthIn(min = 24.dp)
                .clip(CircleShape)
                .then(if (cell.isToday) Modifier.background(colors.text) else Modifier)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                color = when {
                    cell.isToday -> colors.background
                    !cell.inPeriod -> colors.textTertiary.copy(alpha = 0.7f)
                    cell.date.dayOfWeek == java.time.DayOfWeek.SUNDAY -> NexoPalette.Sunday
                    else -> colors.text
                },
            )
        }
        if (cell.memoColors.isNotEmpty()) {
            Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                cell.memoColors.take(4).forEach { MemoDot(it, 5) }
                if (cell.memoColors.size > 4) Text("+${cell.memoColors.size - 4}", fontSize = 8.sp, color = colors.textTertiary)
            }
        }
        Spacer(Modifier.weight(1f))
        if (cell.income != 0.0) Text(AmountFormatter.number(cell.income, main.decimals), Modifier.shrinkToFit(), style = figure, color = money.income.copy(alpha = dim), maxLines = 1)
        if (cell.expense != 0.0) Text(AmountFormatter.number(cell.expense, main.decimals), Modifier.shrinkToFit(), style = figure, color = money.expense.copy(alpha = dim), maxLines = 1)
        if ((cell.income != 0.0 && cell.expense != 0.0) || (cell.hasTx && cell.income == 0.0 && cell.expense == 0.0)) {
            Text(AmountFormatter.number(cell.income - cell.expense, main.decimals), Modifier.shrinkToFit(), style = figure, color = colors.textSecondary.copy(alpha = dim), maxLines = 1)
        }
    }
}

@Composable
private fun MonthlyContent(data: HomeData, main: CurrencyEntity, selected: LocalDate, bottom: Dp, onWeek: (com.nexo.app.core.period.MonthPeriod) -> Unit) {
    val money = LocalMoneyColors.current
    var expanded by rememberSaveable(data.year) { mutableStateOf(data.months.firstOrNull { selected in it.period.range }?.period?.key ?: -1) }
    if (data.months.isEmpty()) {
        com.nexo.app.ui.components.EmptyState()
        return
    }
    val today = LocalDate.now()
    LazyColumn(contentPadding = PaddingValues(bottom = bottom)) {
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
private fun NoteContent(memos: List<MemoEntity>, vm: TransactionsViewModel, bottom: Dp) {
    val nav = LocalNavigator.current
    val saved by vm.savedMemo.collectAsStateWithLifecycle()
    val dark = LocalIsDark.current
    if (memos.isEmpty()) {
        com.nexo.app.ui.components.EmptyState(stringResource(R.string.no_memos))
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottom), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun TotalContent(data: HomeData, main: CurrencyEntity, bottom: Dp) {
    val nav = LocalNavigator.current
    LazyColumn(contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, bottom), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
