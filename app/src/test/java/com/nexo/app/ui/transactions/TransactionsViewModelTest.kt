package com.nexo.app.ui.transactions

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.TxDraft
import com.nexo.app.testing.MainDispatcherRule
import com.nexo.app.testing.TestBook
import com.nexo.app.testing.fixedClock
import com.nexo.app.testing.fixedToday
import java.time.LocalDate
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransactionsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var book: TestBook

    @Before
    fun setUp() {
        book =
            TestBook(
                ApplicationProvider.getApplicationContext<Context>(),
                scope,
                clock = fixedClock,
            )
    }

    @After
    fun tearDown() {
        scope.cancel()
        book.close()
    }

    private suspend fun TestScope.openTab(
        saved: SavedStateHandle = SavedStateHandle(),
    ): TransactionsViewModel {
        val b = book
        val vm =
            TransactionsViewModel(
                b.refs,
                b.memos,
                b.repeats,
                b.transactions,
                b.budgets,
                b.clock,
                saved,
            )
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { it.tab != null && it.data.loaded }
        return vm
    }

    private suspend fun saveLunch(): String = book.transactions.save(
        TxDraft(
            type = EntryType.EXPENSE,
            accountId = "1",
            categoryId = "6",
            dateTime = fixedToday.atTime(0, 1),
            amount = 2450.0,
            currencyId = "LKR",
            note = "Lunch",
        ),
    )

    @Test
    fun aSavedExpenseShowsUpInTheDailyList() = runTest {
        val vm = openTab()
        val id = saveLunch()

        val state = vm.uiState.first { s -> s.data.days.any { d -> d.rows.any { it.id == id } } }
        assertEquals(fixedToday, state.data.days.single().date)
        assertEquals(2450.0, state.data.totals.expense, 0.0)
    }

    @Test
    fun selectionLivesInTheUiState() = runTest {
        val vm = openTab()
        val id = saveLunch()
        vm.toggleSelect(id)
        assertEquals(setOf(id), vm.uiState.value.selection)
        vm.clearSelection()
        assertTrue(vm.uiState.value.selection.isEmpty())
    }

    @Test
    fun aSaveFromTheEntryFormIsHighlightedAndFocused() = runTest {
        val vm = openTab()
        vm.onTransactionSaved("tx1")
        vm.focusOn(LocalDate.of(2026, 11, 2))
        assertEquals("tx1", vm.uiState.value.lastSaved)
        assertEquals(LocalDate.of(2026, 11, 2), vm.uiState.value.focusDate)
        assertEquals(LocalDate.of(2026, 11, 2), vm.uiState.value.selectedDate)

        vm.onFocusHandled()
        vm.clearLastSaved()
        assertEquals(null, vm.uiState.value.focusDate)
        assertEquals(null, vm.uiState.value.lastSaved)
    }

    @Test
    fun theSelectedDaySurvivesTheProcessBeingKilled() = runTest {
        val saved = SavedStateHandle()
        openTab(saved).select(LocalDate.of(2026, 10, 3))

        // A new ViewModel gets only what the system kept.
        val restored = SavedStateHandle(mapOf("selectedDate" to saved.get<String>("selectedDate")))
        assertEquals(LocalDate.of(2026, 10, 3), openTab(restored).uiState.value.selectedDate)
    }
}
