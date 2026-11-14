package com.nexo.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.ColorSet
import com.nexo.app.core.model.InputOrder
import com.nexo.app.core.model.StartScreen
import com.nexo.app.core.model.SwipeMode
import com.nexo.app.core.model.TimeDisplay
import com.nexo.app.core.model.WeekendRule
import com.nexo.app.ui.accounts.CardTimingDialog
import com.nexo.app.ui.accounts.cardAccountingLabel
import com.nexo.app.ui.components.AmountSheet
import com.nexo.app.ui.components.ChoiceDialog
import com.nexo.app.ui.components.ConfirmDialog
import com.nexo.app.ui.components.Formats
import com.nexo.app.ui.components.NexoTopBar
import com.nexo.app.ui.components.SectionHeader
import com.nexo.app.ui.components.SettingRow
import com.nexo.app.ui.navigation.AccountGroupsKey
import com.nexo.app.ui.navigation.AccountListKey
import com.nexo.app.ui.navigation.BudgetSettingsKey
import com.nexo.app.ui.navigation.CategoryListKey
import com.nexo.app.ui.navigation.DeletedAccountsKey
import com.nexo.app.ui.navigation.DeletedGroupsKey
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.LockSettingsKey
import com.nexo.app.ui.navigation.MainCurrencyKey
import com.nexo.app.ui.navigation.MonthStartKey
import com.nexo.app.ui.navigation.ReminderKey
import com.nexo.app.ui.navigation.RepeatListKey
import com.nexo.app.ui.navigation.StyleKey
import com.nexo.app.ui.navigation.SubCurrenciesKey
import com.nexo.app.ui.navigation.TagSettingsKey
import com.nexo.app.ui.navigation.TransferExpenseKey
import com.nexo.app.ui.navigation.WidgetSettingsKey
import java.time.DayOfWeek

@Composable
fun onOff(v: Boolean) = stringResource(if (v) R.string.on else R.string.off)

@Composable
fun timeDisplayLabel(t: TimeDisplay) = stringResource(
    when (t) {
        TimeDisplay.NONE_DESC -> R.string.time_none_desc
        TimeDisplay.NONE_ASC -> R.string.time_none_asc
        TimeDisplay.SHOW_DESC -> R.string.time_show_desc
        TimeDisplay.SHOW_ASC -> R.string.time_show_asc
        TimeDisplay.INPUT_DESC -> R.string.time_input_desc
        TimeDisplay.INPUT_ASC -> R.string.time_input_asc
    },
)

@Composable
fun ConfigurationScreen(vm: SettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val s = ref.settings
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSwipe by rememberSaveable { mutableStateOf<SwipeMode?>(null) }
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.configuration), onBack = { nav.back() }) }) { p ->
        Column(Modifier.padding(p).verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.tab_accounts))
            SettingRow(stringResource(R.string.account_group), onClick = { nav.navigate(AccountGroupsKey) })
            SettingRow(stringResource(R.string.accounts_setting), onClick = { nav.navigate(AccountListKey()) })
            SettingRow(stringResource(R.string.deleted_groups), onClick = { nav.navigate(DeletedGroupsKey) })
            SettingRow(stringResource(R.string.deleted_accounts), onClick = { nav.navigate(DeletedAccountsKey) })
            SettingRow(stringResource(R.string.transfer_expense_setting), onClick = { nav.navigate(TransferExpenseKey) })
            SettingRow(stringResource(R.string.card_expense_config), cardAccountingLabel(s.cardAccounting), onClick = { dialog = "card" })

            SectionHeader(stringResource(R.string.category_repeat))
            SettingRow(stringResource(R.string.income_category_setting), onClick = { nav.navigate(CategoryListKey(CategoryKind.INCOME)) })
            SettingRow(stringResource(R.string.expenses_category_setting), onClick = { nav.navigate(CategoryListKey(CategoryKind.EXPENSE)) })
            SettingRow(stringResource(R.string.subcategory), onOff(s.subcategories), onClick = { dialog = "sub" })
            SettingRow(stringResource(R.string.budget_setting), onClick = { nav.navigate(BudgetSettingsKey) })
            SettingRow(stringResource(R.string.tag_settings), onClick = { nav.navigate(TagSettingsKey) })
            SettingRow(stringResource(R.string.repeat_setting), onClick = { nav.navigate(RepeatListKey) })

            SectionHeader(stringResource(R.string.configuration))
            SettingRow(stringResource(R.string.main_currency_setting), "${ref.mainCurrency.isoCode ?: ""} (${ref.mainCurrency.symbol})", onClick = { nav.navigate(MainCurrencyKey) })
            SettingRow(stringResource(R.string.sub_currency_setting), ref.activeCurrencies.drop(1).joinToString(", ") { it.symbol }, onClick = { nav.navigate(SubCurrenciesKey) })
            SettingRow(stringResource(R.string.start_screen), stringResource(if (s.startScreen == StartScreen.DAILY) R.string.daily else R.string.calendar), onClick = { dialog = "start" })
            SettingRow(stringResource(R.string.monthly_start_date), stringResource(R.string.every_n, s.monthStartDay), onClick = { nav.navigate(MonthStartKey) })
            SettingRow(stringResource(R.string.weekly_start_day), Formats.weekdayFull(s.weekStartDay), onClick = { dialog = "week" })
            SettingRow(stringResource(R.string.carry_over_setting), onOff(s.carryOver), onClick = { dialog = "carry" })
            SettingRow(stringResource(R.string.swipe), swipeLabel(s.swipeMode), onClick = { dialog = "swipe" })
            SettingRow(stringResource(R.string.color_setting), stringResource(if (s.colorSet == ColorSet.A) R.string.color_set_a else R.string.color_set_b), onClick = { dialog = "color" })
            SettingRow(stringResource(R.string.time_input), timeDisplayLabel(s.timeDisplay), onClick = { dialog = "time" })
            SettingRow(stringResource(R.string.show_description), onOff(s.showDescription), onClick = { dialog = "desc" })
            SettingRow(stringResource(R.string.autocomplete), onOff(s.autocomplete), onClick = { dialog = "auto" })
            SettingRow(stringResource(R.string.input_order), stringResource(if (s.inputOrder == InputOrder.AMOUNT_FIRST) R.string.from_amount else R.string.from_account), onClick = { dialog = "order" })
            SettingRow(stringResource(R.string.memo_button_setting), onOff(s.memoButton), onClick = { dialog = "memo" })

            SectionHeader(stringResource(R.string.other))
            SettingRow(stringResource(R.string.lock_settings), onClick = { nav.navigate(LockSettingsKey) })
            SettingRow(stringResource(R.string.alarm_setting), onOff(device?.reminderEnabled == true), onClick = { nav.navigate(ReminderKey) })
            SettingRow(stringResource(R.string.quick_add), onOff(s.quickAddNotification), onClick = { dialog = "quick" })
            SettingRow(stringResource(R.string.number_pad_order), stringResource(if (device?.numberPadDescending == true) R.string.pad_descending else R.string.pad_ascending), onClick = { dialog = "pad" })
            SettingRow(stringResource(R.string.style), onClick = { nav.navigate(StyleKey) })
            SettingRow(stringResource(R.string.widget_settings), onClick = { nav.navigate(WidgetSettingsKey) })
            SettingRow(stringResource(R.string.language_setting), currentLanguageLabel(), onClick = { dialog = "lang" })
            HorizontalDivider()
        }
    }
    val close = { dialog = null }
    when (dialog) {
        "card" -> CardTimingDialog(s.cardAccounting, onSelect = { c -> vm.update { it.copy(cardAccounting = c) } }, onDismiss = close)
        "sub" -> ChoiceDialog(stringResource(R.string.subcategory), listOf(false, true), s.subcategories, { onOff(it) }, onSelect = { v -> vm.update { it.copy(subcategories = v) } }, onDismiss = close, description = stringResource(R.string.subcategory_text))
        "start" -> ChoiceDialog(stringResource(R.string.start_screen), StartScreen.entries, s.startScreen, { stringResource(if (it == StartScreen.DAILY) R.string.daily else R.string.calendar) }, onSelect = { v -> vm.update { it.copy(startScreen = v) } }, onDismiss = close)
        "week" -> ChoiceDialog(stringResource(R.string.weekly_start_day), listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }, s.weekStartDay, { Formats.weekdayFull(it) }, onSelect = { v -> vm.update { it.copy(weekStartDay = v) } }, onDismiss = close)
        "carry" -> ChoiceDialog(stringResource(R.string.carry_over_setting), listOf(false, true), s.carryOver, { onOff(it) }, onSelect = { v -> vm.update { it.copy(carryOver = v) } }, onDismiss = close, description = stringResource(R.string.carry_over_text))
        "swipe" -> ChoiceDialog(stringResource(R.string.swipe), SwipeMode.entries, s.swipeMode, { swipeLabel(it) }, onSelect = { pendingSwipe = it }, onDismiss = close)
        "color" -> ChoiceDialog(stringResource(R.string.color_setting), ColorSet.entries, s.colorSet, { stringResource(if (it == ColorSet.A) R.string.color_set_a else R.string.color_set_b) }, onSelect = { v -> vm.update { it.copy(colorSet = v) } }, onDismiss = close)
        "time" -> ChoiceDialog(stringResource(R.string.time_input), listOf(TimeDisplay.NONE_DESC, TimeDisplay.NONE_ASC, TimeDisplay.SHOW_DESC, TimeDisplay.SHOW_ASC, TimeDisplay.INPUT_DESC, TimeDisplay.INPUT_ASC), s.timeDisplay, { timeDisplayLabel(it) }, onSelect = { v -> vm.update { it.copy(timeDisplay = v) } }, onDismiss = close)
        "desc" -> ChoiceDialog(stringResource(R.string.show_description), listOf(false, true), s.showDescription, { onOff(it) }, onSelect = { v -> vm.update { it.copy(showDescription = v) } }, onDismiss = close)
        "auto" -> ChoiceDialog(
            stringResource(R.string.autocomplete), listOf(false, true), s.autocomplete, { onOff(it) }, onSelect = { v -> vm.update { it.copy(autocomplete = v) } }, onDismiss = close,
            extraButton = {
                TextButton(onClick = {
                    vm.update { it.copy(autocompleteResetAt = System.currentTimeMillis()) }
                    android.widget.Toast.makeText(context, R.string.reset_complete, android.widget.Toast.LENGTH_SHORT).show()
                    close()
                }) { Text(stringResource(R.string.action_reset)) }
            },
        )
        "order" -> ChoiceDialog(stringResource(R.string.input_order), InputOrder.entries, s.inputOrder, { stringResource(if (it == InputOrder.AMOUNT_FIRST) R.string.from_amount else R.string.from_account) }, onSelect = { v -> vm.update { it.copy(inputOrder = v) } }, onDismiss = close)
        "memo" -> ChoiceDialog(stringResource(R.string.memo_button_setting), listOf(false, true), s.memoButton, { onOff(it) }, onSelect = { v -> vm.update { it.copy(memoButton = v) } }, onDismiss = close, description = stringResource(R.string.memo_button_text))
        "quick" -> ChoiceDialog(stringResource(R.string.quick_add), listOf(false, true), s.quickAddNotification, { onOff(it) }, onSelect = { v ->
            vm.update { it.copy(quickAddNotification = v) }
            com.nexo.app.work.Notifications.updateQuickAdd(context, v)
        }, onDismiss = close, description = stringResource(R.string.quick_add_text))
        "pad" -> ChoiceDialog(stringResource(R.string.number_pad_order), listOf(false, true), device?.numberPadDescending == true, { stringResource(if (it) R.string.pad_descending else R.string.pad_ascending) }, onSelect = { v -> vm.device { setNumberPadDescending(v) } }, onDismiss = close)
        "lang" -> LanguageDialog(onDismiss = close)
    }
    pendingSwipe?.let { m ->
        ConfirmDialog(stringResource(R.string.swipe_confirm, swipeLabelText(context, m)), onConfirm = { vm.update { it.copy(swipeMode = m) } }, onDismiss = { pendingSwipe = null })
    }
}

@Composable
fun swipeLabel(m: SwipeMode) = stringResource(
    when (m) {
        SwipeMode.CHANGE_TAB -> R.string.swipe_tab
        SwipeMode.CHANGE_DATE -> R.string.swipe_date
        SwipeMode.NONE -> R.string.none
    },
)

private fun swipeLabelText(context: android.content.Context, m: SwipeMode) = context.getString(
    when (m) {
        SwipeMode.CHANGE_TAB -> R.string.swipe_tab
        SwipeMode.CHANGE_DATE -> R.string.swipe_date
        SwipeMode.NONE -> R.string.none
    },
)

private val LANGUAGES = listOf("" to R.string.language_system, "en" to R.string.language_english)

@Composable
private fun currentLanguageLabel(): String {
    val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    return stringResource(LANGUAGES.firstOrNull { it.first == tag }?.second ?: R.string.language_system)
}

/** In-app language via per-app locales. Only bundled translations are listed. */
@Composable
private fun LanguageDialog(onDismiss: () -> Unit) {
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    ChoiceDialog(
        stringResource(R.string.language_setting), LANGUAGES.map { it.first }, current, { tag -> stringResource(LANGUAGES.first { it.first == tag }.second) },
        onSelect = { tag -> AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)) },
        onDismiss = onDismiss,
    )
}

@Composable
fun MonthStartScreen(vm: SettingsViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    var pad by rememberSaveable { mutableStateOf(false) }
    val s = ref.settings
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.monthly_start_date), onBack = { nav.back() }) }) { p ->
        Column(Modifier.padding(p)) {
            SettingRow(stringResource(R.string.monthly_start_date), stringResource(R.string.every_n, s.monthStartDay), onClick = { pad = true })
            SectionHeader(stringResource(R.string.weekend_rule_title))
            listOf(WeekendRule.NONE to R.string.weekend_none, WeekendRule.PREVIOUS_FRIDAY to R.string.weekend_friday, WeekendRule.FOLLOWING_MONDAY to R.string.weekend_monday).forEach { (rule, label) ->
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp).then(Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(s.weekendRule == rule, onClick = { vm.update { it.copy(weekendRule = rule) } })
                    Text(stringResource(label))
                }
            }
            Text(stringResource(R.string.month_start_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
    }
    if (pad) AmountSheet(stringResource(R.string.monthly_start_date), s.monthStartDay.toDouble(), 0, allowSign = false, calculator = false, maxLength = 2,
        onConfirm = { v -> val d = (v ?: 1.0).toInt().let { if (it in 1..31) it else 1 }; vm.update { it.copy(monthStartDay = d) } }, onDismiss = { pad = false })
}
