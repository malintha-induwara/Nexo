package com.nexo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.common.money.CatalogCurrency
import com.nexo.app.core.model.Currency
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.SymbolPosition
import com.nexo.app.data.repository.CurrenciesRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.ui.designsystem.UiText
import com.nexo.app.ui.designsystem.UserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CurrencyUiState(
    val ref: RefData = RefData(),
    /** A main-currency change is running. */
    val busy: Boolean = false,
    /** Shown once, then cleared with [CurrencyViewModel.onUserMessageShown]. */
    val userMessage: UserMessage? = null,
    /** A change was written: the screen goes back, then calls [CurrencyViewModel.onClosedHandled]. */
    val closed: Boolean = false,
)

/** Shared by the main-currency and sub-currency screens. */
@HiltViewModel
class CurrencyViewModel @Inject constructor(
    refs: ReferenceDataRepository,
    private val repo: CurrenciesRepository,
) : ViewModel() {
    private val effects = MutableStateFlow(CurrencyUiState())

    val uiState: StateFlow<CurrencyUiState> = combine(refs.getRefDataStream(), effects) { r, e ->
        e.copy(ref = r)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CurrencyUiState())

    fun setMainFormat(position: SymbolPosition, decimals: Int) = viewModelScope.launch {
        repo.setMainFormat(position, decimals)
        effects.update { it.copy(closed = true) }
    }

    /** "Data retention": amounts convert with [rate]; a safety backup is restored if anything fails. */
    fun changeMainWithRate(target: CatalogCurrency, rate: Double) = viewModelScope.launch {
        effects.update { it.copy(busy = true) }
        val ok = repo.changeMainWithRate(target, rate)
        effects.update {
            it.copy(
                busy = false,
                userMessage = UserMessage(
                    UiText.of(if (ok) R.string.main_changed else R.string.main_change_failed),
                ),
                closed = true,
            )
        }
    }

    /** "Data initialization": everything is reset with [target] as the main currency. */
    fun changeMainWithReset(target: CatalogCurrency) = viewModelScope.launch {
        effects.update { it.copy(busy = true) }
        repo.changeMainWithReset(target)
        effects.update {
            it.copy(
                busy = false,
                userMessage = UserMessage(UiText.of(R.string.main_changed)),
                closed = true,
            )
        }
    }

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
        effects.update { it.copy(closed = true) }
    }

    fun onUserMessageShown(id: Long) = effects.update {
        if (it.userMessage?.id == id) {
            it.copy(userMessage = null)
        } else {
            it
        }
    }

    fun onClosedHandled() = effects.update { it.copy(closed = false) }
}
