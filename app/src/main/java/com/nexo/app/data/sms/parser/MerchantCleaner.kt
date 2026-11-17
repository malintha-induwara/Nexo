package com.nexo.app.data.sms.parser

import java.util.Locale

// raw as sent, name as shown, key for category learning.
data class Merchant(val raw: String, val name: String, val key: String) {
    companion object {
        val NONE = Merchant("", "", "")
    }
}

object MerchantCleaner {
    private val ci = setOf(RegexOption.IGNORE_CASE)

    // Case-sensitive: ATM names end in a district code + LK, card descriptors in a country code.
    private val atmSuffix = Regex("""\s?(?:[A-Z]{2}\s?)?LK$""")
    private val country = Regex("""\s[A-Z]{2}$""")
    private val phone = Regex("""\+\d{8,}""")
    private val invoice = Regex("""#\d+""")
    private val colomboDistrict = Regex("""\bColombo\s+\d{1,2}\b""", ci)
    private val referenceToken = Regex("""\b(?=[A-Z0-9]*\d)(?=[A-Z0-9]*[A-Z])[A-Z0-9]{8,}\b""", ci)
    private val zeroFiller = Regex("""\b0{4,}\b""")
    private val urlScheme = Regex("""^https?://(?:www\.)?""", ci)
    private val spaces = Regex("""\s+""")
    private val keyJunk = Regex("""[^a-z0-9]+""")

    fun purchase(raw: String): Merchant = of(raw, clean(raw, atm = false))

    fun atm(raw: String): Merchant = of(raw, clean(raw, atm = true))

    fun clean(raw: String, atm: Boolean): String {
        var s = raw.trim()
        s = s.replace(if (atm) atmSuffix else country, "")
        s = s.replace(phone, " ")
        s = s.replace(invoice, " ")
        s = s.replace(colomboDistrict, " ")
        s = s.replace(referenceToken, " ")
        s = s.replace(zeroFiller, " ")
        s = s.trim().replace(urlScheme, "")
        s = s.replace('*', ' ')
        return s.replace(spaces, " ").trim(' ', '-', '.', ',', ':', ';')
    }

    // Lower-case alphanumerics, so spelling variants of one merchant share a key.
    fun key(name: String): String = name.lowercase(Locale.ROOT).replace(keyJunk, " ").trim()

    private fun of(raw: String, name: String) = Merchant(raw.trim(), name, key(name))
}
