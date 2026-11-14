package com.nexo.app.core.common.period

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Epoch millis as a local date-time in [zone]. */
fun Long.toLocalDateTime(zone: ZoneId): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)

/** This local date-time as epoch millis in [zone]. */
fun LocalDateTime.toEpochMillis(zone: ZoneId): Long = atZone(zone).toInstant().toEpochMilli()
