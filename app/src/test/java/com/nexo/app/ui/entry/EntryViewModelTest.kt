package com.nexo.app.ui.entry

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.R
import com.nexo.app.core.common.SystemZoneClock
import com.nexo.app.testing.MainDispatcherRule
import com.nexo.app.testing.TestBook
import com.nexo.app.ui.HomeState
import com.nexo.app.ui.designsystem.UiText
import com.nexo.app.ui.navigation.EntryKey
import java.math.BigDecimal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
class EntryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val home = HomeState(SystemZoneClock)
    private lateinit var book: TestBook

    @Before
    fun setUp() {
        book = TestBook(ApplicationProvider.getApplicationContext<Context>(), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        book.close()
    }

    private suspend fun TestScope.openForm(key: EntryKey = EntryKey()): EntryViewModel {
        val b = book
        val vm = EntryViewModel(
            key,
            b.refs,
            b.sms,
            b.transactions,
            b.favorites,
            b.repeats,
            b.prefs,
            home,
            b.photos,
            b.tags,
            b.importer,
            b.receipts,
            b.entryLoader,
            b.clock,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        vm.initialize(EntryTexts(paymentNote = "Card payment"))
        vm.uiState.first { it.form.loaded && it.ref.accountById.isNotEmpty() }
        return vm
    }

    @Test
    fun savingWithoutACategoryAsksForOne() = runTest {
        val vm = openForm()
        vm.selectAccount("1")
        vm.calculatorResult(BigDecimal("2450"))
        vm.save()

        val state = vm.uiState.value
        assertEquals(UiText.of(R.string.select_specific_category), state.userMessage?.text)
        assertEquals(Panel.CATEGORY, state.form.panel)
        assertNull(state.navigation)
        assertNull(home.lastSaved.value)
    }

    @Test
    fun aCompleteExpenseIsSavedAndTheFormCloses() = runTest {
        val vm = openForm()
        vm.selectAccount("1")
        vm.selectCategory("6")
        vm.calculatorResult(BigDecimal("2450"))
        vm.setNote("Lunch")
        vm.save()

        assertEquals(EntryNavigation.Close, vm.uiState.first { it.navigation != null }.navigation)
        val tx = book.transactions.getTransaction(home.lastSaved.value!!)!!
        assertEquals(2450.0, tx.amount, 0.0)
        assertEquals("1", tx.accountId)
        assertEquals("6", tx.categoryId)
        assertEquals("Lunch", tx.note)
        // The Transactions tab scrolls to the saved day once.
        assertEquals(vm.uiState.value.form.date, home.focus.value)

        vm.onNavigationHandled()
        assertNull(vm.uiState.value.navigation)
    }

    @Test
    fun saveAndContinueStartsAFreshForm() = runTest {
        val vm = openForm()
        vm.selectAccount("1")
        vm.selectCategory("6")
        vm.calculatorResult(BigDecimal("120"))
        vm.save(continueAfter = true)

        val state = vm.uiState.first { it.userMessage != null }
        assertEquals(UiText.of(R.string.saved), state.userMessage?.text)
        assertNull(state.form.amount)
        assertNull(state.navigation)
        assertTrue(home.lastSaved.value != null)
    }
}
