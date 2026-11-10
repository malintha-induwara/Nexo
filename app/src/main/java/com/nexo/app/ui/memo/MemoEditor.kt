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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.database.entity.MemoEntity
import com.nexo.app.core.model.MemoColor
import com.nexo.app.core.period.toIsoText
import com.nexo.app.ui.HomeState
import com.nexo.app.ui.components.ConfirmDialog
import com.nexo.app.ui.components.Formats
import com.nexo.app.ui.components.NexoDatePickerDialog
import com.nexo.app.ui.components.NexoTopBar
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.MemoEditorKey
import com.nexo.app.ui.theme.LocalIsDark
import com.nexo.app.ui.theme.NexoPalette
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

data class MemoUi(
    val loaded: Boolean = false,
    val id: String? = null,
    val title: String = "",
    val content: String = "",
    val date: LocalDate = LocalDate.now(),
    val color: MemoColor = MemoColor.WHITE,
    val pinned: Boolean = false,
    val pinnedAt: Long = 0,
)

@HiltViewModel(assistedFactory = MemoEditorViewModel.Factory::class)
class MemoEditorViewModel @AssistedInject constructor(
    @Assisted private val key: MemoEditorKey,
    private val db: NexoDatabase,
    private val home: HomeState,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: MemoEditorKey): MemoEditorViewModel
    }

    private val _ui = MutableStateFlow(MemoUi())
    val ui = _ui.asStateFlow()
    private var original: MemoEntity? = null

    init {
        viewModelScope.launch {
            val m = key.memoId?.let { db.memoDao().getById(it) }
            original = m
            _ui.value = if (m != null) {
                MemoUi(true, m.id, m.title, m.content.trimEnd('\n'), LocalDate.parse(m.date), m.color, m.pinned, m.pinnedAt)
            } else {
                MemoUi(loaded = true, date = key.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now())
            }
        }
    }

    fun setTitle(v: String) = _ui.update { it.copy(title = v) }
    fun setContent(v: String) = _ui.update { it.copy(content = v) }
    fun setDate(d: LocalDate) = _ui.update { it.copy(date = d) }
    fun setColor(c: MemoColor) = _ui.update { it.copy(color = c) }
    fun togglePin() = _ui.update { if (it.pinned) it.copy(pinned = false, pinnedAt = 0) else it.copy(pinned = true, pinnedAt = System.currentTimeMillis()) }

    /** UI-016: saved on back; an emptied existing memo is deleted. */
    fun saveAndClose(onDone: () -> Unit) = viewModelScope.launch {
        val s = _ui.value
        val empty = s.title.isBlank() && s.content.isBlank()
        val o = original
        when {
            empty && o != null -> db.memoDao().delete(o.id)
            empty -> Unit
            else -> {
                val now = System.currentTimeMillis()
                val time = o?.let { java.time.Instant.ofEpochMilli(it.dateTime.toLong()).atZone(ZoneId.systemDefault()).toLocalTime() } ?: LocalTime.now()
                val row = MemoEntity(
                    id = o?.id ?: UUID.randomUUID().toString(),
                    title = s.title.trim(),
                    content = if (s.content.isBlank()) "" else s.content.trimEnd() + "\n",
                    date = s.date.toIsoText(),
                    dateTime = s.date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    pinned = s.pinned,
                    pinnedAt = if (s.pinned) s.pinnedAt else 0,
                    orderSeq = o?.orderSeq ?: (db.memoDao().maxOrder() + 1),
                    color = s.color,
                    updatedAt = now,
                )
                db.memoDao().upsert(listOf(row))
                home.memoSaved(row.id)
                home.focusOn(s.date)
            }
        }
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        original?.let { db.memoDao().delete(it.id) }
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoEditorScreen(key: MemoEditorKey) {
    val vm = hiltViewModel<MemoEditorViewModel, MemoEditorViewModel.Factory>(creationCallback = { it.create(key) })
    val s by vm.ui.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val dark = LocalIsDark.current
    var palette by rememberSaveable { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val close = { vm.saveAndClose { nav.back() } }
    val titleFocus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(s.loaded) { if (s.loaded && s.id == null) runCatching { titleFocus.requestFocus() } }
    BackHandler { close() }
    if (!s.loaded) return
    val bg = NexoPalette.memo(s.color, dark)
    Scaffold(
        containerColor = bg,
        topBar = {
            NexoTopBar(stringResource(R.string.note), onBack = { close() }, container = bg) {
                IconButton(onClick = { palette = true }) { Icon(Icons.Outlined.Palette, stringResource(R.string.color)) }
                IconButton(onClick = vm::togglePin) { Icon(if (s.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, stringResource(R.string.pin)) }
                if (s.id != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 4.dp)) {
            Text(
                "${Formats.monthDay(s.date)} (${Formats.weekdayShort(s.date)})",
                Modifier.clickable { pickDate = true }.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            val colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            )
            TextField(s.title, vm::setTitle, placeholder = { Text(stringResource(R.string.title)) }, singleLine = true, colors = colors,
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.fillMaxWidth().then(androidx.compose.ui.Modifier.focusRequester(titleFocus)))
            TextField(s.content, vm::setContent, placeholder = { Text(stringResource(R.string.note)) }, colors = colors, modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
    if (palette) {
        ModalBottomSheet(onDismissRequest = { palette = false }) {
            LazyVerticalGrid(GridCells.Fixed(6), Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(MemoColor.entries) { c ->
                    Box(Modifier.padding(4.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(40.dp).background(NexoPalette.memo(c, dark), CircleShape)
                                .border(if (c == s.color) 3.dp else 1.dp, if (c == s.color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable { vm.setColor(c) },
                        )
                    }
                }
            }
        }
    }
    if (pickDate) NexoDatePickerDialog(s.date, onPick = vm::setDate, onDismiss = { pickDate = false })
    if (confirmDelete) ConfirmDialog(stringResource(R.string.confirm_delete), onConfirm = { vm.delete { nav.back() } }, onDismiss = { confirmDelete = false })
}
