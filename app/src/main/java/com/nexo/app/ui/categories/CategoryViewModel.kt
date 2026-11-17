package com.nexo.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.RefData
import com.nexo.app.data.repository.BookSettingsRepository
import com.nexo.app.data.repository.CategoriesRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CategoryNavigation {
    data object Back : CategoryNavigation

    data class SubcategoriesOf(val parentId: String) : CategoryNavigation
}

data class CategoryUiState(
    val ref: RefData = RefData(),
    val navigation: CategoryNavigation? = null,
)

@HiltViewModel
class CategoryViewModel @Inject constructor(
    refs: ReferenceDataRepository,
    private val bookSettings: BookSettingsRepository,
    private val repo: CategoriesRepository,
) : ViewModel() {
    private val navigation = MutableStateFlow<CategoryNavigation?>(null)

    val uiState: StateFlow<CategoryUiState> = combine(
        refs.getRefDataStream(),
        navigation,
        ::CategoryUiState,
    )
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryUiState())

    fun setSubcategories(on: Boolean) = viewModelScope.launch {
        bookSettings.updateBookSettings { it.copy(subcategories = on) }
    }

    fun reorder(ids: List<String>) = viewModelScope.launch { repo.reorder(ids) }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }

    // Adds when existingId is null, otherwise updates; then the form closes.
    fun save(
        kind: CategoryKind,
        existingId: String?,
        name: String,
        parentId: String?,
        emoji: String?,
        colorIndex: Int?,
    ) = viewModelScope.launch {
        if (existingId == null) {
            repo.add(kind, name, parentId, emoji, colorIndex)
        } else {
            repo.update(existingId, name, parentId, emoji, colorIndex)
        }
        navigation.value = CategoryNavigation.Back
    }

    // Its subcategories move too; the target's subcategory list then opens.
    fun moveUnder(sourceId: String, targetId: String) = viewModelScope.launch {
        repo.toSub(sourceId, targetId)
        navigation.value = CategoryNavigation.SubcategoriesOf(targetId)
    }

    fun makeMain(id: String) = viewModelScope.launch {
        repo.toMain(id)
        navigation.value = CategoryNavigation.Back
    }

    fun onNavigationHandled() {
        navigation.value = null
    }
}
