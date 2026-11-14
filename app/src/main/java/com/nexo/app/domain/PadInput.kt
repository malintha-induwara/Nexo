package com.nexo.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

enum class PadKey { D0, D1, D2, D3, D4, D5, D6, D7, D8, D9, DOUBLE_ZERO, TRIPLE_ZERO, DECIMAL, SIGN, BACKSPACE, CLEAR }

/**
 * Number-pad buffer rules. The buffer uses '.' as separator internally.
 * [allowSign] is true for amount fields; [maxDecimals] caps typed decimals.
 */
data class PadInput(
    val buffer: String = "",
    val maxDecimals: Int = 2,
    val allowSign: Boolean = true,
    val maxLength: Int = 15,
) {
    /** Result of a key press: the new input, and a field action when the buffer alone can't express it. */
    data class Result(val input: PadInput, val negateExisting: Boolean = false, val clearField: Boolean = false)

    fun press(key: PadKey): Result = when (key) {
        PadKey.BACKSPACE -> when {
            buffer.isEmpty() -> Result(this, clearField = true)
            else -> {
                val next = buffer.dropLast(1)
                Result(copy(buffer = if (next == "-") "" else next))
            }
        }
        PadKey.CLEAR -> Result(copy(buffer = ""), clearField = true)
        PadKey.SIGN -> when {
            !allowSign -> Result(this)
            buffer.isEmpty() -> Result(this, negateExisting = true)
            buffer.startsWith("-") -> Result(copy(buffer = buffer.drop(1)))
            buffer == "0" -> Result(copy(buffer = "-"))
            else -> Result(copy(buffer = "-$buffer"))
        }
        PadKey.DECIMAL -> when {
            maxDecimals <= 0 -> Result(this)
            buffer.contains('.') -> Result(this)
            buffer.isEmpty() -> Result(copy(buffer = "0."))
            buffer == "-" -> Result(copy(buffer = "-0."))
            buffer.length >= maxLength -> Result(this)
            else -> Result(copy(buffer = "$buffer."))
        }
        PadKey.DOUBLE_ZERO -> Result(typeDigits("00"))
        PadKey.TRIPLE_ZERO -> Result(typeDigits("000"))
        else -> Result(typeDigits(key.ordinal.toString()))
    }

    private fun typeDigits(digits: String): PadInput = digits.fold(this) { acc, c -> acc.typeDigit(c) }

    private fun typeDigit(c: Char): PadInput {
        val b = buffer
        if (b == "0") return copy(buffer = c.toString())
        if (b == "-0") return copy(buffer = "-$c")
        val dot = b.indexOf('.')
        if (dot >= 0 && b.length - dot - 1 >= maxDecimals) {
            // Beyond the cap the last decimal digit is replaced.
            return if (maxDecimals > 0) copy(buffer = b.dropLast(1) + c) else this
        }
        if (b.length >= maxLength) return this
        return copy(buffer = b + c)
    }

    val value: Double?
        get() = buffer.takeIf { it.isNotEmpty() && it != "-" }?.trimEnd('.')?.toBigDecimalOrNull()?.toDouble()

    companion object {
        /** Value shown in a field when it starts editing an existing amount: trailing zeros trimmed. */
        fun plain(value: Double, decimals: Int): String =
            BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}

/** Calculator expression evaluation. */
object Calculator {
    sealed interface Outcome {
        data class Value(val value: BigDecimal) : Outcome
        data object DivideByZero : Outcome
        data object Invalid : Outcome
    }

    fun evaluate(expression: String): Outcome {
        val tokens = tokenize(expression.trimEnd('+', '−', '-', '×', '÷', '*', '/', '.')) ?: return Outcome.Invalid
        if (tokens.isEmpty()) return Outcome.Value(BigDecimal.ZERO)
        // First pass: × and ÷ left to right.
        val terms = ArrayList<BigDecimal>()
        val ops = ArrayList<Char>()
        var current = tokens[0] as BigDecimal
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i] as Char
            val next = tokens[i + 1] as BigDecimal
            when (op) {
                '×' -> current = current.multiply(next)
                '÷' -> {
                    if (next.signum() == 0) return Outcome.DivideByZero
                    current = current.divide(next, 5, RoundingMode.UP)
                }
                else -> {
                    terms += current
                    ops += op
                    current = next
                }
            }
            i += 2
        }
        terms += current
        var result = terms[0]
        for (k in ops.indices) result = if (ops[k] == '+') result.add(terms[k + 1]) else result.subtract(terms[k + 1])
        return Outcome.Value(result.stripTrailingZeros())
    }

    private fun tokenize(s: String): List<Any>? {
        val out = ArrayList<Any>()
        val num = StringBuilder()
        fun flush(): Boolean {
            if (num.isEmpty()) return false
            out += num.toString().toBigDecimalOrNull() ?: return false
            num.clear()
            return true
        }
        for (c in s) {
            when (c) {
                in '0'..'9', '.' -> num.append(c)
                '+', '−', '-', '×', '*', '÷', '/' -> {
                    if (!flush()) return null
                    out += when (c) {
                        '*' -> '×'
                        '/' -> '÷'
                        '-' -> '−'
                        else -> c
                    }
                }
                ' ', ',' -> Unit
                else -> return null
            }
        }
        if (num.isNotEmpty() && !flush()) return null
        return out
    }

    /** Applies a calculator key to the expression. */
    fun type(expr: String, key: String): String {
        val ops = setOf('+', '−', '×', '÷')
        val last = expr.lastOrNull()
        return when {
            key.length == 1 && key[0] in ops -> when {
                expr.isEmpty() -> expr
                last == '.' -> expr
                last in ops -> expr.dropLast(1) + key
                else -> expr + key
            }
            key == "." -> {
                val currentNumber = expr.takeLastWhile { it !in ops }
                when {
                    currentNumber.contains('.') -> expr
                    currentNumber.isEmpty() -> expr + "0."
                    else -> expr + "."
                }
            }
            else -> {
                if (expr.length >= 120) return expr
                val currentNumber = expr.takeLastWhile { it !in ops }
                if (currentNumber == "0") expr.dropLast(1) + key else expr + key
            }
        }
    }
}
