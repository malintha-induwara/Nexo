package com.nexo.app.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.model.Tag
import com.nexo.app.data.repository.TagsRepository
import com.nexo.app.ui.designsystem.component.ConfirmDialog
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.TextFieldDialog
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.navigation.LocalNavigator

@Composable
fun TagSettingsRoute(vm: TagSettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    TagSettingsScreen(
        state = state,
        onBack = nav::back,
        onStartAdd = vm::startAdd,
        onStartRename = vm::startRename,
        onMove = vm::move,
        onDelete = vm::requestDelete,
        onAdd = vm::add,
        onRename = vm::rename,
        onConfirmDelete = vm::confirmDelete,
        onDismissDialog = vm::dismissDialog,
    )
}

@Composable
internal fun TagSettingsScreen(
    state: TagSettingsUiState,
    onBack: () -> Unit,
    onStartAdd: () -> Unit,
    onStartRename: (Tag) -> Unit,
    onMove: (index: Int, by: Int) -> Unit,
    onDelete: (Tag) -> Unit,
    onAdd: (name: String) -> Unit,
    onRename: (name: String) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDialog: () -> Unit,
) {
    val tags = state.tags
    val exists = stringResource(R.string.tag_exists)
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.tag_settings), onBack = onBack) {
            IconButton(onClick = onStartAdd) {
                Icon(Icons.Filled.Add, stringResource(R.string.add_tag))
            }
        }
    }) { p ->
        LazyColumn(Modifier.padding(p)) {
            item {
                Text(
                    stringResource(R.string.tag_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (tags.isEmpty()) item { EmptyState(stringResource(R.string.no_tags)) }
            itemsIndexed(tags, key = { _, t -> t.id }) { i, t ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onStartRename(t)
                    }.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("#" + t.name, Modifier.weight(1f).padding(vertical = 14.dp))
                    IconButton(onClick = {
                        onMove(i, -1)
                    }, enabled = i > 0) {
                        Icon(Icons.Filled.ArrowUpward, stringResource(R.string.move_up))
                    }
                    IconButton(onClick = {
                        onMove(i, 1)
                    }, enabled = i < tags.size - 1) {
                        Icon(Icons.Filled.ArrowDownward, stringResource(R.string.move_down))
                    }
                    IconButton(onClick = {
                        onDelete(t)
                    }) {
                        Icon(
                            Icons.Filled.RemoveCircle,
                            stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    when (val dialog = state.dialog) {
        is TagDialog.Add -> TextFieldDialog(
            stringResource(R.string.add_tag),
            "",
            maxLength = TagsRepository.MAX_LENGTH,
            error = exists.takeIf {
                dialog.nameTaken
            },
            filter = TagsRepository::filter,
            onConfirm = onAdd,
            onDismiss = onDismissDialog,
        )

        is TagDialog.Rename -> TextFieldDialog(
            stringResource(R.string.rename_tag),
            dialog.tag.name,
            maxLength = TagsRepository.MAX_LENGTH,
            error = exists.takeIf {
                dialog.nameTaken
            },
            filter = TagsRepository::filter,
            onConfirm = onRename,
            onDismiss = onDismissDialog,
        )

        is TagDialog.ConfirmDelete -> ConfirmDialog(
            stringResource(R.string.tag_in_use_delete),
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDialog,
            confirmText = stringResource(R.string.action_ok),
            dismissText = stringResource(R.string.action_cancel),
        )

        null -> Unit
    }
}

@Preview
@Composable
private fun TagSettingsScreenPreview() = NexoPreview {
    TagSettingsScreen(
        state = TagSettingsUiState(tags = PreviewData.tags),
        onBack = {},
        onStartAdd = {},
        onStartRename = {},
        onMove = { _, _ -> },
        onDelete = {},
        onAdd = {},
        onRename = {},
        onConfirmDelete = {},
        onDismissDialog = {},
    )
}
