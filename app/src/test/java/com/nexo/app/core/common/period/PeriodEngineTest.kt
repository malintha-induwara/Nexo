package com.nexo.app.core.common.period

import com.nexo.app.core.model.WeekendRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodEngineTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun startDayOneIsCalendarMonth() {
        val p = PeriodEngine(1).monthPeriodContaining(d("2024-05-17"))
        assertEquals(d("2024-05-01"), p.start)
        assertEquals(d("2024-05-31"), p.end)
        assertEquals(YearMonth.of(2024, 5), p.label)
    }

    @Test
    fun startDayTenIsLabelledByStartMonth() {
        val e = PeriodEngine(10)
        val p = e.monthPeriodContaining(d("2024-02-05"))
        assertEquals(d("2024-01-10"), p.start)
        assertEquals(d("2024-02-09"), p.end)
        assertEquals(YearMonth.of(2024, 1), p.label)
    }

    @Test
    fun startDayTwentyFiveIsLabelledByEndMonth() {
        val e = PeriodEngine(25)
        val p = e.monthPeriodContaining(d("2024-02-01"))
        assertEquals(d("2024-01-25"), p.start)
        assertEquals(d("2024-02-24"), p.end)
        assertEquals(YearMonth.of(2024, 2), p.label)
        assertEquals(p, e.monthPeriodFor(YearMonth.of(2024, 2)))
    }

    @Test
    fun startDayClampsInShortMonths() {
        val p = PeriodEngine(31).monthPeriodContaining(d("2023-03-05"))
        assertEquals(d("2023-02-28"), p.start)
        assertEquals(d("2023-03-30"), p.end)
    }

    @Test
    fun weekendPreviousFridayKeepsPeriodsContiguous() {
        // 2024-06-01 is a Saturday: the start moves back to Friday 31 May.
        val e = PeriodEngine(1, WeekendRule.PREVIOUS_FRIDAY)
        val june = e.monthPeriodFor(YearMonth.of(2024, 6))
        assertEquals(d("2024-05-31"), june.start)
        val may = e.previous(june)
        assertEquals(d("2024-05-30"), may.end)
        assertEquals(YearMonth.of(2024, 5), may.label)
        assertEquals(june, e.monthPeriodContaining(d("2024-05-31")))
    }

    @Test
    fun weekendFollowingMonday() {
        val e = PeriodEngine(1, WeekendRule.FOLLOWING_MONDAY)
        val june = e.monthPeriodFor(YearMonth.of(2024, 6))
        assertEquals(d("2024-06-03"), june.start)
        assertEquals(d("2024-06-02"), e.previous(june).end)
    }

    @Test
    fun navigationAndYearRange() {
        val e = PeriodEngine(25)
        val year = e.yearPeriods(2024)
        assertEquals(12, year.size)
        assertEquals(d("2023-12-25"), year.first().start)
        assertEquals(d("2024-12-24"), year.last().end)
        for (i in 0 until 11) assertEquals(year[i].end.plusDays(1), year[i + 1].start)
    }

    @Test
    fun weekStartsOnConfiguredDay() {
        val w = PeriodEngine(1, weekStart = DayOfWeek.MONDAY).weekContaining(d("2024-05-19"))
        assertEquals(d("2024-05-13"), w.start)
        assertEquals(d("2024-05-19"), w.end)
    }
}
