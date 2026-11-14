package com.nexo.app.sms.parse

/**
 * Messages that are clearly not money movements (CB-007, LKSMS-005). Bank templates run first, so an approval-code
 * transfer that says "do not share" is still captured. Only unambiguous phrases belong here (BR-Q2).
 */
object IgnoreRules {
    private val rule = Regex(
        listOf(
            """one time password""",
            """\botp\b""",
            """verification code""",
            """use code \d{4,8}""",
            """do not share""",
            """temporary password""",
            """user id""",
            """attempt to login""",
            """password changed""",
            """please update""",
            """update to .* app""",
            """upgrade to""",
            """system maintenance""",
            """disregard this message""",
            """declined""",
            """minimum due""",
            """promotion""",
            """buy now""",
        ).joinToString("|", prefix = "(", postfix = ")"),
        RegexOption.IGNORE_CASE,
    )

    /** The phrase that blocked [body], or null. */
    fun match(body: String): String? = rule.find(body)?.value
}
