package com.nexo.app.ui.transactions

import com.nexo.app.core.common.period.MonthPeriod
import com.nexo.app.ui.transactionlist.FilterCriteria
import java.time.LocalDate
import java.time.YearMonth

// No-op defaults serve previews and tests.
class TransactionsActions(
    val step: (forward: Boolean) -> Unit = {},
    val pickMonth: (YearMonth) -> Unit = {},
    val openMonth: (MonthPeriod) -> Unit = {},
    val setTab: (SubTab) -> Unit = {},
    val select: (LocalDate) -> Unit = {},
    val setFilterOpen: (Boolean) -> Unit = {},
    val setFilter: (FilterCriteria) -> Unit = {},
    val toggleSelect: (id: String) -> Unit = {},
    val setSelection: (Set<String>) -> Unit = {},
    val clearSelection: () -> Unit = {},
    val onFocusHandled: () -> Unit = {},
    val onSavedMemoShown: () -> Unit = {},
    val openSmsQueue: () -> Unit = {},
    val openBookmarks: () -> Unit = {},
    val openSearch: () -> Unit = {},
    val openEntry: (txId: String) -> Unit = {},
    val addEntry: (date: LocalDate) -> Unit = {},
    val openRepeats: () -> Unit = {},
    val openMemo: (memoId: String) -> Unit = {},
    val newMemo: (date: LocalDate) -> Unit = {},
    val exportToExcel: (from: LocalDate, to: LocalDate) -> Unit = { _, _ -> },
    val openBudgetSettings: () -> Unit = {},
    val editBudget: (budgetId: String) -> Unit = {},
    val addTotalBudget: () -> Unit = {},
    val openBudgetDetail: (budgetId: String, month: YearMonth) -> Unit = { _, _ -> },
)
