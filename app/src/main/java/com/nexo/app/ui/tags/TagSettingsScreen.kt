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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.data.database.entity.TagEntity
import com.nexo.app.data.repository.TagResult
import com.nexo.app.data.repository.TagsRepository
import com.nexo.app.ui.designsystem.component.ConfirmDialog
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.TextFieldDialog
import com.nexo.app.ui.navigation.LocalNavigator
import kotlinx.coroutines.launch

@Composable
fun TagSettingsScreen(vm: TagSettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val all by vm.tags.collectAsStateWithLifecycle()
    val tags = all.filter { !it.isDeleted }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<TagEntity?>(null) }
    var deleting by remember { mutableStateOf<TagEntity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val exists = stringResource(R.string.tag_exists)
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.tag_settings), onBack = { nav.back() }) {
            IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, stringResource(R.string.add_tag)) }
        }
    }) { p ->
        LazyColumn(Modifier.padding(p)) {
            item { Text(stringResource(R.string.tag_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            if (tags.isEmpty()) item { EmptyState(stringResource(R.string.no_tags)) }
            itemsIndexed(tags, key = { _, t -> t.id }) { i, t ->
                Row(Modifier.fillMaxWidth().clickable { renaming = t }.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("#" + t.name, Modifier.weight(1f).padding(vertical = 14.dp))
                    IconButton(onClick = { vm.launch { vm.repo.reorder(tags.map { it.id }.toMutableList().apply { add(i - 1, removeAt(i)) }) } }, enabled = i > 0) { Icon(Icons.Filled.ArrowUpward, stringResource(R.string.move_up)) }
                    IconButton(onClick = { vm.launch { vm.repo.reorder(tags.map { it.id }.toMutableList().apply { add(i + 1, removeAt(i)) }) } }, enabled = i < tags.size - 1) { Icon(Icons.Filled.ArrowDownward, stringResource(R.string.move_down)) }
                    IconButton(onClick = {
                        vm.launch { if (vm.repo.usageCount(t.id) > 0) deleting = t else vm.repo.delete(t.id) }
                    }) { Icon(Icons.Filled.RemoveCircle, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    if (adding) TextFieldDialog(stringResource(R.string.add_tag), "", maxLength = TagsRepository.MAX_LENGTH, error = error, filter = TagsRepository::filter,
        onConfirm = { n -> vm.launch { when (vm.repo.add(n)) { TagResult.Exists -> error = exists; else -> { adding = false; error = null } } } },
        onDismiss = { adding = false; error = null })
    renaming?.let { t ->
        TextFieldDialog(stringResource(R.string.rename_tag), t.name, maxLength = TagsRepository.MAX_LENGTH, error = error, filter = TagsRepository::filter,
            onConfirm = { n -> vm.launch { when (vm.repo.rename(t.id, n)) { TagResult.Exists -> error = exists; else -> { renaming = null; error = null } } } },
            onDismiss = { renaming = null; error = null })
    }
    deleting?.let { t ->
        ConfirmDialog(stringResource(R.string.tag_in_use_delete), onConfirm = { vm.launch { vm.repo.delete(t.id) } }, onDismiss = { deleting = null },
            confirmText = stringResource(R.string.action_ok), dismissText = stringResource(R.string.action_cancel))
    }
}
