package com.nexo.app.ui.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.nexo.app.ui.accounts.AccountDetailRoute
import com.nexo.app.ui.accounts.AccountFormRoute
import com.nexo.app.ui.accounts.AccountGroupFormRoute
import com.nexo.app.ui.accounts.AccountGroupsRoute
import com.nexo.app.ui.accounts.AccountListRoute
import com.nexo.app.ui.accounts.AccountSettingsMenuRoute
import com.nexo.app.ui.accounts.DeletedAccountsRoute
import com.nexo.app.ui.accounts.DeletedGroupsRoute
import com.nexo.app.ui.accounts.TotalStatsRoute
import com.nexo.app.ui.accounts.TransferExpenseRoute
import com.nexo.app.ui.backup.AutoBackupHistoryRoute
import com.nexo.app.ui.backup.BackupDeviceRoute
import com.nexo.app.ui.backup.BackupRoute
import com.nexo.app.ui.backup.ExcelExportRoute
import com.nexo.app.ui.backup.ImportListRoute
import com.nexo.app.ui.backup.ImportPreviewRoute
import com.nexo.app.ui.backup.RestoreFileRoute
import com.nexo.app.ui.budgets.BudgetAddRoute
import com.nexo.app.ui.budgets.BudgetDetailRoute
import com.nexo.app.ui.budgets.BudgetEditorRoute
import com.nexo.app.ui.budgets.BudgetSettingsRoute
import com.nexo.app.ui.categories.CategoryFormRoute
import com.nexo.app.ui.categories.CategoryListRoute
import com.nexo.app.ui.categories.SubcategoryListRoute
import com.nexo.app.ui.entry.BookmarksScreen
import com.nexo.app.ui.entry.EntryRoute
import com.nexo.app.ui.entry.RepeatListScreen
import com.nexo.app.ui.memo.MemoEditorRoute
import com.nexo.app.ui.memo.MemoListRoute
import com.nexo.app.ui.search.SearchRoute
import com.nexo.app.ui.settings.AddSubCurrencyRoute
import com.nexo.app.ui.settings.ConfigurationRoute
import com.nexo.app.ui.settings.FeedbackRoute
import com.nexo.app.ui.settings.HelpPageRoute
import com.nexo.app.ui.settings.HelpRoute
import com.nexo.app.ui.settings.LockSettingsRoute
import com.nexo.app.ui.settings.MainCurrencyPickerRoute
import com.nexo.app.ui.settings.MainCurrencyRoute
import com.nexo.app.ui.settings.MonthStartRoute
import com.nexo.app.ui.settings.ReminderRoute
import com.nexo.app.ui.settings.StyleRoute
import com.nexo.app.ui.settings.SubCurrenciesRoute
import com.nexo.app.ui.settings.SubCurrencyEditRoute
import com.nexo.app.ui.shell.MainShell
import com.nexo.app.ui.sms.SmsCardsScreen
import com.nexo.app.ui.sms.SmsFeesScreen
import com.nexo.app.ui.sms.SmsHistoryScreen
import com.nexo.app.ui.sms.SmsQueueScreen
import com.nexo.app.ui.sms.SmsSendersScreen
import com.nexo.app.ui.sms.SmsSettingsScreen
import com.nexo.app.ui.stats.StatsDetailRoute
import com.nexo.app.ui.tags.TagSettingsRoute

/** Every screen of the app. */
fun nexoEntries(): (NavKey) -> NavEntry<NavKey> = entryProvider {
    entry<MainKey> { MainShell() }
    entry<SmsQueueKey> { SmsQueueScreen() }
    entry<SmsSettingsKey> { SmsSettingsScreen() }
    entry<SmsCardsKey> { SmsCardsScreen() }
    entry<SmsFeesKey> { SmsFeesScreen() }
    entry<SmsHistoryKey> { SmsHistoryScreen(it) }
    entry<SmsSendersKey> { SmsSendersScreen() }
    entry<EntryKey>(metadata = { key ->
        if (key.fromAdd) SheetSceneStrategy.sheet(key) else emptyMap()
    }) { EntryRoute(it) }
    entry<BookmarksKey> { BookmarksScreen(it) }
    entry<RepeatListKey> { RepeatListScreen() }
    entry<SearchKey> { SearchRoute(it) }
    entry<MemoEditorKey> { MemoEditorRoute(it) }
    entry<MemoListKey> { MemoListRoute(it) }
    entry<AccountDetailKey> { AccountDetailRoute(it) }
    entry<AccountFormKey> { AccountFormRoute(it) }
    entry<AccountSettingsMenuKey> { AccountSettingsMenuRoute() }
    entry<AccountGroupsKey> { AccountGroupsRoute() }
    entry<AccountGroupFormKey> { AccountGroupFormRoute(it.groupId) }
    entry<AccountListKey> { AccountListRoute(it) }
    entry<DeletedAccountsKey> { DeletedAccountsRoute() }
    entry<DeletedGroupsKey> { DeletedGroupsRoute() }
    entry<TransferExpenseKey> { TransferExpenseRoute() }
    entry<TotalStatsKey> { TotalStatsRoute() }
    entry<StatsDetailKey> { StatsDetailRoute(it) }
    entry<BudgetDetailKey> { BudgetDetailRoute(it) }
    entry<BudgetSettingsKey> { BudgetSettingsRoute() }
    entry<BudgetAddKey> { BudgetAddRoute(it) }
    entry<BudgetEditorKey> { BudgetEditorRoute(it) }
    entry<CategoryListKey> { CategoryListRoute(it) }
    entry<SubcategoryListKey> { SubcategoryListRoute(it) }
    entry<CategoryFormKey> { CategoryFormRoute(it) }
    entry<TagSettingsKey> { TagSettingsRoute() }
    entry<ConfigurationKey> { ConfigurationRoute() }
    entry<MonthStartKey> { MonthStartRoute() }
    entry<MainCurrencyKey> { MainCurrencyRoute() }
    entry<MainCurrencyPickerKey> { MainCurrencyPickerRoute() }
    entry<SubCurrenciesKey> { SubCurrenciesRoute() }
    entry<AddSubCurrencyKey> { AddSubCurrencyRoute() }
    entry<SubCurrencyEditKey> { SubCurrencyEditRoute(it) }
    entry<StyleKey> { StyleRoute() }
    entry<ReminderKey> { ReminderRoute() }
    entry<LockSettingsKey> { LockSettingsRoute() }
    entry<BackupKey> { BackupRoute() }
    entry<BackupDeviceKey> { BackupDeviceRoute() }
    entry<AutoBackupHistoryKey> { AutoBackupHistoryRoute() }
    entry<ImportListKey> { ImportListRoute() }
    entry<ImportPreviewKey> { ImportPreviewRoute(it) }
    entry<ExcelExportKey> { ExcelExportRoute(it) }
    entry<RestoreFileKey> { RestoreFileRoute(it) }
    entry<HelpKey> { HelpRoute() }
    entry<HelpPageKey> { HelpPageRoute(it) }
    entry<FeedbackKey> { FeedbackRoute() }
}
