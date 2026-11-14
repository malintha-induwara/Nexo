package com.nexo.app.sms

import com.nexo.app.sms.parse.MerchantCleaner
import com.nexo.app.sms.parse.Redactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** CB-002 cleanup steps and LKSMS-013 redaction, on invented descriptors. */
class MerchantCleanerTest {
    private fun purchase(raw: String) = MerchantCleaner.clean(raw, atm = false)

    @Test
    fun countryCodeOnlyWhenUpperCase() {
        assertEquals("Lotus Bakery Galle", purchase("Lotus Bakery Galle LK"))
        assertEquals("Harbour Books Oslo", purchase("Harbour Books Oslo NO"))
        assertEquals("Blue Fin Ltd T/a Shop- Mn", purchase("Blue Fin Ltd T/a Shop- Mn"))
    }

    @Test
    fun phoneInvoiceAndAsterisk() {
        assertEquals("ACME CLOUD", purchase("ACME* CLOUD +15550001111 US"))
        assertEquals("PAYCO INVOICE", purchase("PAYCO* INVOICE #12345 +15550002222 US"))
        assertEquals("Mega Store Singapore", purchase("Mega*Store Singapore SG"))
    }

    @Test
    fun colomboDistrictAndReferenceTokens() {
        assertEquals("Island Telecom PLC", purchase("Island Telecom PLC Colombo 03 LK"))
        assertEquals("Streamly Stockholm", purchase("Streamly Q12W34E56R Stockholm SE"))
        // Short tokens with digits stay: they are often part of the name.
        assertEquals("NE - 1234 MORATUWA", purchase("NE - 1234 MORATUWA LK"))
    }

    @Test
    fun urlSchemeAndZeroFillers() {
        assertEquals("SHOP.EXAMPLE", purchase("HTTPS://WWW.SHOP.EXAMPLE 0000000 GB"))
    }

    @Test
    fun atmDistrictSuffix() {
        assertEquals("MATARA-2 BR MATARA", MerchantCleaner.clean("MATARA-2 BR MATARA SPLK", atm = true))
        assertEquals("Hill Bank GALAHA", MerchantCleaner.clean("Hill Bank GALAHA CP LK", atm = true))
    }

    @Test
    fun keyIsLowerCaseAlphanumerics() {
        assertEquals("pizza place matara", MerchantCleaner.key("PIZZA PLACE - MATARA"))
        assertEquals("blue fin ltd t a shop mn", MerchantCleaner.key("Blue Fin Ltd T/a Shop- Mn"))
    }

    @Test
    fun codesAreAlwaysStripped() {
        val stored = Redactor.stored("ComBank Digital-Transfer to Other Banks LKR 7,500.00 attempted. Please use code 987654 to approve.", keepFullText = true)
        assertFalse(stored.contains("987654"))
        assertTrue(stored.contains("LKR 7,500.00"))
        assertFalse(Redactor.stripCodes("Code 987654 is your One Time Password").contains("987654"))
        assertFalse(Redactor.stripCodes("Your OTP to reset password is 987654.").contains("987654"))
    }

    @Test
    fun redactionHidesNumbersButKeepsAmounts() {
        val body = "Your A/C 8001234567 debited LKR 5,000.00 to A/C 1234XXXX5678 card ending #4321. Call 0771234567 or +94112345678. Ref ***9876"
        val r = Redactor.redact(body)
        listOf("8001234567", "5678", "4321", "0771234567", "+94112345678", "9876").forEach { assertFalse(it, r.contains(it)) }
        assertTrue(r.contains("LKR 5,000.00"))
        assertTrue(Redactor.stored(body, keepFullText = true).contains("8001234567"))
        assertFalse(Redactor.stored(body, keepFullText = false).contains("8001234567"))
    }
}
