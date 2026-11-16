package com.nexo.app.ui.transactions

import com.nexo.app.core.common.period.DateRange
import com.nexo.app.core.common.period.MonthPeriod
import com.nexo.app.core.model.Ledger
import com.nexo.app.core.model.Memo
import com.nexo.app.core.model.MemoColor
import com.nexo.app.core.model.Transaction
import com.nexo.app.core.model.TxKind
import com.nexo.app.domain.BudgetBlockData
import com.nexo.app.domain.BudgetRow
import com.nexo.app.domain.InOut
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.transactionlist.BulkEditActions
import com.nexo.app.ui.transactionlist.TransactionRowMapper
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

private fun previewTx(
    id: String,
    kind: TxKind,
    day: LocalDate,
    time: LocalTime,
    accountId: String,
    categoryId: String,
    amount: Double,
    note: String,
) = Transaction(
    id = id,
    kind = kind,
    accountId = accountId,
    categoryId = categoryId,
    dateTime = day.atTime(time).atZone(PreviewData.clock.zone).toInstant().toEpochMilli(),
    date = day.toString(),
    amount = amount,
    amountMain = amount,
    amountAccount = amount,
    currencyId = "LKR",
    note = note,
    createdAt = 0,
    updatedAt = 0,
)

private val previewYesterday = PreviewData.today.minusDays(1)

private val previewTransactions = listOf(
    previewTx(
        "t1",
        TxKind.EXPENSE,
        PreviewData.today,
        LocalTime.of(9, 15),
        "cash",
        "groceries",
        2_450.0,
        "Weekly groceries",
    ),
    previewTx(
        "t2",
        TxKind.EXPENSE,
        PreviewData.today,
        LocalTime.of(18, 40),
        "card",
        "transport",
        850.0,
        "Taxi home",
    ),
    previewTx(
        "t3",
        TxKind.INCOME,
        previewYesterday,
        LocalTime.of(10, 0),
        "bank",
        "salary",
        185_000.0,
        "November salary",
    ),
    previewTx(
        "t4",
        TxKind.EXPENSE,
        previewYesterday,
        LocalTime.of(13, 5),
        "cash",
        "food",
        1_200.0,
        "Lunch",
    ),
)

internal val previewMemo = Memo(
    id = "m1",
    title = "Rent",
    content = "Pay the rent before the 20th.",
    date = PreviewData.today.toString(),
    dateTime = 0,
    orderSeq = 0,
    color = MemoColor.WHITE,
    updatedAt = 0,
)

private val previewMonth = YearMonth.from(PreviewData.today)

private val previewHomeData = HomeData(
    loaded = true,
    tab = SubTab.DAILY,
    period = MonthPeriod(
        DateRange(previewMonth.atDay(1), previewMonth.atEndOfMonth()),
        previewMonth,
    ),
    year = previewMonth.year,
    days = TransactionRowMapper(PreviewData.ref, PreviewData.clock.zone).dayGroups(
        previewTransactions,
        Ledger(rows = previewTransactions),
        memos = mapOf(PreviewData.today to listOf(previewMemo)),
    ),
    totals = InOut(income = 185_000.0, expense = 4_500.0),
    memos = listOf(previewMemo),
    summary = TotalSummary(
        comparedPercent = 92,
        cashLabel = "Cash",
        cash = 3_650.0,
        debit = 0.0,
        cardOut = 850.0,
        cardIn = 0.0,
        transfer = 0.0,
        income = 185_000.0,
        expense = 4_500.0,
    ),
    periodRows = previewTransactions,
    gridRows = previewTransactions,
    ledger = Ledger(rows = previewTransactions),
)

/** The Daily page of this month: four transactions over two days, and a memo today. */
internal val previewTransactionsState = TransactionsUiState(
    ref = PreviewData.ref,
    tab = SubTab.DAILY,
    data = previewHomeData,
    selectedDate = PreviewData.today,
    budgetBlock = BudgetBlockData(
        total = BudgetRow(null, null, "", 60_000.0, 4_500.0, isTotal = true),
        rows = listOf(BudgetRow("b1", "food", "Food", 40_000.0, 3_650.0)),
    ),
)

/** [previewTransactionsState] showing another sub-tab. */
internal fun previewTransactionsStateOn(tab: SubTab) = previewTransactionsState.copy(
    tab = tab,
    data = previewHomeData.copy(tab = tab),
)

internal val previewBulkActions = BulkEditActions({}, {}, {}, {}, {}, { _, _ -> })
