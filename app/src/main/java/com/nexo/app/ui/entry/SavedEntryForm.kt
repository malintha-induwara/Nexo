package com.nexo.app.ui.entry

import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.PhotoItem
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.TxKind
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.Serializable

/** What the user typed into the entry form; everything else is loaded again from the nav key. */
@Serializable
internal data class SavedEntryForm(
    val viewing: Boolean,
    val type: EntryType,
    val balanceKind: TxKind?,
    val isBalanceAdjustment: Boolean,
    val date: String,
    val time: String,
    val accountId: String?,
    val toAccountId: String?,
    val categoryId: String?,
    val amount: Double?,
    val currencyId: String,
    val rate: Double?,
    val note: String,
    val description: String,
    val tagIds: List<String>,
    val photos: List<SavedPhoto>,
    val feeExpanded: Boolean,
    val feeAmount: Double?,
    val feeCurrencyId: String,
    val repeat: RepeatType,
    val repeatEnd: String?,
    val installments: Int,
)

/** A stored photo by its file, or a picked one by its content URI. */
@Serializable
internal data class SavedPhoto(
    val fileName: String? = null,
    val checksum: String? = null,
    val size: Long = 0,
    val uri: String? = null,
)

internal fun EntryForm.toSaved() = SavedEntryForm(
    viewing = viewing,
    type = type,
    balanceKind = balanceKind,
    isBalanceAdjustment = isBalanceAdjustment,
    date = date.toString(),
    time = time.toString(),
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    amount = amount,
    currencyId = currencyId,
    rate = rate,
    note = note,
    description = description,
    tagIds = tagIds,
    photos = photos.map {
        when (it) {
            is PhotoItem.Stored -> SavedPhoto(it.fileName, it.checksum, it.size)
            is PhotoItem.Picked -> SavedPhoto(uri = it.uri)
        }
    },
    feeExpanded = feeExpanded,
    feeAmount = feeAmount,
    feeCurrencyId = feeCurrencyId,
    repeat = repeat,
    repeatEnd = repeatEnd?.toString(),
    installments = installments,
)

/** [form] with the saved input laid over it; [keepPicked] says which picked photos are still readable. */
internal fun SavedEntryForm.appliedTo(form: EntryForm, keepPicked: (String) -> Boolean) = form.copy(
    viewing = viewing,
    type = type,
    balanceKind = balanceKind,
    isBalanceAdjustment = isBalanceAdjustment,
    date = LocalDate.parse(date),
    time = LocalTime.parse(time),
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    amount = amount,
    currencyId = currencyId,
    rate = rate,
    note = note,
    description = description,
    tagIds = tagIds,
    photos = photos.mapNotNull { p ->
        when {
            p.uri != null -> p.uri.takeIf(keepPicked)?.let(PhotoItem::Picked)
            p.fileName != null -> PhotoItem.Stored(p.fileName, p.checksum.orEmpty(), p.size)
            else -> null
        }
    },
    feeExpanded = feeExpanded,
    feeAmount = feeAmount,
    feeCurrencyId = feeCurrencyId,
    repeat = repeat,
    repeatEnd = repeatEnd?.let(LocalDate::parse),
    installments = installments,
)
