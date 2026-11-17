package com.nexo.app.core.model

enum class SmsKind {
    EXPENSE,
    INCOME,

    // Cash withdrawal.
    TRANSFER_TO_CASH,

    // To a payee or another account.
    TRANSFER_OUT,
    TRANSFER_IN,

    // Credit card payment; general rules only.
    CARD_PAYMENT,

    // Loan instalment; general rules only. Saved as an expense.
    LOAN_PAYMENT,
    ;

    val isInflow: Boolean get() = this == INCOME || this == TRANSFER_IN
    val isTransfer: Boolean get() = this == TRANSFER_TO_CASH || this == TRANSFER_OUT ||
        this == TRANSFER_IN ||
        this == CARD_PAYMENT
}

// How sure Nexo is that it happened, and of the amount.
enum class Certainty { EXACT, ESTIMATED, UNVERIFIED }

// Why an item isn't EXACT.
enum class SmsReason { FX_ESTIMATE, APPROVAL_CODE, GENERIC_RULES, AMOUNT_FALLBACK, KIND_CONFLICT }

enum class FeeMode { ASK, AUTO, NEVER }

// Movements that usually carry a bank charge the SMS never shows.
enum class FeeRoute(val defaultMode: FeeMode) {
    INTERBANK_TRANSFER(FeeMode.ASK),
    OTHER_BANK_ATM(FeeMode.ASK),
    FX_CARD_PURCHASE(FeeMode.ASK),
    OWN_BANK_TRANSFER(FeeMode.NEVER),
    CC_PAYMENT(FeeMode.ASK),
}

enum class SmsStatus { PENDING, APPROVED, DENIED, EXPIRED, CANCELLED }

enum class DenyReason { DIDNT_HAPPEN, DUPLICATE, NOT_MINE, NOT_TRANSACTION }

enum class RateSide { SELL, BUY }

// A null or zero [amount] means "No fee".
data class FeeChoice(
    val amount: Double?,
    val remember: Boolean = false,
    val includeInPurchase: Boolean = false,
)
