package com.nexo.app.ui.designsystem.component

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Locale-aware date helpers used across screens. */
object Formats {
    private val locale: Locale get() = Locale.getDefault()

    fun shortDate(d: LocalDate): String = d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale))
    fun mediumDate(d: LocalDate): String = d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    fun weekdayShort(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    fun weekdayShort(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT, locale)
    fun weekdayFull(d: DayOfWeek): String = d.getDisplayName(TextStyle.FULL, locale)

    /** "5/17/24 (Fri)". */
    fun dateWithWeekday(d: LocalDate): String = "${shortDate(d)} (${weekdayShort(d)})"

    fun time(t: LocalTime, is24h: Boolean): String =
        t.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", locale))

    fun monthYear(m: YearMonth): String = m.format(DateTimeFormatter.ofPattern(bestPattern("MMMyyyy"), locale))
    fun monthShort(month: Int): String = java.time.Month.of(month).getDisplayName(TextStyle.SHORT, locale)
    fun monthFull(month: Int): String = java.time.Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, locale)

    /** "M.D" style. */
    fun monthDay(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern(bestPattern("Md"), locale))
    fun yearMonthDots(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern(if (monthFirst()) "MM.yyyy" else "yyyy.MM", locale))
    fun range(start: LocalDate, end: LocalDate): String = "${monthDay(start)} ~ ${monthDay(end)}"
    fun rangeFull(start: LocalDate, end: LocalDate): String = "${shortDate(start)} ~ ${shortDate(end)}"
    /** "Tue, Nov 10" (locale order), for day headers. */
    fun dayMonth(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern(bestPattern("MMMd"), locale))
    fun weekdayMonthDay(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern(bestPattern("MMMEd"), locale))
    fun dateTime(dt: LocalDateTime): String = dt.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
    fun isoDate(d: LocalDate): String = d.format(DateTimeFormatter.ISO_LOCAL_DATE)

    private fun bestPattern(skeleton: String): String =
        android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)

    private fun monthFirst(): Boolean {
        val p = bestPattern("yMd")
        return p.indexOf('M') < p.indexOf('y')
    }
}
