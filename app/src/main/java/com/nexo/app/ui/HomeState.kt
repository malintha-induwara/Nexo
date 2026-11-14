package com.nexo.app.ui

import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI state shared across screens: the Transactions tab's selected date and results handed from one screen to another.
 * Hand-offs are state, cleared by the screen that acts on them.
 */
@Singleton
class HomeState @Inject constructor(private val clock: Clock) {
    private val _selectedDate = MutableStateFlow(LocalDate.now(clock))
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _focus = MutableStateFlow<LocalDate?>(null)

    /** Date the Transactions tab scrolls to once, after a save; cleared by [focusConsumed]. */
    val focus: StateFlow<LocalDate?> = _focus.asStateFlow()

    private val _pickedFavorite = MutableStateFlow<String?>(null)

    /** Bookmark chosen from the star on the entry form; cleared by [pickedFavoriteConsumed]. */
    val pickedFavorite: StateFlow<String?> = _pickedFavorite.asStateFlow()

    private val _savedMemo = MutableStateFlow<String?>(null)

    /** Memo saved by the editor, for the highlight flash. */
    val savedMemo: StateFlow<String?> = _savedMemo.asStateFlow()

    private val _lastSaved = MutableStateFlow<String?>(null)

    /** Transaction just written by the entry form, so its row can light up when the list shows it. */
    val lastSaved: StateFlow<String?> = _lastSaved.asStateFlow()

    fun saved(id: String?) {
        _lastSaved.value = id
    }

    fun select(date: LocalDate) {
        _selectedDate.value = date
    }

    fun focusOn(date: LocalDate) {
        _selectedDate.value = date
        _focus.value = date
    }

    fun focusConsumed() {
        _focus.value = null
    }

    fun pickFavorite(id: String) {
        _pickedFavorite.value = id
    }

    fun pickedFavoriteConsumed() {
        _pickedFavorite.value = null
    }

    fun memoSaved(id: String?) {
        _savedMemo.value = id
    }
}
