package com.nexo.app.ui.tags

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.data.repository.TagsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TagSettingsViewModel @Inject constructor(val repo: TagsRepository) : ViewModel() {
    val tags = repo.getTagsStream().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
}
