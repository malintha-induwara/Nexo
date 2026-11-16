package com.nexo.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.Budget
import com.nexo.app.core.model.BudgetAmount
import com.nexo.app.core.model.RefData
import com.nexo.app.data.repository.BudgetsRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.domain.BudgetBlockData
import com.nexo.app.domain.BudgetCalc
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BudgetSettingsUiState(
    val ref: RefData = RefData(),
    val budgets: List<Budget> = emptyList(),
    val amounts: List<BudgetAmount> = emptyList(),
    val list: BudgetBlockData? = null,
    /** A budget was added: the add screen goes back, then calls [BudgetSettingsViewModel.onAddedHandled]. */
    val added: Boolean = false,
)

/** Shared by Budget Settings, Add Budget and the monthly editor. */
@HiltViewModel
class BudgetSettingsViewModel @Inject constructor(
    refs: ReferenceDataRepository,
    private val repo: BudgetsRepository,
    private val clock: Clock,
) : ViewModel() {
    /**
     * Kept for event handlers that need the reference data synchronously. It feeds [uiState], so it
     * is current whenever the screen is there to call them.
     */
    private val ref: StateFlow<RefData> = refs.getRefDataStream().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RefData(),
    )
    private val added = MutableStateFlow(false)

    private val currentKey: Int get() = ref.value.settings.periods.monthPeriodContaining(
        LocalDate.now(clock),
    ).key

    val uiState: StateFlow<BudgetSettingsUiState> = combine(
        ref,
        repo.getBudgetsStream(),
        repo.getBudgetAmountsStream(),
        added,
    ) {
            r,
            b,
            a,
            done,
        ->
        val list = BudgetCalc.compute(
            r,
            b,
            a,
            emptyList(),
            r.settings.periods.monthPeriodContaining(LocalDate.now(clock)).key,
            forSettings = true,
        )
        BudgetSettingsUiState(r, b, a, list, done)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetSettingsUiState())

    fun add(categoryId: String?, amount: Double) = viewModelScope.launch {
        repo.add(categoryId, amount, currentKey)
        added.value = true
    }

    fun onAddedHandled() {
        added.value = false
    }

    fun setMonth(budgetId: String, key: Int, amount: Double) = viewModelScope.launch {
        repo.setMonth(budgetId, key, amount)
    }

    fun setDefault(budgetId: String, amount: Double) = viewModelScope.launch {
        repo.setDefault(budgetId, currentKey, amount)
    }

    fun delete(budgetId: String) = viewModelScope.launch { repo.delete(budgetId) }
}
