package com.nexo.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.common.period.DateRange
import com.nexo.app.core.common.period.toIsoText
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.model.RefData
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import com.nexo.app.domain.AccountFigure
import com.nexo.app.domain.AccountsSummary
import com.nexo.app.domain.AccountsSummaryCalculator
import com.nexo.app.domain.Balances
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AccountsMode { ALL, WEEKLY, MONTHLY, ANNUALLY, PERIOD }

data class AccountsQuery(
    val mode: AccountsMode = AccountsMode.ALL,
    val anchor: LocalDate = LocalDate.now(),
    val from: LocalDate = LocalDate.now(),
    val to: LocalDate = LocalDate.now(),
)

data class AccountsData(
    val loaded: Boolean = false,
    val figures: List<AccountFigure> = emptyList(),
    val summary: AccountsSummary = AccountsSummary(0.0, 0.0, emptyMap()),
    /** Change vs the previous period, per account (period modes). */
    val changes: Map<String, Double> = emptyMap(),
    val unassigned: Double? = null,
    val range: DateRange? = null,
    val debitMonthLabel: YearMonth? = null,
)

data class AccountsUiState(
    val ref: RefData = RefData(),
    val device: DeviceSettings? = null,
    val query: AccountsQuery = AccountsQuery(),
    val data: AccountsData = AccountsData(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val transactions: TransactionsRepository,
    refs: ReferenceDataRepository,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {
    /** Kept for event handlers that need the reference data synchronously. */
    private val ref: StateFlow<RefData> = refs.getRefDataStream().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        RefData(),
    )
    private val query = MutableStateFlow(AccountsQuery())

    private val data = combine(ref, query, transactions.getChangeStampStream()) { r, q, _ ->
        r to q
    }
        .mapLatest { (r, q) -> compute(r, q) }

    val uiState: StateFlow<AccountsUiState> = combine(
        ref,
        prefs.getDeviceSettingsStream(),
        query,
        data,
        ::AccountsUiState,
    )
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun range(r: RefData, q: AccountsQuery): DateRange? {
        val e = r.settings.periods
        return when (q.mode) {
            AccountsMode.ALL -> null
            AccountsMode.WEEKLY -> e.weekContaining(q.anchor)
            AccountsMode.MONTHLY -> e.monthPeriodContaining(q.anchor).range
            AccountsMode.ANNUALLY -> e.yearRange(e.yearOf(q.anchor))
            AccountsMode.PERIOD -> DateRange(q.from, q.to)
        }
    }

    private suspend fun compute(r: RefData, q: AccountsQuery): AccountsData {
        val today = LocalDate.now()
        val range = range(r, q)
        if (range == null) {
            val net = Balances.netByAccount(transactions.getAggregatesUpTo("9999-12-31"))
            val month = r.settings.periods.monthPeriodContaining(today)
            val debitNet = Balances.netByAccount(
                transactions.getAggregatesBetween(month.range.startText, month.range.endText),
            )
            val cards = r.accounts.filter { r.groupType(it.id) == GroupType.CREDIT_CARD }
            val cardRows = cards.associate { c ->
                c.id to
                    transactions.getAllForAccounts(
                        listOf(c.id) + r.linkedDebitCards(c.id).map {
                            it.id
                        },
                    )
            }
            val figures = AccountsSummaryCalculator.allModeFigures(
                r,
                net,
                debitNet,
                cardRows,
                today,
            )
            val unassigned = if (net.containsKey(null)) Balances.unassigned(net) else null
            return AccountsData(
                true,
                figures,
                AccountsSummaryCalculator.summarize(r, figures, unassigned),
                unassigned = unassigned,
                debitMonthLabel = month.label,
            )
        }
        val net = Balances.netByAccount(transactions.getAggregatesUpTo(range.endText))
        val previousEnd = when (q.mode) {
            AccountsMode.WEEKLY -> range.start.minusDays(1)
            AccountsMode.MONTHLY -> range.start.minusDays(1)
            AccountsMode.ANNUALLY -> range.start.minusDays(1)
            else -> q.from.minusDays(1)
        }
        val prevNet = Balances.netByAccount(transactions.getAggregatesUpTo(previousEnd.toIsoText()))
        val figures = AccountsSummaryCalculator.periodFigures(r, net)
        val changes = figures.associate { f ->
            f.account.id to
                (f.balance - Balances.balance(r, f.account, prevNet))
        }
        val unassigned = if (net.containsKey(null)) Balances.unassigned(net) else null
        return AccountsData(
            true,
            figures,
            AccountsSummaryCalculator.summarize(r, figures, unassigned),
            changes,
            unassigned,
            range,
        )
    }

    fun setMode(m: AccountsMode) = query.update {
        val month = ref.value.settings.periods.monthPeriodContaining(LocalDate.now())
        it.copy(mode = m, anchor = LocalDate.now(), from = month.start, to = month.end)
    }

    fun setRange(from: LocalDate, to: LocalDate) = query.update { it.copy(from = from, to = to) }
    fun setAnchor(d: LocalDate) = query.update { it.copy(anchor = d) }

    fun step(forward: Boolean) = query.update { q ->
        val e = ref.value.settings.periods
        val d = q.anchor
        q.copy(
            anchor = when (q.mode) {
                AccountsMode.WEEKLY -> d.plusDays(if (forward) 7 else -7)

                AccountsMode.MONTHLY -> e.monthPeriodContaining(d).let {
                    if (forward) e.next(it).end else e.previous(it).end
                }

                AccountsMode.ANNUALLY -> e.yearRange(
                    e.yearOf(d) + if (forward) 1 else -1,
                ).start.plusDays(15)

                else -> d
            },
        )
    }

    fun goToday() = query.update { it.copy(anchor = LocalDate.now()) }

    fun toggleCollapsed(groupId: String) = viewModelScope.launch {
        val cur = prefs.getDeviceSettings().collapsedGroups
        prefs.setCollapsedGroups(if (groupId in cur) cur - groupId else cur + groupId)
    }
}
