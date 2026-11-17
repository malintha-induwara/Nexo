package com.nexo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.common.money.CatalogCurrency
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

data class MainCurrencyUiState(
    val ref: RefData = RefData(),
    val busy: Boolean = false,
    val userMessage: UserMessage? = null,
    val closed: Boolean = false,
)

@HiltViewModel
class MainCurrencyViewModel @Inject constructor(
    refs: ReferenceDataRepository,
    private val repo: CurrenciesRepository,
) : ViewModel() {
    private val effects = MutableStateFlow(MainCurrencyUiState())

    val uiState: StateFlow<MainCurrencyUiState> = combine(refs.getRefDataStream(), effects) {
            r,
            e,
        ->
        e.copy(ref = r)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainCurrencyUiState())

    fun setMainFormat(position: SymbolPosition, decimals: Int) = viewModelScope.launch {
        repo.setMainFormat(position, decimals)
        effects.update { it.copy(closed = true) }
    }

    // A safety backup is restored if anything fails.
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

    fun onUserMessageShown(id: Long) = effects.update {
        if (it.userMessage?.id == id) {
            it.copy(userMessage = null)
        } else {
            it
        }
    }

    fun onClosedHandled() = effects.update { it.copy(closed = false) }
}
