package com.nexo.app.core.model

/** What a captured movement means for the ledger. */
enum class SmsKind {
    EXPENSE,
    INCOME,

    /** Cash withdrawal: a transfer from the card's account to the cash account. */
    TRANSFER_TO_CASH,

    /** Money sent to a payee or another account. */
    TRANSFER_OUT,
    TRANSFER_IN,

    /** Payment into a credit card (generic pipeline only). */
    CARD_PAYMENT,

    /** Loan instalment (generic pipeline only); saved as an expense. */
    LOAN_PAYMENT,
    ;

    val isInflow: Boolean get() = this == INCOME || this == TRANSFER_IN
    val isTransfer: Boolean get() = this == TRANSFER_TO_CASH || this == TRANSFER_OUT || this == TRANSFER_IN || this == CARD_PAYMENT
}

/** Whether it happened and how much. */
enum class Certainty { EXACT, ESTIMATED, UNVERIFIED }

/** Why an item isn't [Certainty.EXACT]. */
enum class SmsReason { FX_ESTIMATE, APPROVAL_CODE, GENERIC_RULES, AMOUNT_FALLBACK, KIND_CONFLICT }

/** How Nexo handles a fee on a route. */
enum class FeeMode { ASK, AUTO, NEVER }

/** Movements that usually carry a bank charge the SMS never shows. */
enum class FeeRoute(val defaultMode: FeeMode) {
    INTERBANK_TRANSFER(FeeMode.ASK),
    OTHER_BANK_ATM(FeeMode.ASK),
    FX_CARD_PURCHASE(FeeMode.ASK),
    OWN_BANK_TRANSFER(FeeMode.NEVER),
    CC_PAYMENT(FeeMode.ASK),
}

/** Queue item life cycle. */
enum class SmsStatus { PENDING, APPROVED, DENIED, EXPIRED, CANCELLED }

/** Optional reason chips on Deny. */
enum class DenyReason { DIDNT_HAPPEN, DUPLICATE, NOT_MINE, NOT_TRANSACTION }

/** Which side of a bank's board rate was used. */
enum class RateSide { SELL, BUY }
