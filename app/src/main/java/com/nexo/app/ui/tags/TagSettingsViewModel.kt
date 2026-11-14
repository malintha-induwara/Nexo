package com.nexo.app.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.Tag
import com.nexo.app.data.repository.TagResult
import com.nexo.app.data.repository.TagsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The dialog open over the tag list. */
sealed interface TagDialog {
    data class Add(val nameTaken: Boolean = false) : TagDialog

    data class Rename(val tag: Tag, val nameTaken: Boolean = false) : TagDialog

    /** The tag is used by transactions: ask before removing it from them. */
    data class ConfirmDelete(val tag: Tag) : TagDialog
}

data class TagSettingsUiState(val tags: List<Tag> = emptyList(), val dialog: TagDialog? = null)

@HiltViewModel
class TagSettingsViewModel @Inject constructor(private val repo: TagsRepository) : ViewModel() {
    private val dialog = MutableStateFlow<TagDialog?>(null)

    val uiState: StateFlow<TagSettingsUiState> = combine(repo.getTagsStream(), dialog) { tags, d ->
        TagSettingsUiState(tags.filter { !it.isDeleted }, d)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagSettingsUiState())

    fun startAdd() {
        dialog.value = TagDialog.Add()
    }

    fun startRename(tag: Tag) {
        dialog.value = TagDialog.Rename(tag)
    }

    fun dismissDialog() {
        dialog.value = null
    }

    fun add(name: String) = viewModelScope.launch {
        if (repo.add(name) == TagResult.Exists) {
            dialog.value = TagDialog.Add(nameTaken = true)
        } else {
            dismissDialog()
        }
    }

    fun rename(name: String) = viewModelScope.launch {
        val current = dialog.value as? TagDialog.Rename ?: return@launch
        if (repo.rename(current.tag.id, name) == TagResult.Exists) {
            dialog.update { current.copy(nameTaken = true) }
        } else {
            dismissDialog()
        }
    }

    /** Unused tags go at once; a tag in use asks first. */
    fun requestDelete(tag: Tag) = viewModelScope.launch {
        if (repo.usageCount(tag.id) > 0) {
            dialog.value = TagDialog.ConfirmDelete(tag)
        } else {
            repo.delete(tag.id)
        }
    }

    fun confirmDelete() = viewModelScope.launch {
        val current = dialog.value as? TagDialog.ConfirmDelete ?: return@launch
        repo.delete(current.tag.id)
        dismissDialog()
    }

    fun move(index: Int, by: Int) = viewModelScope.launch {
        val ids = uiState.value.tags.map { it.id }.toMutableList()
        val target = index + by
        if (target !in ids.indices) return@launch
        ids.add(target, ids.removeAt(index))
        repo.reorder(ids)
    }
}
