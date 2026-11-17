package com.nexo.app.data.sms.parser

// Messages that are clearly not money movements. Only unambiguous phrases belong here.
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

    fun match(body: String): String? = rule.find(body)?.value
}
