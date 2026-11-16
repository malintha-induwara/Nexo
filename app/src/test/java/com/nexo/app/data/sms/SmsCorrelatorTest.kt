package com.nexo.app.data.sms

import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.SmsMessage
import com.nexo.app.core.model.SmsStatus
import com.nexo.app.data.sms.SmsCorrelator.Outcome
import com.nexo.app.data.sms.parser.IgnoreReason
import com.nexo.app.data.sms.parser.SmsParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsCorrelatorTest {
    private val parser = SmsParser()
    private val store = MemorySmsStore()
    private val correlator = SmsCorrelator(store)
    private val minute = 60_000L
    private val base = 1_793_700_000_000L

    private fun send(body: String, at: Long): Outcome = runBlocking {
        val msg = SmsMessage("COMBANK", body, at)
        correlator.accept(msg, parser.parse(msg), body, at)
    }

    private fun purchase(
        merchant: String,
        amount: String,
        cur: String = "LKR",
        card: String = "9876",
        date: String = "03/11/26 07:45 PM",
    ) =
        "Dear Cardholder, Purchase at $merchant for $cur $amount on $date has been authorised on your debit card ending #$card."

    private fun otp(merchant: String, amount: String, cur: String = "LKR") =
        "Dear Cardholder, please verify the merchant name. Your OTP at Merchant '$merchant' for $cur $amount is 987654"

    private fun transferCode(amount: String, route: String = "to Other Banks") =
        "ComBank Digital-Transfer $route LKR $amount attempted. Please use code 987654 to approve. Do NOT share this number with anyone."

    @Test
    fun sameMessageTwiceIsOneItem() {
        val body = purchase("GREEN LEAF CAFE Kandy LK", "2,450.00")
        assertTrue(send(body, base) is Outcome.Created)
        assertTrue(send(body, base + 3_000) is Outcome.Duplicate)
        assertEquals(1, store.items.size)
    }

    @Test
    fun longerOtpNameReplacesTheDescriptor() {
        assertEquals(
            Outcome.HintStored,
            send(otp("Lakeside University Online Payments", "9,000.00"), base),
        )
        val item = (
            send(
                purchase("HTTPS://WWW.PAY.LAKE 0000000 GB", "9,000.00"),
                base + minute,
            ) as Outcome.Created
            ).item
        assertEquals("Lakeside University Online Payments", item.merchant)
        assertEquals("lakeside university online payments", item.merchantKey)
        assertEquals("HTTPS://WWW.PAY.LAKE 0000000 GB", item.merchantRaw)
        assertTrue(item.merchantFromOtp)
        assertTrue(store.hints.isEmpty())
    }

    @Test
    fun shorterOtpNameIsIgnored() {
        send(otp("NINEPINES", "5,000.00"), base)
        val item = (
            send(purchase("NINE PINES COLOMBO 05 LK", "5,000.00"), base + minute) as Outcome.Created
            ).item
        assertEquals("NINE PINES", item.merchant)
        assertFalse(item.merchantFromOtp)
    }

    @Test
    fun otpOlderThanFifteenMinutesIsNotUsed() {
        send(otp("Lakeside University Online Payments", "9,000.00"), base)
        val item = (
            send(
                purchase("HTTPS://WWW.PAY.LAKE 0000000 GB", "9,000.00"),
                base + 16 * minute,
            ) as Outcome.Created
            ).item
        assertEquals("PAY.LAKE", item.merchant)
    }

    @Test
    fun cancellationOfAPendingPurchaseCancelsIt() {
        val created = (
            send(purchase("Mega*Store Singapore SG", "1.00", cur = "USD"), base) as Outcome.Created
            ).item
        val cancelled =
            send(
                "YOUR TRANSACTION AT Mega*Store ON YOUR CARD ENDING WITH 9876 HAS BEEN CANCELLED.",
                base + 2 * 24 * 60 * minute,
            )
        cancelled as Outcome.Cancelled
        assertEquals(created.id, cancelled.item.id)
        assertEquals(SmsStatus.CANCELLED, store.items.getValue(created.id).status)
    }

    @Test
    fun cancellationOfAnApprovedPurchaseQueuesMoneyBack() = runBlocking {
        val created = (
            send(purchase("Mega*Store Singapore SG", "4.00", cur = "USD"), base) as Outcome.Created
            ).item
        store.update(
            created.copy(
                status = SmsStatus.APPROVED,
                accountId = "acc",
                categoryId = "cat",
                transactionId = "tx",
            ),
        )
        val o = send(
            "YOUR TRANSACTION AT Mega*Store ON YOUR CARD ENDING WITH 9876 HAS BEEN CANCELLED.",
            base + minute,
        ) as Outcome.Reversal
        assertEquals(SmsKind.INCOME, o.item.kind)
        assertEquals(SmsStatus.PENDING, o.item.status)
        assertEquals(created.id, o.item.linkedItemId)
        assertEquals("acc", o.item.accountId)
        assertEquals(null, o.item.categoryId)
        assertEquals(null, o.item.transactionId)
        // The bank repeating the cancellation doesn't queue a second refund.
        assertTrue(
            send(
                "YOUR TRANSACTION AT Mega*Store ON YOUR CARD ENDING WITH 9876 HAS BEEN CANCELLED.",
                base + 2 * minute,
            ) is Outcome.Duplicate,
        )
    }

    @Test
    fun cancellationWithoutAPurchaseIsAnOrphan() {
        val o =
            send(
                "YOUR TRANSACTION AT Mega*Store ON YOUR CARD ENDING WITH 9876 HAS BEEN CANCELLED.",
                base,
            )
        assertEquals(IgnoreReason.ORPHAN_CANCEL, (o as Outcome.Skipped).reason)
        // A purchase arriving afterwards is not cancelled retroactively.
        val later = (
            send(
                purchase("Mega*Store Singapore SG", "1.00", cur = "USD"),
                base + minute,
            ) as Outcome.Created
            ).item
        assertEquals(SmsStatus.PENDING, store.items.getValue(later.id).status)
    }

    @Test
    fun cancellationOnAnotherCardDoesNotMatch() {
        send(purchase("Mega*Store Singapore SG", "1.00", cur = "USD", card = "1111"), base)
        assertTrue(
            send(
                "YOUR TRANSACTION AT Mega*Store ON YOUR CARD ENDING WITH 9876 HAS BEEN CANCELLED.",
                base + minute,
            ) is Outcome.Skipped,
        )
    }

    @Test
    fun approvalCodeRetriesMerge() {
        val first = (send(transferCode("7,500.00"), base) as Outcome.Created).item
        val retry = send(transferCode("7,500.00"), base + 5 * minute) as Outcome.Merged
        assertEquals(first.id, retry.item.id)
        assertEquals(2, retry.item.evidenceCount)
        // Twenty minutes later it is a new attempt.
        assertTrue(send(transferCode("7,500.00"), base + 25 * minute) is Outcome.Created)
        // Same amount on the other route is a different transfer.
        assertTrue(
            send(
                transferCode("7,500.00", route = "within ComBank"),
                base + 26 * minute,
            ) is Outcome.Created,
        )
    }

    @Test
    fun completionMessageUpgradesTheApprovalCodeItem() {
        val pending = (send(transferCode("2,500.00"), base) as Outcome.Created).item
        val o =
            send(
                "Fund transfer request WT00001234 P 2026-11-03 at 19:45:10 via Combank Online to J Silva for LKR 2500.00 Inquiries+94110000000.",
                base + 30 * minute,
            )
        o as Outcome.Upgraded
        assertEquals(pending.id, o.item.id)
        assertEquals(Certainty.EXACT, o.item.certainty)
        assertEquals("J Silva", o.item.payee)
        assertEquals("WT00001234", o.item.reference)
        assertEquals(2, o.item.evidenceCount)
        assertEquals(1, store.items.size)
    }

    @Test
    fun approvalCodeArrivingAfterItsCompletionMessageIsNotASecondItem() {
        val completion =
            "Fund transfer request WT00001234 P 2026-11-03 at 19:45:10 via Combank Online to J Silva for LKR 2500.00 Inquiries+94110000000."
        val confirmed = (send(completion, base + 2 * minute) as Outcome.Created).item
        val late = send(transferCode("2,500.00"), base) as Outcome.Duplicate
        assertEquals(confirmed.id, late.existing.id)
        assertEquals(1, store.items.size)
        // A code from well before that transfer, or for another amount, is its own transfer.
        assertTrue(send(transferCode("2,500.00"), base - 20 * minute) is Outcome.Created)
        assertTrue(send(transferCode("2,600.00"), base) is Outcome.Created)
    }
}
