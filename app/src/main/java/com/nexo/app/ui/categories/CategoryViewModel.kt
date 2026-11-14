package com.nexo.app.ui.categories

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.RefData
import com.nexo.app.data.repository.BookSettingsRepository
import com.nexo.app.data.repository.CategoriesRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CategoryViewModel @Inject constructor(private val refs: ReferenceDataRepository, private val bookSettings: BookSettingsRepository, val repo: CategoriesRepository) : ViewModel() {
    val ref = refs.getRefDataStream().stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
    fun setSubcategories(v: Boolean) = viewModelScope.launch { bookSettings.updateBookSettings { it.copy(subcategories = v) } }
}
