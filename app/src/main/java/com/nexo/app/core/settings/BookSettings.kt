package com.nexo.app.core.settings

import com.nexo.app.core.model.CardAccounting
import com.nexo.app.core.model.ColorSet
import com.nexo.app.core.model.InputOrder
import com.nexo.app.core.model.RepeatReflect
import com.nexo.app.core.model.StartScreen
import com.nexo.app.core.model.SwipeMode
import com.nexo.app.core.model.TimeDisplay
import com.nexo.app.core.model.WeekendRule
import com.nexo.app.core.period.PeriodEngine
import java.time.DayOfWeek

/** Per-book settings stored in the `settings` table (they travel with backups). */
data class BookSettings(
    val monthStartDay: Int = 1,
    val weekendRule: WeekendRule = WeekendRule.NONE,
    val weekStartDay: DayOfWeek = DayOfWeek.SUNDAY,
    val startScreen: StartScreen = StartScreen.DAILY,
    val swipeMode: SwipeMode = SwipeMode.CHANGE_DATE,
    val colorSet: ColorSet = ColorSet.A,
    val timeDisplay: TimeDisplay = TimeDisplay.NONE_DESC,
    val showDescription: Boolean = false,
    val autocomplete: Boolean = true,
    val autocompleteResetAt: Long = 0,
    val inputOrder: InputOrder = InputOrder.ACCOUNT_FIRST,
    val memoButton: Boolean = false,
    val quickAddNotification: Boolean = false,
    val carryOver: Boolean = false,
    val cardAccounting: CardAccounting = CardAccounting.AT_USE,
    val transferIncome: Boolean = true,
    val subcategories: Boolean = false,
    val repeatReflect: RepeatReflect = RepeatReflect.ON_DATE,
    /** Account that SMS cash withdrawals go to (CB-005); "" = the first account in the Cash group. */
    val smsCashAccountId: String = "",
    /** System "Bank Fee" category, created on first use (spec 02 improvement 1); "" = not created yet. */
    val bankFeeCategoryId: String = "",
) {
    val periods: PeriodEngine by lazy { PeriodEngine(monthStartDay, weekendRule, weekStartDay) }

    fun toMap(): Map<String, String> = mapOf(
        Keys.MONTH_START_DAY to monthStartDay.toString(),
        Keys.WEEKEND_RULE to weekendRule.name,
        Keys.WEEK_START_DAY to weekStartDay.name,
        Keys.START_SCREEN to startScreen.name,
        Keys.SWIPE_MODE to swipeMode.name,
        Keys.COLOR_SET to colorSet.name,
        Keys.TIME_DISPLAY to timeDisplay.name,
        Keys.SHOW_DESCRIPTION to showDescription.toString(),
        Keys.AUTOCOMPLETE to autocomplete.toString(),
        Keys.AUTOCOMPLETE_RESET_AT to autocompleteResetAt.toString(),
        Keys.INPUT_ORDER to inputOrder.name,
        Keys.MEMO_BUTTON to memoButton.toString(),
        Keys.QUICK_ADD to quickAddNotification.toString(),
        Keys.CARRY_OVER to carryOver.toString(),
        Keys.CARD_ACCOUNTING to cardAccounting.name,
        Keys.TRANSFER_INCOME to transferIncome.toString(),
        Keys.SUBCATEGORIES to subcategories.toString(),
        Keys.REPEAT_REFLECT to repeatReflect.name,
        Keys.SMS_CASH_ACCOUNT to smsCashAccountId,
        Keys.BANK_FEE_CATEGORY to bankFeeCategoryId,
    )

    object Keys {
        const val MONTH_START_DAY = "month_start_day"
        const val WEEKEND_RULE = "weekend_rule"
        const val WEEK_START_DAY = "week_start_day"
        const val START_SCREEN = "start_screen"
        const val SWIPE_MODE = "swipe_mode"
        const val COLOR_SET = "money_color_set"
        const val TIME_DISPLAY = "time_display"
        const val SHOW_DESCRIPTION = "show_description"
        const val AUTOCOMPLETE = "autocomplete"
        const val AUTOCOMPLETE_RESET_AT = "autocomplete_reset_at"
        const val INPUT_ORDER = "input_order"
        const val MEMO_BUTTON = "memo_button"
        const val QUICK_ADD = "quick_add_notification"
        const val CARRY_OVER = "carry_over"
        const val CARD_ACCOUNTING = "card_accounting"
        const val TRANSFER_INCOME = "transfer_income"
        const val SUBCATEGORIES = "subcategories"
        const val REPEAT_REFLECT = "repeat_reflect"
        const val SMS_CASH_ACCOUNT = "sms_cash_account"
        const val BANK_FEE_CATEGORY = "bank_fee_category"
    }

    companion object {
        /** Values written when a fresh book is seeded. */
        val SEED = BookSettings(timeDisplay = TimeDisplay.INPUT_DESC)

        fun fromMap(map: Map<String, String>): BookSettings {
            val d = BookSettings()
            fun bool(key: String, def: Boolean) = map[key]?.toBooleanStrictOrNull() ?: def
            return BookSettings(
                monthStartDay = map[Keys.MONTH_START_DAY]?.toIntOrNull()?.takeIf { it in 1..31 } ?: 1,
                weekendRule = enumOr(map[Keys.WEEKEND_RULE], d.weekendRule),
                weekStartDay = enumOr(map[Keys.WEEK_START_DAY], d.weekStartDay),
                startScreen = enumOr(map[Keys.START_SCREEN], d.startScreen),
                swipeMode = enumOr(map[Keys.SWIPE_MODE], d.swipeMode),
                colorSet = enumOr(map[Keys.COLOR_SET], d.colorSet),
                timeDisplay = enumOr(map[Keys.TIME_DISPLAY], d.timeDisplay),
                showDescription = bool(Keys.SHOW_DESCRIPTION, d.showDescription),
                autocomplete = bool(Keys.AUTOCOMPLETE, d.autocomplete),
                autocompleteResetAt = map[Keys.AUTOCOMPLETE_RESET_AT]?.toLongOrNull() ?: 0,
                inputOrder = enumOr(map[Keys.INPUT_ORDER], d.inputOrder),
                memoButton = bool(Keys.MEMO_BUTTON, d.memoButton),
                quickAddNotification = bool(Keys.QUICK_ADD, d.quickAddNotification),
                carryOver = bool(Keys.CARRY_OVER, d.carryOver),
                cardAccounting = enumOr(map[Keys.CARD_ACCOUNTING], d.cardAccounting),
                transferIncome = bool(Keys.TRANSFER_INCOME, d.transferIncome),
                subcategories = bool(Keys.SUBCATEGORIES, d.subcategories),
                repeatReflect = enumOr(map[Keys.REPEAT_REFLECT], d.repeatReflect),
                smsCashAccountId = map[Keys.SMS_CASH_ACCOUNT].orEmpty(),
                bankFeeCategoryId = map[Keys.BANK_FEE_CATEGORY].orEmpty(),
            )
        }

        private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
            value?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default
    }
}
