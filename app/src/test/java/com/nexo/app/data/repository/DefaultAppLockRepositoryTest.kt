package com.nexo.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.data.lock.BiometricKeyDataSource
import com.nexo.app.data.lock.PinHasher
import com.nexo.app.testing.fixedClock
import com.nexo.app.testing.userPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultAppLockRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val prefs =
        userPreferencesRepository(ApplicationProvider.getApplicationContext<Context>())

    @After
    fun tearDown() {
        runBlocking { prefs.setPin(null) }
        scope.cancel()
    }

    private fun lock() = DefaultAppLockRepository(
        prefs,
        scope,
        BiometricKeyDataSource(),
        Dispatchers.Unconfined,
        fixedClock,
    )

    @Test
    fun withoutAPinTheAppStartsUnlocked() = runBlocking {
        prefs.setPin(null)
        assertFalse(lock().getLockedStream().first())
    }

    @Test
    fun withAPinTheAppStartsLocked() = runBlocking {
        prefs.setPin(PinHasher.hash("1234", iterations = 1))
        assertTrue(lock().getLockedStream().first())
    }

    @Test
    fun theCurrentPinIsRecognisedWithoutCountingFailures() = runBlocking {
        val lock = lock()
        lock.setPin("1234")
        assertTrue(lock.isCurrentPin("1234"))
        assertFalse(lock.isCurrentPin("0000"))
        assertTrue(prefs.getDeviceSettings().pinFailures == 0)
    }
}
