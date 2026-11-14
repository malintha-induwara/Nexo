package com.nexo.app.ui.entry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.RepeatReflect
import com.nexo.app.data.repository.BookSettingsRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.RepeatRulesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RepeatListViewModel @Inject constructor(
    private val repo: RepeatRulesRepository,
    private val refs: ReferenceDataRepository,
    private val bookSettings: BookSettingsRepository,
    private val engine: RepeatRulesRepository,
) : ViewModel() {
    val rules = repo.getRulesStream().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val ref = refs.getRefDataStream().stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    fun delete(ids: List<String>) = viewModelScope.launch { repo.delete(ids) }
    fun setReflect(r: RepeatReflect) = viewModelScope.launch {
        bookSettings.updateBookSettings { it.copy(repeatReflect = r) }
        engine.generate()
    }
}
