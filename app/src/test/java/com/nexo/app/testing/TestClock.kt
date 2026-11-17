package com.nexo.app.testing

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Noon on Saturday 14 November 2026 in Colombo.
val fixedClock: Clock = Clock.fixed(
    Instant.parse("2026-11-14T06:30:00Z"),
    ZoneId.of("Asia/Colombo"),
)

val fixedToday: LocalDate = LocalDate.now(fixedClock)
