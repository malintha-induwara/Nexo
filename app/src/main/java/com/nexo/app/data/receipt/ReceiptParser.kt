package com.nexo.app.data.receipt

import com.nexo.app.core.model.ReceiptRead
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime

/**
 * Reads the total, the date and the shop name from the rows of an English receipt. Stateless; the rules follow what
 * open-source receipt parsers agree on.
 *
 * 1. **Clean each row.** Currency marks go ("Rs.4,250.00" would otherwise hide the number behind the dot), spaces
 *    around a decimal point close up, and letters read in place of digits inside a number are put back ("4,25O.00").
 *    Label words get the opposite repair ("T0TAL").
 * 2. **Label each row** by whole words: total (strong or plain), subtotal, cash, change, other payment, discount, item
 *    count, and tax or charges. Exclusions are tested first, so "Total Savings" and "Total Items" never count as a total.
 * 3. **Pick the total.** A total row has a total word and no exclusion. Its amount is the right-most one on the row, or
 *    the one on the next row when the label stands alone. Among total rows, an amount that the receipt's own arithmetic
 *    confirms (cash − change, subtotal + tax − discount) wins first, then a strong label over plain "total", then the
 *    lowest row, since a "TOTAL" halfway down is often a running subtotal.
 * 4. **No total row:** an amount on an unlabelled row that the arithmetic confirms, else the largest amount on a row that
 *    is not a payment, subtotal, tax, discount or count, marked as a guess.
 */
object ReceiptParser {
    private const val MAX_OTHERS = 6
    private const val MERCHANT_ROWS = 6
    private const val MERCHANT_LENGTH = 40
    private val EARLIEST = LocalDate.of(2000, 1, 1)

    fun read(lines: List<OcrLine>, today: LocalDate): ReceiptRead = parse(ReceiptRows.build(lines), today)

    fun parse(rows: List<String>, today: LocalDate): ReceiptRead {
        if (rows.isEmpty()) return ReceiptRead.EMPTY
        val all = rows.mapIndexed { i, text -> Row(i, text) }
        val (total, guessed) = pickTotal(all)
        val candidates = all.filter { it.isTotal }.flatMap { it.wholeAmounts }
        val others = (all.flatMap { it.amounts } + candidates)
            .filter { it.signum() > 0 && it != total }
            .distinct()
            .sortedDescending()
            .take(MAX_OTHERS)
        val (date, time) = dateAndTime(rows, today)
        return ReceiptRead(total, guessed, others, date, time, merchant(rows))
    }

    // ---- Total ----

    private fun pickTotal(rows: List<Row>): Pair<BigDecimal?, Boolean> {
        val checks = arithmetic(rows)
        data class Candidate(val row: Row, val amount: BigDecimal)
        val candidates = rows.filter { it.isTotal }.mapNotNull { r -> amountFor(r, rows)?.let { Candidate(r, it) } }
        if (candidates.isNotEmpty()) {
            val best = candidates.sortedWith(
                compareByDescending<Candidate> { it.amount in checks }.thenByDescending { it.row.strength }.thenByDescending { it.row.index },
            ).first()
            return best.amount to false
        }
        val open = rows.filter { !it.excluded }
        checks.firstOrNull { c -> open.any { c in it.amounts } }?.let { return it to false }
        return open.flatMap { it.amounts }.filter { it.signum() > 0 }.maxOrNull()?.let { it to true } ?: (null to false)
    }

    /** The right-most amount on a total row; failing that, the amount on the next row when that row carries no label. */
    private fun amountFor(row: Row, rows: List<Row>): BigDecimal? {
        (row.amounts.lastOrNull() ?: row.wholeAmounts.lastOrNull())?.takeIf { it.signum() > 0 }?.let { return it }
        val next = rows.getOrNull(row.index + 1) ?: return null
        if (next.excluded || next.strength > 0) return null
        return (next.amounts.lastOrNull() ?: next.wholeAmounts.lastOrNull()?.takeIf { !next.hasLetters })?.takeIf { it.signum() > 0 }
    }

    /** Totals the receipt states on its own: cash less change, and subtotal plus tax and charges less discounts. */
    private fun arithmetic(rows: List<Row>): List<BigDecimal> = buildList {
        val cash = rows.firstOrNull { it.cash && !it.change }?.amounts?.lastOrNull()
        val change = rows.firstOrNull { it.change }?.amounts?.lastOrNull()
        if (cash != null && change != null && cash > change) add(cash - change)
        val subtotal = rows.lastOrNull { it.subtotal && it.amounts.isNotEmpty() }?.amounts?.last()
        if (subtotal != null) {
            val extras = rows.filter { it.extra }.mapNotNull { it.amounts.lastOrNull() }.fold(BigDecimal.ZERO, BigDecimal::add)
            val discounts = rows.filter { it.discount }.mapNotNull { it.amounts.lastOrNull() }.fold(BigDecimal.ZERO, BigDecimal::add)
            val sum = subtotal + extras - discounts
            if (sum.signum() > 0) add(sum.setScale(2, RoundingMode.HALF_UP))
        }
    }

    /** One receipt row: its amounts (from the cleaned text) and its labels (from the word text). */
    private class Row(val index: Int, raw: String) {
        private val numbers = cleanNumbers(raw)
        private val words = cleanWords(raw)

        /** Amounts with decimals, left to right. */
        val amounts: List<BigDecimal> = amount.findAll(numbers).mapNotNull { toAmount(it.value) }.toList()

        /** Amounts that may be whole numbers, left to right; read only on a total row. */
        val wholeAmounts: List<BigDecimal> = wholeAmount.findAll(numbers).mapNotNull { toAmount(it.value) }.toList()
        val hasLetters = words.any { it.isLetter() }

        val strength = when {
            strongTotal.containsMatchIn(words) -> 2
            plainTotal.containsMatchIn(words) -> 1
            else -> 0
        }
        val subtotal = subtotalWord.containsMatchIn(words)
        val cash = cashWord.containsMatchIn(words)
        val change = changeWord.containsMatchIn(words)
        val discount = discountWord.containsMatchIn(words)
        val extra = extraWord.containsMatchIn(words) && !inclusiveWord.containsMatchIn(words)

        /** "Total items 12" is a count; "Total (12 items) 4,250.00" is still a total. */
        private val count = countWord.containsMatchIn(words) && amounts.isEmpty()
        private val payment = cash || change || paymentWord.containsMatchIn(words)

        val excluded = subtotal || payment || discount || count || extra
        val isTotal = strength > 0 && !excluded
    }

    // ---- Date and time ----

    private fun dateAndTime(rows: List<String>, today: LocalDate): Pair<LocalDate?, LocalTime?> {
        for ((i, row) in rows.withIndex()) {
            val date = dateIn(row, today) ?: continue
            val time = listOfNotNull(row, rows.getOrNull(i + 1), rows.getOrNull(i - 1)).firstNotNullOfOrNull(::timeIn)
            return date to time
        }
        return null to null
    }

    private fun dateIn(row: String, today: LocalDate): LocalDate? {
        val found = sequence {
            isoDate.findAll(row).forEach { m -> yield(Triple(m.int(3), m.int(2), m.int(1))) }
            numericDate.findAll(row).forEach { m ->
                val (a, b) = m.int(1) to m.int(3)
                // Day first; a "month" over 12 means the receipt printed month first.
                yield(if (b > 12 && a <= 12) Triple(b, a, m.int(4)) else Triple(a, b, m.int(4)))
            }
            dayMonthYear.findAll(row).forEach { m -> yield(Triple(m.int(1), monthOf(m.groupValues[2]), m.int(3))) }
            monthDayYear.findAll(row).forEach { m -> yield(Triple(m.int(2), monthOf(m.groupValues[1]), m.int(3))) }
        }
        return found.firstNotNullOfOrNull { (day, month, y) ->
            val year = if (y < 100) 2000 + y else y
            runCatching { LocalDate.of(year, month, day) }.getOrNull()?.takeIf { !it.isAfter(today) && !it.isBefore(EARLIEST) }
        }
    }

    private fun timeIn(row: String): LocalTime? = clock.findAll(row).firstNotNullOfOrNull { m ->
        var hour = m.int(1)
        val minute = m.int(2)
        val half = m.groupValues[3].lowercase()
        if (half.isNotEmpty()) {
            if (hour !in 1..12) return@firstNotNullOfOrNull null
            hour = when {
                half == "p" && hour < 12 -> hour + 12
                half == "a" && hour == 12 -> 0
                else -> hour
            }
        }
        if (hour > 23 || minute > 59) null else LocalTime.of(hour, minute)
    }

    private fun monthOf(name: String): Int = MONTHS.indexOf(name.take(3).lowercase()) + 1

    private fun MatchResult.int(group: Int): Int = groupValues[group].toInt()

    // ---- Merchant ----

    /** The first of the top rows that reads as a name: mostly letters, no amount, date, phone, web or receipt words. */
    private fun merchant(rows: List<String>): String? = rows.take(MERCHANT_ROWS).firstNotNullOfOrNull { raw ->
        val row = raw.replace(welcomeTo, "").replace(spaces, " ").trim().trim(*EDGE_MARKS).trim()
        val letters = row.count { it.isLetter() }
        val others = row.count { !it.isLetter() && !it.isWhitespace() }
        val bad = letters < 3 || letters <= others || notMerchant.containsMatchIn(row) ||
            amount.containsMatchIn(cleanNumbers(row)) || dateIn(row, LocalDate.MAX) != null
        if (bad) null else titleCase(row).take(MERCHANT_LENGTH).trim()
    }

    private fun titleCase(s: String): String =
        if (s.any { it.isLowerCase() }) s
        else s.split(' ').joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.titlecase() } }
}

// ---- Cleaning ----

private fun cleanNumbers(raw: String): String {
    val text = raw.replace(currency, " ").replace(spacedDecimal, ".")
    return numberish.replace(text) { m ->
        val run = m.value
        val digits = run.count(Char::isDigit)
        val letters = run.count { it.isLetter() || it == '|' }
        if (letters == 0 || digits < 2 || letters >= digits) return@replace run
        val fixed = buildString {
            run.forEachIndexed { i, c ->
                append(
                    when (c) {
                        'O', 'o' -> '0'
                        'I', 'l', '|' -> '1'
                        // A leading S is more often a dollar sign than a five.
                        'S' -> if (i == 0) c else '5'
                        'B' -> if (i == 0) c else '8'
                        else -> c
                    },
                )
            }
        }
        if (money.matches(fixed)) fixed else run
    }
}

private fun cleanWords(raw: String): String = raw.lowercase()
    .replace(digitInWord) { m -> wordLetter(m.value[0]) }
    .replace(digitEndingWord) { "l" }

private fun wordLetter(c: Char): String = when (c) {
    '0' -> "o"
    '1' -> "l"
    '5' -> "s"
    '8' -> "b"
    else -> c.toString()
}

/** The last `.` or `,` is the decimal point when one or two digits follow it; every other separator groups thousands. */
private fun toAmount(raw: String): BigDecimal? {
    val last = raw.indexOfLast { it == '.' || it == ',' }
    val decimals = if (last >= 0) raw.length - last - 1 else 0
    val whole = if (decimals in 1..2) raw.substring(0, last) else raw
    val fraction = if (decimals in 1..2) "." + raw.substring(last + 1) else ""
    return (whole.filter(Char::isDigit).ifEmpty { "0" } + fraction).toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
}

private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
private val EDGE_MARKS = charArrayOf('-', '*', '#', '=', '_', '~', '|', '.', ',', ':')

private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

/** A label or labels as whole words: "total" never matches inside "subtotal". */
private fun label(vararg alternatives: String) = Regex("""(?<![a-z])(?:${alternatives.joinToString("|")})(?![a-z])""")

// ---- Number patterns ----

/** Integer part: Indian grouping, Western grouping, or plain digits (nine at most, so barcodes never match). */
private const val NUM = """(?:\d{1,2}(?:,\d{2})+,\d{3}|\d{1,3}(?:[.,]\d{3})+|\d{1,9})"""
private val amount = Regex("""(?<![\d.,])$NUM[.,]\d{1,2}(?![.,]?\d)(?!\s*%)""")
private val wholeAmount = Regex("""(?<![\d.,])$NUM(?:[.,]\d{1,2})?(?![.,]?\d)(?!\s*%)""")
private val money = Regex("""$NUM[.,]\d{2}""")

private val currency = rx("""(?<![a-z])(?:rs|lkr|slrs?)(?![a-z])\s*[.:]?\s*""")
private val spacedDecimal = Regex("""(?<=\d)(?: +\. *|\. +)(?=\d{2}(?!\d))""")

/** A run that may be a number with letters misread for digits; checked as a whole before anything changes. */
private val numberish = Regex("""(?<![A-Za-z])[\dOoIl|][\dOoIl|SB.,]*[\dOoIl|SB](?![A-Za-z])""")
private val digitInWord = Regex("""(?<=[a-z])[0158](?=[a-z])""")
private val digitEndingWord = Regex("""(?<=[a-z]{3})1(?![\da-z])""")

// ---- Label words (matched on lower-cased text) ----

private val strongTotal = label(
    """grand\s*total""", """g\.?\s*total""", """net+\s*(?:total|amount|payable|value)""",
    """total\s*(?:due|payable|amount|bill|value)""", """amount\s*(?:due|payable|to\s*pay)""", """balance\s*due""",
    """payable\s*amount""", """to\s*pay""", """bill\s*(?:total|amount)""", """invoice\s*(?:total|amount)""",
    """final\s*(?:total|amount)""",
)
private val plainTotal = label("total", "ttl")
private val subtotalWord = label(
    """sub\s*-?\s*total""", """gross\s*(?:total|amount|value)""", """total\s*before""", """before\s*(?:tax|vat|discount)""",
    """excl\.?""", "excluding", "exclusive", "without", "w/o",
)
private val cashWord = label("cash", "tendered", "tender")
private val changeWord = label("change", """balance(?!\s*due)""")
private val paymentWord = label(
    "paid", "payment", "received", "card", "credit", "debit", "visa", """master\s*card""", "amex",
    """auth(?:ori[sz]ation)?""", "approval", "ref", "terminal", "tid", "mid", "trace", "rrn",
)
private val discountWord = label(
    "discounts?", "disc", "savings?", "saved", "coupons?", "vouchers?", "promo(?:tion)?s?", "loyalty", "points?",
    "cashback", "rebate", "offers?",
)
private val countWord = label("items?", "qty", "quantity", "pcs", """no\.?\s*of\s*items""", "lines", "count")
private val extraWord = label(
    "tax(?:es)?", "vat", "gst", "nbt", "sscl", "svat", "cess", """service\s*(?:charge|chg|fee)""", "svc", """s\s*/\s*c""",
    "tips?", "gratuity", "delivery", "packing", "packaging",
)
private val inclusiveWord = label("""incl\.?""", "including", "inclusive", """inc\.?""", "with")

// ---- Date, time and merchant patterns ----

private const val MONTH_NAME =
    """(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"""
private val isoDate = Regex("""(?<!\d)(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})(?!\d)""")
private val numericDate = Regex("""(?<![\d.,/-])(\d{1,2})([/.-])(\d{1,2})\2(\d{4}|\d{2})(?![\d.,]?\d)""")
private val dayMonthYear = rx("""(?<!\d)(\d{1,2})(?:st|nd|rd|th)?[\s\-/.,]*$MONTH_NAME(?![a-z])\.?[\s\-/.,]*(\d{4}|\d{2})(?!\d)""")
private val monthDayYear = rx("""(?<![a-z])$MONTH_NAME(?![a-z])\.?[\s\-/.,]*(\d{1,2})(?:st|nd|rd|th)?[\s\-/.,]+(\d{4}|\d{2})(?!\d)""")
private val clock = rx("""(?<![\d:])(\d{1,2}):(\d{2})(?::\d{2})?(?!\d)(?:\s*([ap])\.?\s*m(?![a-z])\.?)?""")

private val welcomeTo = rx("""^\s*welcome\s+to\s+""")
private val spaces = Regex("""\s+""")
private val notMerchant = rx(
    """(?<![a-z])(?:receipt|invoice|tax|bill|welcome|thank|thanks|tel|phone|hotline|fax|e-?mail|cashier|vat|reg|gstin|date|time|table|order|counter|terminal|pos|no\.?)(?![a-z])""" +
        """|www\.|\.com|\.lk|@|\d{3}[-\s.]?\d{3,4}""",
)
