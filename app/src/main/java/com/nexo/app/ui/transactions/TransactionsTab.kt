package com.nexo.app.ui.transactions

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.model.Currency
import com.nexo.app.core.model.StartScreen
import com.nexo.app.core.model.SwipeMode
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.HeroAmount
import com.nexo.app.ui.designsystem.component.HeroLabel
import com.nexo.app.ui.designsystem.component.MonthPickerDialog
import com.nexo.app.ui.designsystem.component.NavMotion
import com.nexo.app.ui.designsystem.component.PeriodNavigator
import com.nexo.app.ui.designsystem.component.RoundIconButton
import com.nexo.app.ui.designsystem.component.SegmentedPill
import com.nexo.app.ui.designsystem.component.collapsingHeader
import com.nexo.app.ui.designsystem.component.rememberCollapsingHeaderState
import com.nexo.app.ui.designsystem.component.rememberHaptics
import com.nexo.app.ui.designsystem.component.shrinkToFit
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.theme.LocalMoneyColors
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Space
import com.nexo.app.ui.navigation.BookmarksKey
import com.nexo.app.ui.navigation.BudgetAddKey
import com.nexo.app.ui.navigation.BudgetDetailKey
import com.nexo.app.ui.navigation.BudgetEditorKey
import com.nexo.app.ui.navigation.BudgetSettingsKey
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.navigation.ExcelExportKey
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.MemoEditorKey
import com.nexo.app.ui.navigation.NavResult
import com.nexo.app.ui.navigation.Navigator
import com.nexo.app.ui.navigation.ReceiveNavResult
import com.nexo.app.ui.navigation.RepeatListKey
import com.nexo.app.ui.navigation.SearchKey
import com.nexo.app.ui.navigation.SmsQueueKey
import com.nexo.app.ui.shell.LocalShell
import com.nexo.app.ui.sms.QueueBadge
import com.nexo.app.ui.sms.SmsInboxAction
import com.nexo.app.ui.transactionlist.BulkAction
import com.nexo.app.ui.transactionlist.BulkDialogs
import com.nexo.app.ui.transactionlist.BulkEditActions
import com.nexo.app.ui.transactionlist.SelectionBar
import com.nexo.app.ui.transactionlist.selectedNet
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.delay

@Composable
fun TransactionsTabRoute(
    todaySignal: Int,
    smsBadge: QueueBadge,
    vm: TransactionsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    // No effects until the starting sub-tab is known, so a pending "today" signal waits for it.
    if (state.tab == null) return
    LaunchedEffect(todaySignal) { if (todaySignal > 0) vm.goToday() }
    ReceiveNavResult(nav.results, NavResult.Focus::class) { vm.focusOn(it.date) }
    ReceiveNavResult(nav.results, NavResult.TransactionSaved::class) {
        vm.onTransactionSaved(it.id)
    }
    ReceiveNavResult(nav.results, NavResult.MemoSaved::class) { vm.onMemoSaved(it.id) }
    // The highlight plays once; clear it so scrolling the row away and back does not replay it.
    val lastSaved = state.lastSaved
    LaunchedEffect(lastSaved) {
        if (lastSaved != null) {
            delay(2_500)
            vm.clearLastSaved()
        }
    }
    val actions = remember(vm, nav) { transactionsActions(vm, nav) }
    TransactionsTab(
        state = state,
        smsBadge = smsBadge,
        actions = actions,
        bulkActions = vm.bulkActions,
    )
}

private fun transactionsActions(vm: TransactionsViewModel, nav: Navigator) = TransactionsActions(
    step = vm::step,
    pickMonth = vm::pickMonth,
    openMonth = vm::openMonth,
    setTab = vm::setTab,
    select = vm::select,
    setFilterOpen = vm::setFilterOpen,
    setFilter = vm::setFilter,
    toggleSelect = vm::toggleSelect,
    setSelection = vm::setSelection,
    clearSelection = vm::clearSelection,
    onFocusHandled = vm::onFocusHandled,
    onSavedMemoShown = vm::consumeSavedMemo,
    openSmsQueue = { nav.navigate(SmsQueueKey) },
    openBookmarks = { nav.navigate(BookmarksKey()) },
    openSearch = { nav.navigate(SearchKey()) },
    openEntry = { nav.navigate(EntryKey(mode = EntryMode.EDIT, txId = it)) },
    addEntry = { nav.navigate(EntryKey(date = it.toString(), fromAdd = true)) },
    openRepeats = { nav.navigate(RepeatListKey) },
    openMemo = { nav.navigate(MemoEditorKey(memoId = it)) },
    newMemo = { nav.navigate(MemoEditorKey(date = it.toString())) },
    exportToExcel = { from, to -> nav.navigate(ExcelExportKey(from.toString(), to.toString())) },
    openBudgetSettings = { nav.navigate(BudgetSettingsKey) },
    editBudget = { nav.navigate(BudgetEditorKey(it)) },
    addTotalBudget = { nav.navigate(BudgetAddKey(presetTotal = true, preset = true)) },
    openBudgetDetail = { id, month -> nav.navigate(BudgetDetailKey(id, month.toString())) },
)

/** The Transactions tab: a period header, the sub-tab pages, the filter panel and the day sheet. */
@Composable
internal fun TransactionsTab(
    state: TransactionsUiState,
    smsBadge: QueueBadge,
    actions: TransactionsActions,
    bulkActions: BulkEditActions,
) {
    val ref = state.ref
    val data = state.data
    val tab = state.tab
    val filter = state.filter
    val filterOpen = state.filterOpen
    val selection = state.selection
    val selectedDate = state.selectedDate
    val lastSaved = state.lastSaved
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
    val header = rememberCollapsingHeaderState()

    LaunchedEffect(currentTab, data.period?.key) { header.expand() }
    val selecting = selection.isNotEmpty()
    // Not reset on dispose: the value is saved with the screen so the bar does not flash in on return.
    val hideBar = filterOpen || selecting || daySheet != null
    LaunchedEffect(hideBar) { shell.bottomBarHidden = hideBar }
    // On the Notes view the bar's add button writes a memo instead of a transaction.
    val memoDate by rememberUpdatedState(selectedDate)
    val newMemo by rememberUpdatedState(actions.newMemo)
    DisposableEffect(currentTab) {
        if (currentTab == SubTab.NOTE) {
            shell.addOverride = { newMemo(memoDate) }
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
            selecting -> actions.clearSelection()
            filterOpen -> actions.setFilterOpen(false)
        }
    }

    val tabs = if (ref.settings.startScreen == StartScreen.CALENDAR) {
        listOf(SubTab.CALENDAR, SubTab.DAILY, SubTab.MONTHLY, SubTab.TOTAL, SubTab.NOTE)
    } else {
        listOf(SubTab.DAILY, SubTab.CALENDAR, SubTab.MONTHLY, SubTab.TOTAL, SubTab.NOTE)
    }
    val visibleTabs = if (filterOpen) {
        tabs.filter {
            it != SubTab.TOTAL && it != SubTab.NOTE
        }
    } else {
        tabs
    }

    val title = if (currentTab == SubTab.MONTHLY) {
        data.year.toString()
    } else {
        data.period?.let { Formats.monthYear(it.label) }
            ?: ""
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(colors.background)) {
            val dailyRows = data.days.flatMap { it.rows }
            AnimatedContent(
                targetState = selecting,
                transitionSpec = {
                    fadeIn(NexoMotion.enter(NexoMotion.MEDIUM)) togetherWith
                        fadeOut(NexoMotion.exit())
                },
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
                                val next = if (selection.size >= all.size) emptySet() else all
                                actions.setSelection(next)
                            },
                            onClose = actions.clearSelection,
                            onAction = {
                                bulkAction = it
                                bulkOpen = true
                            },
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().height(
                            56.dp,
                        ).padding(start = Space.xs, end = Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PeriodNavigator(
                            title = title,
                            onPrevious = { actions.step(false) },
                            onNext = { actions.step(true) },
                            onTitleClick = if (currentTab == SubTab.MONTHLY) {
                                null
                            } else {
                                ({ monthPicker = true })
                            },
                            color = colors.text,
                        )
                        Spacer(Modifier.weight(1f))
                        AnimatedVisibility(
                            !filterOpen,
                            enter =
                                fadeIn(
                                    NexoMotion.enter(),
                                ) + expandHorizontally(NexoMotion.snappy()),
                            exit =
                                fadeOut(
                                    NexoMotion.exit(),
                                ) + shrinkHorizontally(NexoMotion.snappy()),
                        ) {
                            Row {
                                SmsInboxAction(smsBadge, actions.openSmsQueue)
                                HeaderAction(
                                    Icons.Rounded.StarOutline,
                                    stringResource(R.string.bookmarks),
                                    onClick = actions.openBookmarks,
                                )
                                HeaderAction(
                                    Icons.Rounded.Search,
                                    stringResource(R.string.search),
                                    onClick = actions.openSearch,
                                )
                            }
                        }
                        HeaderAction(
                            if (filterOpen) Icons.Rounded.Close else Icons.Rounded.FilterList,
                            stringResource(R.string.filter),
                            active = filterOpen,
                        ) {
                            haptics.tick()
                            actions.setFilterOpen(!filterOpen)
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
                NetHero(
                    data.totals.income,
                    data.totals.expense,
                    main,
                    Modifier.collapsingHeader(header),
                )
            }
            if (!selecting) {
                SegmentedPill(
                    options = visibleTabs,
                    selected = currentTab,
                    onSelect = actions.setTab,
                    label = { stringResource(subTabLabel(it)) },
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = Space.l,
                        vertical = Space.s,
                    ),
                    equalWidths = false,
                    textStyle = MaterialTheme.typography.labelMedium,
                    segmentPadding = 10.dp,
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth().nestedScroll(header.connection)) {
                val swipe = if (selecting) SwipeMode.NONE else ref.settings.swipeMode
                val threshold = with(LocalDensity.current) { 90.dp.toPx() }
                val swipeModifier = if (swipe == SwipeMode.NONE) {
                    Modifier
                } else {
                    Modifier.pointerInput(swipe, currentTab, visibleTabs) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onHorizontalDrag = { _, dx -> total += dx },
                            onDragEnd = {
                                if (abs(total) > threshold) {
                                    val forward = total < 0
                                    if (swipe == SwipeMode.CHANGE_DATE) {
                                        actions.step(forward)
                                    } else {
                                        val i =
                                            visibleTabs.indexOf(currentTab) + if (forward) 1 else -1
                                        visibleTabs.getOrNull(i)?.let(actions.setTab)
                                    }
                                }
                            },
                        )
                    }
                }
                val bottom = shell.bottomInset
                AnimatedContent(
                    targetState = data,
                    contentKey = { d ->
                        Triple(
                            d.tab,
                            if (d.tab == SubTab.MONTHLY) {
                                d.year
                            } else {
                                d.period?.key
                            },
                            d.loaded,
                        )
                    },
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
                        SubTab.DAILY -> DailyContent(
                            d,
                            main,
                            selection,
                            actions.toggleSelect,
                            state.focusDate,
                            actions.onFocusHandled,
                            lastSaved,
                            bottom,
                            onMemoStrip = { day ->
                                daySheet = day.toString()
                                daySheetMemos = true
                            },
                            onOpenEntry = actions.openEntry,
                            onAddEntry = actions.addEntry,
                            onOpenRepeats = actions.openRepeats,
                        )

                        SubTab.CALENDAR -> CalendarContent(
                            d,
                            main,
                            ref.settings.periods.weekStartDay,
                            bottom,
                        ) { day ->
                            daySheet = day.toString()
                            daySheetMemos = false
                        }

                        SubTab.MONTHLY -> MonthlyContent(d, main, selectedDate, bottom) {
                            actions.openMonth(it)
                        }

                        SubTab.TOTAL -> TotalContent(
                            d,
                            main,
                            state.budgetBlock,
                            ref,
                            bottom,
                            actions,
                        )

                        SubTab.NOTE -> NoteContent(
                            d.memos,
                            state.savedMemo,
                            actions.onSavedMemoShown,
                            bottom,
                            onOpenMemo = actions.openMemo,
                        )
                    }
                }
                val memoButton =
                    !selecting && !filterOpen && ref.settings.memoButton &&
                        (currentTab == SubTab.DAILY || currentTab == SubTab.CALENDAR)
                // Qualified so the outer Column's scoped overload isn't picked.
                androidx.compose.animation.AnimatedVisibility(
                    memoButton,
                    modifier = Modifier.align(
                        Alignment.BottomEnd,
                    ).padding(end = Space.gutter, bottom = bottom),
                    enter = scaleIn(NexoMotion.snappy()) + fadeIn(),
                    exit = scaleOut(NexoMotion.exit()) + fadeOut(),
                ) {
                    RoundIconButton(
                        Icons.AutoMirrored.Outlined.StickyNote2,
                        stringResource(R.string.new_memo),
                        onClick = { actions.newMemo(selectedDate) },
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
                        onChange = actions.setFilter,
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
            onDate = {
                daySheet = it.toString()
                actions.select(it)
            },
            onDismiss = {
                daySheet?.let { actions.select(LocalDate.parse(it)) }
                daySheet = null
            },
            onOpenEntry = actions.openEntry,
            onAddEntry = actions.addEntry,
            onOpenMemo = actions.openMemo,
            onNewMemo = actions.newMemo,
            highlightId = lastSaved,
        )
    }

    if (monthPicker) {
        data.period?.let { p ->
            MonthPickerDialog(p.label, onPick = actions.pickMonth, onDismiss = {
                monthPicker = false
            })
        }
    }
    BulkDialogs(bulkAction, bulkOpen, selection.size, ref, bulkActions, onDismiss = {
        bulkOpen = false
    })
}

/** A 40dp header action; [active] fills it to show a mode is on (the filter). */
@Composable
private fun HeaderAction(
    icon: ImageVector,
    description: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalNexoColors.current
    val container by animateColorAsState(
        if (active) colors.raised else Color.Transparent,
        NexoMotion.standard(),
        label = "headerAction",
    )
    RoundIconButton(
        icon,
        description,
        onClick,
        tint = if (active) colors.text else colors.textSecondary,
        container = container,
    )
}

/** The period's net as the screen's one large number, with income and expenses beneath. */
@Composable
private fun NetHero(
    income: Double,
    expense: Double,
    main: Currency,
    modifier: Modifier = Modifier,
) {
    val money = LocalMoneyColors.current
    val colors = LocalNexoColors.current
    Column(
        modifier.fillMaxWidth().padding(
            start = Space.gutter,
            end = Space.gutter,
            top = Space.xs,
            bottom = Space.m,
        ),
    ) {
        HeroLabel(stringResource(R.string.net))
        HeroAmount(income - expense, main, Modifier.shrinkToFit(), color = colors.text)
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.SouthWest,
                null,
                tint = money.income,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Space.xs))
            HeroAmount(
                income,
                main,
                style = NexoType.Amount,
                color = money.income,
                minorScale = 0.8f,
            )
            Spacer(Modifier.width(Space.gutter))
            Icon(
                Icons.Rounded.NorthEast,
                null,
                tint = money.expense,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Space.xs))
            HeroAmount(
                expense,
                main,
                style = NexoType.Amount,
                color = money.expense,
                minorScale = 0.8f,
            )
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

@Preview
@Composable
private fun TransactionsTabPreview() = NexoPreview {
    TransactionsTab(
        state = previewTransactionsState,
        smsBadge = QueueBadge(count = 2),
        actions = TransactionsActions(),
        bulkActions = previewBulkActions,
    )
}

@Preview
@Composable
private fun TransactionsTabNotesPreview() = NexoPreview {
    TransactionsTab(
        state = previewTransactionsStateOn(SubTab.NOTE),
        smsBadge = QueueBadge(),
        actions = TransactionsActions(),
        bulkActions = previewBulkActions,
    )
}

@Preview
@Composable
private fun TransactionsTabTotalPreview() = NexoPreview {
    TransactionsTab(
        state = previewTransactionsStateOn(SubTab.TOTAL),
        smsBadge = QueueBadge(),
        actions = TransactionsActions(),
        bulkActions = previewBulkActions,
    )
}
