package com.nexo.app.core.model

// A transfer is stored as one TRANSFER_OUT and one TRANSFER_IN row.
enum class TxKind {
    INCOME,
    EXPENSE,
    TRANSFER_OUT,
    TRANSFER_IN,
    BALANCE_INCREASE,
    BALANCE_DECREASE,
    ;

    val isInflow: Boolean get() = this == INCOME || this == TRANSFER_IN || this == BALANCE_INCREASE
    val isOutflow: Boolean get() = !isInflow
    val isTransfer: Boolean get() = this == TRANSFER_OUT || this == TRANSFER_IN
    val isBalance: Boolean get() = this == BALANCE_INCREASE || this == BALANCE_DECREASE

    // Part of deterministic repeat-instance ids and import codes; never renumber.
    val code: Int
        get() = when (this) {
            INCOME -> 0
            EXPENSE -> 1
            TRANSFER_OUT -> 3
            TRANSFER_IN -> 4
            BALANCE_INCREASE -> 7
            BALANCE_DECREASE -> 8
        }
}

enum class EntryType { INCOME, EXPENSE, TRANSFER }

// Balance changes open as income or expense.
val TxKind.entryType: EntryType
    get() = when (this) {
        TxKind.INCOME, TxKind.BALANCE_INCREASE -> EntryType.INCOME
        TxKind.EXPENSE, TxKind.BALANCE_DECREASE -> EntryType.EXPENSE
        TxKind.TRANSFER_OUT, TxKind.TRANSFER_IN -> EntryType.TRANSFER
    }

enum class GroupType { NORMAL, CREDIT_CARD, DEBIT_CARD }

enum class GroupStatus { ACTIVE, DELETED, PURGED }

enum class AccountStatus { ACTIVE, HIDDEN, DELETED, PURGED }

enum class CategoryKind { INCOME, EXPENSE }

enum class SymbolPosition { PREFIX, SUFFIX }

enum class MemoColor {

    WHITE,

    RED,

    ORANGE,

    YELLOW,

    GREEN,

    CYAN,

    BLUE,

    DARKBLUE,

    PURPLE,

    PINK,

    BROWN,

    GRAY,
}

// [code] matches the import format so imports map directly.
enum class RepeatType(val code: Int) {
    NONE(0),
    DAILY(5),
    WEEKDAYS(9),
    WEEKENDS(10),
    WEEKLY(6),
    BIWEEKLY(7),
    FOUR_WEEKLY(15),
    MONTHLY(1),
    END_OF_MONTH(8),
    BIMONTHLY(2),
    QUARTERLY(3),
    FOUR_MONTHLY(4),
    SEMIANNUAL(16),
    ANNUAL(17),
    ;

    val monthStep: Int?
        get() = when (this) {
            MONTHLY -> 1
            BIMONTHLY -> 2
            QUARTERLY -> 3
            FOUR_MONTHLY -> 4
            SEMIANNUAL -> 6
            ANNUAL -> 12
            else -> null
        }

    companion object {
        fun fromCode(code: Int): RepeatType? = entries.firstOrNull { it.code == code }

        val pickerOrder: List<RepeatType> = entries.toList()
    }
}

enum class WeekendRule { NONE, PREVIOUS_FRIDAY, FOLLOWING_MONDAY }

enum class StartScreen { DAILY, CALENDAR }

enum class SwipeMode { CHANGE_TAB, CHANGE_DATE, NONE }

enum class ColorSet { A, B }

enum class TimeDisplay(
    val code: Int,
    val timeInput: Boolean,
    val showOnList: Boolean,
    val descending: Boolean,
    val byTime: Boolean,
) {
    NONE_DESC(5, false, false, true, false),
    NONE_ASC(0, false, false, false, false),
    SHOW_DESC(1, true, true, true, true),
    SHOW_ASC(3, true, true, false, true),
    INPUT_DESC(2, true, false, true, true),
    INPUT_ASC(4, true, false, false, true),
    ;

    companion object {
        fun fromCode(code: Int?): TimeDisplay = entries.firstOrNull { it.code == code } ?: NONE_DESC
    }
}

enum class InputOrder { ACCOUNT_FIRST, AMOUNT_FIRST }

enum class CardAccounting { AT_USE, LUMP_SUM }

enum class RepeatReflect { ON_DATE, FIRST_OF_PERIOD }

enum class CashFlow { INCOME, EXPENSE, NEUTRAL }
