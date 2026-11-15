package com.nexo.app.ui.entry

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.R
import com.nexo.app.testing.MainDispatcherRule
import com.nexo.app.testing.TestBook
import com.nexo.app.ui.designsystem.UiText
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.NavResult
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

    private suspend fun TestScope.openForm(
        key: EntryKey = EntryKey(),
        saved: SavedStateHandle = SavedStateHandle(),
    ): EntryViewModel {
        val b = book
        val vm = EntryViewModel(
            key,
            b.refs,
            b.sms,
            b.transactions,
            b.favorites,
            b.repeats,
            b.prefs,
            b.photos,
            b.tags,
            b.receipts,
            b.entryLoader,
            b.saveEntry,
            b.getNoteSuggestions,
            b.clock,
            saved,
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
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun aCompleteExpenseIsSavedAndTheFormCloses() = runTest {
        val vm = openForm()
        vm.selectAccount("1")
        vm.selectCategory("6")
        vm.calculatorResult(BigDecimal("2450"))
        vm.setNote("Lunch")
        vm.save()

        val closed = vm.uiState.first { it.navigation != null }
        assertEquals(EntryNavigation.Close, closed.navigation)
        val id = closed.results.filterIsInstance<NavResult.TransactionSaved>().single().id
        val tx = book.transactions.getTransaction(id)!!
        assertEquals(2450.0, tx.amount, 0.0)
        assertEquals("1", tx.accountId)
        assertEquals("6", tx.categoryId)
        assertEquals("Lunch", tx.note)
        // The Transactions tab scrolls to the saved day once.
        assertTrue(NavResult.Focus(closed.form.date) in closed.results)

        vm.onResultsDelivered(closed.results)
        vm.onNavigationHandled()
        assertNull(vm.uiState.value.navigation)
        assertTrue(vm.uiState.value.results.isEmpty())
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
        assertTrue(state.results.single() is NavResult.TransactionSaved)
    }

    @Test
    fun typedInputSurvivesTheProcessBeingKilled() = runTest {
        val saved = SavedStateHandle()
        val first = openForm(saved = saved)
        first.selectAccount("1")
        first.calculatorResult(BigDecimal("980"))
        first.setNote("Bus fare")
        first.setTags(listOf("x"))
        first.uiState.first { it.form.note == "Bus fare" }

        // A new ViewModel gets only what the system kept.
        val restored = SavedStateHandle(mapOf("entryForm" to saved.get<String>("entryForm")))
        val form = openForm(saved = restored).uiState.first { it.form.note == "Bus fare" }.form
        assertEquals("1", form.accountId)
        assertEquals(980.0, form.amount)
        assertEquals(listOf("x"), form.tagIds)
        assertEquals(Panel.NONE, form.panel)
    }
}
