package com.nexo.app.ui.entry

import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.RepeatType
import com.nexo.app.ui.designsystem.component.PadKey
import com.nexo.app.ui.tags.TagActions
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

// No-op defaults serve previews and tests.
class EntryActions(
    val close: () -> Unit = {},
    val openBookmarks: () -> Unit = {},
    val editAccounts: () -> Unit = {},
    val addAccount: () -> Unit = {},
    val editCategories: (CategoryKind) -> Unit = {},
    val openSubCurrencies: () -> Unit = {},
    val scanReceipt: () -> Unit = {},
    val takePhoto: () -> Unit = {},
    val pickPhoto: () -> Unit = {},
    val dismissScan: () -> Unit = {},
    val applyReceipt: (amount: Double?, useMerchant: Boolean) -> Unit = { _, _ -> },
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
    val padKey: (PadKey) -> Unit = {},
    val padDone: () -> Unit = {},
    val setPadCurrency: (String) -> Unit = {},
    val calculatorResult: (BigDecimal) -> Unit = {},
    val setMainAmount: (Double) -> Unit = {},
    val setRate: (Double) -> Unit = {},
    val setFeeExpanded: (Boolean) -> Unit = {},
    val setRepeat: (RepeatType) -> Unit = {},
    val setRepeatEnd: (LocalDate?) -> Unit = {},
    val setInstallments: (Int) -> Unit = {},
    val clearRepInst: () -> Unit = {},
    val setTags: (List<String>) -> Unit = {},
    val removeTag: (String) -> Unit = {},
    val tags: TagActions = TagActions(),
    val exportPhoto: (fileName: String) -> Unit = {},
    val onPhotoExportShown: () -> Unit = {},
    val removePhoto: (Int) -> Unit = {},
    val save: (continueAfter: Boolean) -> Unit = {},
    val confirmRepeatSave: () -> Unit = {},
    val dismissRepeatConfirm: () -> Unit = {},
    val setAskDelete: (Boolean) -> Unit = {},
    val delete: (wholeGroup: Boolean) -> Unit = {},
    val copy: (withToday: Boolean) -> Unit = {},
    val bookmark: () -> Unit = {},
    val skip: () -> Unit = {},
    val denySms: () -> Unit = {},
    val markPhotoNoticeShown: () -> Unit = {},
    val markTransferGuideShown: () -> Unit = {},
)
