package com.nexo.app.data.sms

import com.nexo.app.core.model.Bank
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.FeeMode
import com.nexo.app.core.model.FeeRoute
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.SmsMessage
import com.nexo.app.core.model.SmsReason
import com.nexo.app.data.sms.parser.BodyDate
import com.nexo.app.data.sms.parser.IgnoreReason
import com.nexo.app.data.sms.parser.ParsedSms
import com.nexo.app.data.sms.parser.SmsParser
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Synthetic messages; names and amounts are invented.
class SmsParserTest {
    private val parser = SmsParser()
    private val nov3 = LocalDateTime.of(
        2026,
        11,
        3,
        19,
        45,
    ).atZone(BodyDate.ZONE).toInstant().toEpochMilli()

    private fun parse(body: String, sender: String = "COMBANK", at: Long = nov3) =
        parser.parse(SmsMessage(sender, body, at))
    private fun movement(body: String, at: Long = nov3) = parse(body, at = at) as ParsedSms.Movement

    @Test
    fun lkrPurchaseIsAnExactExpense() {
        val m =
            movement(
                "Dear Cardholder, Purchase at GREEN LEAF CAFE Kandy LK for LKR 2,450.00 on 03/11/26 07:45 PM has been authorised on your debit card ending #4321.",
            )
        assertEquals(Bank.COMBANK, m.bank)
        assertEquals(SmsKind.EXPENSE, m.kind)
        assertEquals("2450.00", m.amount.toPlainString())
        assertEquals("LKR", m.currency)
        assertEquals("GREEN LEAF CAFE Kandy LK", m.merchant.raw)
        assertEquals("GREEN LEAF CAFE Kandy", m.merchant.name)
        assertEquals("green leaf cafe kandy", m.merchant.key)
        assertEquals("4321", m.cardLast4)
        assertEquals(Certainty.EXACT, m.certainty)
        assertNull(m.feeRoute)
        assertEquals(nov3, m.occurredAt)
        assertEquals("03/11/26 07:45 PM", m.bodyDate)
    }

    @Test
    fun bodyDateFarFromTheSmsTimeIsNotUsed() {
        val later = nov3 + 10L * 24 * 3600 * 1000
        val m =
            movement(
                "Dear Cardholder, Purchase at GREEN LEAF CAFE Kandy LK for LKR 2,450.00 on 03/11/26 07:45 PM has been authorised on your debit card ending #4321.",
                later,
            )
        assertEquals(later, m.occurredAt)
    }

    @Test
    fun foreignPurchaseIsEstimatedWithAnFxFeeRoute() {
        val m =
            movement(
                "Dear Cardholder, Purchase at ACME* CLOUD HOSTING +15551234567 US for USD 12.50 on 03/11/26 07:45 PM has been authorized on your credit card ending #9876.",
            )
        assertEquals("USD", m.currency)
        assertEquals("12.50", m.amount.toPlainString())
        assertEquals("ACME CLOUD HOSTING", m.merchant.name)
        assertEquals(Certainty.ESTIMATED, m.certainty)
        assertEquals(SmsReason.FX_ESTIMATE, m.reason)
        assertEquals(FeeRoute.FX_CARD_PURCHASE, m.feeRoute)
    }

    @Test
    fun rupeeSymbolAndIntegerAmountsAreLkr() {
        val m =
            movement(
                "Dear Cardholder, Purchase at CORNER SHOP Matara LK for Rs.1200 on 03/11/26 07:45 PM has been authorised on your debit card ending #4321.",
            )
        assertEquals("LKR", m.currency)
        assertEquals("1200.00", m.amount.toPlainString())
    }

    @Test
    fun ungroupedAmountIsNotTruncated() {
        val m =
            movement(
                "Fund transfer request WT00001234 P 2026-11-03 at 19:45:10 via Combank Online to J Silva for LKR 2500.00 Inquiries+94110000000.",
            )
        assertEquals(SmsKind.TRANSFER_OUT, m.kind)
        assertEquals("2500.00", m.amount.toPlainString())
        assertEquals("J Silva", m.payee)
        assertEquals("WT00001234", m.reference)
        assertEquals(Certainty.EXACT, m.certainty)
        assertEquals(
            LocalDateTime.of(
                2026,
                11,
                3,
                19,
                45,
                10,
            ).atZone(BodyDate.ZONE).toInstant().toEpochMilli(),
            m.occurredAt,
        )
    }

    @Test
    fun ownAtmWithdrawalIsATransferToCashWithoutFee() {
        val m =
            movement(
                "Withdrawal at ST JOHN'S RD BR KANDY CPLK for LKR 6,000.00 on 03/11/26 07:45 PM from card ending #4321. Click link to view the Digital Receipt for Withdrawal performed at ComBank ATMs https://example.invalid/r",
            )
        assertEquals(SmsKind.TRANSFER_TO_CASH, m.kind)
        assertEquals("ST JOHN'S RD BR KANDY", m.merchant.name)
        assertEquals("st john s rd br kandy", m.merchant.key)
        assertNull(m.feeRoute)
    }

    @Test
    fun otherBankAtmAsksForAFee() {
        val m =
            movement(
                "Withdrawal at Bank of Ceylon CITY BR WPLK for LKR 10,000.00 on 03/11/26 07:45 PM from card ending #4321.",
            )
        assertEquals(FeeRoute.OTHER_BANK_ATM, m.feeRoute)
        assertEquals("Bank of Ceylon CITY BR", m.merchant.name)
    }

    @Test
    fun supermarketNameIsNotABank() {
        val m =
            movement(
                "Withdrawal at CARGILLS SQUARE RD WPLK for LKR 3,000.00 on 03/11/26 07:45 PM from card ending #4321.",
            )
        assertNull(m.feeRoute)
    }

    @Test
    fun approvalCodeTransfersAreUnverified() {
        val other =
            movement(
                "ComBank Digital-Transfer to Other Banks LKR 7,500.00 attempted. Please use code 987654 to approve. Do NOT share this number with anyone. HASH",
            )
        assertEquals(SmsKind.TRANSFER_OUT, other.kind)
        assertEquals(Certainty.UNVERIFIED, other.certainty)
        assertEquals(SmsReason.APPROVAL_CODE, other.reason)
        assertEquals("to Other Banks", other.route)
        assertEquals(FeeRoute.INTERBANK_TRANSFER, other.feeRoute)

        val own =
            movement(
                "ComBank Digital-Transfer within ComBank LKR 1,000.00 attempted. Please use code 987654 to approve. Do NOT share this number with anyone. HASH",
            )
        assertEquals(FeeRoute.OWN_BANK_TRANSFER, own.feeRoute)
        assertEquals(FeeMode.NEVER, own.feeRoute!!.defaultMode)
    }

    @Test
    fun merchantOtpBecomesAHint() {
        val p =
            parse(
                "Dear Cardholder, please verify the merchant name and transaction amount before entering the One Time Password (OTP). Your OTP at Merchant 'Acme Cloud Hosting Ltd' for USD 12.50 is 987654",
            )
        p as ParsedSms.OtpHint
        assertEquals("Acme Cloud Hosting Ltd", p.merchant)
        assertEquals("12.50", p.amount.toPlainString())
    }

    @Test
    fun declinedAttemptsAreIgnored() {
        val p =
            parse(
                "Dear Cardholder, your card was declined due to insufficient funds. The attempted transaction amount was LKR 999.00 at SOME STORE COLOMBO LK on 03/11/26 07:45 PM. Please check your balance and try again.",
            )
        assertEquals(IgnoreReason.DECLINED, (p as ParsedSms.Ignored).reason)
    }

    @Test
    fun cancellationHasMerchantAndCard() {
        val p = parse(
            "YOUR TRANSACTION AT Acme*Cloud ON YOUR CARD ENDING WITH 9876 HAS BEEN CANCELLED.",
        ) as ParsedSms.Cancel
        assertEquals("Acme*Cloud", p.merchant.raw)
        assertEquals("Acme Cloud", p.merchant.name)
        assertEquals("9876", p.cardLast4)
    }

    @Test
    fun securityAndMarketingMessagesAreIgnored() {
        listOf(
            "Dear Customer, your attempt to login to ComBank Digital failed. For inquiries +94110000000",
            "Dear Customer, please update your banking app to the latest version.\r\nThank you.",
            "<#> Your Digital Bank OTP to reset password is 987654. Valid for 10 minutes.\nHASH",
            "Code 987654 is your One Time Password to authorize the WT00000009 transaction",
            "Your temporary password for Digital Banking is [X]",
        ).forEach { body ->
            val p = parse(body)
            assertTrue(body, p is ParsedSms.Ignored && p.reason == IgnoreReason.RULE)
        }
    }

    @Test
    fun unknownSendersAreDroppedAndCustomSendersAccepted() {
        assertEquals(
            IgnoreReason.SENDER,
            (parse("Purchase of LKR 100.00", sender = "RANDOM") as ParsedSms.Ignored).reason,
        )
        val custom = SmsParser(
            customSenders = listOf("my bank"),
        ).parse(SmsMessage("MYBANK", "Hello from Seylan Bank", nov3))
        assertEquals(Bank.SEYLAN, custom.bank)
    }

    @Test
    fun senderIsNormalised() {
        val p =
            parse(
                "Dear Cardholder, Purchase at GREEN LEAF CAFE Kandy LK for LKR 2,450.00 on 03/11/26 07:45 PM has been authorised on your debit card ending #4321.",
                sender = "ComBank",
            )
        assertTrue(p is ParsedSms.Movement)
    }

    @Test
    fun formatsDoNotDependOnTheSender() {
        val p = parser.parse(
            SmsMessage(
                "SAMPATHTXN",
                "Purchase at GREEN LEAF CAFE Kandy LK for LKR 2,450.00 on 03/11/26 07:45 PM has been authorised on your debit card ending #4321.",
                nov3,
            ),
        ) as ParsedSms.Movement
        assertEquals(Bank.SAMPATH, p.bank)
        assertEquals("purchase", p.template)
        assertEquals(Certainty.EXACT, p.certainty)
        assertEquals("GREEN LEAF CAFE Kandy", p.merchant.name)

        val code = parser.parse(
            SmsMessage(
                "SAMPATHTXN",
                "Sampath Online Transfer within Sampath Bank LKR 1,000.00 attempted. Please use code 987654 to approve.",
                nov3,
            ),
        ) as ParsedSms.Movement
        assertEquals("transfer_code", code.template)
        assertEquals("within Sampath Bank", code.route)
        assertEquals(FeeRoute.OWN_BANK_TRANSFER, code.feeRoute)
    }

    @Test
    fun atmBankIsComparedWithTheSender() {
        fun atm(sender: String, body: String) =
            (parser.parse(SmsMessage(sender, body, nov3)) as ParsedSms.Movement).feeRoute
        val atComBank = "Withdrawal at Commercial Bank CITY BR WPLK for LKR 5,000.00 on 03/11/26 " +
            "07:45 PM from card ending #4321."
        assertNull(atm("COMBANK", atComBank))
        assertEquals(FeeRoute.OTHER_BANK_ATM, atm("SAMPATHTXN", atComBank))
        // The receipt footer names the sender's own ATMs, whatever the ATM name says.
        assertNull(
            atm(
                "SAMPATHTXN",
                "Withdrawal at BOC SQUARE BR WPLK for LKR 5,000.00 on 03/11/26 07:45 PM from card ending #4321. Receipt for Withdrawal performed at Sampath Bank ATMs",
            ),
        )
    }

    @Test
    fun sameAlertDedupesWhateverTheSmsTime() {
        val body = "Dear Cardholder, Purchase at GREEN LEAF CAFE Kandy LK for LKR 2,450.00 on " +
            "03/11/26 07:45 PM has been authorised on your debit card ending #4321."
        assertEquals(movement(body).dedupeKey, movement(body, nov3 + 5_000).dedupeKey)
    }
}
