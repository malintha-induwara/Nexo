package com.nexo.app.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.nexo.app.R
import com.nexo.app.core.model.PhotoItem
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.Tag
import com.nexo.app.data.repository.TagResult
import com.nexo.app.data.repository.TagsRepository
import com.nexo.app.ui.designsystem.component.ConfirmDialog
import com.nexo.app.ui.designsystem.component.MessageDialog
import com.nexo.app.ui.designsystem.component.TextFieldDialog
import com.nexo.app.ui.designsystem.theme.nexo
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun repeatLabel(t: RepeatType): String = stringResource(
    when (t) {
        RepeatType.NONE -> R.string.repeat_none
        RepeatType.DAILY -> R.string.repeat_daily
        RepeatType.WEEKDAYS -> R.string.repeat_weekdays
        RepeatType.WEEKENDS -> R.string.repeat_weekends
        RepeatType.WEEKLY -> R.string.repeat_weekly
        RepeatType.BIWEEKLY -> R.string.repeat_biweekly
        RepeatType.FOUR_WEEKLY -> R.string.repeat_four_weekly
        RepeatType.MONTHLY -> R.string.repeat_monthly
        RepeatType.END_OF_MONTH -> R.string.repeat_end_of_month
        RepeatType.BIMONTHLY -> R.string.repeat_bimonthly
        RepeatType.QUARTERLY -> R.string.repeat_quarterly
        RepeatType.FOUR_MONTHLY -> R.string.repeat_four_monthly
        RepeatType.SEMIANNUAL -> R.string.repeat_semiannual
        RepeatType.ANNUAL -> R.string.repeat_annual
    },
)

/** Select Tag sheet: check tags in order, add, and edit (rename, reorder, delete). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagSelectSheet(
    tags: List<Tag>,
    initial: List<String>,
    actions: TagActions,
    onDone: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(initial) }
    var editMode by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Tag?>(null) }
    var deleting by remember { mutableStateOf<Tag?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val exists = stringResource(R.string.tag_exists)
    val live = tags.filter { !it.isDeleted }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.select_tag), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, stringResource(R.string.add_tag)) }
                IconButton(onClick = { editMode = !editMode }) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
            }
            if (live.isEmpty()) Text(stringResource(R.string.no_tags), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline)
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(live, key = { _, t -> t.id }) { index, tag ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (editMode) renaming = tag
                            else selected = if (tag.id in selected) selected - tag.id else selected + tag.id
                        }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!editMode) Checkbox(checked = tag.id in selected, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                        Text(tag.name, modifier = Modifier.weight(1f).padding(vertical = 14.dp, horizontal = 8.dp))
                        if (!editMode && tag.id in selected) Text("${selected.indexOf(tag.id) + 1}", color = MaterialTheme.nexo.primaryText, modifier = Modifier.padding(end = 12.dp))
                        if (editMode) {
                            IconButton(onClick = { scope.launch { actions.reorder(live.map { it.id }.toMutableList().apply { if (index > 0) add(index - 1, removeAt(index)) }) } }, enabled = index > 0) {
                                Icon(Icons.Filled.ArrowUpward, stringResource(R.string.move_up))
                            }
                            IconButton(onClick = { scope.launch { actions.reorder(live.map { it.id }.toMutableList().apply { if (index < size - 1) add(index + 1, removeAt(index)) }) } }, enabled = index < live.size - 1) {
                                Icon(Icons.Filled.ArrowDownward, stringResource(R.string.move_down))
                            }
                            IconButton(onClick = { deleting = tag }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                TextButton(onClick = { onDone(selected.filter { id -> live.any { it.id == id } }); onDismiss() }) { Text(stringResource(R.string.action_ok)) }
            }
        }
    }
    if (adding) {
        TextFieldDialog(
            title = stringResource(R.string.add_tag), initial = "", maxLength = TagsRepository.MAX_LENGTH, error = error,
            filter = TagsRepository::filter,
            onConfirm = { name ->
                scope.launch {
                    when (val r = actions.add(name)) {
                        is TagResult.Ok -> { adding = false; error = null; selected = selected + r.id }
                        TagResult.Exists -> error = exists
                        TagResult.Empty -> adding = false
                    }
                }
            },
            onDismiss = { adding = false; error = null },
        )
    }
    renaming?.let { tag ->
        TextFieldDialog(
            title = stringResource(R.string.rename_tag), initial = tag.name, maxLength = TagsRepository.MAX_LENGTH, error = error,
            filter = TagsRepository::filter,
            onConfirm = { name ->
                scope.launch {
                    when (actions.rename(tag.id, name)) {
                        is TagResult.Ok -> { renaming = null; error = null }
                        TagResult.Exists -> error = exists
                        TagResult.Empty -> renaming = null
                    }
                }
            },
            onDismiss = { renaming = null; error = null },
        )
    }
    deleting?.let { tag ->
        var used by remember(tag.id) { mutableStateOf<Int?>(null) }
        androidx.compose.runtime.LaunchedEffect(tag.id) { used = actions.usageCount(tag.id) }
        when (used) {
            null -> Unit
            0 -> androidx.compose.runtime.LaunchedEffect(Unit) {
                actions.delete(tag.id)
                selected = selected - tag.id
                deleting = null
            }
            else -> ConfirmDialog(
                message = stringResource(R.string.tag_in_use_delete),
                onConfirm = { scope.launch { actions.delete(tag.id); selected = selected - tag.id } },
                onDismiss = { deleting = null },
                confirmText = stringResource(R.string.action_ok),
                dismissText = stringResource(R.string.action_cancel),
            )
        }
    }
}

/** Up to three thumbnails with remove icons. */
@Composable
fun PhotoThumbs(
    photos: List<PhotoItem>,
    photoFile: (String) -> File,
    removable: Boolean,
    onOpen: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onLongPress: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        photos.forEachIndexed { i, p ->
            Box(Modifier.size(72.dp)) {
                AsyncImage(
                    model = when (p) {
                        is PhotoItem.Stored -> photoFile(p.fileName)
                        is PhotoItem.Picked -> p.uri
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                        .clickable { onOpen(i) },
                )
                if (removable) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(22.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape).clickable { onRemove(i) },
                        contentAlignment = Alignment.Center,
                    ) { Text("×", color = Color.White) }
                }
            }
        }
    }
}

/** Full-screen photo pager with export for saved photos. */
@Composable
fun PhotoViewer(photos: List<PhotoItem>, start: Int, photoFile: (String) -> File, onExport: suspend (String) -> Boolean, onDismiss: () -> Unit) {
    val pager = rememberPagerState(initialPage = start) { photos.size }
    val scope = rememberCoroutineScope()
    var savedMessage by remember { mutableStateOf<Boolean?>(null) }
    LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                val p = photos[page]
                AsyncImage(
                    model = when (p) {
                        is PhotoItem.Stored -> photoFile(p.fileName)
                        is PhotoItem.Picked -> p.uri
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Row(Modifier.align(Alignment.TopEnd)) {
                val current = photos.getOrNull(pager.currentPage)
                if (current is PhotoItem.Stored) {
                    IconButton(onClick = { scope.launch { savedMessage = onExport(current.fileName) } }) {
                        Icon(Icons.Outlined.Download, stringResource(R.string.save_to_gallery), tint = Color.White)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = Color.White) }
            }
            if (photos.size > 1) {
                Row(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(photos.size) { i ->
                        Box(Modifier.size(8.dp).background(if (i == pager.currentPage) Color.White else Color.Gray, CircleShape))
                    }
                }
            }
        }
    }
    savedMessage?.let { ok -> MessageDialog(stringResource(if (ok) R.string.saved else R.string.save_failed), onDismiss = { savedMessage = null }) }
}
