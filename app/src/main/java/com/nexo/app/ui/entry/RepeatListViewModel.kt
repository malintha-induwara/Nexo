package com.nexo.app.ui.entry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.RepeatReflect
import com.nexo.app.data.repository.RefRepository
import com.nexo.app.data.repository.RepeatEngine
import com.nexo.app.data.repository.RepeatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RepeatListViewModel @Inject constructor(
    private val repo: RepeatRepository,
    private val refs: RefRepository,
    private val engine: RepeatEngine,
) : ViewModel() {
    val rules = repo.rules.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val ref = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    fun delete(ids: List<String>) = viewModelScope.launch { repo.delete(ids) }
    fun setReflect(r: RepeatReflect) = viewModelScope.launch {
        refs.updateSettings { it.copy(repeatReflect = r) }
        engine.run()
    }
}
