package com.nexo.app.data.sms.parser

import com.nexo.app.core.model.Banks
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

/** The transaction time written in the body, read as Sri Lankan time (UTC+05:30). */
object BodyDate {
    val ZONE: ZoneId = Banks.ZONE
    private const val WINDOW_MS = 3L * 24 * 60 * 60 * 1000

    private val dmy = Regex("""(\d{2})/(\d{2})/(\d{2})""")
    private val hm12 = Regex("""(\d{1,2}):(\d{2})\s*([AP])M""", RegexOption.IGNORE_CASE)
    private val ymd = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    private val hms = Regex("""(\d{2}):(\d{2}):(\d{2})""")

    /** `dd/mm/yy` + `hh:mm AM` (card messages). Never US order. */
    fun card(date: String, time: String): Long? = runCatching {
        val d = dmy.matchEntire(date)!!.groupValues
        val t = hm12.matchEntire(time.trim())!!.groupValues
        val hour12 = t[1].toInt() % 12
        val hour = if (t[3].equals("P", ignoreCase = true)) hour12 + 12 else hour12
        epoch(LocalDateTime.of(2000 + d[3].toInt(), d[2].toInt(), d[1].toInt(), hour, t[2].toInt()))
    }.getOrNull()

    /** `yyyy-mm-dd` + `hh:mm:ss` (fund transfers). */
    fun iso(date: String, time: String): Long? = runCatching {
        val d = ymd.matchEntire(date)!!.groupValues
        val t = hms.matchEntire(time)!!.groupValues
        epoch(
            LocalDateTime.of(
                d[1].toInt(),
                d[2].toInt(),
                d[3].toInt(),
                t[1].toInt(),
                t[2].toInt(),
                t[3].toInt(),
            ),
        )
    }.getOrNull()

    /** Use the body time when it lies within ±3 days of the SMS time, otherwise the SMS time. */
    fun choose(body: Long?, sms: Long): Long = if (body != null && abs(body - sms) <= WINDOW_MS) {
        body
    } else {
        sms
    }

    private fun epoch(dt: LocalDateTime): Long = dt.atZone(ZONE).toInstant().toEpochMilli()
}
