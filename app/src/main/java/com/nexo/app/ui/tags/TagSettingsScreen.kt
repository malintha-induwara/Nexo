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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.data.repository.TagsRepository
import com.nexo.app.ui.designsystem.component.ConfirmDialog
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.TextFieldDialog
import com.nexo.app.ui.navigation.LocalNavigator

@Composable
fun TagSettingsScreen(vm: TagSettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    val tags = state.tags
    val exists = stringResource(R.string.tag_exists)
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.tag_settings), onBack = { nav.back() }) {
            IconButton(onClick = vm::startAdd) {
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
                        vm.startRename(t)
                    }.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("#" + t.name, Modifier.weight(1f).padding(vertical = 14.dp))
                    IconButton(onClick = {
                        vm.move(i, -1)
                    }, enabled = i > 0) {
                        Icon(Icons.Filled.ArrowUpward, stringResource(R.string.move_up))
                    }
                    IconButton(onClick = {
                        vm.move(i, 1)
                    }, enabled = i < tags.size - 1) {
                        Icon(Icons.Filled.ArrowDownward, stringResource(R.string.move_down))
                    }
                    IconButton(onClick = {
                        vm.requestDelete(t)
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
            onConfirm = vm::add,
            onDismiss = vm::dismissDialog,
        )

        is TagDialog.Rename -> TextFieldDialog(
            stringResource(R.string.rename_tag),
            dialog.tag.name,
            maxLength = TagsRepository.MAX_LENGTH,
            error = exists.takeIf {
                dialog.nameTaken
            },
            filter = TagsRepository::filter,
            onConfirm = vm::rename,
            onDismiss = vm::dismissDialog,
        )

        is TagDialog.ConfirmDelete -> ConfirmDialog(
            stringResource(R.string.tag_in_use_delete),
            onConfirm = vm::confirmDelete,
            onDismiss = vm::dismissDialog,
            confirmText = stringResource(R.string.action_ok),
            dismissText = stringResource(R.string.action_cancel),
        )

        null -> Unit
    }
}
