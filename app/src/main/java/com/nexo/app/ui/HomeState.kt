package com.nexo.app.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Cross-screen UI state: the selected date of the Trans. tab and one-shot results between screens. */
@Singleton
class HomeState @Inject constructor() {
    private val _selected = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selected.asStateFlow()

    /** Date to jump to after a save. */
    private val _focus = MutableSharedFlow<LocalDate>(extraBufferCapacity = 4)
    val focus: SharedFlow<LocalDate> = _focus.asSharedFlow()

    /** Bookmark chosen from the star on the entry form. */
    private val _pickedFavorite = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val pickedFavorite: SharedFlow<String> = _pickedFavorite.asSharedFlow()

    /** Memo saved by the editor, for the highlight flash. */
    private val _savedMemo = MutableStateFlow<String?>(null)
    val savedMemo: StateFlow<String?> = _savedMemo.asStateFlow()

    /** Transaction just written by the entry form, so its row can light up when the list shows it. */
    private val _lastSaved = MutableStateFlow<String?>(null)
    val lastSaved: StateFlow<String?> = _lastSaved.asStateFlow()

    fun saved(id: String?) {
        _lastSaved.value = id
    }

    fun select(date: LocalDate) {
        _selected.value = date
    }

    fun focusOn(date: LocalDate) {
        _selected.value = date
        _focus.tryEmit(date)
    }

    fun pickFavorite(id: String) {
        _pickedFavorite.tryEmit(id)
    }

    fun memoSaved(id: String?) {
        _savedMemo.value = id
    }
}
