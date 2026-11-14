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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.MonthPickerDialog
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.PeriodNavigator
import com.nexo.app.ui.designsystem.theme.LocalIsDark
import com.nexo.app.ui.designsystem.theme.NexoPalette
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.MemoEditorKey
import com.nexo.app.ui.navigation.MemoListKey
import java.time.LocalDate

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
