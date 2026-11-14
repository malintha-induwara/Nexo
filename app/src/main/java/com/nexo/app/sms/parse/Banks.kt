package com.nexo.app.sms.parse

import java.util.Locale

/**
 * Sri Lankan banks and fintechs.
 * [senders] are normalised sender IDs; [aliases] are the word-bounded names used to spot the bank in a body;
 * [rateCode] is the bank's id in the AllRatesToday bank-rates API.
 */
enum class Bank(
    val displayName: String,
    val senders: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val rateCode: String? = null,
) {
    COMBANK("Commercial Bank", listOf("COMBANK"), listOf("COMBANK", "COM BANK", "COMMERCIAL BANK", "COMMERCIAL"), "combank"),
    BOC("Bank of Ceylon", listOf("BOC"), listOf("BOC", "BANK OF CEYLON"), "boc"),
    HNB("Hatton National Bank", listOf("HNB"), listOf("HNB", "HNB BANK", "HATTON NATIONAL"), "hnb"),
    SAMPATH("Sampath Bank", listOf("SAMPATHTXN", "SAMPCCTXN"), listOf("SAMPATH", "SAMPATH BANK"), "sampath"),
    PEOPLES("People's Bank", listOf("PEOPLESBANK", "PEOPLESCARD"), listOf("PEOPLES", "PEOPLES BANK", "PEOPLE'S BANK", "PBANK"), "peoples"),
    NTB("NTB Bank", listOf("NATIONSSMS"), listOf("NTB", "NATIONS TRUST"), "ntb"),
    NDB("NDB Bank", aliases = listOf("NDB"), rateCode = "ndb"),
    SEYLAN("Seylan Bank", aliases = listOf("SEYLAN", "SEYLAN BANK"), rateCode = "seylan"),
    DFCC("DFCC Bank", listOf("DFCCALERTS"), listOf("DFCC")),
    HSBC("HSBC", listOf("HSBCALERTS"), listOf("HSBC")),
    FRIMI("FriMi", listOf("FRIMISMS"), listOf("FRIMI")),
    UNION("Union Bank", aliases = listOf("UNION BANK")),
    PAN_ASIA("Pan Asia Bank", aliases = listOf("PANASIA", "PAN ASIA")),
    AMANA("Amana Bank", aliases = listOf("AMANA", "AMANA BANK")),
    SDB("SANASA Development Bank", aliases = listOf("SANASA", "SDB")),

    // "CARGILLS" alone is a supermarket chain; only the full bank name counts.
    CARGILLS("Cargills Bank", aliases = listOf("CARGILLS BANK")),
    HDFC("HDFC Bank", aliases = listOf("HDFC")),
    NSB("National Savings Bank", aliases = listOf("NSB", "NATIONAL SAVINGS")),
    CITI("Citibank", aliases = listOf("CITI", "CITIBANK")),
    GENIE("Genie", aliases = listOf("GENIE")),
    EZCASH("eZ Cash", aliases = listOf("EZCASH", "EZWALLET")),
    MCASH("mCash", aliases = listOf("MCASH")),
    DIALOG_FINANCE("Dialog Finance", aliases = listOf("DIALOG FINANCE")),
    LOLC("LOLC Finance", aliases = listOf("LOLC", "LOLC FINANCE")),
    SOFTLOGIC("Softlogic Finance", aliases = listOf("SOFTLOGIC")),
    CENTRAL_FINANCE("Central Finance", aliases = listOf("CENTRAL FINANCE")),
    VALLIBEL("Vallibel Finance", aliases = listOf("VALLIBEL")),
    LB_FINANCE("LB Finance", aliases = listOf("LB FINANCE")),
    SINGER_FINANCE("Singer Finance", aliases = listOf("SINGER FINANCE")),

    /** A user-added sender whose bank couldn't be recognised. */
    OTHER("Other bank"),
}

object Banks {
    private val senderJunk = Regex("[^A-Z0-9+]")
    private val bySender: Map<String, Bank> = Bank.entries.flatMap { b -> b.senders.map { it to b } }.toMap()
    private val byAlias: Map<String, Bank> = Bank.entries.flatMap { b -> b.aliases.map { it.uppercase(Locale.ROOT) to b } }.toMap()

    /** Longest names first so "PEOPLES BANK" wins over "PEOPLES"; word boundaries stop "citizens" matching CITI. */
    private val vocabulary = Regex(
        byAlias.keys.sortedByDescending { it.length }.joinToString("|", prefix = "\\b(", postfix = ")\\b") { Regex.escape(it) },
        RegexOption.IGNORE_CASE,
    )

    /** Upper-case, then drop everything except A-Z, 0-9 and '+'. */
    fun normalizeSender(sender: String): String = sender.uppercase(Locale.ROOT).replace(senderJunk, "")

    fun fromSender(sender: String): Bank? = bySender[normalizeSender(sender)]

    /** First bank named in [text], word-bounded. */
    fun findIn(text: String): Bank? = vocabulary.find(text)?.let { byAlias[it.value.uppercase(Locale.ROOT)] }

    fun byName(name: String?): Bank? = name?.let { n -> Bank.entries.firstOrNull { it.name == n } }
}
