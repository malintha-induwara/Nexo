package com.nexo.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.RepeatType
import kotlinx.serialization.Serializable

@Serializable
data object MainKey : NavKey

enum class EntryMode { NEW, EDIT, COPY, FAVORITE, REPEAT_NEW, REPEAT_EDIT, PAY_CARD, IMPORT_FIX, SMS }

@Serializable
data class EntryKey(
    val mode: EntryMode = EntryMode.NEW,
    val txId: String? = null,
    val copyWithToday: Boolean = true,
    val date: String? = null,
    val accountId: String? = null,
    val type: EntryType? = null,
    val categoryId: String? = null,
    val favoriteId: String? = null,
    val repeatRuleId: String? = null,
    val importFile: String? = null,
    val importLine: Int = -1,
    val importOrder: String? = null,
    val reviewUnassigned: Boolean = false,
    val sourceText: String? = null,
    val repeat: RepeatType? = null,
    /** When true the form opens on top of the main screen from a shortcut or notification. */
    val external: Boolean = false,
    /** Opened from an add button: the form is shown as a drawer over the current screen. */
    val fromAdd: Boolean = false,
    /** [EntryMode.SMS]: the Review Queue item being reviewed; saving approves it. */
    val smsItemId: String? = null,
) : NavKey

@Serializable
data class BookmarksKey(val pickForForm: Boolean = false) : NavKey

@Serializable
data object RepeatListKey : NavKey

@Serializable
data class SearchKey(val fromNotification: Boolean = false) : NavKey

@Serializable
data class MemoEditorKey(val memoId: String? = null, val date: String? = null) : NavKey

@Serializable
data class MemoListKey(val date: String? = null) : NavKey

@Serializable
data class AccountDetailKey(val accountId: String?, val date: String? = null, val deleted: Boolean = false) : NavKey

@Serializable
data class AccountFormKey(val accountId: String? = null, val groupId: String? = null, val presetName: String? = null) : NavKey

@Serializable
data object AccountSettingsMenuKey : NavKey

@Serializable
data object AccountGroupsKey : NavKey

@Serializable
data class AccountGroupFormKey(val groupId: String? = null) : NavKey

enum class AccountListMode { NORMAL, REORDER, VISIBILITY, DELETE }

@Serializable
data class AccountListKey(val mode: AccountListMode = AccountListMode.NORMAL) : NavKey

@Serializable
data object DeletedAccountsKey : NavKey

@Serializable
data object DeletedGroupsKey : NavKey

@Serializable
data object TransferExpenseKey : NavKey

@Serializable
data object TotalStatsKey : NavKey

enum class StatsScope { WEEKLY, MONTHLY, ANNUALLY, PERIOD }

@Serializable
data class StatsDetailKey(
    val targetId: String?,
    val kind: StatsDetailKind,
    val scope: StatsScope,
    val start: String,
    val end: String,
    val colorIndex: Int,
) : NavKey

enum class StatsDetailKind { INCOME, EXPENSE, TRANSFER_EXPENSE_GROUP, TRANSFER_INCOME_GROUP }

@Serializable
data class BudgetDetailKey(val budgetId: String, val month: String) : NavKey

@Serializable
data object BudgetSettingsKey : NavKey

@Serializable
data class BudgetAddKey(val categoryId: String? = null, val presetTotal: Boolean = false, val preset: Boolean = false) : NavKey

@Serializable
data class BudgetEditorKey(val budgetId: String) : NavKey

@Serializable
data class CategoryListKey(val kind: CategoryKind) : NavKey

@Serializable
data class SubcategoryListKey(val parentId: String) : NavKey

@Serializable
data class CategoryFormKey(
    val kind: CategoryKind,
    val categoryId: String? = null,
    val parentId: String? = null,
    val sub: Boolean = false,
    val presetName: String? = null,
) : NavKey

@Serializable
data object TagSettingsKey : NavKey

@Serializable
data object ConfigurationKey : NavKey

@Serializable
data object MonthStartKey : NavKey

@Serializable
data object MainCurrencyKey : NavKey

@Serializable
data object MainCurrencyPickerKey : NavKey

@Serializable
data object SubCurrenciesKey : NavKey

@Serializable
data object AddSubCurrencyKey : NavKey

@Serializable
data class SubCurrencyEditKey(val currencyId: String? = null, val isoCode: String? = null, val custom: Boolean = false) : NavKey

@Serializable
data object StyleKey : NavKey

@Serializable
data object ReminderKey : NavKey

@Serializable
data object LockSettingsKey : NavKey

@Serializable
data object BackupKey : NavKey

@Serializable
data object BackupDeviceKey : NavKey

@Serializable
data object AutoBackupHistoryKey : NavKey

@Serializable
data object ImportListKey : NavKey

@Serializable
data class ImportPreviewKey(val fileName: String) : NavKey

@Serializable
data object HelpKey : NavKey

@Serializable
data class HelpPageKey(val page: String) : NavKey

@Serializable
data object FeedbackKey : NavKey

@Serializable
data class ExcelExportKey(val start: String? = null, val end: String? = null) : NavKey

@Serializable
data class RestoreFileKey(val uri: String) : NavKey

/** Bank SMS Review Queue. */
@Serializable
data object SmsQueueKey : NavKey

@Serializable
data object SmsSettingsKey : NavKey

@Serializable
data object SmsCardsKey : NavKey

@Serializable
data object SmsFeesKey : NavKey

@Serializable
data object SmsSendersKey : NavKey

/** A non-zero gap opens on the messages that arrived while capture was off. */
@Serializable
data class SmsHistoryKey(val gapFrom: Long = 0, val gapUntil: Long = 0) : NavKey
