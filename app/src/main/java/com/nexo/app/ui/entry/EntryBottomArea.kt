package com.nexo.app.ui.entry

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.EntryType
import com.nexo.app.ui.designsystem.component.NumberPad
import com.nexo.app.ui.designsystem.component.PadKey
import com.nexo.app.ui.designsystem.component.pressScale
import com.nexo.app.ui.designsystem.component.rememberHaptics
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.Space
import com.nexo.app.ui.navigation.EntryMode

// The keypad, an account or category picker, or the save buttons.
@Composable
internal fun EntryBottomArea(
    state: EntryUiState,
    actions: EntryActions,
    dialogs: EntryDialogState,
    typeColor: Color,
    reviewingUnassigned: Boolean,
    onKey: (PadKey) -> Unit,
) {
    val s = state.form
    val ref = state.ref
    val device = state.device
    val haptics = rememberHaptics()
    AnimatedContent(
        targetState = when (s.panel) {
            Panel.AMOUNT, Panel.FEE -> BottomKind.PAD
            Panel.ACCOUNT, Panel.TO_ACCOUNT -> BottomKind.ACCOUNT
            Panel.CATEGORY -> BottomKind.CATEGORY
            Panel.NONE -> BottomKind.ACTIONS
        },
        transitionSpec = {
            // The old content is gone before the new one shows, so the two never overlap.
            (
                slideInVertically(NexoMotion.gentle()) { it / 5 } +
                    fadeIn(tween(180, delayMillis = 90))
                ) togetherWith
                (slideOutVertically(tween(140)) { it / 6 } + fadeOut(tween(90))) using
                SizeTransform(clip = true) { _, _ ->
                    spring(dampingRatio = 1f, stiffness = 420f)
                }
        },
        label = "entryBottom",
    ) { kind ->
        when (kind) {
            BottomKind.ACCOUNT -> AccountPanel(
                title = stringResource(
                    if (!s.isTransfer) {
                        R.string.account
                    } else if (s.panel == Panel.TO_ACCOUNT) {
                        R.string.to
                    } else {
                        R.string.from
                    },
                ),
                ref = ref,
                selectedId = if (s.panel == Panel.TO_ACCOUNT) {
                    s.toAccountId
                } else {
                    s.accountId
                },
                grouped = device?.groupedAccountPicker == true,
                accent = typeColor,
                onToggleGrouped = {
                    actions.setGroupedPicker(device?.groupedAccountPicker != true)
                },
                onSelect = actions.selectAccount,
                onEdit = actions.editAccounts,
                onAdd = actions.addAccount,
                onClose = actions.closePanel,
            )

            BottomKind.CATEGORY -> CategoryPanel(
                kind = if (s.type == EntryType.INCOME) {
                    CategoryKind.INCOME
                } else {
                    CategoryKind.EXPENSE
                },
                ref = ref,
                selectedId = s.categoryId,
                accent = typeColor,
                onSelect = actions.selectCategory,
                onEdit = {
                    actions.editCategories(
                        if (s.type == EntryType.INCOME) {
                            CategoryKind.INCOME
                        } else {
                            CategoryKind.EXPENSE
                        },
                    )
                },
                onClose = actions.closePanel,
            )

            BottomKind.PAD -> {
                val fee = s.panel == Panel.FEE
                val cur = ref.currency(if (fee) s.feeCurrencyId else s.currencyId)
                NumberPad(
                    title = stringResource(if (fee) R.string.fees else R.string.amount),
                    onKey = onKey,
                    onDone = actions.padDone,
                    onCalculator = { dialogs.calculator = true },
                    descending = device?.numberPadDescending == true,
                    decimals = cur.decimals,
                    signKey = !fee,
                    currencies = ref.activeCurrencies,
                    selectedCurrencyId = cur.id,
                    onCurrency = { actions.setPadCurrency(it.id) },
                    onGlobe = actions.openSubCurrencies,
                    accent = typeColor,
                    modifier = Modifier.navigationBarsPadding(),
                )
            }

            BottomKind.ACTIONS -> BottomButtons(
                s = s,
                onSave = {
                    haptics.confirm()
                    actions.save(false)
                },
                onContinue = {
                    haptics.confirm()
                    actions.save(true)
                },
                onDelete = { actions.setAskDelete(true) },
                onCopy = { dialogs.copy = true },
                onBookmark = {
                    haptics.confirm()
                    actions.bookmark()
                },
                onSkip = actions.skip,
                review = reviewingUnassigned,
                onDeny = actions.denySms,
            )
        }
    }
}

private enum class BottomKind { PAD, ACCOUNT, CATEGORY, ACTIONS }

@Composable
private fun BottomButtons(
    s: EntryForm,
    onSave: () -> Unit,
    onContinue: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onBookmark: () -> Unit,
    onSkip: () -> Unit,
    review: Boolean,
    onDeny: () -> Unit,
) {
    val colors = LocalNexoColors.current
    val sms = s.mode == EntryMode.SMS
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(
            horizontal = Space.gutter,
            vertical = Space.m,
        ),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        if (s.isEdit && s.viewing && !review) {
            SoftButton(
                stringResource(R.string.action_delete),
                onDelete,
                Modifier.weight(1f),
                content = MaterialTheme.colorScheme.error,
            )
            SoftButton(stringResource(R.string.copy), onCopy, Modifier.weight(1f))
            SoftButton(stringResource(R.string.bookmark), onBookmark, Modifier.weight(1f))
        } else {
            if (review) {
                SoftButton(stringResource(R.string.skip), onSkip, Modifier.weight(1f))
                SoftButton(
                    stringResource(R.string.action_delete),
                    onDelete,
                    Modifier.weight(1f),
                    content = MaterialTheme.colorScheme.error,
                )
            }
            if (sms) {
                SoftButton(
                    stringResource(R.string.sms_deny),
                    onDeny,
                    Modifier.weight(1f),
                    enabled = !s.saving,
                )
            }
            val interactions = remember { MutableInteractionSource() }
            Button(
                onClick = onSave,
                enabled = !s.saving,
                interactionSource = interactions,
                modifier = Modifier.weight(
                    if (review) 1.4f else 2f,
                ).height(52.dp).pressScale(interactions, 0.97f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Text(
                    when {
                        review -> stringResource(R.string.save_n, s.unassignedCount)
                        sms -> stringResource(R.string.sms_approve)
                        else -> stringResource(R.string.action_save)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (s.showContinue && !review) {
                SoftButton(
                    stringResource(R.string.action_continue),
                    onContinue,
                    Modifier.weight(1f),
                    enabled = !s.saving,
                )
            }
        }
    }
}

@Composable
private fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    content: Color = LocalNexoColors.current.text,
    enabled: Boolean = true,
) {
    val colors = LocalNexoColors.current
    val interactions = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactions,
        modifier = modifier.height(52.dp).pressScale(interactions, 0.97f),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.raised,
            contentColor = content,
        ),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
