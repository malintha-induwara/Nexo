package com.nexo.app.ui.transactions

import com.nexo.app.core.common.period.MonthPeriod
import java.time.LocalDate
import java.time.YearMonth

/**
 * Everything the Transactions tab can do. The route wires it to the ViewModel and navigation;
 * the defaults do nothing, for previews and tests.
 */
class TransactionsActions(
    // Period, sub-tab and filter.
    val step: (forward: Boolean) -> Unit = {},
    val pickMonth: (YearMonth) -> Unit = {},
    val openMonth: (MonthPeriod) -> Unit = {},
    val setTab: (SubTab) -> Unit = {},
    val select: (LocalDate) -> Unit = {},
    val setFilterOpen: (Boolean) -> Unit = {},
    val setFilter: (FilterCriteria) -> Unit = {},
    // Multi-select.
    val toggleSelect: (id: String) -> Unit = {},
    val setSelection: (Set<String>) -> Unit = {},
    val clearSelection: () -> Unit = {},
    // One-time scroll and highlight.
    val onFocusHandled: () -> Unit = {},
    val onSavedMemoShown: () -> Unit = {},
    // Screens the tab opens.
    val openSmsQueue: () -> Unit = {},
    val openBookmarks: () -> Unit = {},
    val openSearch: () -> Unit = {},
    val openEntry: (txId: String) -> Unit = {},
    val addEntry: (date: LocalDate) -> Unit = {},
    val openRepeats: () -> Unit = {},
    val openMemo: (memoId: String) -> Unit = {},
    val newMemo: (date: LocalDate) -> Unit = {},
    val exportToExcel: (from: LocalDate, to: LocalDate) -> Unit = { _, _ -> },
    // The Total page's budget panel.
    val openBudgetSettings: () -> Unit = {},
    val editBudget: (budgetId: String) -> Unit = {},
    val addTotalBudget: () -> Unit = {},
    val openBudgetDetail: (budgetId: String, month: YearMonth) -> Unit = { _, _ -> },
)
