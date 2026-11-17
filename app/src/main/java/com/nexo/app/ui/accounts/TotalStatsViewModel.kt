package com.nexo.app.ui.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.common.period.MonthPeriod
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.ui.common.savedStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class TotalStatsUiState(
    val ref: RefData = RefData(),
    val anchor: LocalDate,
    val periods: List<MonthPeriod> = emptyList(),
    val balances: List<Double> = emptyList(),
    val income: List<Double> = emptyList(),
    val expense: List<Double> = emptyList(),
)

@HiltViewModel
class TotalStatsViewModel @Inject constructor(
    refs: ReferenceDataRepository,
    private val transactions: TransactionsRepository,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    // Read synchronously by event handlers; it feeds uiState, so it's current while shown.
    private val ref = refs.getRefDataStream().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RefData(),
    )
    private val anchor = savedState.savedStateFlow("anchor", LocalDate.now(clock))

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TotalStatsUiState> = combine(
        ref,
        anchor,
        transactions.getChangeStampStream(),
    ) {
            r,
            a,
            _,
        ->
        r to
            a
    }.mapLatest { (r, a) ->
        val e = r.settings.periods
        var p = e.monthPeriodContaining(a)
        val periods = ArrayList<MonthPeriod>()
        repeat(6) {
            periods.add(0, p)
            p = e.previous(p)
        }
        // Included non-debit accounts, their debit cards, and unassigned rows.
        val included = r.accounts.filter { acc ->
            val g = r.groupById[acc.groupId] ?: return@filter false
            g.type != GroupType.DEBIT_CARD && acc.status != AccountStatus.DELETED &&
                acc.status != AccountStatus.PURGED &&
                acc.includeInTotals
        }.map { it.id }.toSet()
        val withDebits =
            included +
                r.accounts.filter {
                    it.linkedAccountId in included &&
                        r.groupType(it.id) == GroupType.DEBIT_CARD
                }.map { it.id }
        val balances = periods.map { per ->
            transactions.getAggregatesUpTo(per.range.endText).filter {
                it.accountId == null ||
                    it.accountId in withDebits
            }
                .fold(BigDecimal.ZERO) { acc, x ->
                    acc +
                        BigDecimal.valueOf(if (x.kind.isInflow) x.main else -x.main)
                }.toDouble()
        }
        val sums = periods.map { per ->
            transactions.getAggregatesBetween(per.range.startText, per.range.endText)
        }
        TotalStatsUiState(
            r,
            a,
            periods,
            balances,
            sums.map { s -> s.filter { it.kind == TxKind.INCOME }.sumOf { it.main } },
            sums.map { s -> s.filter { it.kind == TxKind.EXPENSE }.sumOf { it.main } },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TotalStatsUiState(anchor = anchor.value),
    )

    fun pickMonth(month: YearMonth) {
        anchor.value = ref.value.settings.periods.monthPeriodFor(month).end
    }

    fun step(forward: Boolean) {
        val e = ref.value.settings.periods
        val p = e.monthPeriodContaining(anchor.value)
        anchor.value = (if (forward) e.next(p) else e.previous(p)).end
    }
}
