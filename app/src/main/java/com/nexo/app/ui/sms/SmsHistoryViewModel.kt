package com.nexo.app.ui.sms

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.model.FeeChoice
import com.nexo.app.core.model.FeeRoute
import com.nexo.app.core.model.HistoryRow
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.SmsHistoryImport
import com.nexo.app.core.model.SmsItem
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.needsDetails
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.SmsInboxRepository
import com.nexo.app.data.repository.SmsRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import com.nexo.app.domain.sms.ApproveResult
import com.nexo.app.domain.sms.ApproveSmsUseCase
import com.nexo.app.domain.sms.FeePlan
import com.nexo.app.domain.sms.GetSmsFeePlanUseCase
import com.nexo.app.ui.common.LocalDateSerializer
import com.nexo.app.ui.common.restoreAndSave
import com.nexo.app.ui.designsystem.UiText
import com.nexo.app.ui.designsystem.joinToUiText
import com.nexo.app.ui.navigation.SmsHistoryKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
enum class HistoryRange(val label: Int, val days: Long?) {
    WEEK(R.string.sms_history_range_7, 7),
    MONTH(R.string.sms_history_range_30, 30),
    QUARTER(R.string.sms_history_range_90, 90),
    CUSTOM(R.string.sms_history_range_custom, null),
    GAP(R.string.sms_history_range_gap, null),
}

@Serializable
enum class HistoryKind(val label: Int) {
    ALL(R.string.sms_history_kind_all),
    PURCHASES(R.string.sms_history_kind_purchases),
    CASH(R.string.sms_history_kind_cash),
    TRANSFERS(R.string.sms_history_kind_transfers),
    ;

    fun matches(kind: SmsKind) = when (this) {
        ALL -> true
        PURCHASES -> kind == SmsKind.EXPENSE || kind == SmsKind.INCOME
        CASH -> kind == SmsKind.TRANSFER_TO_CASH
        TRANSFERS -> kind.isTransfer && kind != SmsKind.TRANSFER_TO_CASH
    }
}

data class HistoryResult(val added: Int, val toQueue: Int) {
    // Null when there is nothing to report.
    val message: UiText?
        get() = buildList {
            if (added > 0) add(UiText.plural(R.plurals.sms_history_added, added))
            if (toQueue > 0) add(UiText.plural(R.plurals.sms_history_to_queue, toQueue))
        }.takeIf { it.isNotEmpty() }?.joinToUiText(" · ")
}

data class SmsHistoryUiState(
    val ref: RefData = RefData(),
    // Opened from the re-enable hint, so the missed stretch is one of the ranges.
    val gap: Boolean = false,
    val range: HistoryRange = HistoryRange.MONTH,
    val custom: Pair<LocalDate, LocalDate>,
    // Null while reading the inbox.
    val rows: List<HistoryRow>? = null,
    val permitted: Boolean = false,
    val kind: HistoryKind = HistoryKind.ALL,
    val card: String? = null,
    val visible: List<HistoryRow> = emptyList(),
    val selection: Set<String> = emptySet(),
    val adding: Boolean = false,
    // One fee question at a time; the rest wait their turn.
    val feePrompt: FeePrompt? = null,
    // Set when "Add" is done; the screen reports it and leaves.
    val result: HistoryResult? = null,
)

// Only the range and filters are saved; the rows are read from the inbox again.
@Serializable
private data class SavedHistory(
    val range: HistoryRange,
    @Serializable(with = LocalDateSerializer::class) val from: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val to: LocalDate,
    val kind: HistoryKind,
    val card: String?,
)

@HiltViewModel(assistedFactory = SmsHistoryViewModel.Factory::class)
class SmsHistoryViewModel @AssistedInject constructor(
    @Assisted private val key: SmsHistoryKey,
    private val sms: SmsRepository,
    private val refs: ReferenceDataRepository,
    private val prefs: UserPreferencesRepository,
    private val getFeePlan: GetSmsFeePlanUseCase,
    private val approveSms: ApproveSmsUseCase,
    private val inbox: SmsInboxRepository,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: SmsHistoryKey): SmsHistoryViewModel
    }
    private val zone = clock.zone
    private val gap: Boolean = key.gapFrom > 0 && key.gapUntil > key.gapFrom

    private val saved: SavedHistory? = savedState.restoreAndSave("place") {
        screenState.value.let {
            SavedHistory(it.range, it.custom.first, it.custom.second, it.kind, it.card)
        }
    }

    private val screenState = MutableStateFlow(
        SmsHistoryUiState(
            gap = gap,
            custom = saved?.let { it.from to it.to }
                ?: LocalDate.now(clock).let { it.minusDays(30) to it },
            range = saved?.range ?: if (gap) HistoryRange.GAP else HistoryRange.MONTH,
            permitted = inbox.hasPermission(),
            kind = saved?.kind ?: HistoryKind.ALL,
            card = saved?.card,
        ),
    )
    private val feePrompts = MutableStateFlow<List<FeePrompt>>(emptyList())

    val uiState: StateFlow<SmsHistoryUiState> = combine(
        refs.getRefDataStream(),
        screenState,
        feePrompts,
    ) {
            ref,
            s,
            prompts,
        ->
        val visible = s.rows.orEmpty().filter {
            s.kind.matches(it.item.kind) &&
                (s.card == null || it.item.cardLast4 == s.card)
        }
        s.copy(ref = ref, visible = visible, feePrompt = prompts.firstOrNull())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), screenState.value)

    private var initialized = false
    private var added = 0
    private var toQueue = 0

    // Reads the inbox only the first time the screen opens.
    fun initialize() {
        if (initialized) return
        initialized = true
        load()
    }

    fun setRange(r: HistoryRange) {
        screenState.update { it.copy(range = r) }
        load()
    }

    fun setCustom(from: LocalDate, to: LocalDate) {
        screenState.update { it.copy(custom = from to to, range = HistoryRange.CUSTOM) }
        load()
    }

    fun setKind(k: HistoryKind) {
        screenState.update { it.copy(kind = k) }
    }

    fun setCard(last4: String?) {
        screenState.update { it.copy(card = last4) }
    }

    fun onResultHandled() {
        screenState.update { it.copy(result = null) }
    }

    private fun bounds(): Pair<Long, Long> {
        val now = clock.millis()
        val state = screenState.value
        return when (val r = state.range) {
            HistoryRange.GAP -> key.gapFrom to key.gapUntil

            HistoryRange.CUSTOM -> {
                val (from, to) = state.custom
                from.atStartOfDay(zone).toInstant().toEpochMilli() to
                    to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            }

            else -> now - r.days!! * DAY_MS to now
        }
    }

    private fun load() = viewModelScope.launch {
        val permitted = inbox.hasPermission()
        screenState.update { it.copy(rows = null, permitted = permitted) }
        if (!permitted) {
            screenState.update { it.copy(rows = emptyList()) }
            return@launch
        }
        val (from, until) = bounds()
        val rows = inbox.scanHistory(from, until)
        // Nothing is selected by default; keep only choices that are still on screen.
        screenState.update { s ->
            s.copy(
                rows = rows,
                selection = s.selection.filter { id ->
                    rows.any {
                        it.item.id ==
                            id
                    }
                }.toSet(),
            )
        }
    }

    fun toggle(row: HistoryRow) {
        if (row.cancelled) return
        screenState.update { s ->
            s.copy(
                selection = if (row.item.id in s.selection) {
                    s.selection - row.item.id
                } else {
                    s.selection + row.item.id
                },
            )
        }
    }

    // Clears the selection instead when every visible row is already selected.
    fun selectAll(rows: List<HistoryRow>) {
        val ids = rows.filter { !it.cancelled }.map { it.item.id }.toSet()
        screenState.update { s ->
            s.copy(
                selection = if (ids.all { id -> id in s.selection }) {
                    s.selection -
                        ids
                } else {
                    s.selection + ids
                },
            )
        }
    }

    // Remembered for live capture too, and applied to these rows.
    fun mapCard(card: CardRef, accountId: String) = viewModelScope.launch {
        sms.mapCard(card.bank, card.last4, accountId)
        val ref = refs.getRefData()
        screenState.update { s ->
            s.copy(
                rows = s.rows?.map { row ->
                    if (row.item.bank == card.bank && row.item.cardLast4 == card.last4 &&
                        row.item.accountId == null
                    ) {
                        row.copy(item = sms.prepare(row.item.copy(accountId = accountId), ref))
                    } else {
                        row
                    }
                },
            )
        }
    }

    // Picking counts as approving; rows still lacking an account or category wait in the queue.
    fun add() = viewModelScope.launch {
        val state = screenState.value
        val chosen = state.rows.orEmpty().filter {
            it.item.id in state.selection && !it.cancelled
        }.map { it.item }
        if (chosen.isEmpty() || state.adding) return@launch
        screenState.update { it.copy(adding = true) }
        val stored = sms.getItems(sms.keep(chosen))
        prefs.setSmsHistoryImport(SmsHistoryImport.DONE)
        if (gap) prefs.clearSmsGap()
        added = 0
        toQueue = 0
        val asks = LinkedHashMap<FeeRoute, MutableList<Pair<SmsItem, FeePlan.Ask>>>()
        val prompts = ArrayList<FeePrompt>()
        for (item in stored) {
            if (item.needsDetails) {
                toQueue++
                continue
            }
            when (val plan = getFeePlan(item)) {
                FeePlan.None -> if (approveSms(item.id) is ApproveResult.Done) added++

                is FeePlan.Auto -> if (approveSms(
                        item.id,
                        FeeChoice(plan.amount),
                    ) is ApproveResult.Done
                ) {
                    added++
                }

                is FeePlan.Ask -> if (plan.fxDifference) {
                    prompts += FeePrompt(listOf(item), plan)
                } else {
                    asks.getOrPut(plan.route) { ArrayList() } +=
                        item to plan
                }
            }
        }
        asks.values.forEach { group ->
            prompts +=
                FeePrompt(group.map { it.first }, group.first().second)
        }
        feePrompts.value = prompts
        if (prompts.isEmpty()) finish()
    }

    // Null is Cancel, which leaves those items in the Review Queue.
    fun answerFee(choice: FeeChoice?) = viewModelScope.launch {
        val prompt = feePrompts.value.firstOrNull() ?: return@launch
        feePrompts.update { it.drop(1) }
        if (choice == null) {
            toQueue += prompt.items.size
        } else {
            prompt.items.forEach {
                if (approveSms(it.id, choice) is ApproveResult.Done) added++ else toQueue++
            }
        }
        if (feePrompts.value.isEmpty()) finish()
    }

    private fun finish() {
        screenState.update { it.copy(adding = false, result = HistoryResult(added, toQueue)) }
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
