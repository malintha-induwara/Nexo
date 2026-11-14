package com.nexo.app.core.model

/** A Review Queue item. Nothing reaches the ledger until it is approved. */
data class SmsItem(
    val id: String,
    val status: SmsStatus = SmsStatus.PENDING,
    val certainty: Certainty,
    val reason: SmsReason? = null,
    val kind: SmsKind,
    /** [com.nexo.app.core.model.Bank] name. */
    val bank: String,
    val sender: String,
    /** Format that recognised the message, e.g. "purchase", or "generic" for the general rules. */
    val template: String,
    /** In [currency]; for a foreign-currency purchase this is the foreign amount. */
    val amount: Double,
    val currency: String,
    val merchantRaw: String = "",
    val merchant: String = "",
    val merchantKey: String = "",
    /** The name came from the OTP message that preceded the purchase. */
    val merchantFromOtp: Boolean = false,
    val cardLast4: String? = null,
    val payee: String? = null,
    val reference: String? = null,
    /** Channel or route text, e.g. "to Other Banks". */
    val route: String? = null,
    val feeRoute: FeeRoute? = null,
    /** When the movement happened: the body time if usable, else the SMS time. */
    val occurredAt: Long,
    /** Sender-side SMS time; matching windows are measured on it. */
    val smsAt: Long,
    /** When the item entered the queue; the 30-day window runs from here. */
    val capturedAt: Long,
    val dedupeKey: String,
    /** Stored body: codes always stripped, redacted unless "Store full SMS text" is on. */
    val text: String,
    /** Source account (suggested or chosen). */
    val accountId: String? = null,
    /** Destination account for transfers. */
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val note: String = "",
    /** The account-currency amount the user entered for a foreign-currency item. */
    val actualAmount: Double? = null,
    /** Account-currency units per one unit of [currency]. */
    val fxRate: Double? = null,
    val fxSide: RateSide? = null,
    /** API bank code whose rate was used, or "median" / "official" / "manual". */
    val fxSource: String? = null,
    val fxSnapshotAt: Long? = null,
    val estimatedAmount: Double? = null,
    /** How many SMS created or updated this item (retries and confirmations). */
    val evidenceCount: Int = 1,
    val denyReason: DenyReason? = null,
    val decidedAt: Long? = null,
    /** Primary ledger row once approved. */
    val transactionId: String? = null,
    /** The purchase a reversal item undoes. */
    val linkedItemId: String? = null,
    val updatedAt: Long,
    /** A bank charge the SMS itself states, e.g. "CEFT charges LKR 30.00". Added on approve. */
    val statedFee: Double? = null,
) {
    /** Foreign-currency estimate still waiting for the real account amount. */
    val awaitingActual: Boolean get() = certainty == Certainty.ESTIMATED && actualAmount == null
}
