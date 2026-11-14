package com.nexo.app.ui.budgets

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.model.RefData
import com.nexo.app.data.repository.BudgetRepository
import com.nexo.app.data.repository.RefRepository
import com.nexo.app.domain.BudgetBlockData
import com.nexo.app.domain.BudgetCalc
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shared by Budget Settings, Add Budget and the monthly editor. */
@HiltViewModel
class BudgetSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val refs: RefRepository,
    val repo: BudgetRepository,
) : ViewModel() {
    val ref: StateFlow<RefData> = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    val budgets = repo.budgets.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val amounts = repo.amounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentKey: Int get() = ref.value.settings.periods.monthPeriodContaining(LocalDate.now()).key

    val list: StateFlow<BudgetBlockData?> = combine(ref, repo.budgets, repo.amounts) { r, b, a ->
        BudgetCalc.compute(r, b, a, emptyList(), r.settings.periods.monthPeriodContaining(LocalDate.now()).key, context.getString(R.string.total_budget), forSettings = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun add(categoryId: String?, amount: Double, onDone: () -> Unit) = viewModelScope.launch {
        repo.add(categoryId, amount, currentKey)
        onDone()
    }

    fun setMonth(budgetId: String, key: Int, amount: Double) = viewModelScope.launch { repo.setMonth(budgetId, key, amount) }
    fun setDefault(budgetId: String, amount: Double) = viewModelScope.launch { repo.setDefault(budgetId, currentKey, amount) }
    fun delete(budgetId: String, onDone: () -> Unit = {}) = viewModelScope.launch { repo.delete(budgetId); onDone() }
}
