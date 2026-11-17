package com.nexo.app.data.sms.parser

import com.nexo.app.core.model.Bank
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.FeeRoute
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.SmsReason
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest

enum class IgnoreReason {
    // Not a bank sender or a user-added one.
    SENDER,

    DECLINED,

    // OTP, security or marketing message.
    RULE,

    // A known bank, but no rule recognised the message.
    NO_MATCH,

    ORPHAN_CANCEL,
}

sealed interface ParsedSms {
    val bank: Bank?

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
        val bodyDate: String? = null,
        val dedupeKey: String,
        // A bank charge stated in the body, kept apart from [amount].
        val statedFee: BigDecimal? = null,
    ) : ParsedSms

    // The cancellation message carries no amount.
    data class Cancel(override val bank: Bank, val merchant: Merchant, val cardLast4: String) :
        ParsedSms

    // An OTP names the merchant cleanly; kept briefly to improve the next purchase.
    data class OtpHint(
        override val bank: Bank,
        val merchant: String,
        val amount: BigDecimal,
        val currency: String,
    ) : ParsedSms

    data class Ignored(
        override val bank: Bank?,
        val reason: IgnoreReason,
        val detail: String = "",
    ) : ParsedSms
}

internal object Amounts {
    fun currency(raw: String): String = raw.trimEnd('.').uppercase().let {
        if (it == "RS") {
            "LKR"
        } else {
            it
        }
    }

    fun parse(raw: String): BigDecimal? =
        raw.replace(",", "").toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)?.takeIf {
            it.signum() >
                0
        }
}

// Fixed formats key on parsed fields, so identical alerts seconds apart stay one item.
object DedupeKeys {
    fun of(vararg parts: Any?): String {
        val joined = parts.joinToString("\u001F") { it?.toString().orEmpty() }
        val digest = MessageDigest.getInstance("SHA-256").digest(joined.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    // The money-back item for a purchase cancelled after approval.
    fun reversal(itemId: String): String = of("reversal", itemId)
}
