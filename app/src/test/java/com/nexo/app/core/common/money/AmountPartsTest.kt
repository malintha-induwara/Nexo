package com.nexo.app.core.common.money

import com.nexo.app.core.model.Currency
import com.nexo.app.core.model.SymbolPosition
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AmountPartsTest {
    private lateinit var saved: Locale

    @Before
    fun setUp() {
        saved = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() = Locale.setDefault(saved)

    private fun currency(symbol: String, position: SymbolPosition, decimals: Int) =
        Currency("X", "XXX", "X", symbol, position, decimals, 1.0, true, true, 0, 0)

    @Test
    fun splitsPrefixIntegerAndFraction() {
        val p = AmountParts.of(48250.0, currency("Rs", SymbolPosition.PREFIX, 2))
        assertEquals(AmountParts("", "Rs", "48,250", ".00", ""), p)
        assertEquals("Rs 48,250.00", p.text)
        assertEquals(
            AmountFormatter.format(48250.0, currency("Rs", SymbolPosition.PREFIX, 2)),
            p.text,
        )
    }

    @Test
    fun negativeUsesTrueMinusAndSuffixSymbol() {
        val p = AmountParts.of(-1850.5, currency("€", SymbolPosition.SUFFIX, 2))
        assertEquals(AmountParts("−", "", "1,850", ".50", "€"), p)
    }

    @Test
    fun signModes() {
        val c = currency("$", SymbolPosition.PREFIX, 2)
        assertEquals("+", AmountParts.of(5.0, c, AmountParts.Sign.ALWAYS).sign)
        assertEquals("", AmountParts.of(5.0, c, AmountParts.Sign.NEGATIVE).sign)
        assertEquals("", AmountParts.of(-5.0, c, AmountParts.Sign.NONE).sign)
        // Rounds to zero, so no sign at all.
        assertEquals("", AmountParts.of(-0.004, c, AmountParts.Sign.ALWAYS).sign)
    }

    @Test
    fun zeroDecimalCurrencyHasNoFraction() {
        val p = AmountParts.of(1234567.0, currency("¥", SymbolPosition.PREFIX, 0))
        assertEquals("1,234,567", p.integer)
        assertEquals("", p.fraction)
    }

    @Test
    fun followsLocaleSeparators() {
        Locale.setDefault(Locale.GERMANY)
        val p = AmountParts.of(1234.5, currency("€", SymbolPosition.SUFFIX, 2))
        assertEquals("1.234", p.integer)
        assertEquals(",50", p.fraction)
    }
}
