package com.nexo.app.ui.entry

import android.net.Uri
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.FeeDraft
import com.nexo.app.core.model.PhotoItem
import com.nexo.app.core.model.ReceiptRead
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.TxKind
import com.nexo.app.ui.designsystem.component.PadInput
import com.nexo.app.ui.designsystem.component.PadKey
import com.nexo.app.ui.designsystem.preview.PreviewData
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EntryFormLogicTest {
    private val day = LocalDate.of(2026, 11, 14)
    private val blank = EntryForm(
        date = day,
        time = LocalTime.of(9, 30),
        currencyId = "LKR",
        feeCurrencyId = "LKR",
    )
    private val lunch = blank.copy(accountId = "cash", categoryId = "food", amount = 1200.0)

    @Test
    fun aTransferDraftCarriesItsFeeAndNoCategory() {
        val form = lunch.copy(
            type = EntryType.TRANSFER,
            toAccountId = "bank",
            feeExpanded = true,
            feeAmount = 25.0,
        )
        val draft = form.toDraft()
        assertNull(draft.categoryId)
        assertEquals("bank", draft.toAccountId)
        assertEquals(FeeDraft(25.0, "LKR"), draft.fee)
        assertEquals(day.atTime(9, 30), draft.dateTime)
    }

    @Test
    fun aRepeatingDraftIsStoredAtMidnightAndARuleKeepsItsDay() {
        val form = lunch.copy(repeat = RepeatType.MONTHLY, note = " Rent ")
        assertEquals(day.atStartOfDay(), form.toDraft().dateTime)

        val rule = form.toRepeatRule("r1", PreviewData.ref.mainCurrency, createdAt = 1, now = 2)
        assertEquals(TxKind.EXPENSE, rule.kind)
        assertEquals("Rent", rule.note)
        assertEquals(14, rule.anchorDay)
        assertEquals("2026-11-14", rule.nextDate)
    }

    @Test
    fun changingTypeStartsTheCategoryOver() {
        val income = lunch.copy(installments = 3).withType(EntryType.INCOME)
        assertEquals(EntryType.INCOME, income.type)
        assertNull(income.categoryId)
        assertEquals(0, income.installments)
        assertSame(lunch, lunch.withType(EntryType.EXPENSE))
    }

    @Test
    fun repeatsAndInstallmentsExcludeEachOther() {
        val repeating = lunch.copy(tagIds = listOf("work"), feeAmount = 5.0, installments = 4)
            .withRepeat(RepeatType.WEEKLY)
        assertEquals(0, repeating.installments)
        assertEquals(emptyList<String>(), repeating.tagIds)
        assertNull(repeating.feeAmount)

        val split = repeating.withInstallments(900)
        assertEquals(600, split.installments)
        assertEquals(RepeatType.NONE, split.repeat)
    }

    @Test
    fun typedDigitsBecomeTheAmountAndTheFeeIsNeverNegative() {
        val typed = blank.copy(pad = PadInput("", 2))
            .afterPadKey(PadKey.D1)
            .afterPadKey(PadKey.D2)
        assertEquals(12.0, typed.amount)

        val fee = blank.copy(panel = Panel.FEE, feeAmount = 8.0, pad = PadInput("", 2))
            .afterPadKey(PadKey.SIGN)
        assertEquals(8.0, fee.feeAmount)
    }

    @Test
    fun swappingTurnsTheTransferAroundAndLooksTheFeeUpAgain() {
        val swapped = lunch.copy(toAccountId = "bank", feeLoaded = true).swapped(PreviewData.ref)
        assertEquals("bank", swapped.accountId)
        assertEquals("cash", swapped.toAccountId)
        assertEquals(false, swapped.feeLoaded)
    }

    @Test
    fun aConfirmedReceiptFillsOnlyWhatWasAskedFor() {
        val read = ReceiptRead(
            total = BigDecimal("1499.995"),
            guessed = false,
            others = emptyList(),
            merchant = "Keells",
        )
        val done = ScanUi.Done(Uri.parse("content://receipts/1"), read)
        val noted = lunch.copy(note = "Groceries", scan = done)

        val applied = noted.withReceipt(done, 1499.995, useMerchant = true, 2)

        assertEquals(1500.0, applied.amount)
        assertEquals(noted.date, applied.date)
        assertEquals(noted.time, applied.time)
        assertEquals("Groceries", applied.note)
        assertEquals(listOf(PhotoItem.Picked("content://receipts/1")), applied.photos)
        assertNull(applied.scan)
        assertEquals(applied.photos, applied.withReceipt(done, null, false, 2).photos)
    }

    @Test
    fun theNextFieldIsTheFirstEmptyOne() {
        assertEquals(Field.ACCOUNT, blank.nextEmptyField())
        assertNull(lunch.nextEmptyField())
    }
}
