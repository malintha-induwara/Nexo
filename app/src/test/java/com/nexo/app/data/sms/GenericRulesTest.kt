package com.nexo.app.data.sms

import com.nexo.app.core.model.Bank
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.FeeRoute
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.SmsMessage
import com.nexo.app.core.model.SmsReason
import com.nexo.app.data.sms.parser.IgnoreReason
import com.nexo.app.data.sms.parser.ParsedSms
import com.nexo.app.data.sms.parser.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericRulesTest {
    private val parser = SmsParser()
    private fun parse(sender: String, body: String) =
        parser.parse(SmsMessage(sender, body, 1_793_700_000_000L))
    private fun movement(sender: String, body: String) = parse(sender, body) as ParsedSms.Movement

    @Test
    fun hnbAlertSkipsTheBalance() {
        val m =
            movement(
                "HNB",
                "HNB SMS ALERT:PURCHASE, Account:0280***3456,Location:KEELLS SUPER - NUGEGODA,COLOMBO, LK,Amount(Approx.):2350.00 LKR,Av.Bal:12345.67 LKR, Hot Line:0112462462",
            )
        assertEquals(Bank.HNB, m.bank)
        assertEquals(SmsKind.EXPENSE, m.kind)
        assertEquals("2350.00", m.amount.toPlainString())
        assertEquals("LKR", m.currency)
        assertEquals("KEELLS SUPER - NUGEGODA", m.merchant.name)
        assertEquals("3456", m.cardLast4)
        assertEquals(Certainty.UNVERIFIED, m.certainty)
        assertEquals(SmsReason.GENERIC_RULES, m.reason)
        assertEquals("generic", m.template)
    }

    @Test
    fun bankComesFromTheSenderNotTheSupermarket() {
        val m =
            movement(
                "SAMPATHTXN",
                "LKR 2,350.00 debited from AC **3456 for POS Transaction at CARGILLS FOOD CITY NUGEGODA on 12/11/26",
            )
        assertEquals(Bank.SAMPATH, m.bank)
        assertEquals("CARGILLS FOOD CITY NUGEGODA", m.merchant.name)
        assertEquals("3456", m.cardLast4)
    }

    @Test
    fun accountTextIsNotAMerchant() {
        val m =
            movement(
                "PeoplesBank",
                "Rs. 10,000.00 credited to your A/C ***1234 on 12/11/26. Call 1961",
            )
        assertEquals(SmsKind.INCOME, m.kind)
        assertEquals("10000.00", m.amount.toPlainString())
        assertEquals("", m.merchant.name)
    }

    @Test
    fun overCapturedMerchantFallsThrough() {
        val m =
            movement(
                "DFCC Alerts",
                "Transaction of LKR 3,400.00 at UBER *TRIP on card ending 1234 approved. Avl Bal LKR 20,000.00",
            )
        assertEquals("UBER TRIP", m.merchant.name)
        assertEquals("3400.00", m.amount.toPlainString())
        assertEquals("1234", m.cardLast4)
    }

    @Test
    fun tildePayeeAndAnchoredAmount() {
        val m =
            movement("DFCC Alerts", "A/C **1234 debited LKR 1,000.00 ~ JOHN PERERA ~ Ref No 12345")
        assertEquals("JOHN PERERA", m.merchant.name)
        assertEquals("1000.00", m.amount.toPlainString())
    }

    @Test
    fun atmWithdrawalIsATransferToCash() {
        val m =
            movement(
                "BOC",
                "BOC ATM withdrawal of Rs. 20,000.00 from A/C ***1234 at BOC ATM COLOMBO 07. Bal Rs. 5,000.00",
            )
        assertEquals(SmsKind.TRANSFER_TO_CASH, m.kind)
        assertEquals("20000.00", m.amount.toPlainString())
    }

    @Test
    fun cardAndLoanPayments() {
        // Unknown formats fall through to the general rules, whichever bank sent them.
        val cc =
            movement(
                "COMBANK",
                "Own credit card payment of LKR 15,000.00 made to card no 4111XXXXXXXX1234 successful.",
            )
        assertEquals(SmsKind.CARD_PAYMENT, cc.kind)
        assertEquals(FeeRoute.CC_PAYMENT, cc.feeRoute)
        val loan = movement("SAMPATHTXN", "Loan recovery of LKR 25,000.00 debited from A/C **3456")
        assertEquals(SmsKind.LOAN_PAYMENT, loan.kind)
    }

    @Test
    fun europeanAndIntegerAmounts() {
        assertEquals(
            "1234.56",
            movement("BOC", "Debited LKR 1.234,56 at SHOP X").amount.toPlainString(),
        )
        assertEquals(
            "5000.00",
            movement(
                "BOC",
                "Your A/C ***1234 debited LKR 5000 for bill payment",
            ).amount.toPlainString(),
        )
    }

    @Test
    fun firstKeywordDecidesDebitOrCredit() {
        val m =
            movement(
                "NationsSMS",
                "LKR 5,000.00 debited from your account and will be credited to the beneficiary within the day",
            )
        assertEquals(SmsKind.EXPENSE, m.kind)
        assertEquals(SmsReason.KIND_CONFLICT, m.reason)
    }

    @Test
    fun statedFeeIsKeptApartFromTheAmount() {
        val m =
            movement(
                "NationsSMS",
                "Fund transfer of LKR 10,000.00 to A/C 1234XXXX5678 successful. CEFT charges LKR 30.00. Avl Bal LKR 20,000.00",
            )
        assertEquals(SmsKind.TRANSFER_OUT, m.kind)
        assertEquals("10000.00", m.amount.toPlainString())
        assertEquals("30.00", m.statedFee!!.toPlainString())
        assertEquals("A/C 5678", m.payee)
    }

    @Test
    fun otpAndNoticesAreIgnored() {
        assertTrue(
            parse(
                "BOC",
                "Your OTP is 123456 for the LKR 500.00 purchase. Do not share it.",
            ) is ParsedSms.Ignored,
        )
        assertTrue(
            parse(
                "HNB",
                "We wish to inform you that the tariff for SMS alerts will be revised to LKR 50.00 w.e.f. 01/12/2026",
            ) is ParsedSms.Ignored,
        )
        val noKeyword = parse(
            "BOC",
            "Thank you for banking with BOC. Visit us at any branch.",
        ) as ParsedSms.Ignored
        assertEquals(IgnoreReason.NO_MATCH, noKeyword.reason)
    }

    @Test
    fun amountWithoutAnAnchorIsFlagged() {
        val m = movement("BOC", "Rs. 1,500.00 POS transaction at GREEN GROCER Kandy")
        assertEquals(SmsReason.AMOUNT_FALLBACK, m.reason)
        assertNull(m.feeRoute)
    }
}
