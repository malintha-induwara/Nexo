package com.nexo.app.widget

import android.content.Context
import android.content.res.Configuration
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.money.AmountFormatter
import com.nexo.app.core.money.Money
import com.nexo.app.core.settings.DevicePrefs
import com.nexo.app.core.settings.ThemeMode
import com.nexo.app.data.RefRepository
import com.nexo.app.domain.CardStatement
import com.nexo.app.domain.Totals
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun db(): NexoDatabase
    fun refs(): RefRepository
    fun prefs(): DevicePrefs
}

data class WidgetColors(val background: Long, val text: Long, val muted: Long, val dark: Boolean, val opacity: Float)

data class MonthFigures(
    val ready: Boolean,
    val income: String = "",
    val expense: String = "",
    val total: String = "",
    val incomeOnly: Double = 0.0,
    val expenseOnly: Double = 0.0,
    val carryOver: Double = 0.0,
    val incomeFormatted: String = "",
    val expenseFormatted: String = "",
    val carryFormatted: String = "",
    val incomeDiff: Double = 0.0,
    val expenseDiff: Double = 0.0,
    val incomeDiffText: String = "",
    val expenseDiffText: String = "",
    val incomeColor: Long = 0xFF1F88FF,
    val expenseColor: Long = 0xFFFF603B,
)

data class CardLine(val id: String, val name: String, val payable: String, val outstanding: String, val payableNegative: Boolean, val outstandingNegative: Boolean)

object WidgetData {
    private val _revision = MutableStateFlow(0L)

    /** Moves after every data or settings change; widget sessions that are still running reload on it. */
    val revision: StateFlow<Long> = _revision

    fun bump() = _revision.update { it + 1 }

    fun entry(context: Context): WidgetEntryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

    suspend fun colors(context: Context): WidgetColors {
        val s = entry(context).prefs().current()
        val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = when (s.widgetTheme) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> systemDark
        }
        return if (dark) WidgetColors(0xFF1E1E1E, 0xFFFFFFFF, 0xFFAAAAAA, true, s.widgetOpacity)
        else WidgetColors(0xFFFFFFFF, 0xFF1B1B1B, 0xFF777777, false, s.widgetOpacity)
    }

    /** UI-028 figures for the current month period, with the "vs. MoM" comparison (BR-9). */
    suspend fun month(context: Context): MonthFigures {
        val e = entry(context)
        if (!e.prefs().current().setupDone) return MonthFigures(false)
        val ref = e.refs().snapshot()
        val dao = e.db().transactionDao()
        val today = LocalDate.now()
        val engine = ref.settings.periods
        val period = engine.monthPeriodContaining(today)
        val prev = engine.previous(period)
        val now = Totals.fromAggregates(ref, dao.aggregatesBetween(period.range.startText, period.range.endText))
        val toDate = Totals.fromAggregates(ref, dao.aggregatesBetween(period.range.startText, today.toString()))
        val prevToDate = Totals.fromAggregates(ref, dao.aggregatesBetween(prev.range.startText, today.minusMonths(1).toString()))
        val carry = if (ref.settings.carryOver) Totals.fromAggregates(ref, dao.aggregatesBefore(period.range.startText)).let { Money.subtract(it.income, it.expense) } else 0.0
        val main = ref.mainCurrency
        val inc = now.income + if (carry > 0) carry else 0.0
        val exp = now.expense + if (carry < 0) -carry else 0.0
        fun diff(cur: Double, before: Double): String {
            val d = cur - before
            val pct = if (before != 0.0) kotlin.math.abs(d) * 100 / before else null
            val arrow = if (d > 0) "▲" else if (d < 0) "▼" else ""
            return "$arrow ${AmountFormatter.format(kotlin.math.abs(d), main)}" + if (pct != null && pct < 500) " (${pct.toInt()}%)" else ""
        }
        val set = ref.settings.colorSet
        val blue = 0xFF1F88FF
        val red = 0xFFFF603B
        return MonthFigures(
            ready = true,
            income = AmountFormatter.format(inc, main),
            expense = AmountFormatter.format(exp, main),
            total = AmountFormatter.format(inc - exp, main),
            incomeOnly = now.income,
            expenseOnly = now.expense,
            carryOver = carry,
            incomeFormatted = AmountFormatter.format(now.income, main),
            expenseFormatted = AmountFormatter.format(now.expense, main),
            carryFormatted = AmountFormatter.format(carry, main),
            incomeDiff = toDate.income - prevToDate.income,
            expenseDiff = toDate.expense - prevToDate.expense,
            incomeDiffText = diff(toDate.income, prevToDate.income),
            expenseDiffText = diff(toDate.expense, prevToDate.expense),
            incomeColor = if (set == com.nexo.app.core.model.ColorSet.A) blue else red,
            expenseColor = if (set == com.nexo.app.core.model.ColorSet.A) red else blue,
        )
    }

    /** UI-030 credit-card rows. Null when the app isn't set up yet. */
    suspend fun cards(context: Context): List<CardLine>? {
        val e = entry(context)
        if (!e.prefs().current().setupDone) return null
        val ref = e.refs().snapshot()
        val dao = e.db().transactionDao()
        return ref.orderedAccounts().filter { a ->
            ref.groupType(a.id) == GroupType.CREDIT_CARD && a.status == AccountStatus.ACTIVE && ref.groupById[a.groupId]?.status == GroupStatus.ACTIVE
        }.map { a ->
            val f = CardStatement.compute(ref, a, dao.getAllForAccounts(listOf(a.id) + ref.linkedDebitCards(a.id).map { it.id }), LocalDate.now())
            val cur = ref.accountCurrency(a.id)
            CardLine(a.id, a.name, AmountFormatter.format(kotlin.math.abs(f.payable + f.other), cur), AmountFormatter.format(kotlin.math.abs(f.outstanding), cur), f.payable + f.other < 0, f.outstanding < 0)
        }
    }
}
