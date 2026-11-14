package com.nexo.app.sms.parse

/** What Nexo keeps of a message body. */
object Redactor {
    private val ci = setOf(RegexOption.IGNORE_CASE)
    const val MASK = "[REDACTED]"
    private const val CODE_MASK = "••••"

    // Approval / OTP codes are never stored, whatever the settings say.
    private val codeAfter = Regex("""\b(code|otp|one time password|pin)\b(\D{0,40}?)\b\d{4,8}\b""", ci)
    private val codeBefore = Regex("""\b\d{4,8}(\s+is\s+(?:your\s+)?(?:otp|one time password)\b)""", ci)

    private val phone = Regex("""(?<!\d)(?:\+94|0094|0)\d{9}(?!\d)""")
    private val longNumber = Regex("""\b\d{8,}\b""")
    private val maskedDigits = Regex("""(?:\*+|X{2,})\d{4,6}\b""")
    private val cardEnding = Regex("""(card ending(?:\s+with|\s+in)?\s*#?\s*)\d{4}\b""", ci)

    fun stripCodes(text: String): String = text
        .replace(codeAfter) { "${it.groupValues[1]}${it.groupValues[2]}$CODE_MASK" }
        .replace(codeBefore) { "$CODE_MASK${it.groupValues[1]}" }

    /** Phone numbers, then long numbers, then masked and card digits. Run after the hints were extracted. */
    fun redact(text: String): String = text
        .replace(phone, MASK)
        .replace(longNumber, MASK)
        .replace(maskedDigits, MASK)
        .replace(cardEnding) { "${it.groupValues[1]}$MASK" }

    fun stored(body: String, keepFullText: Boolean): String = stripCodes(body).let { if (keepFullText) it else redact(it) }
}
