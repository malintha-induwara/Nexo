package com.nexo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexo.app.R
import com.nexo.app.core.database.entity.CurrencyEntity
import com.nexo.app.domain.PadKey
import java.text.DecimalFormatSymbols

/** In-app number pad (TX-008). */
@Composable
fun NumberPad(
    title: String,
    onKey: (PadKey) -> Unit,
    onDone: () -> Unit,
    onCalculator: (() -> Unit)?,
    modifier: Modifier = Modifier,
    descending: Boolean = false,
    decimals: Int = 2,
    signKey: Boolean = true,
    currencies: List<CurrencyEntity> = emptyList(),
    selectedCurrencyId: String? = null,
    onCurrency: ((CurrencyEntity) -> Unit)? = null,
    onGlobe: (() -> Unit)? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val separator = DecimalFormatSymbols.getInstance().decimalSeparator.toString()
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (onGlobe != null) IconButton(onClick = onGlobe) { Icon(Icons.Outlined.Language, stringResource(R.string.sub_currency_setting)) }
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, stringResource(R.string.action_close)) }
        }
        if (currencies.size > 1 && onCurrency != null) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                currencies.forEach { c ->
                    FilterChip(
                        selected = c.id == selectedCurrencyId,
                        onClick = { onCurrency(c) },
                        label = { Text(c.isoCode?.takeIf { it.isNotEmpty() } ?: c.symbol) },
                    )
                }
            }
        }
        val digitRows = if (descending) listOf(listOf(7, 8, 9), listOf(4, 5, 6), listOf(1, 2, 3)) else listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
        Column(Modifier.padding(4.dp)) {
            PadRow {
                digitRows[0].forEach { d -> PadButton(d.toString()) { onKey(PadKey.entries[d]) } }
                PadButton(icon = { Icon(Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.action_delete)) }) { onKey(PadKey.BACKSPACE) }
            }
            PadRow {
                digitRows[1].forEach { d -> PadButton(d.toString()) { onKey(PadKey.entries[d]) } }
                if (signKey) PadButton("±") { onKey(PadKey.SIGN) } else PadButton("C") { onKey(PadKey.CLEAR) }
            }
            PadRow {
                digitRows[2].forEach { d -> PadButton(d.toString()) { onKey(PadKey.entries[d]) } }
                if (onCalculator != null) PadButton(icon = { Icon(Icons.Filled.Calculate, stringResource(R.string.calculator)) }) { onCalculator() }
                else PadButton("") {}
            }
            PadRow {
                PadButton("00") { onKey(PadKey.DOUBLE_ZERO) }
                PadButton("0") { onKey(PadKey.D0) }
                if (decimals > 0) PadButton(separator) { onKey(PadKey.DECIMAL) } else PadButton("000") { onKey(PadKey.TRIPLE_ZERO) }
                PadButton(stringResource(R.string.action_done), background = accent, content = Color.White) { onDone() }
            }
        }
    }
}

@Composable
private fun PadRow(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth().height(54.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), content = content)

@Composable
private fun RowScope.PadButton(
    label: String = "",
    background: Color = MaterialTheme.colorScheme.surface,
    content: Color = MaterialTheme.colorScheme.onSurface,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .weight(1f)
            .padding(vertical = 2.dp)
            .fillMaxWidth()
            .height(50.dp)
            .background(background, MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) icon() else Text(label, fontSize = 20.sp, color = content, fontWeight = FontWeight.Medium)
    }
}
