package com.nexo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexo.app.R
import com.nexo.app.domain.Calculator
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/** Full-screen calculator (TX-009). Returns the evaluated value on OK. */
@Composable
fun CalculatorDialog(initial: Double?, onResult: (BigDecimal) -> Unit, onDismiss: () -> Unit) {
    val start = initial?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }?.takeIf { it != "0" } ?: ""
    var expr by rememberSaveable { mutableStateOf(start) }
    var echo by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    val sep = DecimalFormatSymbols.getInstance().decimalSeparator
    val grouping = DecimalFormat("#,##0.#####")

    fun display(e: String): String {
        val sb = StringBuilder()
        val num = StringBuilder()
        fun flush() {
            if (num.isNotEmpty()) {
                val s = num.toString()
                val intPart = s.substringBefore('.')
                val frac = if (s.contains('.')) sep + s.substringAfter('.') else ""
                sb.append((intPart.toBigDecimalOrNull()?.let { grouping.format(it) } ?: intPart) + frac)
                num.clear()
            }
        }
        for (c in e) if (c.isDigit() || c == '.') num.append(c) else { flush(); sb.append(' ').append(c).append(' ') }
        flush()
        return sb.toString()
    }

    fun evaluate(): BigDecimal? = when (val r = Calculator.evaluate(expr)) {
        is Calculator.Outcome.Value -> r.value
        else -> null
    }

    fun press(k: String) {
        error = false
        when (k) {
            "AC" -> { expr = ""; echo = "" }
            "BS" -> expr = expr.dropLast(1)
            "=" -> {
                when (val r = Calculator.evaluate(expr)) {
                    is Calculator.Outcome.Value -> { echo = display(expr); expr = r.value.toPlainString() }
                    Calculator.Outcome.DivideByZero -> { error = true; echo = display(expr); expr = "" }
                    Calculator.Outcome.Invalid -> error = true
                }
            }
            else -> expr = Calculator.type(expr, k)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                NexoTopBar(stringResource(R.string.calculator), onBack = onDismiss)
                Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Bottom) {
                    Text(echo, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (error) stringResource(R.string.calc_error) else display(expr).ifEmpty { "0" },
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End, fontSize = 40.sp, fontWeight = FontWeight.Light,
                    )
                }
                val rows = listOf(
                    listOf("AC", "BS", "÷", "×"),
                    listOf("7", "8", "9", "−"),
                    listOf("4", "5", "6", "+"),
                    listOf("1", "2", "3", "="),
                    listOf("00", "0", ".", "OK"),
                )
                Column(Modifier.padding(6.dp)) {
                    rows.forEach { row ->
                        Row(Modifier.fillMaxWidth().height(68.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { k ->
                                CalcKey(
                                    k,
                                    accent = k == "OK",
                                    operator = k in setOf("÷", "×", "−", "+", "="),
                                    label = if (k == ".") sep.toString() else if (k == "OK") stringResource(R.string.action_ok) else k,
                                ) {
                                    if (k == "OK") {
                                        val v = evaluate()
                                        if (v != null) { onResult(v); onDismiss() } else error = true
                                    } else press(k)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.CalcKey(key: String, accent: Boolean, operator: Boolean, label: String, onClick: () -> Unit) {
    val bg = when {
        accent -> MaterialTheme.colorScheme.primary
        operator -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Box(
        Modifier.weight(1f).padding(vertical = 3.dp).fillMaxSize().background(bg, MaterialTheme.shapes.medium).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (key == "BS") Icon(Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.action_delete))
        else Text(label, fontSize = 22.sp, color = if (accent) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}
