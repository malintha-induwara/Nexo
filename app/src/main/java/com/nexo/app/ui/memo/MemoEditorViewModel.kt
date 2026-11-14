package com.nexo.app.ui.memo

import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.common.period.toIsoText
import com.nexo.app.core.model.Memo
import com.nexo.app.core.model.MemoColor
import com.nexo.app.data.repository.MemosRepository
import com.nexo.app.ui.HomeState
import com.nexo.app.ui.navigation.MemoEditorKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemoEditorUiState(
    val loaded: Boolean = false,
    val id: String? = null,
    val title: String = "",
    val content: String = "",
    val date: LocalDate = LocalDate.now(),
    val color: MemoColor = MemoColor.WHITE,
    val pinned: Boolean = false,
    val pinnedAt: Long = 0,
    /** The memo was saved or deleted: the screen goes back, then calls [MemoEditorViewModel.onClosed]. */
    val closed: Boolean = false,
)

@HiltViewModel(assistedFactory = MemoEditorViewModel.Factory::class)
class MemoEditorViewModel @AssistedInject constructor(
    @Assisted private val key: MemoEditorKey,
    private val memos: MemosRepository,
    private val home: HomeState,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: MemoEditorKey): MemoEditorViewModel
    }

    private val _uiState = MutableStateFlow(MemoEditorUiState())
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
                            ?: LocalDate.now(),
                )
            }
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
            it.copy(pinned = true, pinnedAt = System.currentTimeMillis())
        }
    }

    /** Saved on back; an emptied existing memo is deleted. */
    fun saveAndClose() = viewModelScope.launch {
        val s = _uiState.value
        val empty = s.title.isBlank() && s.content.isBlank()
        val o = original
        when {
            empty && o != null -> memos.delete(o.id)

            empty -> Unit

            else -> {
                val now = System.currentTimeMillis()
                val time =
                    o?.let {
                        Instant.ofEpochMilli(
                            it.dateTime.toLong(),
                        ).atZone(ZoneId.systemDefault()).toLocalTime()
                    }
                        ?: LocalTime.now()
                val row = Memo(
                    id = o?.id ?: UUID.randomUUID().toString(),
                    title = s.title.trim(),
                    content = if (s.content.isBlank()) "" else s.content.trimEnd() + "\n",
                    date = s.date.toIsoText(),
                    dateTime = s.date.atTime(
                        time,
                    ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    pinned = s.pinned,
                    pinnedAt = if (s.pinned) s.pinnedAt else 0,
                    orderSeq = o?.orderSeq ?: memos.nextOrder(),
                    color = s.color,
                    updatedAt = now,
                )
                memos.save(row)
                home.memoSaved(row.id)
                home.focusOn(s.date)
            }
        }
        _uiState.update { it.copy(closed = true) }
    }

    fun delete() = viewModelScope.launch {
        original?.let { memos.delete(it.id) }
        _uiState.update { it.copy(closed = true) }
    }

    fun onClosed() = _uiState.update { it.copy(closed = false) }
}
