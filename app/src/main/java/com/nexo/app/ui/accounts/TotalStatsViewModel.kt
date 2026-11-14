package com.nexo.app.ui.accounts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.common.period.MonthPeriod
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.repository.RefRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class TotalStatsUi(val periods: List<MonthPeriod> = emptyList(), val balances: List<Double> = emptyList(), val income: List<Double> = emptyList(), val expense: List<Double> = emptyList())

@HiltViewModel
class TotalStatsViewModel @Inject constructor(refs: RefRepository, private val db: NexoDatabase) : ViewModel() {
    val ref = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    val anchor = MutableStateFlow(LocalDate.now())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val ui = combine(ref, anchor, db.transactionDao().observeStamp()) { r, a, _ -> r to a }.mapLatest { (r, a) ->
        val e = r.settings.periods
        var p = e.monthPeriodContaining(a)
        val periods = ArrayList<MonthPeriod>()
        repeat(6) { periods.add(0, p); p = e.previous(p) }
        // B10: included accounts (non-debit, not deleted, included in totals) + their debit cards + unassigned rows.
        val included = r.accounts.filter { acc ->
            val g = r.groupById[acc.groupId] ?: return@filter false
            g.type != GroupType.DEBIT_CARD && acc.status != AccountStatus.DELETED && acc.status != AccountStatus.PURGED && acc.includeInTotals
        }.map { it.id }.toSet()
        val withDebits = included + r.accounts.filter { it.linkedAccountId in included && r.groupType(it.id) == GroupType.DEBIT_CARD }.map { it.id }
        val balances = periods.map { per ->
            db.transactionDao().aggregatesUpTo(per.range.endText).filter { it.accountId == null || it.accountId in withDebits }
                .fold(BigDecimal.ZERO) { acc, x -> acc + BigDecimal.valueOf(if (x.kind.isInflow) x.main else -x.main) }.toDouble()
        }
        val sums = periods.map { per -> db.transactionDao().aggregatesBetween(per.range.startText, per.range.endText) }
        TotalStatsUi(
            periods, balances,
            sums.map { s -> s.filter { it.kind == TxKind.INCOME }.sumOf { it.main } },
            sums.map { s -> s.filter { it.kind == TxKind.EXPENSE }.sumOf { it.main } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TotalStatsUi())

    fun step(forward: Boolean) {
        val e = ref.value.settings.periods
        val p = e.monthPeriodContaining(anchor.value)
        anchor.value = (if (forward) e.next(p) else e.previous(p)).end
    }
}
