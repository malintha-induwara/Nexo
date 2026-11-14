package com.nexo.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockRulesTest {
    private val immediately = 5_000L

    @Test
    fun leavingTheAppLocksPastTheLimit() {
        assertFalse(LockManager.shouldLock(4_000, immediately, handoff = false))
        assertTrue(LockManager.shouldLock(6_000, immediately, handoff = false))
    }

    @Test
    fun aTripToTheCameraDoesNotLock() {
        assertFalse(LockManager.shouldLock(45_000, immediately, handoff = true))
        assertFalse(LockManager.shouldLock(LockManager.HANDOFF_GRACE, immediately, handoff = true))
    }

    @Test
    fun aHandoffLeftTooLongStillLocks() {
        assertTrue(LockManager.shouldLock(LockManager.HANDOFF_GRACE + 1, immediately, handoff = true))
    }

    @Test
    fun aLongerTimeoutIsNeverShortenedByAHandoff() {
        val tenMinutes = 10 * 60_000L
        assertFalse(LockManager.shouldLock(8 * 60_000L, tenMinutes, handoff = true))
        assertTrue(LockManager.shouldLock(11 * 60_000L, tenMinutes, handoff = true))
    }
}
