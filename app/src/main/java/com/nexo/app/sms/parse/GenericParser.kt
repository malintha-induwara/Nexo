package com.nexo.app.sms.parse

import com.nexo.app.sms.Certainty
import com.nexo.app.sms.FeeRoute
import com.nexo.app.sms.SmsKind
import com.nexo.app.sms.SmsReason
import java.math.BigDecimal

/**
 * The last-resort pipeline (spec 01 LKSMS-006 – 011) for banks without templates, with the fixes spec 01 lists under
 * "Nexo implementation notes": integer and European amounts, anchor-based amount choice that never picks a balance,
 * validated merchant captures, debit/credit by first keyword, `X` masks, and fees read out of the body.
 *
 * Everything it produces is [Certainty.UNVERIFIED]: these rules were only checked on synthetic messages.
 */
object GenericParser : SmsParser.GenericRules {
    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    /** Stage 4 keyword gate (catalogue `payment`, plus "transaction of", which real alerts use: BR-Q2). */
    private val payment = rx(
        """(transaction\s+of|purchase|debited|debit ed|(?:\bdebit(?!\s+(?:cards?|accounts?)\b))|withdrawal|transaction for|transaction at|transaction approved|transaction\s+made|txn\s+for|pos(?:[/\-]atm)?\s+transaction|\bpos\b|amount\(approx\)|amt\s|vc\s*:|location:|credited|credit\s+rs|online transfer credit|online\s+transfer|transfer\s+of|mobile payment successful|mobile payment|bill payment successful|bill payment|reversal|auth pmt|pmt\b|fund\s+transfer|transferred|wire\s+transfer|cash\s+deposit|atm\s+deposit|deposited|\bdeposit\b|credit\s+card\s+payment|distributed|profit\s+amount|made\s+to\s+card|\btrxn\b|lankaQR|was\s+performed)""",
    )

    /** Rate and tariff notices: ignored unless a clear money-movement word is also there (BR-Q2). */
    private val notice = rx("""(billing|wish\s+to\s+inform|will\s+be\s+revised|\btariff\b|for\s+(?:more\s+)?details|w\.e\.f\.|effective\s+from)""")
    private val movement = rx("""\b(debited|credited|withdrawn|purchase|deposited|transferred)\b""")

    private val creditCardPayment = rx("""(?:own\s+credit\s+card\s+payment|credit\s+card\s+payment\b.*\b(?:received|successful)|made\s+to\s+card\s+(?:no\.?|number)\s*[\dX\*\-]{4,})""")
    private val loanPayment = rx("""(?:loan\s+recovery|loan\s+repayment|loan\s+installment|loan\s+instalment|\bemi\s+(?:deducted|debited|paid)\b)""")
    private val reversal = rx("""\b(reversal|reversed|refund|chargeback|cancelled\s+transaction)\b""")
    private val withdrawal = rx("""(?:\bcash\s+withdrawal\b|\batm\s+withdrawal\b|\bwithdrawal\s+at\b|\bwithdrew\b|\bwithdrawn\b|\(atm\b|\batm\s+@|\bvia\s+atm\b|\bthrough\s+atm\b|\batm\s+at\b)""")
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

    /** Spec 02 improvement 3: a stated charge is read first and kept out of the amount search. */
    private val fee = rx("""(?:charges?|fee|commission|ceft\s+charge|service\s+charge)\s*(?:of|:)?\s*(?<cur>LKR|Rs\.?)\s?(?<amt>$NUM)(?![\d,])""")

    /** The 14 merchant alternatives of the catalogue, tried separately so a bad capture can fall through (LKSMS-009). */
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

    /** CB-001 generic card digits, and masked digits with `*` or `X`. */
    private val cardDigits = rx("""(?:card|a/?c|account)\s+(?:no\.?\s*)?(?:ending(?:\s+with)?|ending\s+in)?\s*[#:]?\s*[X\*]*(\d{4})\b""")
    private val masked = rx("""(?:\*+|X{2,})(\d{4,6})\b""")
    private val destination = rx("""(?:to\s+a/?c\b(?:\s+(?:no\.?|number))?\s*([A-Z0-9\*\-]{2,}))|(?:beneficiary[: ]+([A-Z0-9\s\.&]+?)(?:\s+(?:on|ref)|$))""")

    override fun parse(bank: Bank, body: String, msg: SmsMessage): ParsedSms? {
        if (!payment.containsMatchIn(body)) return ParsedSms.Ignored(bank, IgnoreReason.NO_MATCH, "no payment keyword")
        if (notice.containsMatchIn(body) && !movement.containsMatchIn(body)) return ParsedSms.Ignored(bank, IgnoreReason.RULE, "notice")

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
            bank = bank, template = TEMPLATE, kind = kind, amount = picked.amount, currency = picked.currency,
            merchant = merchant,
            cardLast4 = last4,
            payee = if (kind == SmsKind.TRANSFER_OUT || kind == SmsKind.TRANSFER_IN) payee else null,
            feeRoute = if (kind == SmsKind.CARD_PAYMENT) FeeRoute.CC_PAYMENT else null,
            certainty = Certainty.UNVERIFIED, reason = reason,
            occurredAt = msg.timestamp,
            // No template: the body itself, with the SMS time so a repeated alert on another day stays apart (CB-010).
            dedupeKey = DedupeKeys.of(bank, TEMPLATE, Banks.normalizeSender(msg.sender), body, msg.timestamp),
            statedFee = statedFee,
        )
    }

    private data class Picked(val amount: BigDecimal, val currency: String, val anchored: Boolean)

    /** LKSMS-008 with the Nexo fixes: nearest after an anchor word wins; anything right after a balance word is skipped. */
    private fun pickAmount(text: String): Picked? {
        data class Hit(val start: Int, val amount: BigDecimal, val currency: String)
        val hits = buildList {
            amountBefore.findAll(text).forEach { m -> Amounts.parse(m.groups["amt"]!!.value)?.let { add(Hit(m.range.first, it, Amounts.currency(m.groups["cur"]!!.value))) } }
            amountAfter.findAll(text).forEach { m -> Amounts.parse(m.groups["amt"]!!.value)?.let { add(Hit(m.range.first, it, Amounts.currency(m.groups["cur"]!!.value))) } }
            amtOnly.findAll(text).forEach { m -> Amounts.parse(m.groups["amt"]!!.value)?.let { add(Hit(m.range.first, it, "LKR")) } }
        }.distinctBy { it.start }.sortedBy { it.start }
            .filterNot { h -> balanceBefore.containsMatchIn(text.substring(maxOf(0, h.start - 24), h.start)) }
        if (hits.isEmpty()) return null
        val anchors = anchor.findAll(text).map { it.range.last }.toList()
        val best = hits.mapNotNull { h -> anchors.filter { it < h.start && h.start - it <= 40 }.maxOrNull()?.let { h to h.start - it } }.minByOrNull { it.second }?.first
        val chosen = best ?: hits.first()
        return Picked(chosen.amount, chosen.currency, anchored = best != null)
    }

    /** LKSMS-007 order; plain debit vs credit is decided by whichever keyword comes first (Nexo fix 5). */
    private fun classify(text: String): Pair<SmsKind, Boolean> {
        if (creditCardPayment.containsMatchIn(text)) return SmsKind.CARD_PAYMENT to false
        if (loanPayment.containsMatchIn(text)) return SmsKind.LOAN_PAYMENT to false
        if (reversal.containsMatchIn(text)) return SmsKind.INCOME to false
        if (withdrawal.containsMatchIn(text)) return SmsKind.TRANSFER_TO_CASH to false
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

    const val TEMPLATE = "generic"
}
