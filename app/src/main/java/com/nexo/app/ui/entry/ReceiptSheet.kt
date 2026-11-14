package com.nexo.app.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.common.money.AmountParts
import com.nexo.app.data.database.entity.CurrencyEntity
import com.nexo.app.ui.designsystem.component.AmountSheet
import com.nexo.app.ui.designsystem.component.AmountText
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.rememberHaptics
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoShapes
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Space
import java.util.Locale

/**
 * Scan receipt's confirm step. While the photo is read it shows progress; then the total it found (tap to correct it),
 * the other amounts on the receipt (tap one to use it instead), and the shop and date with a tick to use or skip each.
 * Use this fills the entry; Cancel, back, the scrim or a drag leave the entry as it was.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.ReceiptSheetContent(
    scan: ScanUi,
    currency: CurrencyEntity,
    noteEmpty: Boolean,
    onUse: (amount: Double?, useDate: Boolean, useMerchant: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val context = LocalContext.current

    if (scan is ScanUi.Reading) {
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = Space.gutter)) {
            Text(stringResource(R.string.receipt_reading), style = MaterialTheme.typography.titleMedium, color = colors.text)
            Spacer(Modifier.height(Space.l))
            LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = colors.primary, trackColor = colors.raised)
        }
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = Space.m)) {
            Text(stringResource(R.string.action_cancel), color = colors.textSecondary)
        }
        return
    }

    val read = (scan as ScanUi.Done).read
    var amount by remember(scan) { mutableStateOf(read.total?.toDouble()) }
    var useDate by remember(scan) { mutableStateOf(true) }
    var useMerchant by remember(scan) { mutableStateOf(true) }
    var editing by remember(scan) { mutableStateOf(false) }
    val choices = remember(scan) { (listOfNotNull(read.total) + read.others).distinct().map { it.toDouble() } }

    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter)) {
        Text(stringResource(R.string.receipt_title), style = MaterialTheme.typography.titleMedium, color = colors.text)
        val note = when {
            read.total == null && read.others.isEmpty() -> R.string.receipt_nothing
            read.guessed -> R.string.receipt_guessed
            else -> null
        }
        if (note != null) {
            Text(stringResource(note), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(top = Space.xxs))
        }
        Spacer(Modifier.height(Space.l))
        Row(
            Modifier.fillMaxWidth().clip(NexoShapes.control).background(colors.raised).clickable { editing = true }
                .heightIn(min = 56.dp).padding(horizontal = Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.receipt_total), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, modifier = Modifier.weight(1f))
            val a = amount
            if (a != null) AmountText(a, currency, sign = AmountParts.Sign.NONE) else Text("—", style = NexoType.Amount, color = colors.textTertiary)
            Spacer(Modifier.width(Space.s))
            Icon(Icons.Rounded.Edit, stringResource(R.string.action_edit), tint = colors.textTertiary, modifier = Modifier.size(18.dp))
        }

        if (choices.size > 1 || (choices.size == 1 && choices[0] != amount)) {
            Text(
                stringResource(R.string.receipt_other_amounts).uppercase(Locale.getDefault()),
                style = NexoType.Caps, color = colors.textTertiary, modifier = Modifier.padding(top = Space.l, bottom = Space.s),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                choices.forEach { v ->
                    val selected = v == amount
                    Text(
                        AmountFormatter.format(v, currency),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) colors.primary else colors.text,
                        maxLines = 1,
                        modifier = Modifier.clip(CircleShape)
                            .background(if (selected) colors.tint(colors.primary, colors.raised) else colors.raised)
                            .border(1.5.dp, if (selected) colors.primary else Color.Transparent, CircleShape)
                            .clickable { haptics.tick(); amount = v }
                            .padding(horizontal = Space.m, vertical = Space.s),
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.m))
        val merchant = read.merchant
        if (merchant != null && noteEmpty) {
            TickRow(stringResource(R.string.receipt_use_note, merchant), useMerchant) { haptics.tick(); useMerchant = !useMerchant }
        }
        val date = read.date
        if (date != null) {
            val time = read.time?.let { Formats.time(it, android.text.format.DateFormat.is24HourFormat(context)) }
            val shown = Formats.mediumDate(date) + (time?.let { ", $it" } ?: "")
            TickRow(stringResource(R.string.receipt_use_date, shown), useDate) { haptics.tick(); useDate = !useDate }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.m),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Button(
            onClick = onCancel,
            modifier = Modifier.weight(1f).height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.raised, contentColor = colors.text),
            contentPadding = PaddingValues(horizontal = Space.s),
        ) { Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge, maxLines = 1) }
        Button(
            onClick = { haptics.confirm(); onUse(amount, useDate, useMerchant) },
            modifier = Modifier.weight(1.3f).height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            contentPadding = PaddingValues(horizontal = Space.s),
        ) { Text(stringResource(R.string.receipt_use), style = MaterialTheme.typography.titleSmall, maxLines = 1) }
    }

    if (editing) {
        AmountSheet(
            title = stringResource(R.string.receipt_total),
            initial = amount,
            decimals = currency.decimals,
            allowSign = false,
            onConfirm = { v -> amount = v?.takeIf { it > 0 } },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun TickRow(text: String, checked: Boolean, onToggle: () -> Unit) {
    val colors = LocalNexoColors.current
    Row(
        Modifier.fillMaxWidth().clip(NexoShapes.control).clickable(onClick = onToggle).padding(vertical = Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = colors.primary, uncheckedColor = colors.textTertiary, checkmarkColor = colors.onPrimary),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
