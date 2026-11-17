package com.nexo.app.ui.common

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import com.nexo.app.ui.navigation.StatsScope
import com.nexo.app.ui.stats.StatsQuery
import com.nexo.app.ui.transactionlist.FilterCriteria
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SavedStateFlowTest {
    private val day = LocalDate.of(2026, 11, 14)

    // The handle a new process gets back from what the old one saved.
    private fun SavedStateHandle.afterProcessDeath() =
        SavedStateHandle.createHandle(savedStateProvider().saveState(), null)

    @Test
    fun aScreensPlaceComesBackAfterTheProcessIsKilled() {
        val handle = SavedStateHandle()
        val query = handle.savedStateFlow("query", StatsQuery(anchor = day))
        val filter = handle.savedStateFlow("filter", FilterCriteria())
        val selection = handle.savedStateFlow("selection", emptySet<String>())
        val date = handle.savedStateFlow("date", day)
        val month = handle.savedStateFlow("month", YearMonth.of(2026, 11))
        val tab = handle.savedStateFlow<StatsScope?>("tab", null)

        query.value = StatsQuery(StatsScope.PERIOD, day.plusDays(3), day.minusDays(7), day)
        filter.value = FilterCriteria(expenseAll = true, accounts = setOf("1", "11"))
        selection.value = setOf("a", "b")
        date.value = day.plusMonths(1)
        month.value = YearMonth.of(2027, 1)
        tab.value = StatsScope.WEEKLY

        val restored = handle.afterProcessDeath()
        assertEquals(query.value, restored.savedStateFlow("query", StatsQuery(anchor = day)).value)
        assertEquals(filter.value, restored.savedStateFlow("filter", FilterCriteria()).value)
        assertEquals(
            setOf("a", "b"),
            restored.savedStateFlow("selection", emptySet<String>()).value,
        )
        assertEquals(day.plusMonths(1), restored.savedStateFlow("date", day).value)
        assertEquals(
            YearMonth.of(2027, 1),
            restored.savedStateFlow("month", YearMonth.of(2026, 11)).value,
        )
        assertEquals(StatsScope.WEEKLY, restored.savedStateFlow<StatsScope?>("tab", null).value)
    }

    @Test
    fun aFreshScreenStartsFromItsInitialValue() {
        val handle = SavedStateHandle()

        assertEquals(day, handle.savedStateFlow("date", day).value)
        assertNull(handle.savedStateFlow<StatsScope?>("tab", null).value)
    }

    @Test
    fun aSavedValueThatNoLongerDecodesIsDropped() {
        val handle = SavedStateHandle(
            mapOf<String, Any?>("query" to bundleOf(SAVED_JSON to "{\"scope\":\"GONE\"}")),
        )

        assertEquals(
            StatsQuery(anchor = day),
            handle.savedStateFlow("query", StatsQuery(anchor = day)).value,
        )
    }

    @Test
    fun aSnapshotIsTakenOnlyWhenTheStateIsSaved() {
        val handle = SavedStateHandle()
        var taken = 0
        handle.restoreAndSave("place") {
            taken++
            "here"
        }
        assertEquals(0, taken)

        val saved: Bundle = handle.savedStateProvider().saveState()

        assertEquals(1, taken)
        assertEquals(
            "here",
            SavedStateHandle.createHandle(saved, null).restoreAndSave("place") {
                ""
            },
        )
    }
}
