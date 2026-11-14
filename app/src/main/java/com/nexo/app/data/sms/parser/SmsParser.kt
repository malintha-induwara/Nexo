package com.nexo.app.data.sms.parser

import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.FeeRoute
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.SmsReason
import java.math.BigDecimal

/**
 * Turns one SMS into a [ParsedSms]. Stateless: merging, enrichment and cancellation happen in the correlator.
 *
 * One rule set for every bank. Nothing below depends on which bank sent the message, only on how the message reads;
 * the bank is a label on the result. Order:
 *
 * 1. **Sender.** A known bank or a user-added sender; anything else is dropped.
 * 2. **Fixed formats.** Whole-sentence shapes where every field sits in a known place: card purchase, ATM withdrawal,
 *    fund-transfer request, cancellation, merchant OTP, declined attempt, approval-code transfer. They run before the
 *    ignore rules because an OTP or approval-code message can still name a movement.
 * 3. **Ignore rules.**
 * 4. **General rules.** Integer and European amounts, anchor-based amount choice that never picks a balance, validated
 *    merchant captures, debit/credit by first keyword, `X` masks, and fees read out of the body. Everything they
 *    produce is [Certainty.UNVERIFIED].
 */
class SmsParser(customSenders: Collection<String> = emptyList()) {
    private val custom = customSenders.map(Banks::normalizeSender).filter { it.isNotEmpty() }.toSet()

    fun parse(msg: SmsMessage): ParsedSms {
        val body = msg.body.replace(lineBreaks, "\n").trim()
        val sender = Banks.normalizeSender(msg.sender)
        val bank = Banks.fromSender(sender)
            ?: if (sender in custom) Banks.findIn(body) ?: Bank.OTHER else return ParsedSms.Ignored(null, IgnoreReason.SENDER)
        fixedFormat(bank, body, msg)?.let { return it }
        IgnoreRules.match(body)?.let { return ParsedSms.Ignored(bank, IgnoreReason.RULE, it) }
        return generalRules(bank, body, msg)
    }

    // ---- Fixed formats ----

    private fun fixedFormat(bank: Bank, body: String, msg: SmsMessage): ParsedSms? {
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
            return ParsedSms.Movement(
                bank = bank, template = "withdrawal", kind = SmsKind.TRANSFER_TO_CASH, amount = amount, currency = currency,
                merchant = merchant, cardLast4 = last4,
                feeRoute = if (otherBankAtm(bank, merchant, body)) FeeRoute.OTHER_BANK_ATM else null,
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

    /**
     * The ATM's name names a different bank than the sender. A receipt footer that says the withdrawal was
     * "performed at <sender's bank> ATMs" settles it the other way.
     */
    private fun otherBankAtm(bank: Bank, atm: Merchant, body: String): Boolean {
        if (ownAtmFooter.find(body)?.let { Banks.findIn(it.g("atmBank")) } == bank) return false
        val atmBank = Banks.findIn(atm.raw)
        return atmBank != null && atmBank != bank
    }

    // ---- General rules ----

    private fun generalRules(bank: Bank, body: String, msg: SmsMessage): ParsedSms {
        if (!payment.containsMatchIn(body)) return ParsedSms.Ignored(bank, IgnoreReason.NO_MATCH, "no payment keyword")
        if (notice.containsMatchIn(body) && !movementWord.containsMatchIn(body)) return ParsedSms.Ignored(bank, IgnoreReason.RULE, "notice")

        var text = body.replace(europeanDecimal) { it.value.replace(".", "").replace(',', '.') }
        val feeMatch = fee.find(text)
        val statedFee = feeMatch?.let { Amounts.parse(it.groups["amt"]!!.value) }
        if (feeMatch != null) text = text.replaceRange(feeMatch.range, " ")

        val picked = pickAmount(text) ?: return ParsedSms.Ignored(bank, IgnoreReason.NO_MATCH, "no amount")
        val (kind, conflict) = classify(text)
        val merchant = merchantOf(text)
        val last4 = cardDigits.find(text)?.groupValues?.get(1) ?: masked.find(text)?.groupValues?.get(1)?.takeLast(4)
        val dest = destination.find(text)
        val payee = dest?.groups?.get(2)?.value?.trim() ?: dest?.groups?.get(1)?.value?.let { "A/C ${it.takeLast(4)}" }
        val reason = when {
            conflict -> SmsReason.KIND_CONFLICT
            !picked.anchored -> SmsReason.AMOUNT_FALLBACK
            else -> SmsReason.GENERIC_RULES
        }
        return ParsedSms.Movement(
            bank = bank, template = GENERIC, kind = kind, amount = picked.amount, currency = picked.currency,
            merchant = merchant,
            cardLast4 = last4,
            payee = if (kind == SmsKind.TRANSFER_OUT || kind == SmsKind.TRANSFER_IN) payee else null,
            feeRoute = if (kind == SmsKind.CARD_PAYMENT) FeeRoute.CC_PAYMENT else null,
            certainty = Certainty.UNVERIFIED, reason = reason,
            occurredAt = msg.timestamp,
            // No fixed fields: the body itself, with the SMS time so a repeated alert on another day stays apart.
            dedupeKey = DedupeKeys.of(bank, GENERIC, Banks.normalizeSender(msg.sender), body, msg.timestamp),
            statedFee = statedFee,
        )
    }

    private data class Picked(val amount: BigDecimal, val currency: String, val anchored: Boolean)

    /** The amount nearest after an anchor word wins; anything right after a balance word is skipped. */
    private fun pickAmount(text: String): Picked? {
        data class Hit(val start: Int, val amount: BigDecimal, val currency: String)
        val hits = buildList {
            amountBefore.findAll(text).forEach { m -> Amounts.parse(m.groups["amt"]!!.value)?.let { add(Hit(m.range.first, it, Amounts.currency(m.groups["cur"]!!.value))) } }
            amountAfter.findAll(text).forEach { m -> Amounts.parse(m.groups["amt"]!!.value)?.let { add(Hit(m.range.first, it, Amounts.currency(m.groups["cur"]!!.value))) } }
            amtOnly.findAll(text).forEach { m -> Amounts.parse(m.groups["amt"]!!.value)?.let { add(Hit(m.range.first, it, HOME_CURRENCY)) } }
        }.distinctBy { it.start }.sortedBy { it.start }
            .filterNot { h -> balanceBefore.containsMatchIn(text.substring(maxOf(0, h.start - 24), h.start)) }
        if (hits.isEmpty()) return null
        val anchors = anchor.findAll(text).map { it.range.last }.toList()
        val best = hits.mapNotNull { h -> anchors.filter { it < h.start && h.start - it <= 40 }.maxOrNull()?.let { h to h.start - it } }.minByOrNull { it.second }?.first
        val chosen = best ?: hits.first()
        return Picked(chosen.amount, chosen.currency, anchored = best != null)
    }

    /** Plain debit vs credit is decided by whichever keyword comes first. */
    private fun classify(text: String): Pair<SmsKind, Boolean> {
        if (creditCardPayment.containsMatchIn(text)) return SmsKind.CARD_PAYMENT to false
        if (loanPayment.containsMatchIn(text)) return SmsKind.LOAN_PAYMENT to false
        if (reversal.containsMatchIn(text)) return SmsKind.INCOME to false
        if (withdrawalWord.containsMatchIn(text)) return SmsKind.TRANSFER_TO_CASH to false
        if (transfer.containsMatchIn(text)) return (if (transferIn.containsMatchIn(text)) SmsKind.TRANSFER_IN else SmsKind.TRANSFER_OUT) to false
        val c = credit.find(text)?.range?.first
        val d = debit.find(text)?.range?.first
        return when {
            c != null && d != null -> (if (c < d) SmsKind.INCOME else SmsKind.EXPENSE) to true
            c != null -> SmsKind.INCOME to false
            else -> SmsKind.EXPENSE to false
        }
    }

    /** Leftmost valid capture across the alternatives; captures with account words, masked digits or balances fall through. */
    private fun merchantOf(text: String): Merchant {
        val candidates = merchants.flatMapIndexed { i, r ->
            r.findAll(text).mapNotNull { m ->
                val g = m.groups.drop(1).firstOrNull { it != null }?.value?.trim().orEmpty()
                if (g.isEmpty() || g.length > 60 || badMerchant.containsMatchIn(g)) null else Triple(m.range.first, i, g)
            }.toList()
        }
        val raw = candidates.minWithOrNull(compareBy({ it.first }, { it.second }))?.third ?: return Merchant.NONE
        return MerchantCleaner.purchase(raw)
    }

    private fun MatchResult.g(name: String): String = groups[name]?.value.orEmpty()

    companion object {
        /** [ParsedSms.Movement.template] for messages read by the general rules. */
        const val GENERIC = "generic"
    }
}

private const val HOME_CURRENCY = "LKR"
private val lineBreaks = Regex("\r\n?")
private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

// ---- Fixed-format patterns ----

/**
 * Shared amount fragment. The comma-grouped form needs at least one group and comes first, and the trailing `(?![\d,])`
 * stops "LKR 1500.00" being read as 150.
 */
private const val AMT = """(?<cur>LKR|USD|EUR|GBP|SGD|AUD|INR|Rs\.?)\s?(?<amt>(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?)(?![\d,])"""
private const val CARD_DATE = """(?<date>\d{2}/\d{2}/\d{2}) (?<time>\d{1,2}:\d{2} [AP]M)"""

private val purchase = rx(
    """^(?:Dear \w+,\s*)?Purchase at (?<merchant>.+?) for $AMT on $CARD_DATE has been authori[sz]ed on your (?<cardType>debit|credit) card ending #(?<last4>\d{4})\.?$""",
)
private val withdrawal = rx("""^Withdrawal at (?<merchant>.+?) for $AMT on $CARD_DATE from card ending #(?<last4>\d{4})\.""")
private val fundTransfer = rx(
    """^Fund transfer request (?<ref>[A-Z]{2}\d+) (?<status>[A-Z]) (?<date>\d{4}-\d{2}-\d{2}) at (?<time>\d{2}:\d{2}:\d{2}) via (?<channel>.+?) to (?<payee>.+?) for $AMT""",
)
private val cancelled = rx("""^YOUR TRANSACTION AT (?<merchant>.+?) ON YOUR CARD ENDING WITH (?<last4>\d{4}) HAS BEEN CANCELL?ED\.?$""")
private val otpMerchant = rx("""Your OTP at Merchant '(?<merchant>[^']+)' for $AMT is \d{4,8}""")
private val declined = rx("""your card was declined due to .+?\. The attempted transaction amount was $AMT at .+? on \d{2}/\d{2}/\d{2}""")

/** An app transfer waiting for its approval code: "… Transfer within <bank> | to Other Banks LKR 1,000.00 attempted. Please use code …". */
private val transferCode = rx("""\bTransfer (?<route>within .+?|to Other Banks) $AMT attempted\. Please use code \d{4,8}""")
private val ownAtmFooter = rx("""performed at (?<atmBank>.+?) ATMs\b""")

// ---- General-rule patterns ----

/** A message must name a payment; "transaction of" counts because real alerts use it. */
private val payment = rx(
    """(transaction\s+of|purchase|debited|debit ed|(?:\bdebit(?!\s+(?:cards?|accounts?)\b))|withdrawal|transaction for|transaction at|transaction approved|transaction\s+made|txn\s+for|pos(?:[/\-]atm)?\s+transaction|\bpos\b|amount\(approx\)|amt\s|vc\s*:|location:|credited|credit\s+rs|online transfer credit|online\s+transfer|transfer\s+of|mobile payment successful|mobile payment|bill payment successful|bill payment|reversal|auth pmt|pmt\b|fund\s+transfer|transferred|wire\s+transfer|cash\s+deposit|atm\s+deposit|deposited|\bdeposit\b|credit\s+card\s+payment|distributed|profit\s+amount|made\s+to\s+card|\btrxn\b|lankaQR|was\s+performed)""",
)

/** Rate and tariff notices: ignored unless a clear money-movement word is also there. */
private val notice = rx("""(billing|wish\s+to\s+inform|will\s+be\s+revised|\btariff\b|for\s+(?:more\s+)?details|w\.e\.f\.|effective\s+from)""")
private val movementWord = rx("""\b(debited|credited|withdrawn|purchase|deposited|transferred)\b""")

private val creditCardPayment = rx("""(?:own\s+credit\s+card\s+payment|credit\s+card\s+payment\b.*\b(?:received|successful)|made\s+to\s+card\s+(?:no\.?|number)\s*[\dX\*\-]{4,})""")
private val loanPayment = rx("""(?:loan\s+recovery|loan\s+repayment|loan\s+installment|loan\s+instalment|\bemi\s+(?:deducted|debited|paid)\b)""")
private val reversal = rx("""\b(reversal|reversed|refund|chargeback|cancelled\s+transaction)\b""")
private val withdrawalWord = rx("""(?:\bcash\s+withdrawal\b|\batm\s+withdrawal\b|\bwithdrawal\s+at\b|\bwithdrew\b|\bwithdrawn\b|\(atm\b|\batm\s+@|\bvia\s+atm\b|\bthrough\s+atm\b|\batm\s+at\b)""")
private val transfer = rx("""(?:fund\s+transfer|wire\s+transfer|online\s+transfer\s+(?:to|debit|of)|transferred\s+(?:to|from)|transfer(?:red)?\s+(?:lkr|usd|sgd|rs))""")
private val transferIn = rx("""(?:transferred\s+from|transfer\s+credit|online\s+transfer\s+credit|credit\s+transfer)""")
private val credit = rx("""(credited|credit\s+rs|online transfer credit|transfer\s+credit|credit\s+transfer|reversal|cash\s+deposit|atm\s+deposit|deposited|\bdeposit\b|distributed|profit\s+amount)""")
private val debit = rx("""(debited|debit ed|\bdebit\b(?!\s+(?:cards?|accounts?)\b)|purchase|withdrawal|withdrawn|\bpaid\b|\bspent\b|charged|payment)""")

private const val CUR = """(?:LKR|USD|SGD|EUR|GBP|AUD|INR|Rs\.?)"""
private const val NUM = """(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?"""
private val amountBefore = rx("""(?<cur>$CUR)\s?(?<amt>$NUM)(?![\d,])""")
private val amountAfter = rx("""(?<![\d.,])(?<amt>$NUM)\s?(?<cur>LKR|USD|SGD|EUR|GBP|AUD|INR)\b""")
private val amtOnly = rx("""\bAMT\s?:?\s?(?<amt>$NUM)(?![\d,])""")
private val europeanDecimal = Regex("""\b\d{1,3}(?:\.\d{3})+,\d{2}\b""")
private val anchor = rx("""\b(debited|credited|charged|amount|amt|withdrawn|paid|purchase|deposited|transferred|of|for)\b""")
private val balanceBefore = rx("""(bal|balance|avl|available|av\.\s*bal|limit)\W{0,6}(?:is\s+)?$""")

/** A stated charge is read first and kept out of the amount search. */
private val fee = rx("""(?:charges?|fee|commission|ceft\s+charge|service\s+charge)\s*(?:of|:)?\s*(?<cur>LKR|Rs\.?)\s?(?<amt>$NUM)(?![\d,])""")

/** Merchant alternatives, tried separately so a bad capture can fall through. */
private val merchants = listOf(
    """:\s*\*\*\d+\s*:(.*?)\s*\((?:Apprx|Approx)\)""",
    """Location:\s*([^,]+)""",
    """at\s+(.+?)\s+(?:Avail(?:able)?|Avl)\.?\s*Bal\b""",
    """(?:AT\s+|Purchase at\s+|transaction at\s+)([A-Z0-9\s\.&,()+'*/-]+?)\s+(?:ON\b|for\b|authoris[ei]d\b|authorized\b)""",
    """POS Transaction\s*-\s*(.+?)\s+was performed""",
    """Debited\s+(?:LKR|USD|SGD|Rs\.?)\s+[\d,]+\.\d+\s+(.+?)\s+\d{6,}""",
    """~\s*([^~]+?)\s*~""",
    """to\s+(?!a/?c\b|account\b|your\b)(.+?)\s+(?:Ref No\b|on\b)""",
    """@\s*(.+?)\s*\.?\s*\[?Av\.?Bal\b""",
    """CR\s+CARD:\s*([A-Z0-9\s\.&,()/'+-]+?)\s+CARD\*+\d+\s+DEBITED""",
    """via\s+pos\s+at\s+([A-Z][A-Z0-9\s\.&,()/'+-]+?)\s+\d{5,}""",
    """\bref:\s*([^.]+)""",
    """via\s+"([^"]+)"""",
    """\bat\s+([A-Z0-9\s\.&,()/'+*-]+?)(?:\)|\.(?:\s|$)|\[|\s+on\b|$)""",
).map(::rx)
private val badMerchant = rx("""(a/?c\b|card\s+ending|\*+\d|X{2,}\d|\b(?:bal|balance|avl|available)\b|^\W*\d[\d\s,.]*$)""")

/** Card digits, and masked digits with `*` or `X`. */
private val cardDigits = rx("""(?:card|a/?c|account)\s+(?:no\.?\s*)?(?:ending(?:\s+with)?|ending\s+in)?\s*[#:]?\s*[X\*]*(\d{4})\b""")
private val masked = rx("""(?:\*+|X{2,})(\d{4,6})\b""")
private val destination = rx("""(?:to\s+a/?c\b(?:\s+(?:no\.?|number))?\s*([A-Z0-9\*\-]{2,}))|(?:beneficiary[: ]+([A-Z0-9\s\.&]+?)(?:\s+(?:on|ref)|$))""")
