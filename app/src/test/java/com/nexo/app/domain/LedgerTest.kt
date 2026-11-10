package com.nexo.app.domain

import com.nexo.app.core.database.entity.AccountEntity
import com.nexo.app.core.database.entity.AccountGroupEntity
import com.nexo.app.core.database.entity.CurrencyEntity
import com.nexo.app.core.database.entity.TransactionEntity
import com.nexo.app.core.model.CardAccounting
import com.nexo.app.core.model.CashFlow
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.model.SymbolPosition
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.money.AmountFormatter
import com.nexo.app.core.settings.BookSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class LedgerTest {
    private val usd = CurrencyEntity("USD", "USD", "USD", "$", SymbolPosition.PREFIX, 2, 1.0, true, true, 0, 0)
    private val eur = CurrencyEntity("EUR", "EUR", "EUR", "€", SymbolPosition.PREFIX, 2, 1.1, false, true, 1, 0)
    private val groups = listOf(
        AccountGroupEntity("1", "Accounts", GroupType.NORMAL, 1, 1, updatedAt = 0),
        AccountGroupEntity("2", "Card", GroupType.CREDIT_CARD, 2, 2, updatedAt = 0),
        AccountGroupEntity("3", "Debit", GroupType.DEBIT_CARD, 3, 3, updatedAt = 0),
        AccountGroupEntity("4", "Savings", GroupType.NORMAL, 4, 4, updatedAt = 0),
    )
    private fun acc(id: String, group: String, flagged: Boolean = false, linked: String? = null, settle: Int = 1) =
        AccountEntity(id, id, group, "USD", 0, transferExpense = flagged, linkedAccountId = linked, settlementDay = settle, createdAt = 0, updatedAt = 0)
    private val accounts = listOf(acc("bank", "1"), acc("card", "2", linked = "bank", settle = 10), acc("debit", "3", linked = "bank"), acc("save", "4", flagged = true))

    private fun ref(lump: Boolean = false, ti: Boolean = true) = RefData(
        groups, accounts, emptyList(), listOf(usd, eur), emptyList(),
        BookSettings(cardAccounting = if (lump) CardAccounting.LUMP_SUM else CardAccounting.AT_USE, transferIncome = ti),
    )

    @Test
    fun classificationMatrix() {
        val a = Classifier(ref())
        assertEquals(CashFlow.EXPENSE, a.classify(TxKind.TRANSFER_OUT, "bank", "save"))
        assertEquals(CashFlow.INCOME, a.classify(TxKind.TRANSFER_OUT, "save", "bank"))
        assertEquals(CashFlow.NEUTRAL, a.classify(TxKind.TRANSFER_OUT, "bank", "card"))
        assertEquals(CashFlow.EXPENSE, a.classify(TxKind.EXPENSE, "card", null))
        val b = Classifier(ref(lump = true))
        assertEquals(CashFlow.NEUTRAL, b.classify(TxKind.EXPENSE, "card", null))
        assertEquals(CashFlow.EXPENSE, b.classify(TxKind.EXPENSE, null, null))
        assertEquals(CashFlow.EXPENSE, b.classify(TxKind.TRANSFER_OUT, "bank", "card"))
        val off = Classifier(ref(ti = false))
        assertEquals(CashFlow.NEUTRAL, off.classify(TxKind.TRANSFER_OUT, "save", "bank"))
        assertEquals(CashFlow.EXPENSE, off.classify(TxKind.TRANSFER_OUT, "save", "save"))
        assertEquals(CashFlow.NEUTRAL, a.classify(TxKind.BALANCE_INCREASE, "bank", null))
    }

    private var n = 0
    private fun tx(kind: TxKind, account: String, date: String, amount: Double) = TransactionEntity(
        id = "t${n++}", kind = kind, accountId = account, dateTime = 0, date = date, amount = amount, amountMain = amount,
        amountAccount = amount, currencyId = "USD", createdAt = 0, updatedAt = 0,
    )

    @Test
    fun cardStatementBuckets() {
        // Settlement day 10, today 2024-05-15: current = 05-10..06-09, previous = 04-10..05-09.
        val rows = listOf(
            tx(TxKind.EXPENSE, "card", "2024-04-01", 30.0), // old
            tx(TxKind.EXPENSE, "card", "2024-04-20", 100.0), // previous
            tx(TxKind.EXPENSE, "debit", "2024-05-12", 40.0), // current (via linked rows passed in)
            tx(TxKind.TRANSFER_IN, "card", "2024-05-11", 50.0), // payment
        )
        val f = CardStatement.compute(ref(), accounts[1], rows, LocalDate.parse("2024-05-15"))
        // Payment 50 clears old 30, the remaining 20 reduces the previous statement to 80.
        assertEquals(-80.0, f.payable, 0.0)
        assertEquals(-40.0, f.outstanding, 0.0)
        assertEquals(0.0, f.other, 0.0)
        assertEquals(80.0, f.payAmount, 0.0)
    }

    @Test
    fun conversionThreeAmounts() {
        val a = Conversion.compute(10.0, eur, usd, usd)
        assertEquals(10.0, a.amount, 0.0)
        assertEquals(11.0, a.main, 0.0)
        assertEquals(11.0, a.account, 0.0)
        val b = Conversion.compute(11.0, usd, eur, usd)
        assertEquals(10.0, b.account, 0.0)
    }

    @Test
    fun formatting() {
        Locale.setDefault(Locale.US)
        assertEquals("$ 1,234.50", AmountFormatter.format(1234.5, usd))
        assertEquals("-$ 0.50", AmountFormatter.format(-0.5, usd))
        assertEquals("1,000", AmountFormatter.number(999.6, 0))
        assertEquals("0.0012", AmountFormatter.rate(0.00123))
        assertEquals("1.1", AmountFormatter.rate(1.1))
    }
}
