package com.nexo.app.core.common.period

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

fun Long.toLocalDateTime(zone: ZoneId): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)

fun LocalDateTime.toEpochMillis(zone: ZoneId): Long = atZone(zone).toInstant().toEpochMilli()
