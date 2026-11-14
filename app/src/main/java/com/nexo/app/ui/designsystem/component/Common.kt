package com.nexo.app.ui.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.data.database.entity.CurrencyEntity
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Space
import java.util.Locale

@Composable
fun EmptyState(text: String = stringResource(R.string.no_data), modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.Inbox) {
    val colors = LocalNexoColors.current
    Column(modifier.fillMaxWidth().padding(horizontal = Space.xxl, vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = colors.textTertiary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(Space.m))
        Text(text, color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

/** Small upper-case label above a group of rows. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.getDefault()),
        style = NexoType.Caps,
        color = LocalNexoColors.current.textTertiary,
        modifier = modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.gutter, top = Space.xl, bottom = Space.s),
    )
}

/** Settings row with title, optional subtitle and trailing content. */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: ImageVector? = null,
    showChevron: Boolean = onClick != null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val colors = LocalNexoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.gutter, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, null, tint = if (enabled) colors.textSecondary else colors.textTertiary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Space.l))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) colors.text else colors.textTertiary)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        if (trailing != null) trailing()
        else if (showChevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textTertiary, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null, enabled: Boolean = true) {
    val haptics = rememberHaptics()
    val toggle = { v: Boolean -> haptics.toggle(v); onChange(v) }
    SettingRow(title, subtitle, onClick = { toggle(!checked) }, showChevron = false, enabled = enabled, trailing = {
        Switch(checked = checked, onCheckedChange = toggle, enabled = enabled, colors = nexoSwitchColors())
    })
}

@Composable
fun nexoSwitchColors() = LocalNexoColors.current.let { c ->
    SwitchDefaults.colors(
        checkedThumbColor = c.onPrimary,
        checkedTrackColor = c.primary,
        checkedBorderColor = c.primary,
        uncheckedThumbColor = c.textTertiary,
        uncheckedTrackColor = c.raised,
        uncheckedBorderColor = c.hairline,
    )
}

@Composable
fun Divider() = HorizontalDivider(color = LocalNexoColors.current.hairline)

/** Income / Expenses / Total strip. */
@Composable
fun TotalsBar(
    income: Double,
    expense: Double,
    currency: CurrencyEntity?,
    incomeColor: Color,
    expenseColor: Color,
    modifier: Modifier = Modifier,
    labels: Triple<String, String, String> = Triple(stringResource(R.string.income), stringResource(R.string.expenses), stringResource(R.string.total)),
    totalValue: Double = income - expense,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        TotalCell(labels.first, income, currency, incomeColor, Modifier.weight(1f))
        TotalCell(labels.second, expense, currency, expenseColor, Modifier.weight(1f))
        TotalCell(labels.third, totalValue, currency, LocalNexoColors.current.text, Modifier.weight(1f))
    }
}

@Composable
private fun TotalCell(label: String, value: Double, currency: CurrencyEntity?, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(Locale.getDefault()), style = NexoType.Caps, color = LocalNexoColors.current.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(Space.xs))
        AmountText(value, currency, style = NexoType.Amount, color = color)
    }
}
