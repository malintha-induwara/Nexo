package com.nexo.app.sms.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexo.app.sms.Certainty
import com.nexo.app.sms.DenyReason
import com.nexo.app.sms.FeeMode
import com.nexo.app.sms.FeeRoute
import com.nexo.app.sms.RateSide
import com.nexo.app.sms.SmsKind
import com.nexo.app.sms.SmsReason
import com.nexo.app.sms.SmsStatus

/** A Review Queue item (spec 04 "Queue item"). Nothing reaches the ledger until it is approved (BR-Q1). */
@Entity(
    tableName = "sms_items",
    indices = [Index(value = ["dedupeKey"], unique = true), Index("status"), Index("smsAt"), Index("transactionId")],
)
data class SmsItemEntity(
    @PrimaryKey val id: String,
    val status: SmsStatus = SmsStatus.PENDING,
    val certainty: Certainty,
    val reason: SmsReason? = null,
    val kind: SmsKind,
    /** [com.nexo.app.sms.parse.Bank] name. */
    val bank: String,
    val sender: String,
    /** Template that recognised the message, e.g. "purchase", or "generic". */
    val template: String,
    /** In [currency]; for a foreign-currency purchase this is the foreign amount. */
    val amount: Double,
    val currency: String,
    val merchantRaw: String = "",
    val merchant: String = "",
    val merchantKey: String = "",
    /** CB-003: the name came from the OTP message that preceded the purchase. */
    val merchantFromOtp: Boolean = false,
    val cardLast4: String? = null,
    val payee: String? = null,
    val reference: String? = null,
    /** Channel or route text, e.g. "to Other Banks". */
    val route: String? = null,
    val feeRoute: FeeRoute? = null,
    /** When the movement happened: the body time if usable (CB-008), else the SMS time. */
    val occurredAt: Long,
    /** Sender-side SMS time; matching windows are measured on it. */
    val smsAt: Long,
    /** When the item entered the queue; the 30-day window runs from here (NQ-033). */
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
    /** NQ-012: the account-currency amount the user entered for a foreign-currency item. */
    val actualAmount: Double? = null,
    /** NQ-011: account-currency units per one unit of [currency]. */
    val fxRate: Double? = null,
    val fxSide: RateSide? = null,
    /** API bank code whose rate was used, or "median" / "official" / "manual". */
    val fxSource: String? = null,
    val fxSnapshotAt: Long? = null,
    val estimatedAmount: Double? = null,
    /** How many SMS created or updated this item (NQ-004 retries and confirmations). */
    val evidenceCount: Int = 1,
    val denyReason: DenyReason? = null,
    val decidedAt: Long? = null,
    /** Primary ledger row once approved. */
    val transactionId: String? = null,
    /** The purchase a reversal item undoes (CB-004). */
    val linkedItemId: String? = null,
    val updatedAt: Long,
) {
    /** Foreign-currency estimate still waiting for the real account amount (NQ-012). */
    val awaitingActual: Boolean get() = certainty == Certainty.ESTIMATED && actualAmount == null
}

/** CB-003: the merchant name from an OTP message, kept for 15 minutes. The code itself is never stored. */
@Entity(tableName = "sms_otp_hints", indices = [Index("at")])
data class SmsOtpHintEntity(
    @PrimaryKey val id: String,
    val bank: String,
    val merchant: String,
    val amount: Double,
    val currency: String,
    val at: Long,
)

/** Which account a bank card (or the bank as a whole, when [last4] is empty) belongs to (CB-001, NQ-032 card mapping). */
@Entity(tableName = "sms_sources", primaryKeys = ["bank", "last4"], indices = [Index("accountId")])
data class SmsSourceEntity(
    val bank: String,
    /** Card or account digits; "" is the bank's default account. */
    val last4: String,
    val accountId: String,
    val updatedAt: Long,
)

/** Learned merchant → category (LKSMS-018, CB-012). Keyed on the cleaned merchant key. */
@Entity(tableName = "sms_merchant_rules")
data class SmsMerchantRuleEntity(
    @PrimaryKey val merchantKey: String,
    val categoryId: String,
    val updatedAt: Long,
)

/** NQ fee preset, keyed by source account and route. */
@Entity(tableName = "fee_presets", primaryKeys = ["accountId", "route"])
data class FeePresetEntity(
    val accountId: String,
    val route: FeeRoute,
    val lastFee: Double? = null,
    val mode: FeeMode,
    val updatedAt: Long,
)

/** One bank's board rate for one currency at one fetch (NQ-010). Kept to build a local rate history. */
@Entity(tableName = "fx_snapshots", primaryKeys = ["capturedAt", "bank", "ccy"], indices = [Index("ccy", "capturedAt")])
data class FxSnapshotEntity(
    val capturedAt: Long,
    /** API bank code, e.g. "combank". */
    val bank: String,
    val ccy: String,
    /** LKR per one unit of [ccy]. */
    val buy: Double? = null,
    val sell: Double? = null,
    val mid: Double? = null,
    val official: Double? = null,
    val spreadPct: Double? = null,
)
