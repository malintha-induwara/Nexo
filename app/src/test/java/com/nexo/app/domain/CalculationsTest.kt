package com.nexo.app.domain

import com.nexo.app.core.database.entity.BudgetAmountEntity
import com.nexo.app.core.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CalculationsTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun installmentSplitFirstRowCarriesRemainder() {
        assertEquals(listOf(33.34, 33.33, 33.33), Installments.split(100.0, 3, 2))
        assertEquals(listOf(334.0, 333.0, 333.0), Installments.split(1000.0, 3, 0))
    }

    @Test
    fun installmentDatesClampToShortMonths() {
        assertEquals(listOf(d("2024-01-31"), d("2024-02-29"), d("2024-03-31")), Installments.dates(d("2024-01-31"), 3))
    }

    @Test
    fun monthlyRepeatAnchoredToOriginalDay() {
        val feb = RepeatSchedule.next(d("2023-01-31"), RepeatType.MONTHLY, 31)!!
        assertEquals(d("2023-02-28"), feb)
        assertEquals(d("2023-03-31"), RepeatSchedule.next(feb, RepeatType.MONTHLY, 31))
        assertEquals(d("2024-02-29"), RepeatSchedule.next(d("2023-02-28"), RepeatType.ANNUAL, 29))
    }

    @Test
    fun weekdayAndWeekendRepeats() {
        // 2024-05-17 is a Friday.
        assertEquals(d("2024-05-20"), RepeatSchedule.next(d("2024-05-17"), RepeatType.WEEKDAYS, 17))
        assertEquals(d("2024-05-18"), RepeatSchedule.next(d("2024-05-17"), RepeatType.WEEKENDS, 17))
        assertEquals(d("2024-05-19"), RepeatSchedule.next(d("2024-05-18"), RepeatType.WEEKENDS, 18))
        assertEquals(d("2024-05-25"), RepeatSchedule.next(d("2024-05-19"), RepeatType.WEEKENDS, 19))
        assertEquals(d("2024-06-30"), RepeatSchedule.next(d("2024-05-31"), RepeatType.END_OF_MONTH, 31))
    }

    @Test
    fun repeatSnapping() {
        assertEquals(d("2024-05-20"), RepeatSchedule.snap(d("2024-05-18"), RepeatType.WEEKDAYS))
        assertEquals(d("2024-05-18"), RepeatSchedule.snap(d("2024-05-15"), RepeatType.WEEKENDS))
        assertEquals(d("2024-05-31"), RepeatSchedule.snap(d("2024-05-15"), RepeatType.END_OF_MONTH))
    }

    private fun rec(key: Int, amount: Double) = BudgetAmountEntity(BudgetAmountEntity.idFor("b", key), "b", key, amount, 0)

    @Test
    fun budgetEffectiveAmount() {
        val r = listOf(rec(0, 100.0), rec(202405, 150.0), rec(202406, 100.0))
        assertEquals(100.0, BudgetMath.effective(r, 202404), 0.0)
        assertEquals(150.0, BudgetMath.effective(r, 202405), 0.0)
        assertEquals(100.0, BudgetMath.effective(r, 202409), 0.0)
    }

    @Test
    fun budgetPerMonthEditOnlyChangesThatMonth() {
        val r = listOf(rec(0, 100.0))
        val out = BudgetMath.perMonthEdit("b", r, 202412, 300.0, 1)
        val merged = (r + out).associateBy { it.periodKey }.values.toList()
        assertEquals(100.0, BudgetMath.effective(merged, 202411), 0.0)
        assertEquals(300.0, BudgetMath.effective(merged, 202412), 0.0)
        assertEquals(100.0, BudgetMath.effective(merged, 202501), 0.0)
    }

    @Test
    fun budgetDefaultEditAppliesFromNextMonth() {
        val r = listOf(rec(0, 100.0))
        val out = BudgetMath.defaultEdit("b", r, 202405, 200.0, 1)
        val merged = (r + out).associateBy { it.periodKey }.values.toList()
        assertEquals(100.0, BudgetMath.effective(merged, 202405), 0.0)
        assertEquals(200.0, BudgetMath.effective(merged, 202406), 0.0)
        assertEquals(200.0, BudgetMath.latest(merged), 0.0)
    }

    @Test
    fun budgetRate() {
        assertEquals(50, BudgetMath.rate(50.0, 100.0))
        assertEquals(100, BudgetMath.rate(5.0, 0.0))
        assertEquals(0, BudgetMath.rate(0.0, 0.0))
        assertNull(BudgetMath.rate(1_000_000.0, 1.0))
    }
}
