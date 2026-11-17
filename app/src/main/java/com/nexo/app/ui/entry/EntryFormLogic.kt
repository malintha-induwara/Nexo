package com.nexo.app.ui.entry

import com.nexo.app.core.common.money.Money
import com.nexo.app.core.common.period.toIsoText
import com.nexo.app.core.model.Currency
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.FeeDraft
import com.nexo.app.core.model.PhotoItem
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.RepeatRule
import com.nexo.app.core.model.RepeatSchedule
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.TxDraft
import com.nexo.app.core.model.TxKind
import com.nexo.app.ui.designsystem.component.PadInput
import com.nexo.app.ui.designsystem.component.PadKey
import com.nexo.app.ui.navigation.EntryMode
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

internal fun EntryForm.toDraft(): TxDraft = TxDraft(
    editingId = originalId,
    type = type,
    balanceKind = balanceKind,
    isBalanceAdjustment = isBalanceAdjustment && (isBalance || categoryId == null),
    accountId = accountId,
    toAccountId = if (isTransfer) toAccountId else null,
    categoryId = if (isTransfer) null else categoryId,
    dateTime = date.atTime(if (showTime) time else LocalTime.MIDNIGHT),
    amount = amount ?: 0.0,
    currencyId = currencyId,
    rate = rate,
    note = note,
    description = description,
    tagIds = tagIds,
    photos = photos,
    fee = if (isTransfer && feeExpanded && feeAmount != null) {
        FeeDraft(feeAmount, feeCurrencyId)
    } else {
        null
    },
    installments = if (type == EntryType.EXPENSE && !isBalance) installments else 0,
    sourceText = sourceText,
)

// [id] and [createdAt] carry over from the rule being edited.
internal fun EntryForm.toRepeatRule(id: String, currency: Currency, createdAt: Long, now: Long) =
    RepeatRule(
        id = id,
        kind = when (type) {
            EntryType.INCOME -> TxKind.INCOME
            EntryType.EXPENSE -> TxKind.EXPENSE
            EntryType.TRANSFER -> TxKind.TRANSFER_OUT
        },
        accountId = accountId,
        toAccountId = if (isTransfer) toAccountId else null,
        categoryId = if (isTransfer) null else categoryId,
        currencyId = currency.id,
        amount = Money.round(amount ?: 0.0, currency.decimals),
        note = note.trim(),
        description = description,
        repeatType = repeat,
        nextDate = date.toIsoText(),
        endDate = repeatEnd?.toIsoText(),
        anchorDay = date.dayOfMonth,
        createdAt = createdAt,
        updatedAt = now,
    )

// The category, destination and installments start over.
internal fun EntryForm.withType(t: EntryType): EntryForm {
    if (type == t && !isBalance) return this
    return copy(
        type = t,
        balanceKind = null,
        isBalanceAdjustment = false,
        categoryId = null,
        toAccountId = null,
        installments = 0,
        // An SMS expense keeps its fee: the bank charged it either way.
        feeExpanded = if (t == EntryType.TRANSFER ||
            (mode == EntryMode.SMS && t == EntryType.EXPENSE)
        ) {
            feeExpanded
        } else {
            false
        },
    )
}

// With no amount yet, the form takes the account's currency.
internal fun EntryForm.withAccount(id: String, r: RefData): EntryForm = when (panel) {
    Panel.TO_ACCOUNT -> copy(toAccountId = id)

    else -> {
        var n = copy(accountId = id)
        if (amount == null) {
            n = n.copy(
                currencyId = r.accountCurrency(id).id,
                feeCurrencyId = r.accountCurrency(id).id,
                rate = null,
            )
        }
        n
    }
}

// The fee is looked up again for the new direction.
internal fun EntryForm.swapped(r: RefData): EntryForm {
    var n = copy(accountId = toAccountId, toAccountId = accountId, feeLoaded = false)
    if (amount == null) n = n.copy(currencyId = r.accountCurrency(n.accountId).id)
    return n
}

// Applies [k] to the amount, or to the fee when its pad is open.
internal fun EntryForm.afterPadKey(k: PadKey): EntryForm {
    val r = pad.press(k)
    val forFee = panel == Panel.FEE
    val current = if (forFee) feeAmount else amount
    val value = when {
        r.negateExisting -> current?.let { -it }
        r.input.value != null -> r.input.value
        r.clearField -> null
        r.input.buffer.isEmpty() && pad.buffer.isEmpty() -> current
        else -> null
    }
    val n = if (forFee) copy(feeAmount = value?.let { abs(it) }) else copy(amount = value)
    return n.copy(pad = r.input, viewing = false)
}

// A repeat date always lands on a day the schedule can hit.
internal fun EntryForm.withDate(d: LocalDate): EntryForm =
    copy(date = if (repeat != RepeatType.NONE) RepeatSchedule.snap(d, repeat) else d)

// Repeating rules carry no tags, fee or installments.
internal fun EntryForm.withRepeat(t: RepeatType): EntryForm = if (t == RepeatType.NONE) {
    copy(repeat = RepeatType.NONE, repeatEnd = null)
} else {
    copy(
        repeat = t,
        installments = 0,
        date = RepeatSchedule.snap(date, t),
        tagIds = emptyList(),
        feeExpanded = false,
        feeAmount = null,
    )
}

// Installments and repeats exclude each other.
internal fun EntryForm.withInstallments(n: Int): EntryForm = copy(
    installments = n.coerceIn(0, 600),
    repeat = if (n > 0) RepeatType.NONE else repeat,
)

// Rounds [amount] to [decimals]; the shop becomes the note when [useMerchant] and the note is
// empty. The date is left alone.
internal fun EntryForm.withReceipt(
    done: ScanUi.Done,
    amount: Double?,
    useMerchant: Boolean,
    decimals: Int,
): EntryForm {
    val read = done.read
    val photo = PhotoItem.Picked(done.uri.toString())
    var n = copy(scan = null, photos = if (photo in photos) photos else (photos + photo).take(3))
    if (amount != null && amount > 0) {
        n = n.copy(
            amount = BigDecimal.valueOf(amount).setScale(decimals, RoundingMode.HALF_UP).toDouble(),
            pad = PadInput("", decimals, allowSign = true),
        )
    }
    if (useMerchant && read.merchant != null && note.isBlank()) n = n.copy(note = read.merchant)
    return n
}

// The first empty field in visual order.
internal fun EntryForm.nextEmptyField(): Field? =
    fields.firstOrNull { isEmpty(it) && !(it == Field.CATEGORY && isBalance) }
