package com.nexo.app.ui.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols

// Numeric entry sheet: keypad plus optional calculator.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountSheet(
    title: String,
    initial: Double?,
    decimals: Int,
    onConfirm: (Double?) -> Unit,
    onDismiss: () -> Unit,
    allowSign: Boolean = true,
    calculator: Boolean = true,
    maxLength: Int = 15,
    descending: Boolean = false,
    subtitle: String? = null,
    validate: (Double?) -> Boolean = { true },
    format: (Double) -> String = { AmountFormatter.number(it, decimals) },
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by remember { mutableStateOf(PadInput("", decimals, allowSign, maxLength)) }
    var current by remember { mutableStateOf(initial) }
    var calc by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = LocalNexoColors.current.surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            if (subtitle != null) {
                Text(
                    subtitle,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val shown = input.value ?: if (input.buffer.isEmpty()) current else null
            Text(
                text = if (input.buffer.isNotEmpty()) {
                    input.buffer.replace('.', DecimalFormatSymbols.getInstance().decimalSeparator)
                } else {
                    shown?.let(format) ?: ""
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.displaySmall,
            )
            NumberPad(
                title = title,
                decimals = decimals,
                signKey = allowSign,
                descending = descending,
                onKey = { key ->
                    val r = input.press(key)
                    input = r.input
                    if (r.clearField) current = null
                    if (r.negateExisting) current = current?.let { -it }
                },
                onDone = {
                    val v = input.value ?: current
                    if (validate(v)) {
                        onConfirm(
                            v?.let {
                                BigDecimal.valueOf(
                                    it,
                                ).setScale(decimals, RoundingMode.HALF_UP).toDouble()
                            },
                        )
                        onDismiss()
                    }
                },
                onCalculator = if (calculator) ({ calc = true }) else null,
            )
        }
    }
    if (calc) {
        CalculatorDialog(initial = input.value ?: current, onResult = { v ->
            val value = if (allowSign) v.toDouble() else v.abs().toDouble()
            current = value
            input = input.copy(buffer = "")
        }, onDismiss = { calc = false })
    }
}
