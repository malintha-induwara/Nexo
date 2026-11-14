package com.nexo.app.ui.budgets

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.common.period.MonthPeriod
import com.nexo.app.core.model.RefData
import com.nexo.app.data.repository.BudgetRepository
import com.nexo.app.data.repository.LedgerRepository
import com.nexo.app.data.repository.RefRepository
import com.nexo.app.domain.BudgetBlockData
import com.nexo.app.domain.BudgetCalc
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** Budget block of the Total page for one month period. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetBlockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    refs: RefRepository,
    repo: BudgetRepository,
    private val ledgers: LedgerRepository,
) : ViewModel() {
    val ref: StateFlow<RefData> = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    private val period = MutableStateFlow<MonthPeriod?>(null)

    fun setPeriod(p: MonthPeriod) {
        period.value = p
    }

    val data: StateFlow<BudgetBlockData?> = period.filterNotNull().flatMapLatest { p ->
        combine(ref, repo.budgets, repo.amounts, ledgers.observe(p.range)) { r, b, a, l ->
            BudgetCalc.compute(r, b, a, l.rows, p.key, context.getString(R.string.total_budget))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
