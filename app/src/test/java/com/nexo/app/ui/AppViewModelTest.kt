package com.nexo.app.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.data.database.BookSeeder
import com.nexo.app.data.file.AppDirs
import com.nexo.app.data.lock.BiometricKeyDataSource
import com.nexo.app.data.repository.DefaultAppLockRepository
import com.nexo.app.data.repository.DefaultBookRepository
import com.nexo.app.data.repository.PinResult
import com.nexo.app.testing.MainDispatcherRule
import com.nexo.app.testing.TestBook
import com.nexo.app.testing.fixedClock
import com.nexo.app.ui.navigation.BookmarksKey
import com.nexo.app.ui.navigation.SmsQueueKey
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var context: Context
    private lateinit var book: TestBook
    private lateinit var lock: DefaultAppLockRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        book = TestBook(context, scope, clock = fixedClock)
        lock = DefaultAppLockRepository(
            book.prefs,
            scope,
            BiometricKeyDataSource(Dispatchers.Unconfined),
            Dispatchers.Unconfined,
            fixedClock,
        )
    }

    @After
    fun tearDown() {
        runBlocking { book.prefs.setPin(null) }
        scope.cancel()
        book.close()
    }

    private fun app(saved: SavedStateHandle = SavedStateHandle()) = AppViewModel(
        book.prefs,
        book.refs,
        lock,
        DefaultBookRepository(
            book.db,
            BookSeeder(context, book.db, fixedClock),
            AppDirs(context),
            book.prefs,
            lock,
            Dispatchers.IO,
            scope,
            fixedClock,
        ),
        saved,
    )

    /** The handle a new process gets back from what the old one saved. */
    private fun SavedStateHandle.afterProcessDeath() =
        SavedStateHandle.createHandle(savedStateProvider().saveState(), null)

    @Test
    fun aLaunchTargetWaitsUntilItIsOpened() = runTest {
        val vm = app()
        backgroundScope.launch { vm.uiState.collect {} }
        val keys = listOf(BookmarksKey())

        vm.setPending(keys)
        assertEquals(keys, vm.uiState.first { it.pending.isNotEmpty() }.pending)
        assertFalse(vm.launchHandled)

        vm.onPendingHandled()
        assertTrue(vm.uiState.first { it.pending.isEmpty() }.pending.isEmpty())
        assertTrue(vm.launchHandled)
    }

    @Test
    fun aTargetStillWaitingIsRoutedAgainAfterTheProcessIsKilled() {
        val saved = SavedStateHandle()
        app(saved).setPending(listOf(SmsQueueKey))

        assertFalse(app(saved.afterProcessDeath()).launchHandled)
    }

    @Test
    fun aTargetAlreadyOpenedIsNotRoutedAgainAfterTheProcessIsKilled() {
        val saved = SavedStateHandle()
        val vm = app(saved)
        vm.setPending(listOf(SmsQueueKey))
        vm.onPendingHandled()

        assertTrue(app(saved.afterProcessDeath()).launchHandled)
    }

    @Test
    fun aWrongPinIsReportedOnceAndTheRightOneUnlocks() = runTest {
        lock.setPin("1234")
        lock.lockNow()
        val vm = app()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { it.locked }

        vm.submitPin("0000")
        val attempt = vm.uiState.first { it.lock.pinAttempt != null }.lock.pinAttempt
        assertTrue(attempt?.result is PinResult.Wrong)

        vm.onPinAttemptShown()
        assertNull(vm.uiState.first { it.lock.pinAttempt == null }.lock.pinAttempt)

        vm.submitPin("1234")
        val unlocked = vm.uiState.first { !it.locked }
        assertNull(unlocked.lock.pinAttempt)
    }
}
