package com.nexo.app.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.Tag
import com.nexo.app.data.repository.TagsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TagSettingsUiState(val tags: List<Tag> = emptyList(), val dialog: TagDialog? = null)

@HiltViewModel
class TagSettingsViewModel @Inject constructor(repo: TagsRepository) : ViewModel() {
    private val editor = TagEditor(repo, viewModelScope) { uiState.value.tags }

    val actions: TagActions = editor.actions

    val uiState: StateFlow<TagSettingsUiState> =
        combine(repo.getTagsStream(), editor.dialog) { tags, d ->
            TagSettingsUiState(tags.filter { !it.isDeleted }, d)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagSettingsUiState())
}
