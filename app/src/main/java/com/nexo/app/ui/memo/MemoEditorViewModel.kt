package com.nexo.app.ui.memo

import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.common.period.toIsoText
import com.nexo.app.core.model.Memo
import com.nexo.app.core.model.MemoColor
import com.nexo.app.data.repository.MemosRepository
import com.nexo.app.ui.common.getJson
import com.nexo.app.ui.common.setJson
import com.nexo.app.ui.navigation.MemoEditorKey
import com.nexo.app.ui.navigation.NavResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

data class MemoEditorUiState(
    val loaded: Boolean = false,
    val id: String? = null,
    val title: String = "",
    val content: String = "",
    val date: LocalDate,
    val color: MemoColor = MemoColor.WHITE,
    val pinned: Boolean = false,
    val pinnedAt: Long = 0,
    /** The memo was saved or deleted: the screen goes back, then calls [MemoEditorViewModel.onClosed]. */
    val closed: Boolean = false,
    /** Handed to the screens under the editor as it closes. */
    val results: List<NavResult> = emptyList(),
)

/** What the user typed into the memo; the rest is loaded again from the nav key. */
@Serializable
private data class SavedMemo(
    val title: String,
    val content: String,
    val date: String,
    val color: MemoColor,
    val pinned: Boolean,
) {
    fun appliedTo(s: MemoEditorUiState) = s.copy(
        title = title,
        content = content,
        date = LocalDate.parse(date),
        color = color,
        pinned = pinned,
    )

    companion object {
        fun of(s: MemoEditorUiState) =
            SavedMemo(s.title, s.content, s.date.toString(), s.color, s.pinned)
    }
}

@HiltViewModel(assistedFactory = MemoEditorViewModel.Factory::class)
class MemoEditorViewModel @AssistedInject constructor(
    @Assisted private val key: MemoEditorKey,
    private val memos: MemosRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: MemoEditorKey): MemoEditorViewModel
    }

    private val _uiState = MutableStateFlow(MemoEditorUiState(date = LocalDate.now(clock)))
    val uiState: StateFlow<MemoEditorUiState> = _uiState.asStateFlow()
    private var original: Memo? = null
    private var initialized = false

    /** Loads the memo once; safe to call again. */
    @MainThread
    fun initialize() {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            val m = key.memoId?.let { memos.getMemo(it) }
            original = m
            _uiState.value = if (m != null) {
                MemoEditorUiState(
                    true,
                    m.id,
                    m.title,
                    m.content.trimEnd('\n'),
                    LocalDate.parse(m.date),
                    m.color,
                    m.pinned,
                    m.pinnedAt,
                )
            } else {
                MemoEditorUiState(
                    loaded = true,
                    date =
                        key.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                            ?: LocalDate.now(clock),
                )
            }
            // Back after the process was killed: the typed memo over the loaded one.
            savedState.getJson<SavedMemo>(SAVED_MEMO)?.let { saved ->
                _uiState.update { saved.appliedTo(it) }
            }
            _uiState.collect { savedState.setJson(SAVED_MEMO, SavedMemo.of(it)) }
        }
    }

    fun setTitle(v: String) = _uiState.update { it.copy(title = v) }
    fun setContent(v: String) = _uiState.update { it.copy(content = v) }
    fun setDate(d: LocalDate) = _uiState.update { it.copy(date = d) }
    fun setColor(c: MemoColor) = _uiState.update { it.copy(color = c) }
    fun togglePin() = _uiState.update {
        if (it.pinned) {
            it.copy(pinned = false, pinnedAt = 0)
        } else {
            it.copy(pinned = true, pinnedAt = clock.millis())
        }
    }

    /** Saved on back; an emptied existing memo is deleted. */
    fun saveAndClose() = viewModelScope.launch {
        val s = _uiState.value
        val empty = s.title.isBlank() && s.content.isBlank()
        val o = original
        var results = emptyList<NavResult>()
        when {
            empty && o != null -> memos.delete(o.id)

            empty -> Unit

            else -> {
                val now = clock.millis()
                val time =
                    o?.let {
                        Instant.ofEpochMilli(
                            it.dateTime.toLong(),
                        ).atZone(clock.zone).toLocalTime()
                    }
                        ?: LocalTime.now(clock)
                val row = Memo(
                    id = o?.id ?: UUID.randomUUID().toString(),
                    title = s.title.trim(),
                    content = if (s.content.isBlank()) "" else s.content.trimEnd() + "\n",
                    date = s.date.toIsoText(),
                    dateTime = s.date.atTime(
                        time,
                    ).atZone(clock.zone).toInstant().toEpochMilli(),
                    pinned = s.pinned,
                    pinnedAt = if (s.pinned) s.pinnedAt else 0,
                    orderSeq = o?.orderSeq ?: memos.nextOrder(),
                    color = s.color,
                    updatedAt = now,
                )
                memos.save(row)
                results = listOf(NavResult.MemoSaved(row.id), NavResult.Focus(s.date))
            }
        }
        _uiState.update { it.copy(closed = true, results = results) }
    }

    fun delete() = viewModelScope.launch {
        original?.let { memos.delete(it.id) }
        _uiState.update { it.copy(closed = true) }
    }

    fun onClosed() = _uiState.update { it.copy(closed = false, results = emptyList()) }

    private companion object {
        const val SAVED_MEMO = "memo"
    }
}
