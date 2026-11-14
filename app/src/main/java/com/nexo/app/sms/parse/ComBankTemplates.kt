package com.nexo.app.sms.parse

import com.nexo.app.sms.Certainty
import com.nexo.app.sms.FeeRoute
import com.nexo.app.sms.SmsKind
import com.nexo.app.sms.SmsReason

/** Bank-specific formats, tried before the ignore rules and the generic pipeline (spec 03 processing order). */
internal interface BankTemplates {
    fun match(bank: Bank, body: String, msg: SmsMessage): ParsedSms?
}

/** Commercial Bank of Ceylon (spec 03 "Nexo ComBank templates"). */
internal object ComBankTemplates : BankTemplates {
    /**
     * Shared amount fragment. The comma-grouped form needs at least one group and comes first, and the trailing
     * `(?![\d,])` stops "LKR 1500.00" being read as 150.
     */
    private const val AMT = """(?<cur>LKR|USD|EUR|GBP|SGD|AUD|INR|Rs\.?)\s?(?<amt>(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?)(?![\d,])"""
    private const val CARD_DATE = """(?<date>\d{2}/\d{2}/\d{2}) (?<time>\d{1,2}:\d{2} [AP]M)"""

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    private val purchase = rx(
        """^Dear Cardholder, Purchase at (?<merchant>.+?) for $AMT on $CARD_DATE has been authori[sz]ed on your (?<cardType>debit|credit) card ending #(?<last4>\d{4})\.?$""",
    )
    private val withdrawal = rx("""^Withdrawal at (?<merchant>.+?) for $AMT on $CARD_DATE from card ending #(?<last4>\d{4})\.""")
    private val fundTransfer = rx(
        """^Fund transfer request (?<ref>[A-Z]{2}\d+) (?<status>[A-Z]) (?<date>\d{4}-\d{2}-\d{2}) at (?<time>\d{2}:\d{2}:\d{2}) via (?<channel>.+?) to (?<payee>.+?) for $AMT""",
    )
    private val cancelled = rx("""^YOUR TRANSACTION AT (?<merchant>.+?) ON YOUR CARD ENDING WITH (?<last4>\d{4}) HAS BEEN CANCELL?ED\.?$""")
    private val otpMerchant = rx("""Your OTP at Merchant '(?<merchant>[^']+)' for $AMT is (?<otp>\d{4,8})""")
    private val declined = rx("""your card was declined due to (?<reason>.+?)\. The attempted transaction amount was $AMT at (?<merchant>.+?) on (?<date>\d{2}/\d{2}/\d{2})""")
    private val transferCode = rx("""^ComBank Digital-Transfer (?<route>within ComBank|to Other Banks) $AMT attempted\. Please use code (?<otp>\d{4,8})""")

    /** ComBank's own-ATM receipt footer (NQ-020 `other_bank_atm` detection). */
    private val ownAtmFooter = rx("""performed at ComBank ATMs""")

    private const val HOME_CURRENCY = "LKR"

    override fun match(bank: Bank, body: String, msg: SmsMessage): ParsedSms? {
        purchase.find(body)?.let { m ->
            val amount = Amounts.parse(m.g("amt")) ?: return null
            val currency = Amounts.currency(m.g("cur"))
            val merchant = MerchantCleaner.purchase(m.g("merchant"))
            val last4 = m.g("last4")
            val bodyDate = "${m.g("date")} ${m.g("time")}"
            val foreign = currency != HOME_CURRENCY
            return ParsedSms.Movement(
                bank = bank, template = "purchase", kind = SmsKind.EXPENSE, amount = amount, currency = currency,
                merchant = merchant, cardLast4 = last4,
                certainty = if (foreign) Certainty.ESTIMATED else Certainty.EXACT,
                reason = if (foreign) SmsReason.FX_ESTIMATE else null,
                feeRoute = if (foreign) FeeRoute.FX_CARD_PURCHASE else null,
                occurredAt = BodyDate.choose(BodyDate.card(m.g("date"), m.g("time")), msg.timestamp),
                bodyDate = bodyDate,
                dedupeKey = DedupeKeys.of(bank, "purchase", last4, amount, currency, merchant.raw, bodyDate),
            )
        }
        withdrawal.find(body)?.let { m ->
            val amount = Amounts.parse(m.g("amt")) ?: return null
            val currency = Amounts.currency(m.g("cur"))
            val merchant = MerchantCleaner.atm(m.g("merchant"))
            val last4 = m.g("last4")
            val bodyDate = "${m.g("date")} ${m.g("time")}"
            val atmBank = Banks.findIn(merchant.raw)
            val otherBank = !ownAtmFooter.containsMatchIn(body) && atmBank != null && atmBank != bank
            return ParsedSms.Movement(
                bank = bank, template = "withdrawal", kind = SmsKind.TRANSFER_TO_CASH, amount = amount, currency = currency,
                merchant = merchant, cardLast4 = last4,
                feeRoute = if (otherBank) FeeRoute.OTHER_BANK_ATM else null,
                occurredAt = BodyDate.choose(BodyDate.card(m.g("date"), m.g("time")), msg.timestamp),
                bodyDate = bodyDate,
                dedupeKey = DedupeKeys.of(bank, "withdrawal", last4, amount, currency, merchant.raw, bodyDate),
            )
        }
        fundTransfer.find(body)?.let { m ->
            val amount = Amounts.parse(m.g("amt")) ?: return null
            val currency = Amounts.currency(m.g("cur"))
            val bodyDate = "${m.g("date")} ${m.g("time")}"
            val payee = m.g("payee").trim()
            return ParsedSms.Movement(
                bank = bank, template = "fund_transfer", kind = SmsKind.TRANSFER_OUT, amount = amount, currency = currency,
                payee = payee, reference = m.g("ref"), route = m.g("channel").trim(),
                occurredAt = BodyDate.choose(BodyDate.iso(m.g("date"), m.g("time")), msg.timestamp),
                bodyDate = bodyDate,
                dedupeKey = DedupeKeys.of(bank, "fund_transfer", m.g("ref"), amount, currency, payee, bodyDate),
            )
        }
        cancelled.find(body)?.let { m ->
            return ParsedSms.Cancel(bank, MerchantCleaner.purchase(m.g("merchant")), m.g("last4"))
        }
        otpMerchant.find(body)?.let { m ->
            val amount = Amounts.parse(m.g("amt")) ?: return null
            return ParsedSms.OtpHint(bank, m.g("merchant").trim(), amount, Amounts.currency(m.g("cur")))
        }
        if (declined.containsMatchIn(body)) return ParsedSms.Ignored(bank, IgnoreReason.DECLINED)
        transferCode.find(body)?.let { m ->
            val amount = Amounts.parse(m.g("amt")) ?: return null
            val currency = Amounts.currency(m.g("cur"))
            val route = m.g("route")
            val otherBanks = route.startsWith("to", ignoreCase = true)
            return ParsedSms.Movement(
                bank = bank, template = "transfer_code", kind = SmsKind.TRANSFER_OUT, amount = amount, currency = currency,
                route = route,
                feeRoute = if (otherBanks) FeeRoute.INTERBANK_TRANSFER else FeeRoute.OWN_BANK_TRANSFER,
                certainty = Certainty.UNVERIFIED, reason = SmsReason.APPROVAL_CODE,
                occurredAt = msg.timestamp,
                // No body time: the SMS time keeps two real transfers of the same amount apart. Retries merge in the correlator.
                dedupeKey = DedupeKeys.of(bank, "transfer_code", route, amount, currency, msg.timestamp),
            )
        }
        return null
    }

    private fun MatchResult.g(name: String): String = groups[name]?.value.orEmpty()
}
