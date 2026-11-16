package com.nexo.app.ui.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.ui.designsystem.component.FlowCharts
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.MonthPickerDialog
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.PeriodNavigator
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.theme.Space
import com.nexo.app.ui.designsystem.theme.nexo
import com.nexo.app.ui.navigation.LocalNavigator
import java.time.YearMonth

@Composable
fun TotalStatsRoute(vm: TotalStatsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ui by vm.uiState.collectAsStateWithLifecycle()
    TotalStatsScreen(ui = ui, onBack = nav::back, onStep = vm::step, onPickMonth = vm::pickMonth)
}

@Composable
internal fun TotalStatsScreen(
    ui: TotalStatsUiState,
    onBack: () -> Unit,
    onStep: (forward: Boolean) -> Unit,
    onPickMonth: (YearMonth) -> Unit,
) {
    val ref = ui.ref
    var picker by rememberSaveable { mutableStateOf(false) }
    val main = ref.mainCurrency
    val labels = ui.periods.map { Formats.monthShort(it.label.monthValue) }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.total_stats), onBack = onBack)
    }) { p ->
        Column(Modifier.fillMaxSize().padding(p)) {
            ui.periods.lastOrNull()?.let { last ->
                PeriodNavigator(Formats.monthYear(last.label), {
                    onStep(false)
                }, { onStep(true) }, {
                    picker = true
                }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
            }
            if (ui.periods.isNotEmpty()) {
                FlowCharts(
                    labels, ui.balances, ui.income, ui.expense, main,
                    flowTitle =
                        stringResource(R.string.income) + " / " + stringResource(R.string.expenses),
                    inflowLabel = stringResource(R.string.income),
                    outflowLabel = stringResource(R.string.expenses),
                    contentPadding = PaddingValues(top = Space.xs, bottom = Space.xl),
                )
            }
        }
    }
    if (picker) {
        ui.periods.lastOrNull()?.let { last ->
            MonthPickerDialog(last.label, onPick = onPickMonth, onDismiss = { picker = false })
        }
    }
}

@Preview
@Composable
private fun TotalStatsScreenPreview() = NexoPreview {
    TotalStatsScreen(ui = previewTotalStatsState, onBack = {}, onStep = {}, onPickMonth = {})
}
