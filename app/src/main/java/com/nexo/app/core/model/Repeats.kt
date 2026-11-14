package com.nexo.app.core.model

import com.nexo.app.core.common.period.DateRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Next-date rules. Month steps are anchored to [anchorDay] so the 29th–31st no longer drift. */
object RepeatSchedule {
    fun next(previous: LocalDate, type: RepeatType, anchorDay: Int): LocalDate? = when (type) {
        RepeatType.NONE -> null

        RepeatType.DAILY -> previous.plusDays(1)

        RepeatType.WEEKLY -> previous.plusDays(7)

        RepeatType.BIWEEKLY -> previous.plusDays(14)

        RepeatType.FOUR_WEEKLY -> previous.plusDays(28)

        RepeatType.END_OF_MONTH -> previous.plusDays(1).with(TemporalAdjusters.lastDayOfMonth())

        RepeatType.WEEKDAYS -> {
            val d = previous.plusDays(1)
            when (d.dayOfWeek) {
                DayOfWeek.SATURDAY -> d.plusDays(2)
                DayOfWeek.SUNDAY -> d.plusDays(1)
                else -> d
            }
        }

        RepeatType.WEEKENDS -> {
            val d = previous.plusDays(1)
            if (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) {
                d
            } else {
                d.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
            }
        }

        else -> {
            val step = type.monthStep ?: return null
            val shifted = previous.withDayOfMonth(1).plusMonths(step.toLong())
            shifted.withDayOfMonth(minOf(anchorDay.coerceIn(1, 31), shifted.lengthOfMonth()))
        }
    }

    /** Date snapping when a repeat type is chosen on the form. */
    fun snap(date: LocalDate, type: RepeatType): LocalDate = when (type) {
        RepeatType.END_OF_MONTH -> date.with(TemporalAdjusters.lastDayOfMonth())

        RepeatType.WEEKDAYS -> when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> date.plusDays(2)
            DayOfWeek.SUNDAY -> date.plusDays(1)
            else -> date
        }

        RepeatType.WEEKENDS -> if (date.dayOfWeek == DayOfWeek.SATURDAY ||
            date.dayOfWeek == DayOfWeek.SUNDAY
        ) {
            date
        } else {
            date.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
        }

        else -> date
    }

    /** Oldest allowed start date for a new repeat. */
    fun oldestAllowed(type: RepeatType, today: LocalDate): LocalDate = when (type) {
        RepeatType.DAILY, RepeatType.WEEKDAYS -> today.minusMonths(1)
        RepeatType.SEMIANNUAL, RepeatType.ANNUAL -> today.minusMonths(18)
        else -> today.minusMonths(5)
    }

    /** Occurrences from [first] (inclusive) that fall inside [range], respecting [endDate]. */
    fun occurrencesIn(
        first: LocalDate,
        type: RepeatType,
        anchorDay: Int,
        range: DateRange,
        endDate: LocalDate?,
        limit: Int = 10_000,
    ): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var d: LocalDate? = if (type == RepeatType.END_OF_MONTH) {
            first.with(TemporalAdjusters.lastDayOfMonth())
        } else {
            first
        }
        var steps = 0
        while (d != null && !d.isAfter(range.end) && steps < limit) {
            if (endDate != null && d.isAfter(endDate)) break
            if (!d.isBefore(range.start)) out += d
            d = next(d, type, anchorDay)
            steps++
        }
        return out
    }
}

/** Horizon of the materialisation engine. */
data class RepeatWindow(val lowerExclusive: LocalDate, val horizonExclusive: LocalDate) {
    companion object {
        fun of(
            today: LocalDate,
            firstOfPeriod: Boolean,
            currentPeriodEnd: LocalDate,
        ): RepeatWindow {
            val horizon = if (firstOfPeriod) currentPeriodEnd.plusDays(1) else today.plusDays(1)
            return RepeatWindow(horizon.minusDays(700), horizon)
        }
    }
}
