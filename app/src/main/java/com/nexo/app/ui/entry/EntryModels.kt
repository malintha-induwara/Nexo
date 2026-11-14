package com.nexo.app.ui.entry

import android.net.Uri
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.InputOrder
import com.nexo.app.core.model.PhotoItem
import com.nexo.app.core.model.ReceiptRead
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.SmsItem
import com.nexo.app.core.model.Tag
import com.nexo.app.core.model.TxKind
import com.nexo.app.ui.designsystem.UserMessage
import com.nexo.app.ui.designsystem.component.PadInput
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import java.time.LocalDate
import java.time.LocalTime

enum class Panel { NONE, ACCOUNT, TO_ACCOUNT, CATEGORY, AMOUNT, FEE }

/** Visual slots of the account / category / amount block. */
enum class Field { ACCOUNT, CATEGORY, FROM, TO, AMOUNT, FEE }

data class InstallmentInfo(val index: Int, val count: Int, val balance: Double, val total: Double)

data class NoteSuggestion(val note: String, val count: Int, val lastDate: String)

/** The entry form being edited. */
data class EntryForm(
    val loaded: Boolean = false,
    val mode: EntryMode = EntryMode.NEW,
    val viewing: Boolean = false,
    val type: EntryType = EntryType.EXPENSE,
    val balanceKind: TxKind? = null,
    val isBalanceAdjustment: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val accountId: String? = null,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val amount: Double? = null,
    val currencyId: String = "",
    /** Main-currency units per transaction-currency unit for this entry; null = stored rate. */
    val rate: Double? = null,
    val note: String = "",
    val description: String = "",
    val tagIds: List<String> = emptyList(),
    val photos: List<PhotoItem> = emptyList(),
    val feeExpanded: Boolean = false,
    val feeAmount: Double? = null,
    val feeCurrencyId: String = "",
    val feeLoaded: Boolean = false,
    val repeat: RepeatType = RepeatType.NONE,
    val repeatEnd: LocalDate? = null,
    val installments: Int = 0,
    val panel: Panel = Panel.NONE,
    val pad: PadInput = PadInput(),
    val saving: Boolean = false,
    val originalId: String? = null,
    val installmentInfo: InstallmentInfo? = null,
    val sourceText: String? = null,
    val unassignedCount: Int = 0,
    val noteFocusRequest: Int = 0,
    val filledOnce: Boolean = false,
    val confirmRepeat: Boolean = false,
    val askDelete: Boolean = false,
    val inputOrder: InputOrder = InputOrder.ACCOUNT_FIRST,
    val presetAccountId: String? = null,
    val presetCategoryId: String? = null,
    /** The foreign amount and rate behind an SMS item booked at an FX estimate; [fxFromApi] adds the attribution link. */
    val fxNote: FxNote? = null,
    val fxFromApi: Boolean = false,
    val scan: ScanUi? = null,
) {
    val isTransfer: Boolean get() = type == EntryType.TRANSFER

    /** Scan receipt is offered wherever an expense or income amount can come from a photo of a bill; it is how photos are attached. */
    val scanAllowed: Boolean
        get() = !isTransfer && !isBalance && !isBalanceAdjustment && !repeatMode && repeat == RepeatType.NONE && photos.size < 3

    /** Transfers carry a fee; so does an SMS expense, as a linked bank-fee row. */
    val feeAllowed: Boolean get() = isTransfer || (mode == EntryMode.SMS && type == EntryType.EXPENSE)
    val isBalance: Boolean get() = balanceKind != null
    val isEdit: Boolean get() = originalId != null && mode == EntryMode.EDIT
    val repeatMode: Boolean get() = mode == EntryMode.REPEAT_NEW || mode == EntryMode.REPEAT_EDIT
    val showRepInst: Boolean get() = mode !in setOf(EntryMode.EDIT, EntryMode.PAY_CARD, EntryMode.IMPORT_FIX)
    val showContinue: Boolean get() = mode == EntryMode.NEW && repeat == RepeatType.NONE
    val showTime: Boolean get() = repeat == RepeatType.NONE

    val fields: List<Field>
        get() {
            val pair = if (isTransfer) listOf(Field.FROM, Field.TO) else listOf(Field.ACCOUNT, Field.CATEGORY)
            val amount = if (feeAllowed && feeExpanded) listOf(Field.AMOUNT, Field.FEE) else listOf(Field.AMOUNT)
            return if (inputOrder == InputOrder.ACCOUNT_FIRST) pair + amount
            else if (isTransfer) amount + pair else amount + listOf(Field.CATEGORY, Field.ACCOUNT)
        }

    fun isEmpty(f: Field): Boolean = when (f) {
        Field.ACCOUNT, Field.FROM -> accountId == null
        Field.CATEGORY -> categoryId == null && !isBalance && !isBalanceAdjustment
        Field.TO -> toAccountId == null
        Field.AMOUNT -> amount == null
        Field.FEE -> feeExpanded && feeAmount == null
    }

    companion object {
        fun panelFor(f: Field): Panel = when (f) {
            Field.ACCOUNT, Field.FROM -> Panel.ACCOUNT
            Field.TO -> Panel.TO_ACCOUNT
            Field.CATEGORY -> Panel.CATEGORY
            Field.AMOUNT -> Panel.AMOUNT
            Field.FEE -> Panel.FEE
        }
    }
}

/** A receipt photo being read, then waiting for the user to confirm or correct what was read. */
sealed interface ScanUi {
    val uri: Uri

    data class Reading(override val uri: Uri) : ScanUi
    data class Done(override val uri: Uri, val read: ReceiptRead) : ScanUi
}

/** The FX estimate behind an SMS amount; [editHint] adds "edit the amount to record the actual one". */
data class FxNote(val item: SmsItem, val editHint: Boolean)

/** Where the form goes once it is done. */
sealed interface EntryNavigation {
    data object Close : EntryNavigation

    data class Open(val key: EntryKey, val replace: Boolean = true) : EntryNavigation
}

/** Strings the form needs as data; the screen resolves them, since ViewModels hold no Context. */
data class EntryTexts(val paymentNote: String)

data class EntryUiState(
    val form: EntryForm = EntryForm(),
    val ref: RefData = RefData(),
    val device: DeviceSettings? = null,
    val suggestions: List<NoteSuggestion> = emptyList(),
    val tags: List<Tag> = emptyList(),
    /** Shown once, then cleared with [EntryViewModel.onUserMessageShown]. */
    val userMessage: UserMessage? = null,
    /** Handled once, then cleared with [EntryViewModel.onNavigationHandled]. */
    val navigation: EntryNavigation? = null,
)
