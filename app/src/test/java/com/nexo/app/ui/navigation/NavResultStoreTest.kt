package com.nexo.app.ui.navigation

import androidx.compose.runtime.saveable.SaverScope
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavResultStoreTest {
    @Test
    fun aNewerResultReplacesTheOlderOneOfTheSameKind() {
        val store = NavResultStore()
        store.post(NavResult.TransactionSaved("a"))
        store.post(NavResult.Focus(LocalDate.of(2026, 11, 2)))
        store.post(NavResult.TransactionSaved("b"))

        assertEquals(
            listOf(NavResult.Focus(LocalDate.of(2026, 11, 2)), NavResult.TransactionSaved("b")),
            store.pending,
        )
        store.consume(NavResult.TransactionSaved("b"))
        assertEquals(listOf(NavResult.Focus(LocalDate.of(2026, 11, 2))), store.pending)
    }

    @Test
    fun pendingResultsSurviveBeingSavedAndRestored() {
        val store = NavResultStore()
        store.post(NavResult.MemoSaved("m1"))
        store.post(NavResult.Focus(LocalDate.of(2026, 10, 3)))
        store.post(NavResult.FavoritePicked("f1"))

        val saved = with(NavResultStore.Saver) { SaverScope { true }.save(store) }!!
        val restored = NavResultStore.Saver.restore(saved)!!

        assertEquals(store.pending.toList(), restored.pending.toList())
        assertEquals(LocalDate.of(2026, 10, 3), (restored.pending[1] as NavResult.Focus).date)
        assertTrue(NavResultStore().pending.isEmpty())
    }
}
