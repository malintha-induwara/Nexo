package com.nexo.app.sms.parse

/**
 * Turns one SMS into a [ParsedSms]. Stateless: merging, enrichment and cancellation happen in the correlator.
 *
 * Order (spec 03): sender → bank templates → ignore rules → generic pipeline.
 */
class SmsParser(
    customSenders: Collection<String> = emptyList(),
    private val generic: GenericRules? = GenericParser,
) {
    /** The last-resort pipeline for banks without templates, or template banks' unknown formats (spec 01). */
    fun interface GenericRules {
        fun parse(bank: Bank, body: String, msg: SmsMessage): ParsedSms?
    }

    private val custom = customSenders.map(Banks::normalizeSender).filter { it.isNotEmpty() }.toSet()
    private val lineBreaks = Regex("\r\n?")

    fun parse(msg: SmsMessage): ParsedSms {
        val body = msg.body.replace(lineBreaks, "\n").trim()
        val sender = Banks.normalizeSender(msg.sender)
        val bank = Banks.fromSender(sender)
            ?: if (sender in custom) Banks.findIn(body) ?: Bank.OTHER else return ParsedSms.Ignored(null, IgnoreReason.SENDER)
        templates(bank)?.match(bank, body, msg)?.let { return it }
        IgnoreRules.match(body)?.let { return ParsedSms.Ignored(bank, IgnoreReason.RULE, it) }
        return generic?.parse(bank, body, msg) ?: ParsedSms.Ignored(bank, IgnoreReason.NO_MATCH)
    }

    private fun templates(bank: Bank): BankTemplates? = when (bank) {
        Bank.COMBANK -> ComBankTemplates
        else -> null
    }
}
