package com.nexo.app.core.common.period

import com.nexo.app.core.model.WeekendRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

// Both ends inclusive.
data class DateRange(val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
    val startText: String get() = start.toIsoText()
    val endText: String get() = end.toIsoText()
    val dayCount: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
    fun days(): List<LocalDate> = (0 until dayCount).map { start.plusDays(it.toLong()) }

    companion object {
        val ALL = DateRange(LocalDate.of(1970, 1, 1), LocalDate.of(2099, 12, 31))
    }
}

// [label] is the calendar month the period is named after.
data class MonthPeriod(val range: DateRange, val label: YearMonth) {
    val start: LocalDate get() = range.start
    val end: LocalDate get() = range.end

    // Budget month key: year * 100 + month.
    val key: Int get() = label.year * 100 + label.monthValue
}

fun LocalDate.toIsoText(): String = format(DateTimeFormatter.ISO_LOCAL_DATE)
fun String.toLocalDate(): LocalDate = LocalDate.parse(this)

// [startDay] is the day accounting months start on (1–31).
class PeriodEngine(
    startDay: Int,
    private val weekendRule: WeekendRule = WeekendRule.NONE,
    private val weekStart: DayOfWeek = DayOfWeek.SUNDAY,
) {
    private val s: Int = if (startDay in 1..31) startDay else 1

    private fun dayIn(month: YearMonth, day: Int): LocalDate =
        month.atDay(minOf(day, month.lengthOfMonth()))

    private fun nominalStart(d: LocalDate): LocalDate {
        if (s == 1) return d.withDayOfMonth(1)
        val month = if (d.dayOfMonth >= s) YearMonth.from(d) else YearMonth.from(d).minusMonths(1)
        return dayIn(month, s)
    }

    private fun nominalEnd(d: LocalDate): LocalDate {
        if (s == 1) return d.with(TemporalAdjusters.lastDayOfMonth())
        val month = if (d.dayOfMonth < s) YearMonth.from(d) else YearMonth.from(d).plusMonths(1)
        return dayIn(month, s).minusDays(1)
    }

    private fun adjustStart(start: LocalDate): LocalDate = when (weekendRule) {
        WeekendRule.NONE -> start

        WeekendRule.PREVIOUS_FRIDAY -> when (start.dayOfWeek) {
            DayOfWeek.SATURDAY -> start.minusDays(1)
            DayOfWeek.SUNDAY -> start.minusDays(2)
            else -> start
        }

        WeekendRule.FOLLOWING_MONDAY -> when (start.dayOfWeek) {
            DayOfWeek.SATURDAY -> start.plusDays(2)
            DayOfWeek.SUNDAY -> start.plusDays(1)
            else -> start
        }
    }

    private fun adjustEnd(end: LocalDate): LocalDate = when (weekendRule) {
        WeekendRule.NONE -> end

        WeekendRule.PREVIOUS_FRIDAY -> when (end.dayOfWeek) {
            DayOfWeek.FRIDAY -> end.minusDays(1)
            DayOfWeek.SATURDAY -> end.minusDays(2)
            else -> end
        }

        WeekendRule.FOLLOWING_MONDAY -> when (end.dayOfWeek) {
            DayOfWeek.FRIDAY -> end.plusDays(2)
            DayOfWeek.SATURDAY -> end.plusDays(1)
            else -> end
        }
    }

    private fun rawPeriod(d: LocalDate): DateRange =
        DateRange(adjustStart(nominalStart(d)), adjustEnd(nominalEnd(d)))

    fun monthPeriodContaining(date: LocalDate): MonthPeriod {
        var range = rawPeriod(date)
        if (date.isBefore(range.start)) {
            range = rawPeriod(range.start.minusDays(10))
        } else if (date.isAfter(range.end)) {
            range = rawPeriod(range.end.plusDays(10))
        }
        return MonthPeriod(range, labelOf(range))
    }

    private fun labelOf(range: DateRange): YearMonth = if (s < 20) {
        val start = range.start
        if (start.dayOfMonth > 25) YearMonth.from(start).plusMonths(1) else YearMonth.from(start)
    } else {
        val end = range.end
        if (end.dayOfMonth < 15) YearMonth.from(end).minusMonths(1) else YearMonth.from(end)
    }

    fun next(period: MonthPeriod): MonthPeriod = monthPeriodContaining(period.end.plusDays(10))
    fun previous(period: MonthPeriod): MonthPeriod =
        monthPeriodContaining(period.start.minusDays(10))

    fun monthPeriodFor(month: YearMonth): MonthPeriod {
        val base = if (s != 1 && s >= 20) month.minusMonths(1) else month
        val probe = base.atDay(1).plusDays((s + 10 - 1).toLong())
        val period = monthPeriodContaining(probe)
        // Guard against rare weekend-rule edges: walk until the label matches.
        return when {
            period.label == month -> period
            period.label.isBefore(month) -> next(period)
            else -> previous(period)
        }
    }

    fun yearPeriods(year: Int): List<MonthPeriod> =
        (1..12).map { monthPeriodFor(YearMonth.of(year, it)) }

    fun yearRange(year: Int): DateRange = DateRange(
        monthPeriodFor(YearMonth.of(year, 1)).start,
        monthPeriodFor(YearMonth.of(year, 12)).end,
    )

    fun yearOf(date: LocalDate): Int = monthPeriodContaining(date).label.year

    fun weekContaining(date: LocalDate): DateRange {
        val start = date.with(TemporalAdjusters.previousOrSame(weekStart))
        return DateRange(start, start.plusDays(6))
    }

    val weekStartDay: DayOfWeek get() = weekStart
    val monthStartDay: Int get() = s
}
