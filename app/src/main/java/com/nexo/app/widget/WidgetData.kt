package com.nexo.app.widget

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import com.nexo.app.core.common.ApplicationScope
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.common.money.Money
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.datastore.DevicePrefs
import com.nexo.app.data.datastore.ThemeMode
import com.nexo.app.data.repository.RefRepository
import com.nexo.app.domain.CardStatement
import com.nexo.app.domain.Totals
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun db(): NexoDatabase
    fun refs(): RefRepository
    fun prefs(): DevicePrefs

    @ApplicationScope
    fun appScope(): CoroutineScope
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
    val incomeColor: Long = 0xFF2462E4,
    val expenseColor: Long = 0xFFD92D20,
)

data class CardLine(val id: String, val name: String, val payable: String, val outstanding: String, val payableNegative: Boolean, val outstandingNegative: Boolean)

object WidgetData {
    private val _revision = MutableStateFlow(0L)

    /** Moves after every data or settings change; widget sessions that are still running reload on it. */
    val revision: StateFlow<Long> = _revision

    fun bump() = _revision.update { it + 1 }

    fun entry(context: Context): WidgetEntryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

    private fun isDark(context: Context, theme: ThemeMode): Boolean = when (theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        // The app's own configuration follows the in-app theme (MainActivity hands it to the system),
        // so ask for the phone's setting first; scheduled dark mode falls back to the configuration.
        ThemeMode.SYSTEM -> when (context.getSystemService(UiModeManager::class.java)?.nightMode) {
            UiModeManager.MODE_NIGHT_YES -> true
            UiModeManager.MODE_NIGHT_NO -> false
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }

    /** Loop palette: graphite surface in dark, pure white in light. */
    suspend fun colors(context: Context): WidgetColors {
        val s = entry(context).prefs().current()
        return if (isDark(context, s.widgetTheme)) WidgetColors(0xFF1C1C1C, 0xFFF5F5F5, 0xFFA8A8A8, true, s.widgetOpacity)
        else WidgetColors(0xFFFFFFFF, 0xFF111111, 0xFF5C5C5C, false, s.widgetOpacity)
    }

    /** Figures for the current month period, with the "vs. MoM" comparison. */
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
        val dark = isDark(context, e.prefs().current().widgetTheme)
        val blue = if (dark) 0xFF64AFFD else 0xFF2462E4
        val red = if (dark) 0xFFFF6961 else 0xFFD92D20
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

    /** Credit-card rows. Null when the app isn't set up yet. */
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
