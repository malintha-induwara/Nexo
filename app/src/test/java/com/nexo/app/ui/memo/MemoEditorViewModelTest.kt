package com.nexo.app.ui.memo

import androidx.lifecycle.SavedStateHandle
import com.nexo.app.core.model.Memo
import com.nexo.app.testing.FakeMemosRepository
import com.nexo.app.testing.MainDispatcherRule
import com.nexo.app.testing.fixedClock
import com.nexo.app.ui.navigation.MemoEditorKey
import com.nexo.app.ui.navigation.NavResult
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MemoEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun newMemoIsSavedAndTheListScrollsToItsDate() = runTest {
        val memos = FakeMemosRepository()
        val vm =
            MemoEditorViewModel(
                MemoEditorKey(date = "2026-11-02"),
                memos,
                fixedClock,
                SavedStateHandle(),
            )
        vm.initialize()
        assertTrue(vm.uiState.value.loaded)
        assertEquals(LocalDate.of(2026, 11, 2), vm.uiState.value.date)

        vm.setTitle("  Rent  ")
        vm.setContent("Pay before the 5th")
        vm.saveAndClose().join()

        val saved = memos.current.single()
        assertEquals("Rent", saved.title)
        assertEquals("Pay before the 5th\n", saved.content)
        assertEquals("2026-11-02", saved.date)
        assertEquals(1, saved.orderSeq)
        assertTrue(vm.uiState.value.closed)
        assertEquals(
            listOf(NavResult.MemoSaved(saved.id), NavResult.Focus(LocalDate.of(2026, 11, 2))),
            vm.uiState.value.results,
        )
    }

    @Test
    fun emptyingAnExistingMemoDeletesIt() = runTest {
        val memos = FakeMemosRepository(listOf(memo()))
        val vm =
            MemoEditorViewModel(
                MemoEditorKey(memoId = "m1"),
                memos,
                fixedClock,
                SavedStateHandle(),
            )
        vm.initialize()
        assertEquals("Groceries", vm.uiState.value.title)
        assertEquals("milk", vm.uiState.value.content)

        vm.setTitle("")
        vm.setContent("  ")
        vm.saveAndClose().join()

        assertTrue(memos.current.isEmpty())
        assertTrue(vm.uiState.value.closed)
        assertTrue(vm.uiState.value.results.isEmpty())
    }

    @Test
    fun editingKeepsTheMemoIdAndOrder() = runTest {
        val memos = FakeMemosRepository(listOf(memo()))
        val vm =
            MemoEditorViewModel(
                MemoEditorKey(memoId = "m1"),
                memos,
                fixedClock,
                SavedStateHandle(),
            )
        vm.initialize()
        vm.setContent("milk, bread")
        vm.togglePin()
        vm.saveAndClose().join()

        val saved = memos.current.single()
        assertEquals("m1", saved.id)
        assertEquals(3, saved.orderSeq)
        assertEquals("milk, bread\n", saved.content)
        assertTrue(saved.pinned)
    }

    @Test
    fun closingIsHandledOnce() = runTest {
        val vm =
            MemoEditorViewModel(
                MemoEditorKey(),
                FakeMemosRepository(),
                fixedClock,
                SavedStateHandle(),
            )
        vm.initialize()
        vm.saveAndClose().join()
        assertTrue(vm.uiState.value.closed)
        vm.onClosed()
        assertFalse(vm.uiState.value.closed)
    }

    private fun memo() = Memo(
        id = "m1",
        title = "Groceries",
        content = "milk\n",
        date = "2026-11-01",
        dateTime = 0,
        orderSeq = 3,
        updatedAt = 0,
    )

    @Test
    fun aHalfWrittenMemoSurvivesTheProcessBeingKilled() = runTest {
        val saved = SavedStateHandle()
        val first =
            MemoEditorViewModel(
                MemoEditorKey(date = "2026-11-02"),
                FakeMemosRepository(),
                fixedClock,
                saved,
            )
        first.initialize()
        first.setTitle("Groceries")
        first.setContent("milk, eggs")

        val again = MemoEditorViewModel(
            MemoEditorKey(date = "2026-11-02"),
            FakeMemosRepository(),
            fixedClock,
            SavedStateHandle(mapOf("memo" to saved.get<String>("memo"))),
        )
        again.initialize()
        assertEquals("Groceries", again.uiState.value.title)
        assertEquals("milk, eggs", again.uiState.value.content)
    }
}
