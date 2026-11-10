package com.nexo.app.ui.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.nexo.app.ui.accounts.AccountDetailScreen
import com.nexo.app.ui.accounts.AccountFormScreen
import com.nexo.app.ui.accounts.AccountGroupFormScreen
import com.nexo.app.ui.accounts.AccountGroupsScreen
import com.nexo.app.ui.accounts.AccountListScreen
import com.nexo.app.ui.accounts.AccountSettingsMenu
import com.nexo.app.ui.accounts.DeletedAccountsScreen
import com.nexo.app.ui.accounts.DeletedGroupsScreen
import com.nexo.app.ui.accounts.TotalStatsScreen
import com.nexo.app.ui.accounts.TransferExpenseScreen
import com.nexo.app.ui.budgets.BudgetAddScreen
import com.nexo.app.ui.budgets.BudgetDetailScreen
import com.nexo.app.ui.budgets.BudgetEditorScreen
import com.nexo.app.ui.budgets.BudgetSettingsScreen
import com.nexo.app.ui.categories.CategoryFormScreen
import com.nexo.app.ui.categories.CategoryListScreen
import com.nexo.app.ui.categories.SubcategoryListScreen
import com.nexo.app.ui.stats.StatsDetailScreen
import com.nexo.app.ui.tags.TagSettingsScreen
import com.nexo.app.ui.backup.AutoBackupHistoryScreen
import com.nexo.app.ui.backup.BackupDeviceScreen
import com.nexo.app.ui.backup.BackupScreen
import com.nexo.app.ui.backup.ExcelExportScreen
import com.nexo.app.ui.backup.ImportListScreen
import com.nexo.app.ui.backup.ImportPreviewScreen
import com.nexo.app.ui.backup.RestoreFileScreen
import com.nexo.app.ui.settings.AddSubCurrencyScreen
import com.nexo.app.ui.settings.ConfigurationScreen
import com.nexo.app.ui.settings.FeedbackScreen
import com.nexo.app.ui.settings.HelpPageScreen
import com.nexo.app.ui.settings.HelpScreen
import com.nexo.app.ui.settings.LockSettingsScreen
import com.nexo.app.ui.settings.MainCurrencyPickerScreen
import com.nexo.app.ui.settings.MainCurrencyScreen
import com.nexo.app.ui.settings.MonthStartScreen
import com.nexo.app.ui.settings.ReminderScreen
import com.nexo.app.ui.settings.StyleScreen
import com.nexo.app.ui.settings.SubCurrenciesScreen
import com.nexo.app.ui.settings.SubCurrencyEditScreen
import com.nexo.app.ui.settings.WidgetSettingsScreen
import com.nexo.app.ui.entry.BookmarksScreen
import com.nexo.app.ui.entry.EntryScreen
import com.nexo.app.ui.entry.RepeatListScreen
import com.nexo.app.ui.memo.MemoEditorScreen
import com.nexo.app.ui.memo.MemoListScreen
import com.nexo.app.ui.search.SearchScreen
import com.nexo.app.ui.shell.MainShell

/** Every screen of the app. */
fun nexoEntries(): (NavKey) -> NavEntry<NavKey> = entryProvider {
    entry<MainKey> { MainShell() }
    entry<EntryKey>(metadata = { key -> if (key.fromAdd) SheetSceneStrategy.sheet(key) else emptyMap() }) { EntryScreen(it) }
    entry<BookmarksKey> { BookmarksScreen(it) }
    entry<RepeatListKey> { RepeatListScreen() }
    entry<SearchKey> { SearchScreen(it) }
    entry<MemoEditorKey> { MemoEditorScreen(it) }
    entry<MemoListKey> { MemoListScreen(it) }
    entry<AccountDetailKey> { AccountDetailScreen(it) }
    entry<AccountFormKey> { AccountFormScreen(it) }
    entry<AccountSettingsMenuKey> { AccountSettingsMenu() }
    entry<AccountGroupsKey> { AccountGroupsScreen() }
    entry<AccountGroupFormKey> { AccountGroupFormScreen(it.groupId) }
    entry<AccountListKey> { AccountListScreen(it) }
    entry<DeletedAccountsKey> { DeletedAccountsScreen() }
    entry<DeletedGroupsKey> { DeletedGroupsScreen() }
    entry<TransferExpenseKey> { TransferExpenseScreen() }
    entry<TotalStatsKey> { TotalStatsScreen() }
    entry<StatsDetailKey> { StatsDetailScreen(it) }
    entry<BudgetDetailKey> { BudgetDetailScreen(it) }
    entry<BudgetSettingsKey> { BudgetSettingsScreen() }
    entry<BudgetAddKey> { BudgetAddScreen(it) }
    entry<BudgetEditorKey> { BudgetEditorScreen(it) }
    entry<CategoryListKey> { CategoryListScreen(it) }
    entry<SubcategoryListKey> { SubcategoryListScreen(it) }
    entry<CategoryFormKey> { CategoryFormScreen(it) }
    entry<TagSettingsKey> { TagSettingsScreen() }
    entry<ConfigurationKey> { ConfigurationScreen() }
    entry<MonthStartKey> { MonthStartScreen() }
    entry<MainCurrencyKey> { MainCurrencyScreen() }
    entry<MainCurrencyPickerKey> { MainCurrencyPickerScreen() }
    entry<SubCurrenciesKey> { SubCurrenciesScreen() }
    entry<AddSubCurrencyKey> { AddSubCurrencyScreen() }
    entry<SubCurrencyEditKey> { SubCurrencyEditScreen(it) }
    entry<StyleKey> { StyleScreen() }
    entry<WidgetSettingsKey> { WidgetSettingsScreen() }
    entry<ReminderKey> { ReminderScreen() }
    entry<LockSettingsKey> { LockSettingsScreen() }
    entry<BackupKey> { BackupScreen() }
    entry<BackupDeviceKey> { BackupDeviceScreen() }
    entry<AutoBackupHistoryKey> { AutoBackupHistoryScreen() }
    entry<ImportListKey> { ImportListScreen() }
    entry<ImportPreviewKey> { ImportPreviewScreen(it) }
    entry<ExcelExportKey> { ExcelExportScreen(it) }
    entry<RestoreFileKey> { RestoreFileScreen(it) }
    entry<HelpKey> { HelpScreen() }
    entry<HelpPageKey> { HelpPageScreen(it) }
    entry<FeedbackKey> { FeedbackScreen() }
}
