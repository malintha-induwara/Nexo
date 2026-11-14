package com.nexo.app.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.Favorite
import com.nexo.app.core.model.RefData
import com.nexo.app.data.repository.FavoritesRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookmarksUiState(
    val ref: RefData = RefData(),
    val favorites: List<Favorite> = emptyList(),
)

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val repo: FavoritesRepository,
    refs: ReferenceDataRepository,
) : ViewModel() {
    val uiState: StateFlow<BookmarksUiState> = combine(
        refs.getRefDataStream(),
        repo.getFavoritesStream(),
        ::BookmarksUiState,
    )
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookmarksUiState())

    fun move(ids: List<String>) = viewModelScope.launch { repo.reorder(ids) }
    fun delete(ids: List<String>) = viewModelScope.launch { repo.delete(ids) }
}
