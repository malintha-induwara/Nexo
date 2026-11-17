package com.nexo.app.core.common

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

// Reads the zone on every call, unlike Clock.systemDefaultZone(), so time-zone changes apply
// without a restart.
object SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()

    override fun millis(): Long = System.currentTimeMillis()
}
