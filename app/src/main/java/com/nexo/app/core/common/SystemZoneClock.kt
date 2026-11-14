package com.nexo.app.core.common

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The system clock in the phone's current time zone. The zone is read on every call, unlike
 * [Clock.systemDefaultZone], so travelling across zones is picked up without a restart.
 */
object SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()

    override fun millis(): Long = System.currentTimeMillis()
}
