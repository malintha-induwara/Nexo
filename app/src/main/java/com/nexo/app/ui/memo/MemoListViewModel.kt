package com.nexo.app.ui.memo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.common.period.MonthPeriod
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.model.Memo
import com.nexo.app.core.model.RefData
import com.nexo.app.data.repository.BookSettingsRepository
import com.nexo.app.data.repository.MemosRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import com.nexo.app.ui.navigation.MemoListKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MemoListUiState(
    val ref: RefData = RefData(),
    val date: LocalDate = LocalDate.now(),
    val period: MonthPeriod? = null,
    val device: DeviceSettings? = null,
    val memos: List<Memo> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = MemoListViewModel.Factory::class)
class MemoListViewModel @AssistedInject constructor(
    @Assisted key: MemoListKey,
    private val memosRepository: MemosRepository,
    private val refs: ReferenceDataRepository,
    private val bookSettings: BookSettingsRepository,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: MemoListKey): MemoListViewModel
    }

    private val date = MutableStateFlow(key.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now())

    /** Kept for event handlers that need the reference data synchronously. */
    private val ref = refs.getRefDataStream().stateIn(viewModelScope, SharingStarted.Eagerly, RefData())

    private val memos = combine(ref, date) { r, d -> r.settings.periods.monthPeriodContaining(d) }.flatMapLatest { p ->
        memosRepository.getMemosStream(p.startText(), p.endText()).map { list ->
            list.filter { it.pinned }.sortedByDescending { it.pinnedAt } +
                list.filter { !it.pinned && LocalDate.parse(it.date) in p.range }.sortedWith(compareByDescending<Memo> { it.date }.thenByDescending { it.dateTime })
        }
    }

    val uiState: StateFlow<MemoListUiState> = combine(ref, date, prefs.getDeviceSettingsStream(), memos) { r, d, device, list ->
        MemoListUiState(r, d, r.settings.periods.monthPeriodContaining(d), device, list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemoListUiState())

    private fun MonthPeriod.startText() = range.startText
    private fun MonthPeriod.endText() = range.endText

    fun pickMonth(month: YearMonth) {
        date.value = ref.value.settings.periods.monthPeriodFor(month).end
    }

    fun step(forward: Boolean) {
        val e = ref.value.settings.periods
        val p = e.monthPeriodContaining(date.value)
        date.value = (if (forward) e.next(p) else e.previous(p)).end
    }

    fun setMemoButton(v: Boolean) = viewModelScope.launch { bookSettings.updateBookSettings { it.copy(memoButton = v) } }
    fun setHelpHidden(v: Boolean) = viewModelScope.launch { prefs.setMemoHelpHidden(v) }
}
