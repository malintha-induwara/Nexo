package com.nexo.app.core.model

// Nothing reaches the ledger until the item is approved.
data class SmsItem(
    val id: String,
    val status: SmsStatus = SmsStatus.PENDING,
    val certainty: Certainty,
    val reason: SmsReason? = null,
    val kind: SmsKind,
    // A Bank enum name.
    val bank: String,
    val sender: String,
    // e.g. "purchase", or "generic" for the general rules.
    val template: String,
    // In [currency]: for a foreign-currency purchase, the foreign amount.
    val amount: Double,
    val currency: String,
    val merchantRaw: String = "",
    val merchant: String = "",
    val merchantKey: String = "",
    val merchantFromOtp: Boolean = false,
    val cardLast4: String? = null,
    val payee: String? = null,
    val reference: String? = null,
    // e.g. "to Other Banks".
    val route: String? = null,
    val feeRoute: FeeRoute? = null,
    // The body time if usable, else the SMS time.
    val occurredAt: Long,
    // Sender-side time; matching windows are measured on it.
    val smsAt: Long,
    // The 30-day expiry window runs from here.
    val capturedAt: Long,
    val dedupeKey: String,
    // Codes always stripped; redacted unless "Store full SMS text" is on.
    val text: String,
    val accountId: String? = null,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val note: String = "",
    // What the user entered, in the account's currency, for a foreign-currency item.
    val actualAmount: Double? = null,
    // Account-currency units per one unit of [currency].
    val fxRate: Double? = null,
    val fxSide: RateSide? = null,
    // Rates API bank code, or "median" / "official" / "manual".
    val fxSource: String? = null,
    val fxSnapshotAt: Long? = null,
    val estimatedAmount: Double? = null,
    // Messages that created or updated this item (retries, confirmations).
    val evidenceCount: Int = 1,
    val denyReason: DenyReason? = null,
    val decidedAt: Long? = null,
    // Primary ledger row once approved.
    val transactionId: String? = null,
    // The purchase a reversal undoes.
    val linkedItemId: String? = null,
    val updatedAt: Long,
    // A charge the SMS itself states, e.g. "CEFT charges LKR 30.00"; added on approve.
    val statedFee: Double? = null,
) {
    val awaitingActual: Boolean get() = certainty == Certainty.ESTIMATED && actualAmount == null
}
