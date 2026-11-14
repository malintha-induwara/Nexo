package com.nexo.app.ui.transactions

import com.nexo.app.core.database.entity.MemoEntity
import com.nexo.app.core.database.entity.TransactionEntity
import com.nexo.app.core.model.CashFlow
import com.nexo.app.core.model.MemoColor
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.period.DateRange
import com.nexo.app.core.period.MonthPeriod
import com.nexo.app.domain.Classifier
import com.nexo.app.domain.InOut
import com.nexo.app.domain.RefData
import java.time.LocalDate

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

data class WeekRow(val range: DateRange, val income: Double, val expense: Double, val containsToday: Boolean)

data class MonthRow(val period: MonthPeriod, val income: Double, val expense: Double, val weeks: List<WeekRow>)

data class RepeatPreview(val dates: List<LocalDate>, val names: List<String>, val amount: Double)

/** Accounts card of the Total page. */
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
    val year: Int = LocalDate.now().year,
    val days: List<DayGroup> = emptyList(),
    val repeatPreview: RepeatPreview? = null,
    val cells: List<CalendarCell> = emptyList(),
    val months: List<MonthRow> = emptyList(),
    val totals: InOut = InOut(),
    val memos: List<MemoEntity> = emptyList(),
    val summary: TotalSummary? = null,
    /** Unfiltered rows of the displayed period, used by the filter panel and the day sheet. */
    val periodRows: List<TransactionEntity> = emptyList(),
    val gridRows: List<TransactionEntity> = emptyList(),
    val carryOver: Double = 0.0,
    val ledger: com.nexo.app.data.Ledger = com.nexo.app.data.Ledger(),
)

/** Main-screen filter. */
data class FilterCriteria(
    val incomeAll: Boolean = false,
    val expenseAll: Boolean = false,
    /** Main or sub category ids. */
    val categories: Set<String> = emptySet(),
    /** Main category ids whose "Other" (no subcategory) entry is selected. */
    val otherOf: Set<String> = emptySet(),
    val noneIncome: Boolean = false,
    val noneExpense: Boolean = false,
    val modifiedIncome: Boolean = false,
    val modifiedExpense: Boolean = false,
    /** Transfer-expense accounts: transfers into them (expense side) / out of them (income side). */
    val teExpense: Set<String> = emptySet(),
    val teIncome: Set<String> = emptySet(),
    val accounts: Set<String> = emptySet(),
) {
    val hasCategoryPart: Boolean
        get() = incomeAll || expenseAll || categories.isNotEmpty() || otherOf.isNotEmpty() || noneIncome || noneExpense ||
            modifiedIncome || modifiedExpense || teExpense.isNotEmpty() || teIncome.isNotEmpty()
    val active: Boolean get() = hasCategoryPart || accounts.isNotEmpty()

    fun matches(tx: TransactionEntity, ref: RefData, c: Classifier): Boolean {
        val catOk = !hasCategoryPart || categoryMatch(tx, ref, c)
        val accOk = accounts.isEmpty() || tx.accountId in accounts || (tx.kind == TxKind.TRANSFER_OUT && tx.counterpartAccountId in accounts)
        return catOk && accOk
    }

    private fun categoryMatch(tx: TransactionEntity, ref: RefData, c: Classifier): Boolean {
        val flow = c.classify(tx.kind, tx.accountId, tx.counterpartAccountId)
        if (incomeAll && flow == CashFlow.INCOME) return true
        if (expenseAll && flow == CashFlow.EXPENSE) return true
        when (tx.kind) {
            TxKind.INCOME, TxKind.EXPENSE -> {
                val income = tx.kind == TxKind.INCOME
                if (tx.isBalanceAdjustment) return if (income) modifiedIncome else modifiedExpense
                val main = ref.mainCategoryId(tx.categoryId) ?: return if (income) noneIncome else noneExpense
                val sub = ref.subCategoryId(tx.categoryId)
                if (main in categories) return true
                if (sub != null && sub in categories) return true
                if (sub == null && main in otherOf) return true
            }
            TxKind.TRANSFER_OUT -> {
                if (flow == CashFlow.EXPENSE && tx.counterpartAccountId in teExpense) return true
                if (flow == CashFlow.INCOME && tx.accountId in teIncome) return true
            }
            else -> Unit
        }
        return false
    }
}
