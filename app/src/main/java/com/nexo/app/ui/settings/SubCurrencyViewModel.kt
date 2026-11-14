package com.nexo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.common.money.CatalogCurrency
import com.nexo.app.core.model.Currency
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.SymbolPosition
import com.nexo.app.data.repository.CurrenciesRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SubCurrencyUiState(
    val ref: RefData = RefData(),
    /** A sub-currency was saved: the screen goes back, then calls [SubCurrencyViewModel.onClosedHandled]. */
    val closed: Boolean = false,
)

/** Sub-currencies: their order, hiding them, and adding or editing one. */
@HiltViewModel
class SubCurrencyViewModel @Inject constructor(
    refs: ReferenceDataRepository,
    private val repo: CurrenciesRepository,
) : ViewModel() {
    private val closed = MutableStateFlow(false)

    val uiState: StateFlow<SubCurrencyUiState> =
        combine(refs.getRefDataStream(), closed, ::SubCurrencyUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubCurrencyUiState())

    fun reorder(ids: List<String>) = viewModelScope.launch { repo.reorder(ids) }

    fun hide(id: String) = viewModelScope.launch { repo.hide(id) }

    /** Saves a custom sub-currency when [catalog] is null, otherwise a built-in one. */
    fun saveSub(
        catalog: CatalogCurrency?,
        existing: Currency?,
        name: String,
        symbol: String,
        position: SymbolPosition,
        decimals: Int,
        rate: Double,
    ) = viewModelScope.launch {
        val row = if (catalog == null) {
            repo.customSub(existing?.id, name, symbol, position, decimals, rate)
        } else {
            repo.builtinSub(catalog, rate, position, decimals, existing)
        }
        repo.saveSub(row)
        closed.value = true
    }

    fun onClosedHandled() {
        closed.value = false
    }
}
