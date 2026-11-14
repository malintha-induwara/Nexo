package com.nexo.app.ui.entry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.RefData
import com.nexo.app.data.repository.FavoriteRepository
import com.nexo.app.data.repository.RefRepository
import com.nexo.app.ui.HomeState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    val repo: FavoriteRepository,
    refs: RefRepository,
    val home: HomeState,
) : ViewModel() {
    val favorites = repo.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val ref = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    fun move(ids: List<String>) = viewModelScope.launch { repo.reorder(ids) }
    fun delete(ids: List<String>) = viewModelScope.launch { repo.delete(ids) }
}
