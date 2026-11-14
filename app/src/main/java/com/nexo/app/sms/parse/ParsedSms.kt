package com.nexo.app.sms.parse

import com.nexo.app.sms.Certainty
import com.nexo.app.sms.FeeRoute
import com.nexo.app.sms.SmsKind
import com.nexo.app.sms.SmsReason
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest

/** One incoming SMS. [timestamp] is the sender-side time (inbox `date_sent`), so realtime and inbox reads agree. */
data class SmsMessage(val sender: String, val body: String, val timestamp: Long)

/** Why a message produced no queue item (BR-2 blocked / rejected). */
enum class IgnoreReason {
    /** Not a known bank sender or a user-added one (LKSMS-003). */
    SENDER,

    /** Declined card attempt: nothing moved. */
    DECLINED,

    /** OTP, security or marketing message (CB-007). */
    RULE,

    /** A known bank, but no fixed format or general rule recognised it. */
    NO_MATCH,

    /** A cancellation with no earlier purchase to cancel (CB-004). */
    ORPHAN_CANCEL,
}

sealed interface ParsedSms {
    val bank: Bank?

    /** A money movement: becomes a queue item. */
    data class Movement(
        override val bank: Bank,
        val template: String,
        val kind: SmsKind,
        val amount: BigDecimal,
        val currency: String,
        val merchant: Merchant = Merchant.NONE,
        val cardLast4: String? = null,
        val payee: String? = null,
        val reference: String? = null,
        val route: String? = null,
        val feeRoute: FeeRoute? = null,
        val certainty: Certainty = Certainty.EXACT,
        val reason: SmsReason? = null,
        val occurredAt: Long,
        /** The date and time as written in the body, if any. */
        val bodyDate: String? = null,
        val dedupeKey: String,
        /** A bank charge stated in the body, kept apart from [amount]. */
        val statedFee: BigDecimal? = null,
    ) : ParsedSms

    /** CB-004: a purchase was cancelled. The message carries no amount. */
    data class Cancel(override val bank: Bank, val merchant: Merchant, val cardLast4: String) : ParsedSms

    /** CB-003: an OTP that names the merchant cleanly; kept briefly to improve the next purchase. */
    data class OtpHint(override val bank: Bank, val merchant: String, val amount: BigDecimal, val currency: String) : ParsedSms

    data class Ignored(override val bank: Bank?, val reason: IgnoreReason, val detail: String = "") : ParsedSms
}

internal object Amounts {
    /** "Rs"/"Rs." mean LKR (LKSMS-008). */
    fun currency(raw: String): String = raw.trimEnd('.').uppercase().let { if (it == "RS") "LKR" else it }

    fun parse(raw: String): BigDecimal? = raw.replace(",", "").toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)?.takeIf { it.signum() > 0 }
}

/** CB-010: fixed-format messages dedupe on their parsed fields, so two byte-identical alerts seconds apart stay one item. */
object DedupeKeys {
    fun of(vararg parts: Any?): String {
        val joined = parts.joinToString("\u001F") { it?.toString().orEmpty() }
        val digest = MessageDigest.getInstance("SHA-256").digest(joined.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** The money-back item for an approved purchase that was later cancelled (CB-004). */
    fun reversal(itemId: String): String = of("reversal", itemId)
}
