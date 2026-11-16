package com.nexo.app.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.data.lock.BiometricKeyDataSource
import com.nexo.app.data.repository.DefaultAppLockRepository
import com.nexo.app.testing.MainDispatcherRule
import com.nexo.app.testing.fixedClock
import com.nexo.app.testing.userPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LockSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val prefs =
        userPreferencesRepository(ApplicationProvider.getApplicationContext<Context>())
    private val lock = DefaultAppLockRepository(
        prefs,
        scope,
        BiometricKeyDataSource(Dispatchers.Unconfined),
        Dispatchers.Unconfined,
        fixedClock,
    )

    @After
    fun tearDown() {
        runBlocking { prefs.setPin(null) }
        scope.cancel()
    }

    @Test
    fun aTypedPinIsAnsweredThroughTheStateAndClearedOnceHandled() = runTest {
        lock.setPin("1234")
        val vm = LockSettingsViewModel(prefs, lock)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.checkPin("0000").join()
        val wrong = vm.uiState.first { it.pinCheck != null }.pinCheck!!
        assertEquals(false, wrong.correct)

        vm.onPinCheckHandled()
        assertNull(vm.uiState.first { it.pinCheck == null }.pinCheck)

        vm.checkPin("1234").join()
        val right = vm.uiState.first { it.pinCheck != null }.pinCheck!!
        assertEquals(true, right.correct)
        assertNotEquals(wrong.id, right.id)
        assertEquals(0, prefs.getDeviceSettings().pinFailures)
    }
}
