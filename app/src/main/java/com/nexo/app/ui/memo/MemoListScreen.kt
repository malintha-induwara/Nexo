package com.nexo.app.ui.memo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.settings.DevicePrefs
import com.nexo.app.data.RefRepository
import com.nexo.app.ui.components.EmptyState
import com.nexo.app.ui.components.Formats
import com.nexo.app.ui.components.MonthPickerDialog
import com.nexo.app.ui.components.NexoTopBar
import com.nexo.app.ui.components.PeriodNavigator
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.MemoEditorKey
import com.nexo.app.ui.navigation.MemoListKey
import com.nexo.app.ui.theme.LocalIsDark
import com.nexo.app.ui.theme.NexoPalette
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = MemoListViewModel.Factory::class)
class MemoListViewModel @AssistedInject constructor(
    @Assisted key: MemoListKey,
    private val db: NexoDatabase,
    private val refs: RefRepository,
    private val prefs: DevicePrefs,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: MemoListKey): MemoListViewModel
    }

    val date = MutableStateFlow(key.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now())
    val ref = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, com.nexo.app.domain.RefData())
    val device = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val period = combine(ref, date) { r, d -> r.settings.periods.monthPeriodContaining(d) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val memos = combine(ref, date) { r, d -> r.settings.periods.monthPeriodContaining(d) }.flatMapLatest { p ->
        db.memoDao().observeForRange(p.startText(), p.endText()).map { list ->
            list.filter { it.pinned }.sortedByDescending { it.pinnedAt } +
                list.filter { !it.pinned && LocalDate.parse(it.date) in p.range }.sortedWith(compareByDescending<com.nexo.app.core.database.entity.MemoEntity> { it.date }.thenByDescending { it.dateTime })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun com.nexo.app.core.period.MonthPeriod.startText() = range.startText
    private fun com.nexo.app.core.period.MonthPeriod.endText() = range.endText

    fun step(forward: Boolean) {
        val e = ref.value.settings.periods
        val p = e.monthPeriodContaining(date.value)
        date.value = (if (forward) e.next(p) else e.previous(p)).end
    }

    fun setMemoButton(v: Boolean) = viewModelScope.launch { refs.updateSettings { it.copy(memoButton = v) } }
    fun setHelpHidden(v: Boolean) = viewModelScope.launch { prefs.setMemoHelpHidden(v) }
}

/** Standalone memo list. */
@Composable
fun MemoListScreen(key: MemoListKey) {
    val vm = hiltViewModel<MemoListViewModel, MemoListViewModel.Factory>(creationCallback = { it.create(key) })
    val memos by vm.memos.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val ref by vm.ref.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val dark = LocalIsDark.current
    var picker by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            NexoTopBar("", onBack = { nav.back() }, titleContent = {
                PeriodNavigator(period?.let { Formats.monthYear(it.label) } ?: "", { vm.step(false) }, { vm.step(true) }, { picker = true })
            }) {
                IconButton(onClick = { vm.setHelpHidden(device?.memoHelpHidden != true) }) { Icon(Icons.Outlined.Settings, stringResource(R.string.memo_button_setting)) }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(MemoEditorKey(date = vm.date.value.toString())) }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.AutoMirrored.Outlined.StickyNote2, stringResource(R.string.new_memo), tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (device?.memoHelpHidden != true) item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                            Text(stringResource(R.string.memo_button_setting), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.memo_button_text), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(ref.settings.memoButton, vm::setMemoButton)
                        IconButton(onClick = { vm.setHelpHidden(true) }) { Icon(Icons.Filled.Close, stringResource(R.string.action_close)) }
                    }
                }
            }
            if (memos.isEmpty()) item { EmptyState(stringResource(R.string.no_memos)) }
            items(memos, key = { it.id }) { m ->
                Card(onClick = { nav.navigate(MemoEditorKey(memoId = m.id)) }, colors = CardDefaults.cardColors(containerColor = NexoPalette.memo(m.color, dark)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(Formats.monthDay(LocalDate.parse(m.date)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (m.title.isNotBlank()) Text(m.title, fontWeight = FontWeight.Bold)
                        if (m.content.isNotBlank()) Text(m.content.trimEnd(), maxLines = 5, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    if (picker) period?.let { p -> MonthPickerDialog(p.label, onPick = { m -> vm.date.value = ref.settings.periods.monthPeriodFor(m).end }, onDismiss = { picker = false }) }
}
