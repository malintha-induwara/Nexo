package com.nexo.app.ui.memo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.model.MemoColor
import com.nexo.app.ui.designsystem.component.ConfirmDialog
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.NexoDatePickerDialog
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.designsystem.theme.LocalIsDark
import com.nexo.app.ui.designsystem.theme.NexoPalette
import com.nexo.app.ui.designsystem.theme.nexo
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.MemoEditorKey
import java.time.LocalDate

@Composable
fun MemoEditorRoute(key: MemoEditorKey) {
    val vm =
        hiltViewModel<MemoEditorViewModel, MemoEditorViewModel.Factory>(creationCallback = {
            it.create(key)
        })
    val s by vm.uiState.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    LaunchedEffect(Unit) { vm.initialize() }
    LaunchedEffect(s.closed) {
        if (s.closed) {
            nav.back()
            vm.onClosed()
        }
    }
    MemoEditorScreen(
        s = s,
        onClose = { vm.saveAndClose() },
        onTogglePin = vm::togglePin,
        onTitleChange = vm::setTitle,
        onContentChange = vm::setContent,
        onDateChange = vm::setDate,
        onColorChange = vm::setColor,
        onDelete = { vm.delete() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoEditorScreen(
    s: MemoEditorUiState,
    onClose: () -> Unit,
    onTogglePin: () -> Unit,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onColorChange: (MemoColor) -> Unit,
    onDelete: () -> Unit,
) {
    val dark = LocalIsDark.current
    var palette by rememberSaveable { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(s.loaded) {
        if (s.loaded && s.id == null) {
            runCatching { titleFocus.requestFocus() }
        }
    }
    BackHandler { onClose() }
    if (!s.loaded) return
    val bg = NexoPalette.memo(s.color, dark)
    Scaffold(
        containerColor = bg,
        topBar = {
            NexoTopBar(stringResource(R.string.note), onBack = onClose, container = bg) {
                IconButton(onClick = {
                    palette = true
                }) { Icon(Icons.Outlined.Palette, stringResource(R.string.color)) }
                IconButton(onClick = onTogglePin) {
                    Icon(
                        if (s.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        stringResource(R.string.pin),
                    )
                }
                if (s.id != null) {
                    IconButton(onClick = {
                        confirmDelete = true
                    }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 4.dp)) {
            Text(
                "${Formats.monthDay(s.date)} (${Formats.weekdayShort(s.date)})",
                Modifier.clickable { pickDate = true }.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.nexo.primaryText,
            )
            val colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            )
            TextField(
                s.title,
                onTitleChange,
                placeholder = {
                    Text(stringResource(R.string.title))
                },
                singleLine = true,
                colors = colors,
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.fillMaxWidth().then(Modifier.focusRequester(titleFocus)),
            )
            TextField(s.content, onContentChange, placeholder = {
                Text(stringResource(R.string.note))
            }, colors = colors, modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
    if (palette) {
        ModalBottomSheet(onDismissRequest = { palette = false }) {
            LazyVerticalGrid(
                GridCells.Fixed(6),
                Modifier.padding(16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(MemoColor.entries) { c ->
                    Box(Modifier.padding(4.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(40.dp).background(NexoPalette.memo(c, dark), CircleShape)
                                .border(
                                    if (c == s.color) {
                                        3.dp
                                    } else {
                                        1.dp
                                    },
                                    if (c == s.color) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    CircleShape,
                                )
                                .clickable { onColorChange(c) },
                        )
                    }
                }
            }
        }
    }
    if (pickDate) {
        NexoDatePickerDialog(s.date, onPick = onDateChange, onDismiss = {
            pickDate = false
        })
    }
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.confirm_delete), onConfirm = onDelete, onDismiss = {
            confirmDelete = false
        })
    }
}

@Preview
@Composable
private fun MemoEditorScreenPreview() = NexoPreview {
    MemoEditorScreen(
        s = MemoEditorUiState(
            loaded = true,
            id = "memo",
            title = "Groceries",
            content = "Rice, dhal, coconut milk\nPay the electricity bill",
            date = PreviewData.today,
            color = MemoColor.YELLOW,
            pinned = true,
        ),
        onClose = {},
        onTogglePin = {},
        onTitleChange = {},
        onContentChange = {},
        onDateChange = {},
        onColorChange = {},
        onDelete = {},
    )
}
