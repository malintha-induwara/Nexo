package com.nexo.app.ui.tags

import com.nexo.app.core.model.Tag
import com.nexo.app.data.repository.TagResult
import com.nexo.app.data.repository.TagsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TagDialog {
    data class Add(val nameTaken: Boolean = false) : TagDialog

    data class Rename(val tag: Tag, val nameTaken: Boolean = false) : TagDialog

    // The tag is used by transactions: ask before removing it from them.
    data class ConfirmDelete(val tag: Tag) : TagDialog
}

class TagActions(
    val startAdd: () -> Unit = {},
    val startRename: (Tag) -> Unit = {},
    val add: (name: String) -> Unit = {},
    val rename: (name: String) -> Unit = {},
    // Unused tags go at once; a tag in use asks first.
    val requestDelete: (Tag) -> Unit = {},
    val confirmDelete: () -> Unit = {},
    val dismissDialog: () -> Unit = {},
    val move: (index: Int, by: Int) -> Unit = { _, _ -> },
)

// Shared by the tag settings and the entry form's tag picker. shown is the list as on screen,
// which is what reordering moves within.
class TagEditor(
    private val repo: TagsRepository,
    private val scope: CoroutineScope,
    private val shown: () -> List<Tag>,
) {
    private val _dialog = MutableStateFlow<TagDialog?>(null)
    val dialog: StateFlow<TagDialog?> = _dialog.asStateFlow()

    val actions = TagActions(
        startAdd = { _dialog.value = TagDialog.Add() },
        startRename = { _dialog.value = TagDialog.Rename(it) },
        add = ::add,
        rename = ::rename,
        requestDelete = ::requestDelete,
        confirmDelete = ::confirmDelete,
        dismissDialog = { _dialog.value = null },
        move = ::move,
    )

    private fun add(name: String) {
        scope.launch {
            _dialog.value =
                if (repo.add(name) == TagResult.Exists) TagDialog.Add(nameTaken = true) else null
        }
    }

    private fun rename(name: String) {
        val current = _dialog.value as? TagDialog.Rename ?: return
        scope.launch {
            if (repo.rename(current.tag.id, name) == TagResult.Exists) {
                _dialog.update { current.copy(nameTaken = true) }
            } else {
                _dialog.value = null
            }
        }
    }

    private fun requestDelete(tag: Tag) {
        scope.launch {
            if (repo.usageCount(tag.id) > 0) {
                _dialog.value = TagDialog.ConfirmDelete(tag)
            } else {
                repo.delete(tag.id)
            }
        }
    }

    private fun confirmDelete() {
        val current = _dialog.value as? TagDialog.ConfirmDelete ?: return
        scope.launch {
            repo.delete(current.tag.id)
            _dialog.value = null
        }
    }

    private fun move(index: Int, by: Int) {
        val ids = shown().map { it.id }.toMutableList()
        val target = index + by
        if (index !in ids.indices || target !in ids.indices) return
        ids.add(target, ids.removeAt(index))
        scope.launch { repo.reorder(ids) }
    }
}
