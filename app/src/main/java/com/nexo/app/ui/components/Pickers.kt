package com.nexo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset

private fun LocalDate.utcMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
private fun Long.utcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Date picker limited to 1985–2050 (TX-004). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexoDatePickerDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    min: LocalDate = LocalDate.of(1985, 1, 1),
    max: LocalDate = LocalDate.of(2050, 12, 31),
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.utcMillis(),
        yearRange = min.year..max.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = utcTimeMillis.utcDate()
                return !d.isBefore(min) && !d.isAfter(max)
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(it.utcDate()) }
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexoTimePickerDialog(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(initial.hour, initial.minute, is24)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                onPick(initial.withHour(state.hour).withMinute(state.minute))
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Month picker (UI-005): year ±, 12 months, "This month". */
@Composable
fun MonthPickerDialog(selected: YearMonth, onPick: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    var year by rememberSaveable { mutableIntStateOf(selected.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = { year-- }) { Icon(Icons.Filled.ChevronLeft, stringResource(R.string.action_previous)) }
                Text(year.toString(), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { year++ }) { Icon(Icons.Filled.ChevronRight, stringResource(R.string.action_next)) }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        for (col in 0 until 3) {
                            val m = row * 3 + col + 1
                            val isSel = selected.year == year && selected.monthValue == m
                            val action = { onPick(YearMonth.of(year, m)); onDismiss() }
                            if (isSel) FilledTonalButton(onClick = action, modifier = Modifier.weight(1f)) { Text(Formats.monthShort(m), maxLines = 1) }
                            else OutlinedButton(onClick = action, modifier = Modifier.weight(1f)) { Text(Formats.monthShort(m), maxLines = 1) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(YearMonth.now()); onDismiss() }) { Text(stringResource(R.string.this_month)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** Picks a date range: From then To, keeping from ≤ to. */
@Composable
fun DateRangeChips(
    start: LocalDate,
    end: LocalDate,
    onChange: (LocalDate, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    var editing by rememberSaveable { mutableIntStateOf(0) }
    Row(modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { editing = 1 }) { Text(Formats.shortDate(start), color = color) }
        Text("~", color = color)
        TextButton(onClick = { editing = 2 }) { Text(Formats.shortDate(end), color = color) }
    }
    when (editing) {
        1 -> NexoDatePickerDialog(start, onPick = { d -> onChange(d, if (d.isAfter(end)) d else end) }, onDismiss = { editing = 0 })
        2 -> NexoDatePickerDialog(end, onPick = { d -> onChange(if (d.isBefore(start)) d else start, d) }, onDismiss = { editing = 0 })
    }
}
