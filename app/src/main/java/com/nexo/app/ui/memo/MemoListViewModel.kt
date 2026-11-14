package com.nexo.app.ui.memo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    val date = MutableStateFlow(key.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now())
    val ref = refs.getRefDataStream().stateIn(viewModelScope, SharingStarted.Eagerly, com.nexo.app.core.model.RefData())
    val device = prefs.getDeviceSettingsStream().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val period = combine(ref, date) { r, d -> r.settings.periods.monthPeriodContaining(d) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val memos = combine(ref, date) { r, d -> r.settings.periods.monthPeriodContaining(d) }.flatMapLatest { p ->
        memosRepository.getMemosStream(p.startText(), p.endText()).map { list ->
            list.filter { it.pinned }.sortedByDescending { it.pinnedAt } +
                list.filter { !it.pinned && LocalDate.parse(it.date) in p.range }.sortedWith(compareByDescending<com.nexo.app.data.database.entity.MemoEntity> { it.date }.thenByDescending { it.dateTime })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun com.nexo.app.core.common.period.MonthPeriod.startText() = range.startText
    private fun com.nexo.app.core.common.period.MonthPeriod.endText() = range.endText

    fun step(forward: Boolean) {
        val e = ref.value.settings.periods
        val p = e.monthPeriodContaining(date.value)
        date.value = (if (forward) e.next(p) else e.previous(p)).end
    }

    fun setMemoButton(v: Boolean) = viewModelScope.launch { bookSettings.updateBookSettings { it.copy(memoButton = v) } }
    fun setHelpHidden(v: Boolean) = viewModelScope.launch { prefs.setMemoHelpHidden(v) }
}
