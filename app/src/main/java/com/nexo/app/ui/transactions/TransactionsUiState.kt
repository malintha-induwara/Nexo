package com.nexo.app.ui.transactions

import com.nexo.app.core.common.period.DateRange
import com.nexo.app.core.common.period.MonthPeriod
import com.nexo.app.core.model.Ledger
import com.nexo.app.core.model.Memo
import com.nexo.app.core.model.MemoColor
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.Transaction
import com.nexo.app.domain.BudgetBlockData
import com.nexo.app.domain.InOut
import com.nexo.app.ui.transactionlist.DayGroup
import com.nexo.app.ui.transactionlist.FilterCriteria
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
enum class SubTab { DAILY, CALENDAR, MONTHLY, TOTAL, NOTE }

data class CalendarCell(
    val date: LocalDate,
    val inPeriod: Boolean,
    val income: Double,
    val expense: Double,
    val hasTx: Boolean,
    val memoColors: List<MemoColor>,
    val isToday: Boolean,
)

data class WeekRow(
    val range: DateRange,
    val income: Double,
    val expense: Double,
    val containsToday: Boolean,
)

data class MonthRow(
    val period: MonthPeriod,
    val income: Double,
    val expense: Double,
    val weeks: List<WeekRow>,
)

data class RepeatPreview(val dates: List<LocalDate>, val names: List<String>, val amount: Double)

data class TotalSummary(
    val comparedPercent: Int,
    val cashLabel: String,
    val cash: Double,
    val debit: Double,
    val cardOut: Double,
    val cardIn: Double,
    val transfer: Double,
    val income: Double,
    val expense: Double,
)

data class HomeData(
    val loaded: Boolean = false,
    val tab: SubTab = SubTab.DAILY,
    val period: MonthPeriod? = null,
    val year: Int,
    val days: List<DayGroup> = emptyList(),
    val repeatPreview: RepeatPreview? = null,
    val cells: List<CalendarCell> = emptyList(),
    val months: List<MonthRow> = emptyList(),
    val totals: InOut = InOut(),
    val memos: List<Memo> = emptyList(),
    val summary: TotalSummary? = null,
    // Unfiltered, for the filter panel and the day sheet.
    val periodRows: List<Transaction> = emptyList(),
    val gridRows: List<Transaction> = emptyList(),
    val carryOver: Double = 0.0,
    val ledger: Ledger = Ledger(),
)

data class TransactionsUiState(
    val ref: RefData = RefData(),
    // Null until the reference data says which sub-tab the book starts on.
    val tab: SubTab? = null,
    val data: HomeData,
    val selectedDate: LocalDate,
    val filter: FilterCriteria = FilterCriteria(),
    val filterOpen: Boolean = false,
    val selection: Set<String> = emptySet(),
    // Scrolled to once; cleared with [TransactionsViewModel.onFocusHandled].
    val focusDate: LocalDate? = null,
    // Row the entry form just saved, for its highlight.
    val lastSaved: String? = null,
    // Memo the editor just saved, for its highlight.
    val savedMemo: String? = null,
    val budgetBlock: BudgetBlockData? = null,
)
