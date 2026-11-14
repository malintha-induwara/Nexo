package com.nexo.app.domain

import com.nexo.app.core.database.dao.TxAggregate
import com.nexo.app.core.database.entity.AccountEntity
import com.nexo.app.core.database.entity.TransactionEntity
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.money.Money
import com.nexo.app.core.period.DateRange
import com.nexo.app.core.period.PeriodEngine
import java.math.BigDecimal
import java.time.LocalDate

/** Net (inflows − outflows) of one account's own rows, in main and in account currency. */
data class Net(val main: Double = 0.0, val account: Double = 0.0)

object Balances {
    /** Net per account (key null = the "None" pseudo-account). */
    fun netByAccount(rows: List<TxAggregate>): Map<String?, Net> {
        val main = HashMap<String?, BigDecimal>()
        val acct = HashMap<String?, BigDecimal>()
        for (r in rows) {
            val sign = if (r.kind.isInflow) BigDecimal.ONE else BigDecimal.ONE.negate()
            main[r.accountId] = (main[r.accountId] ?: BigDecimal.ZERO) + sign * BigDecimal.valueOf(r.main)
            acct[r.accountId] = (acct[r.accountId] ?: BigDecimal.ZERO) + sign * BigDecimal.valueOf(r.account)
        }
        return main.keys.associateWith { Net(main.getValue(it).toDouble(), acct.getValue(it).toDouble()) }
    }

    /** Balance of [account] in its own currency, including debit cards linked to it. */
    fun balance(ref: RefData, account: AccountEntity, net: Map<String?, Net>): Double {
        val useMain = account.currencyId == ref.mainCurrency.id
        val ids = listOf(account.id) + ref.linkedDebitCards(account.id).map { it.id }
        return Money.sum(ids.map { id -> net[id]?.let { if (useMain) it.main else it.account } ?: 0.0 })
    }

    /** Net of the "None" pseudo-account in main currency. */
    fun unassigned(net: Map<String?, Net>): Double = net[null]?.main ?: 0.0

    /** Value converted to the main currency at the current rate. */
    fun toMain(ref: RefData, value: Double, accountId: String): Double {
        val c = ref.accountCurrency(accountId)
        val rounded = Money.round(value, c.decimals)
        if (c.id == ref.mainCurrency.id) return rounded
        val rate = if (c.rate == 0.0) 1.0 else c.rate
        return Money.round(Money.multiply(rounded, rate), ref.mainCurrency.decimals)
    }

    fun rowMeasure(ref: RefData, row: TransactionEntity, forAccount: AccountEntity): Double =
        if (forAccount.currencyId == ref.mainCurrency.id) row.amountMain else row.amountAccount

    fun signed(row: TransactionEntity, value: Double): Double = if (row.kind.isInflow) value else -value
}

/** One account as shown on the Accounts tab. Values are in the account's currency. */
data class AccountFigure(
    val account: AccountEntity,
    val groupType: GroupType,
    val balance: Double,
    val card: CardFigures? = null,
    val excluded: Boolean = false,
)

data class GroupFigure(
    val groupId: String,
    val subtotal: Double,
    /** Credit-card groups: Σ(payable + other). */
    val payableSubtotal: Double = 0.0,
)

data class AccountsSummary(
    val assets: Double,
    val liabilities: Double,
    val groups: Map<String, GroupFigure>,
) {
    val total: Double get() = Money.subtract(kotlin.math.abs(assets), kotlin.math.abs(liabilities))
}

object AccountsSummaryCalculator {
    fun summarize(ref: RefData, figures: List<AccountFigure>, unassigned: Double?): AccountsSummary {
        var assets = BigDecimal.ZERO
        var liabilities = BigDecimal.ZERO
        val subtotal = HashMap<String, BigDecimal>()
        val payable = HashMap<String, BigDecimal>()
        fun addAssetOrLiability(v: Double) {
            if (v > 0) assets += BigDecimal.valueOf(v) else liabilities += BigDecimal.valueOf(kotlin.math.abs(v))
        }
        for (f in figures) {
            val a = f.account
            val group = ref.groupById[a.groupId] ?: continue
            if (group.status != GroupStatus.ACTIVE) continue
            if (a.status == AccountStatus.DELETED || a.status == AccountStatus.PURGED) continue
            val isDebit = f.groupType == GroupType.DEBIT_CARD
            if (!a.includeInTotals && !isDebit) continue
            val main = Balances.toMain(ref, f.card?.outstanding ?: f.balance, a.id)
            subtotal[group.id] = (subtotal[group.id] ?: BigDecimal.ZERO) + BigDecimal.valueOf(main)
            if (f.card != null) {
                val p = Balances.toMain(ref, f.card.payable, a.id)
                val o = Balances.toMain(ref, f.card.other, a.id)
                payable[group.id] = (payable[group.id] ?: BigDecimal.ZERO) + BigDecimal.valueOf(p + o)
            }
            if (isDebit) continue
            addAssetOrLiability(main)
            if (f.card != null) {
                val p = Balances.toMain(ref, f.card.payable, a.id)
                val o = Balances.toMain(ref, f.card.other, a.id)
                if (p < 0) liabilities += BigDecimal.valueOf(-p)
                if (o < 0) liabilities += BigDecimal.valueOf(-o)
            }
        }
        if (unassigned != null) addAssetOrLiability(unassigned)
        val groups = (subtotal.keys + payable.keys).associateWith {
            GroupFigure(it, (subtotal[it] ?: BigDecimal.ZERO).toDouble(), (payable[it] ?: BigDecimal.ZERO).toDouble())
        }
        return AccountsSummary(assets.toDouble(), liabilities.toDouble(), groups)
    }

    /** Figures for "All" mode: balances without date limit, card statements as of [today], debit cards by current month. */
    fun allModeFigures(
        ref: RefData,
        net: Map<String?, Net>,
        debitMonthNet: Map<String?, Net>,
        cardRows: Map<String, List<TransactionEntity>>,
        today: LocalDate,
    ): List<AccountFigure> = ref.orderedAccounts().mapNotNull { a ->
        val type = ref.groupType(a.id) ?: return@mapNotNull null
        when (type) {
            GroupType.DEBIT_CARD -> AccountFigure(a, type, debitMonthNet[a.id]?.account ?: 0.0, excluded = true)
            GroupType.CREDIT_CARD -> {
                val rows = cardRows[a.id].orEmpty()
                val card = CardStatement.compute(ref, a, rows, today)
                AccountFigure(a, type, Balances.balance(ref, a, net), card, excluded = !a.includeInTotals)
            }
            GroupType.NORMAL -> AccountFigure(a, type, Balances.balance(ref, a, net), excluded = !a.includeInTotals)
        }
    }

    /** Figures for period modes: cumulative balances as of the period end, no statement split. */
    fun periodFigures(ref: RefData, net: Map<String?, Net>): List<AccountFigure> = ref.orderedAccounts().mapNotNull { a ->
        val type = ref.groupType(a.id) ?: return@mapNotNull null
        AccountFigure(a, type, Balances.balance(ref, a, net), excluded = type == GroupType.DEBIT_CARD || !a.includeInTotals)
    }
}

/** Credit-card statement figures, negative = owed. */
data class CardFigures(
    val payable: Double,
    val outstanding: Double,
    val other: Double,
    val current: DateRange,
    val previous: DateRange,
) {
    /** Pre-fill overdue + payable. */
    val payAmount: Double
        get() {
            val owed = Money.add(payable, other)
            return if (owed < 0) -owed else 0.0
        }
}

object CardStatement {
    /** [rows] = all rows of the card and its linked debit cards. Prepayment tags are not used in Nexo. */
    fun compute(ref: RefData, card: AccountEntity, rows: List<TransactionEntity>, today: LocalDate): CardFigures {
        val engine = PeriodEngine(card.settlementDay.coerceIn(1, 31))
        val c = engine.monthPeriodContaining(today).range
        val p = engine.monthPeriodContaining(c.start.minusDays(1)).range
        var cb = BigDecimal.ZERO
        var ca = BigDecimal.ZERO
        var old = BigDecimal.ZERO
        var prev = BigDecimal.ZERO
        var cur = BigDecimal.ZERO
        for (r in rows) {
            val d = LocalDate.parse(r.date)
            val value = Balances.rowMeasure(ref, r, card)
            val signed = if (r.kind.isInflow) value else -value
            val isCredit = signed > 0
            val amt = BigDecimal.valueOf(kotlin.math.abs(signed))
            if (isCredit) {
                if (!d.isAfter(p.end)) cb += amt
                if (!d.isBefore(c.start)) ca += amt
            } else {
                when {
                    d.isBefore(p.start) -> old += amt
                    d in p -> prev += amt
                    d in c -> cur += amt
                    else -> Unit // future statements are ignored
                }
            }
        }
        val zero = BigDecimal.ZERO
        val u1 = (old - cb).max(zero)
        val s1 = (cb - old).max(zero)
        val overdue = (u1 - ca).max(zero)
        val caRest = (ca - u1).max(zero)
        val pool = s1 + caRest
        val payableAmt = (prev - pool).max(zero)
        val s3 = (pool - prev).max(zero)
        var payable = payableAmt.negate().toDouble()
        val outstanding = (cur - s3).negate().toDouble()
        var other = overdue.negate().toDouble()
        if (kotlin.math.abs(other) < 1) {
            payable = Money.add(payable, other)
            other = 0.0
        }
        val d = ref.accountCurrency(card.id).decimals
        return CardFigures(Money.round(payable, d), Money.round(outstanding, d), Money.round(other, d), c, p)
    }

    /** Payment date for a statement ending on [end]: first payment-day date strictly after it. */
    fun paymentDate(end: LocalDate, paymentDay: Int): LocalDate {
        var month = java.time.YearMonth.from(end)
        repeat(3) {
            val candidate = month.atDay(minOf(paymentDay.coerceIn(1, 31), month.lengthOfMonth()))
            if (candidate.isAfter(end)) return candidate
            month = month.plusMonths(1)
        }
        return month.atDay(minOf(paymentDay, month.lengthOfMonth()))
    }
}
