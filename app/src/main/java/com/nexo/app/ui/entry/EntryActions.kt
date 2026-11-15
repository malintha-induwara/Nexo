package com.nexo.app.ui.entry

import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.RepeatType
import com.nexo.app.data.repository.TagResult
import com.nexo.app.ui.designsystem.component.PadKey
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Everything the entry form can do. The route wires it to the ViewModel, navigation and the
 * receipt scanner; the defaults do nothing, for previews and tests.
 */
class EntryActions(
    // Leaving the form and the screens it opens.
    val close: () -> Unit = {},
    val openBookmarks: () -> Unit = {},
    val editAccounts: () -> Unit = {},
    val addAccount: () -> Unit = {},
    val editCategories: (CategoryKind) -> Unit = {},
    val openSubCurrencies: () -> Unit = {},
    // Receipt capture.
    val scanReceipt: () -> Unit = {},
    val takePhoto: () -> Unit = {},
    val pickPhoto: () -> Unit = {},
    val dismissScan: () -> Unit = {},
    val applyReceipt: (amount: Double?, useMerchant: Boolean) -> Unit = { _, _ -> },
    // Fields and panels.
    val setType: (EntryType) -> Unit = {},
    val revert: () -> Unit = {},
    val enterEdit: () -> Unit = {},
    val openPanel: (Panel) -> Unit = {},
    val closePanel: () -> Unit = {},
    val setNote: (String) -> Unit = {},
    val setDescription: (String) -> Unit = {},
    val swap: () -> Unit = {},
    val selectAccount: (String) -> Unit = {},
    val selectCategory: (String) -> Unit = {},
    val setGroupedPicker: (Boolean) -> Unit = {},
    val setDate: (LocalDate) -> Unit = {},
    val setTime: (LocalTime) -> Unit = {},
    // Amount, fee and exchange rate.
    val padKey: (PadKey) -> Unit = {},
    val padDone: () -> Unit = {},
    val setPadCurrency: (String) -> Unit = {},
    val calculatorResult: (BigDecimal) -> Unit = {},
    val setMainAmount: (Double) -> Unit = {},
    val setRate: (Double) -> Unit = {},
    val setFeeExpanded: (Boolean) -> Unit = {},
    // Repeat and installments.
    val setRepeat: (RepeatType) -> Unit = {},
    val setRepeatEnd: (LocalDate?) -> Unit = {},
    val setInstallments: (Int) -> Unit = {},
    val clearRepInst: () -> Unit = {},
    // Tags and photos.
    val setTags: (List<String>) -> Unit = {},
    val removeTag: (String) -> Unit = {},
    val tags: TagActions = TagActions(
        add = { TagResult.Empty },
        rename = { _, _ -> TagResult.Empty },
        usageCount = { 0 },
        delete = {},
        reorder = {},
    ),
    val photoFile: (String) -> File = ::File,
    val exportPhoto: suspend (String) -> Boolean = { false },
    val removePhoto: (Int) -> Unit = {},
    // Saving and the other bottom buttons.
    val save: (continueAfter: Boolean) -> Unit = {},
    val confirmRepeatSave: () -> Unit = {},
    val dismissRepeatConfirm: () -> Unit = {},
    val setAskDelete: (Boolean) -> Unit = {},
    val delete: (wholeGroup: Boolean) -> Unit = {},
    val copy: (withToday: Boolean) -> Unit = {},
    val bookmark: () -> Unit = {},
    val skip: () -> Unit = {},
    val denySms: () -> Unit = {},
    // One-time notices.
    val markPhotoNoticeShown: () -> Unit = {},
    val markTransferGuideShown: () -> Unit = {},
)
