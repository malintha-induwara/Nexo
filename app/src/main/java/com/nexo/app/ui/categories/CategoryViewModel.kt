package com.nexo.app.ui.categories

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.RefData
import com.nexo.app.data.repository.CategoryRepository
import com.nexo.app.data.repository.RefRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CategoryViewModel @Inject constructor(private val refs: RefRepository, val repo: CategoryRepository) : ViewModel() {
    val ref = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
    fun setSubcategories(v: Boolean) = viewModelScope.launch { refs.updateSettings { it.copy(subcategories = v) } }
}
