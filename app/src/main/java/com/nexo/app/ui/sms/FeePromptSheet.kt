package com.nexo.app.ui.sms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.FeeRoute
import com.nexo.app.domain.sms.FeeChoice
import com.nexo.app.domain.sms.SmsApprover
import com.nexo.app.ui.designsystem.component.AmountSheet
import com.nexo.app.ui.designsystem.component.AmountText
import com.nexo.app.ui.designsystem.component.rememberHaptics
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoShapes
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Space

fun feeRouteLabel(route: FeeRoute): Int = when (route) {
    FeeRoute.INTERBANK_TRANSFER -> R.string.fee_route_interbank
    FeeRoute.OTHER_BANK_ATM -> R.string.fee_route_other_atm
    FeeRoute.FX_CARD_PURCHASE -> R.string.fee_route_fx
    FeeRoute.OWN_BANK_TRANSFER -> R.string.fee_route_own
    FeeRoute.CC_PAYMENT -> R.string.fee_route_cc
}

/** The route as it reads mid-sentence ("Remember for other banks' ATMs from …"). */
private fun feeRouteInline(route: FeeRoute): Int = when (route) {
    FeeRoute.INTERBANK_TRANSFER -> R.string.fee_route_interbank_inline
    FeeRoute.OTHER_BANK_ATM -> R.string.fee_route_other_atm_inline
    FeeRoute.FX_CARD_PURCHASE -> R.string.fee_route_fx_inline
    FeeRoute.OWN_BANK_TRANSFER -> R.string.fee_route_own_inline
    FeeRoute.CC_PAYMENT -> R.string.fee_route_cc_inline
}

private fun feeRouteQuestion(route: FeeRoute): Int? = when (route) {
    FeeRoute.INTERBANK_TRANSFER -> R.string.fee_ask_interbank
    FeeRoute.OTHER_BANK_ATM -> R.string.fee_ask_other_atm
    FeeRoute.OWN_BANK_TRANSFER -> R.string.fee_ask_own
    FeeRoute.CC_PAYMENT -> R.string.fee_ask_cc
    FeeRoute.FX_CARD_PURCHASE -> null
}

/**
 * "Did <bank> charge a fee?": the fee pre-filled from the last one on this route (or the FX difference),
 * "Remember", and Add fee / No fee. The FX variant offers Record as bank fee / Include in purchase.
 * Cancel is the sheet's own dismissal (scrim, back, drag) or the text button.
 */
@Composable
fun ColumnScope.FeePromptContent(prompt: FeePrompt, ref: RefData, onAnswer: (FeeChoice?) -> Unit) {
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val item = prompt.items.first()
    val plan = prompt.plan
    val currency = ref.accountCurrency(item.accountId)
    var amount by remember(prompt) { mutableStateOf(plan.prefill) }
    var remember by remember(prompt) { mutableStateOf(false) }
    var confirmLarge by remember(prompt) { mutableStateOf(false) }
    var editing by remember(prompt) { mutableStateOf(false) }
    val smallest = prompt.items.minOf { SmsApprover.ledgerAmount(it) }
    val tooLarge = (amount ?: 0.0) > smallest

    Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = Space.gutter)) {
        Text(
            if (plan.fxDifference) stringResource(R.string.fee_ask_fx_title, AmountFormatter.format(plan.prefill ?: 0.0, currency))
            else stringResource(R.string.fee_ask_title, SmsApprover.bankName(item)),
            style = MaterialTheme.typography.titleMedium, color = colors.text,
        )
        val sub = if (plan.fxDifference) stringResource(R.string.fee_ask_fx_sub) else feeRouteQuestion(plan.route)?.let { stringResource(it) }
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(top = Space.xxs))
        if (prompt.items.size > 1) {
            Text(
                pluralStringResource(R.plurals.fee_batch, prompt.items.size, prompt.items.size),
                style = NexoType.Meta, color = colors.textTertiary, modifier = Modifier.padding(top = Space.xs),
            )
        }
        Spacer(Modifier.height(Space.l))
        Row(
            Modifier.fillMaxWidth().clip(NexoShapes.control).background(colors.raised).clickable { editing = true }
                .heightIn(min = 56.dp).padding(horizontal = Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.fee_amount), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, modifier = Modifier.weight(1f))
            if (amount != null) AmountText(amount!!, currency, sign = com.nexo.app.core.common.money.AmountParts.Sign.NONE)
            else Text("—", style = NexoType.Amount, color = colors.textTertiary)
        }
        if (confirmLarge && tooLarge) {
            Text(stringResource(R.string.fee_too_large), style = NexoType.Meta, color = colors.textSecondary, modifier = Modifier.padding(top = Space.s))
        }
        val account = ref.accountById[item.accountId]?.name.orEmpty()
        Row(
            Modifier.fillMaxWidth().padding(top = Space.s).clip(NexoShapes.control).clickable { haptics.tick(); remember = !remember }.padding(vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = remember,
                onCheckedChange = { remember = it },
                colors = CheckboxDefaults.colors(checkedColor = colors.primary, uncheckedColor = colors.textTertiary, checkmarkColor = colors.onPrimary),
            )
            Text(
                stringResource(R.string.fee_remember, stringResource(feeRouteInline(plan.route)), account),
                style = MaterialTheme.typography.bodyMedium, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.gutter, vertical = Space.m)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Button(
                onClick = {
                    haptics.confirm()
                    onAnswer(if (plan.fxDifference) FeeChoice(amount, remember, includeInPurchase = true) else FeeChoice(null, remember))
                },
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.raised, contentColor = colors.text),
                contentPadding = PaddingValues(horizontal = Space.s),
            ) { Text(stringResource(if (plan.fxDifference) R.string.fee_include else R.string.fee_none), style = MaterialTheme.typography.labelLarge, maxLines = 1) }
            Button(
                onClick = {
                    val a = amount
                    when {
                        a == null || a <= 0 -> onAnswer(FeeChoice(null, remember))
                        tooLarge && !confirmLarge -> {
                            haptics.reject()
                            confirmLarge = true
                        }
                        else -> {
                            haptics.confirm()
                            onAnswer(FeeChoice(a, remember))
                        }
                    }
                },
                modifier = Modifier.weight(1.3f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                contentPadding = PaddingValues(horizontal = Space.s),
            ) { Text(stringResource(if (plan.fxDifference) R.string.fee_record_difference else R.string.fee_add), style = MaterialTheme.typography.titleSmall, maxLines = 1) }
        }
        TextButton(onClick = { onAnswer(null) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.action_cancel), color = colors.textSecondary)
        }
    }

    if (editing) {
        AmountSheet(
            title = stringResource(R.string.fee_amount),
            initial = amount,
            decimals = currency.decimals,
            allowSign = false,
            onConfirm = { v ->
                amount = v?.takeIf { it >= 0 }
                confirmLarge = false
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
}
