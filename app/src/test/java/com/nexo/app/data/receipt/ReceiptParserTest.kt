package com.nexo.app.data.receipt

import com.nexo.app.core.model.ReceiptRead
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic receipts only; real bills stay out of the repository. */
class ReceiptParserTest {
    private val today = LocalDate.of(2026, 11, 14)
    private fun read(vararg rows: String) = ReceiptParser.parse(rows.toList(), today)
    private fun amount(s: String) = BigDecimal(s).setScale(2)

    @Test
    fun supermarketBillTakesTheTotalNotTheSubtotalCashOrChange() {
        val r = read(
            "KEELLS SUPER",
            "No. 45, Galle Road, Colombo 03",
            "Tel: 011 2345678",
            "TAX INVOICE",
            "14/11/2026  18:42",
            "Basmati Rice 5kg  2,450.00",
            "Fresh Milk 1L  480.00",
            "Bread  1,020.00",
            "SUB TOTAL  3,950.00",
            "VAT 18%  300.00",
            "TOTAL  4,250.00",
            "CASH  5,000.00",
            "BALANCE  750.00",
            "Total Items: 3",
            "Thank you, come again",
        )
        assertEquals(amount("4250"), r.total)
        assertFalse(r.guessed)
        assertEquals(LocalDate.of(2026, 11, 14), r.date)
        assertEquals(LocalTime.of(18, 42), r.time)
        assertEquals("Keells Super", r.merchant)
        assertEquals(amount("5000"), r.others.first())
        assertTrue(amount("3950") in r.others)
    }

    @Test
    fun lowestTotalWinsOverARunningTotalHigherUp() {
        val r = read("Item A  100.00", "TOTAL  100.00", "Item B  50.00", "TOTAL  150.00")
        assertEquals(amount("150"), r.total)
    }

    @Test
    fun strongLabelBeatsPlainTotal() {
        val r =
            read(
                "Total  4,000.00",
                "Service Charge 10%  400.00",
                "Net Total  4,400.00",
                "Total Savings  120.00",
            )
        assertEquals(amount("4400"), r.total)
    }

    @Test
    fun cashLessChangeSettlesBetweenTwoTotals() {
        val r =
            read(
                "TOTAL  4,250.00",
                "Discount  250.00",
                "TOTAL  4,000.00 ",
                "TOTAL  4,250.00",
                "CASH  5,000.00",
                "CHANGE  1,000.00",
            )
        assertEquals(amount("4000"), r.total)
    }

    @Test
    fun amountOnTheRowBelowALoneLabel() {
        val r = read("Rice  250.00", "GRAND TOTAL", "Rs. 1,250.00", "Visa  1,250.00")
        assertEquals(amount("1250"), r.total)
    }

    @Test
    fun lonelyLabelDoesNotTakeACashRowBelow() {
        val r = read("Rice  250.00", "TOTAL", "CASH  1,000.00")
        assertTrue(r.guessed)
        assertEquals(amount("250"), r.total)
    }

    @Test
    fun currencyMarksAndSuffixesAreIgnored() {
        assertEquals(amount("4250"), read("Grand Total Rs.4,250.00").total)
        assertEquals(amount("4250"), read("NETT AMOUNT LKR 4250.00").total)
        assertEquals(amount("4250"), read("Total Bill  4250/=").total)
        assertEquals(amount("4250"), read("Amount Due  4,250/-").total)
    }

    @Test
    fun ocrSlipsInsideNumbersAreRepaired() {
        assertEquals(amount("4250"), read("TOTAL  4,25O.00").total)
        assertEquals(amount("1250"), read("TOTAL  l,250.00").total)
        assertEquals(amount("100"), read("TOTAL  1OO.00").total)
        assertEquals(amount("4250"), read("TOTAL  4,250 .00").total)
        assertEquals(amount("4250"), read("TOTAL  4,250. 00").total)
    }

    @Test
    fun ocrSlipsInsideLabelsAreRepaired() {
        assertEquals(amount("980"), read("Item  980.00", "T0TAL  980.00", "CASH  1,000.00").total)
        assertFalse(read("T0TAL  980.00").guessed)
        assertFalse(read("TOTA1  980.00").guessed)
    }

    @Test
    fun commaReadForTheDecimalPoint() {
        assertEquals(amount("4250"), read("TOTAL  4,250,00").total)
        assertEquals(amount("4250"), read("TOTAL  4.250.00").total)
    }

    @Test
    fun indianGroupingAndWholeNumberTotals() {
        assertEquals(amount("125000"), read("Grand Total  1,25,000.00").total)
        assertEquals(amount("4250"), read("TOTAL: 4250").total)
    }

    @Test
    fun countsTaxesAndDiscountsAreNeverTheTotal() {
        val r =
            read(
                "Bread  250.00",
                "Total Items  12",
                "Total Qty  12",
                "Total Discount  50.00",
                "Total VAT  45.00",
                "Total  245.00",
            )
        assertEquals(amount("245"), r.total)
        assertFalse(r.guessed)
    }

    @Test
    fun totalWithTheItemCountOnTheSameRowIsStillTheTotal() {
        assertEquals(amount("4250"), read("TOTAL (12 items)  4,250.00").total)
    }

    @Test
    fun inclusiveAndExclusiveTaxWording() {
        assertEquals(
            amount("4250"),
            read("Total excl. VAT  3,950.00", "Total incl. VAT  4,250.00").total,
        )
        assertEquals(
            amount("4250"),
            read("Grand Total (Incl. S/C)  4,250.00", "Total S/C  250.00").total,
        )
    }

    @Test
    fun percentagesAreNotAmounts() {
        val r = read("Service Charge 10.00%  395.00", "Total  4,345.00")
        assertFalse(amount("10") in r.others)
        assertTrue(amount("395") in r.others)
    }

    @Test
    fun noLabelFallsBackToTheLargestOrdinaryAmountAsAGuess() {
        val r =
            read("Coffee  450.00", "Cake  600.00", "1,050.00", "Cash  2,000.00", "Change  950.00")
        assertEquals(amount("1050"), r.total)
        assertFalse("cash less change confirms it", r.guessed)

        val g = read("Coffee  450.00", "Cake  600.00", "Thank you")
        assertEquals(amount("600"), g.total)
        assertTrue(g.guessed)
    }

    @Test
    fun nothingReadableGivesAnEmptyResult() {
        val r = read("THANK YOU", "Visit again")
        assertNull(r.total)
        assertTrue(r.others.isEmpty())
        assertEquals(ReceiptRead.EMPTY, ReceiptParser.parse(emptyList(), today))
    }

    @Test
    fun barcodesDatesAndPhonesAreNotAmounts() {
        val r = read("4902430123456", "14.11.2026", "077-1234567", "Total  99.50")
        assertEquals(amount("99.50"), r.total)
        assertTrue(r.others.isEmpty())
    }

    @Test
    fun dateFormats() {
        fun date(row: String) = read(row).date
        assertEquals(LocalDate.of(2026, 11, 3), date("Date: 03/11/2026"))
        assertEquals(LocalDate.of(2026, 11, 3), date("03-11-26 10:15"))
        assertEquals(LocalDate.of(2026, 11, 3), date("03.11.2026"))
        assertEquals(LocalDate.of(2026, 11, 3), date("2026-11-03 10:15:22"))
        assertEquals(LocalDate.of(2026, 11, 3), date("3 Nov 2026"))
        assertEquals(LocalDate.of(2026, 11, 3), date("03-NOV-26"))
        assertEquals(LocalDate.of(2026, 11, 3), date("3rd November, 2026"))
        assertEquals(LocalDate.of(2026, 11, 3), date("Nov 3, 2026"))
        assertEquals(
            "month first when the day cannot be a month",
            LocalDate.of(2026, 10, 31),
            date("10/31/2026"),
        )
    }

    @Test
    fun futureAndImpossibleDatesAreSkipped() {
        assertNull(read("Exp 12/12/2030").date)
        assertNull(read("31/02/2026").date)
        assertNull(read("Market 12 24").date)
        assertEquals(
            LocalDate.of(2026, 11, 1),
            read("Valid till 01/01/2027", "Date 01/11/2026").date,
        )
    }

    @Test
    fun timeFromTheDateRowOrItsNeighbour() {
        assertEquals(LocalTime.of(18, 5), read("14/11/2026 6:05 PM").time)
        assertEquals(LocalTime.of(0, 30), read("14/11/2026", "Time 12:30 am").time)
        assertEquals(LocalTime.of(9, 7), read("Time: 09:07:44", "Date: 14/11/2026").time)
        assertNull(read("14/11/2026").time)
    }

    @Test
    fun merchantSkipsReceiptWordsContactsAndNumbers() {
        assertEquals(
            "Cargills Food City",
            read("TAX INVOICE", "Welcome to CARGILLS FOOD CITY", "Tel 0112 345 678").merchant,
        )
        assertEquals("Café Mocha", read("*** Café Mocha ***", "Bill No 1234").merchant)
        assertEquals(
            "Pizza Hut",
            read("14/11/2026", "www.example.lk", "PIZZA HUT", "Total 1,200.00").merchant,
        )
        assertNull(read("TOTAL 1,200.00", "CASH 2,000.00").merchant)
    }
}
