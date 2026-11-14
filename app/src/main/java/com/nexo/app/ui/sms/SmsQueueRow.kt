package com.nexo.app.ui.sms

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalAtm
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.common.money.AmountParts
import com.nexo.app.core.common.period.toLocalDateTime
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.DenyReason
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.SmsItem
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.SmsReason
import com.nexo.app.core.model.SmsStatus
import com.nexo.app.data.repository.SmsRepository
import com.nexo.app.data.sms.Missing
import com.nexo.app.data.sms.bankName
import com.nexo.app.data.sms.missing
import com.nexo.app.data.sms.noteFor
import com.nexo.app.ui.designsystem.component.AmountText
import com.nexo.app.ui.designsystem.component.CategoryTile
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.IconTile
import com.nexo.app.ui.designsystem.theme.CategoryHues
import com.nexo.app.ui.designsystem.theme.LocalMoneyColors
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.NexoShapes
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Sizes
import com.nexo.app.ui.designsystem.theme.Space

private const val DAY_MS = 24 * 60 * 60 * 1000L

/**
 * One Review Queue item: tile, title, account and time, a status line (certainty badge, what's missing, ageing),
 * and the amount. The foreign amount sits under an estimate. Tapping the amount edits it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmsQueueRow(
    item: SmsItem,
    ref: RefData,
    selected: Boolean,
    now: Long,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onAmountClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null,
    /** Replaces the status line's text, e.g. "Possibly already added" in the history picker. */
    note: String? = null,
) {
    val colors = LocalNexoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.row)
            .background(if (selected) colors.raised else colors.background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Space.gutter, vertical = Space.s + Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                (
                    scaleIn(NexoMotion.snappy(), initialScale = 0.6f) +
                        fadeIn(NexoMotion.enter(NexoMotion.SHORT))
                    ) togetherWith
                    (scaleOut(NexoMotion.exit(), targetScale = 0.6f) + fadeOut(NexoMotion.exit()))
            },
            label = "smsTile",
        ) { on ->
            if (on) {
                Box(
                    Modifier.size(Sizes.tile).clip(NexoShapes.tile).background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                ItemTile(item, ref)
            }
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(
                noteFor(item),
                style = NexoType.RowTitle,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                metaLine(item, ref),
                style = NexoType.Meta,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusLine(item, now, note)
        }
        Spacer(Modifier.width(Space.m))
        if (trailing != null) {
            trailing()
        } else {
            ItemAmount(item, ref, onAmountClick)
        }
    }
}

@Composable
private fun ItemTile(item: SmsItem, ref: RefData) {
    val colors = LocalNexoColors.current
    val v = ref.visual(item.categoryId)
    when {
        v != null -> CategoryTile(v.emoji, v.monogram, CategoryHues.of(v.colorIndex, colors.isDark))

        item.kind == SmsKind.TRANSFER_TO_CASH -> IconTile(
            Icons.Rounded.LocalAtm,
            colors.textSecondary,
        )

        item.kind.isTransfer -> IconTile(Icons.Rounded.SwapHoriz, colors.textSecondary)

        item.kind.isInflow -> IconTile(Icons.Rounded.SouthWest, colors.textSecondary)

        else -> IconTile(Icons.Rounded.NorthEast, colors.textSecondary)
    }
}

@Composable
private fun metaLine(item: SmsItem, ref: RefData): String {
    val context = LocalContext.current
    val bank = bankName(item)
    val account =
        ref.accountById[item.accountId]?.name ?: item.cardLast4?.let { "$bank ••$it" } ?: bank
    val dt = item.occurredAt.toLocalDateTime()
    val day = Formats.weekdayMonthDay(dt.toLocalDate())
    val time = "$day ${Formats.time(dt.toLocalTime(), DateFormat.is24HourFormat(context))}"
    val where = when {
        item.kind.isTransfer || item.kind == SmsKind.TRANSFER_OUT -> {
            val to = ref.accountById[item.toAccountId]?.name ?: item.payee
            if (to != null) "$account → $to" else account
        }

        else -> account
    }
    return "$where · $time"
}

@Composable
private fun StatusLine(item: SmsItem, now: Long, note: String?) {
    val colors = LocalNexoColors.current
    val badge = when {
        item.status != SmsStatus.PENDING -> null
        item.certainty == Certainty.ESTIMATED -> stringResource(R.string.sms_estimated)
        item.certainty == Certainty.UNVERIFIED -> stringResource(R.string.sms_unverified)
        else -> null
    }
    val text = note ?: statusText(item, now)
    if (badge == null && text == null) return
    Row(
        Modifier.padding(top = Space.xxs),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        if (badge != null) {
            Text(
                badge,
                Modifier.clip(
                    NexoShapes.pill,
                ).background(colors.raised).padding(horizontal = Space.s, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
        if (text != null) {
            Text(
                text,
                style = NexoType.Meta,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun statusText(item: SmsItem, now: Long): String? {
    when (item.status) {
        SmsStatus.DENIED -> return listOfNotNull(
            stringResource(R.string.sms_denied_label),
            item.denyReason?.let {
                stringResource(denyLabel(it))
            },
        ).joinToString(" · ")

        SmsStatus.EXPIRED -> return stringResource(R.string.sms_expired_label)

        SmsStatus.CANCELLED -> return stringResource(R.string.sms_cancelled_label)

        SmsStatus.APPROVED -> return null

        SmsStatus.PENDING -> Unit
    }
    val ageDays = ((now - item.capturedAt) / DAY_MS).toInt()
    val left = SmsRepository.QUEUE_WINDOW_DAYS - ageDays
    val missing = item.missing().firstOrNull()
    return when {
        missing != null -> stringResource(missingLabel(missing))

        left <= 5 -> pluralStringResource(
            R.plurals.sms_expires_in,
            left.coerceAtLeast(1),
            left.coerceAtLeast(1),
        )

        item.linkedItemId != null -> stringResource(R.string.sms_refund)

        item.certainty == Certainty.ESTIMATED && item.fxRate != null -> fxRateLine(
            LocalContext.current,
            item,
        )

        item.reason != null -> stringResource(reasonLabel(item.reason))

        ageDays >= 7 -> stringResource(R.string.sms_still_pending)

        item.evidenceCount > 1 && item.certainty == Certainty.UNVERIFIED ->
            pluralStringResource(
                R.plurals.sms_approval_codes,
                item.evidenceCount,
                item.evidenceCount,
            )

        else -> null
    }
}

@Composable
private fun ItemAmount(item: SmsItem, ref: RefData, onClick: (() -> Unit)?) {
    val colors = LocalNexoColors.current
    val money = LocalMoneyColors.current
    val foreign = item.certainty == Certainty.ESTIMATED || item.actualAmount != null
    val account = ref.accountCurrency(
        if (item.kind == SmsKind.TRANSFER_IN) {
            item.toAccountId
        } else {
            item.accountId
        },
    )
    val currency = if (foreign) {
        account
    } else {
        ref.currencies.firstOrNull {
            it.isoCode.equals(item.currency, true)
        }
    }
    val value = if (foreign) item.actualAmount ?: item.estimatedAmount else item.amount
    val color = when {
        item.kind.isTransfer -> colors.text
        item.kind.isInflow -> money.income
        else -> money.expense
    }
    val sign = when {
        item.kind.isTransfer -> AmountParts.Sign.NONE
        item.kind.isInflow -> AmountParts.Sign.ALWAYS
        else -> AmountParts.Sign.NEGATIVE
    }
    val signed = if (!item.kind.isTransfer && !item.kind.isInflow) -(value ?: 0.0) else value ?: 0.0
    Column(
        Modifier.clip(NexoShapes.control).then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            },
        ).padding(Space.xs),
        horizontalAlignment = Alignment.End,
    ) {
        when {
            item.status == SmsStatus.CANCELLED || item.status == SmsStatus.DENIED ||
                item.status == SmsStatus.EXPIRED ->
                Text(
                    "${item.currency} ${AmountFormatter.number(item.amount, 2)}",
                    style = NexoType.Amount,
                    color = colors.textTertiary,
                    textDecoration = TextDecoration.LineThrough,
                    maxLines = 1,
                )

            value != null -> AmountText(signed, currency, color = color, sign = sign)

            else -> Text("—", style = NexoType.Amount, color = colors.textTertiary)
        }
        if (foreign && item.status == SmsStatus.PENDING) {
            Text(
                (if (item.actualAmount == null) "≈ " else "") +
                    "${item.currency} ${AmountFormatter.number(item.amount, 2)}",
                style = NexoType.Meta,
                color = colors.textTertiary,
                maxLines = 1,
            )
        }
    }
}

fun denyLabel(r: DenyReason): Int = when (r) {
    DenyReason.DIDNT_HAPPEN -> R.string.sms_deny_didnt_happen
    DenyReason.DUPLICATE -> R.string.sms_deny_duplicate
    DenyReason.NOT_MINE -> R.string.sms_deny_not_mine
    DenyReason.NOT_TRANSACTION -> R.string.sms_deny_not_transaction
}

fun missingLabel(m: Missing): Int = when (m) {
    Missing.ACCOUNT -> R.string.sms_missing_account
    Missing.DESTINATION -> R.string.sms_missing_destination
    Missing.CATEGORY -> R.string.sms_missing_category
    Missing.AMOUNT -> R.string.sms_missing_amount
}

fun reasonLabel(r: SmsReason): Int = when (r) {
    SmsReason.FX_ESTIMATE -> R.string.sms_reason_fx
    SmsReason.APPROVAL_CODE -> R.string.sms_reason_approval
    SmsReason.GENERIC_RULES -> R.string.sms_reason_generic
    SmsReason.AMOUNT_FALLBACK -> R.string.sms_reason_amount
    SmsReason.KIND_CONFLICT -> R.string.sms_reason_kind
}
