package com.nexo.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.RefData
import com.nexo.app.data.repository.CurrenciesRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CurrencyViewModel @Inject constructor(refs: ReferenceDataRepository, val repo: CurrenciesRepository) : ViewModel() {
    val ref = refs.getRefDataStream().stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    val busy = MutableStateFlow(false)
    fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
}
