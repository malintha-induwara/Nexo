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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.model.Memo
import com.nexo.app.core.model.MemoColor
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.MonthPickerDialog
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.PeriodNavigator
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.designsystem.theme.LocalIsDark
import com.nexo.app.ui.designsystem.theme.NexoPalette
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.MemoEditorKey
import com.nexo.app.ui.navigation.MemoListKey
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun MemoListRoute(key: MemoListKey) {
    val vm =
        hiltViewModel<MemoListViewModel, MemoListViewModel.Factory>(creationCallback = {
            it.create(key)
        })
    val state by vm.uiState.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    MemoListScreen(
        state = state,
        onBack = nav::back,
        onStep = vm::step,
        onPickMonth = vm::pickMonth,
        onSetHelpHidden = vm::setHelpHidden,
        onSetMemoButton = vm::setMemoButton,
        onNewMemo = { nav.navigate(MemoEditorKey(date = it.toString())) },
        onOpenMemo = { nav.navigate(MemoEditorKey(memoId = it)) },
    )
}

@Composable
internal fun MemoListScreen(
    state: MemoListUiState,
    onBack: () -> Unit,
    onStep: (forward: Boolean) -> Unit,
    onPickMonth: (YearMonth) -> Unit,
    onSetHelpHidden: (Boolean) -> Unit,
    onSetMemoButton: (Boolean) -> Unit,
    onNewMemo: (date: LocalDate) -> Unit,
    onOpenMemo: (memoId: String) -> Unit,
) {
    val memos = state.memos
    val period = state.period
    val device = state.device
    val ref = state.ref
    val dark = LocalIsDark.current
    var picker by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            NexoTopBar("", onBack = onBack, titleContent = {
                PeriodNavigator(
                    period?.let {
                        Formats.monthYear(it.label)
                    } ?: "",
                    { onStep(false) },
                    { onStep(true) },
                    {
                        picker = true
                    },
                )
            }) {
                IconButton(onClick = {
                    onSetHelpHidden(device?.memoHelpHidden != true)
                }) { Icon(Icons.Outlined.Settings, stringResource(R.string.memo_button_setting)) }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                onNewMemo(state.date)
            }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(
                    Icons.AutoMirrored.Outlined.StickyNote2,
                    stringResource(R.string.new_memo),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (device?.memoHelpHidden != true) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                                Text(
                                    stringResource(R.string.memo_button_setting),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    stringResource(R.string.memo_button_text),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(ref.settings.memoButton, onSetMemoButton)
                            IconButton(onClick = {
                                onSetHelpHidden(true)
                            }) { Icon(Icons.Filled.Close, stringResource(R.string.action_close)) }
                        }
                    }
                }
            }
            if (memos.isEmpty()) item { EmptyState(stringResource(R.string.no_memos)) }
            items(memos, key = { it.id }) { m ->
                Card(
                    onClick = {
                        onOpenMemo(m.id)
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = NexoPalette.memo(m.color, dark),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            Formats.monthDay(LocalDate.parse(m.date)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (m.title.isNotBlank()) Text(m.title, fontWeight = FontWeight.Bold)
                        if (m.content.isNotBlank()) {
                            Text(
                                m.content.trimEnd(),
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
    if (picker) {
        period?.let { p ->
            MonthPickerDialog(p.label, onPick = onPickMonth, onDismiss = {
                picker = false
            })
        }
    }
}

private val previewMemos = listOf(
    Memo(
        id = "rent",
        title = "Rent",
        content = "Transfer before the 5th",
        date = PreviewData.today.toString(),
        dateTime = 0,
        pinned = true,
        orderSeq = 0,
        color = MemoColor.YELLOW,
        updatedAt = 0,
    ),
    Memo(
        id = "groceries",
        title = "Groceries",
        content = "Rice, dhal, coconut milk",
        date = PreviewData.today.minusDays(2).toString(),
        dateTime = 0,
        orderSeq = 1,
        updatedAt = 0,
    ),
)

@Preview
@Composable
private fun MemoListScreenPreview() = NexoPreview {
    val periods = PreviewData.ref.settings.periods
    MemoListScreen(
        state = MemoListUiState(
            ref = PreviewData.ref,
            date = PreviewData.today,
            period = periods.monthPeriodContaining(PreviewData.today),
            device = PreviewData.device,
            memos = previewMemos,
        ),
        onBack = {},
        onStep = {},
        onPickMonth = {},
        onSetHelpHidden = {},
        onSetMemoButton = {},
        onNewMemo = {},
        onOpenMemo = {},
    )
}
