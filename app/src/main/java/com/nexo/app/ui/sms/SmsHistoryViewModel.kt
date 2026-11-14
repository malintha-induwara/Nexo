package com.nexo.app.ui.sms

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.datastore.DevicePrefs
import com.nexo.app.data.datastore.SmsHistoryImport
import com.nexo.app.data.repository.RefRepository
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.FeeRoute
import com.nexo.app.core.model.SmsKind
import com.nexo.app.domain.sms.ApproveResult
import com.nexo.app.domain.sms.FeeChoice
import com.nexo.app.domain.sms.FeePlan
import com.nexo.app.domain.sms.SmsApprover
import com.nexo.app.data.repository.SmsCapture
import com.nexo.app.data.sms.SmsSuggester
import com.nexo.app.data.database.entity.SmsItemEntity
import com.nexo.app.data.repository.HistoryRow
import com.nexo.app.data.repository.SmsHistory
import com.nexo.app.data.sms.needsDetails
import com.nexo.app.ui.navigation.SmsHistoryKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class HistoryRange(val label: Int, val days: Long?) {
    WEEK(R.string.sms_history_range_7, 7),
    MONTH(R.string.sms_history_range_30, 30),
    QUARTER(R.string.sms_history_range_90, 90),
    CUSTOM(R.string.sms_history_range_custom, null),
    GAP(R.string.sms_history_range_gap, null),
}

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

/** The result of "Add N transactions": written to the ledger, and left in the queue for details. */
data class HistoryResult(val added: Int, val toQueue: Int)

@HiltViewModel(assistedFactory = SmsHistoryViewModel.Factory::class)
class SmsHistoryViewModel @AssistedInject constructor(
    @Assisted private val key: SmsHistoryKey,
    @ApplicationContext private val context: Context,
    db: NexoDatabase,
    refs: RefRepository,
    private val prefs: DevicePrefs,
    private val history: SmsHistory,
    private val approver: SmsApprover,
    private val suggester: SmsSuggester,
    private val capture: SmsCapture,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: SmsHistoryKey): SmsHistoryViewModel
    }

    private val dao = db.smsDao()
    private val zone = ZoneId.systemDefault()
    val gap: Boolean = key.gapFrom > 0 && key.gapUntil > key.gapFrom

    val ref: StateFlow<RefData> = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())

    private val _range = MutableStateFlow(if (gap) HistoryRange.GAP else HistoryRange.MONTH)
    val range: StateFlow<HistoryRange> = _range.asStateFlow()

    private val _custom = MutableStateFlow(LocalDate.now().minusDays(30) to LocalDate.now())
    val custom: StateFlow<Pair<LocalDate, LocalDate>> = _custom.asStateFlow()

    /** Null while reading the inbox. */
    private val _rows = MutableStateFlow<List<HistoryRow>?>(null)
    val rows: StateFlow<List<HistoryRow>?> = _rows.asStateFlow()

    private val _kind = MutableStateFlow(HistoryKind.ALL)
    val kind: StateFlow<HistoryKind> = _kind.asStateFlow()

    private val _card = MutableStateFlow<String?>(null)
    val card: StateFlow<String?> = _card.asStateFlow()

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _adding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = _adding.asStateFlow()

    private val _feePrompts = MutableStateFlow<List<FeePrompt>>(emptyList())
    val feePrompt: StateFlow<FeePrompt?> = _feePrompts.map { it.firstOrNull() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _done = Channel<HistoryResult>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    val permitted: Boolean get() = capture.hasPermission()

    private var added = 0
    private var toQueue = 0

    init {
        load()
    }

    fun setRange(r: HistoryRange) {
        _range.value = r
        load()
    }

    fun setCustom(from: LocalDate, to: LocalDate) {
        _custom.value = from to to
        _range.value = HistoryRange.CUSTOM
        load()
    }

    fun setKind(k: HistoryKind) {
        _kind.value = k
    }

    fun setCard(last4: String?) {
        _card.value = last4
    }

    private fun bounds(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        return when (val r = _range.value) {
            HistoryRange.GAP -> key.gapFrom to key.gapUntil
            HistoryRange.CUSTOM -> {
                val (from, to) = _custom.value
                from.atStartOfDay(zone).toInstant().toEpochMilli() to to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            }
            else -> now - r.days!! * DAY_MS to now
        }
    }

    fun load() = viewModelScope.launch {
        _rows.value = null
        if (!capture.hasPermission()) {
            _rows.value = emptyList()
            return@launch
        }
        val (from, until) = bounds()
        val rows = history.scan(from, until)
        _rows.value = rows
        // Nothing is selected by default; keep only choices that are still on screen.
        _selection.update { sel -> sel.filter { id -> rows.any { it.item.id == id } }.toSet() }
    }

    /** Rows passing the kind and card filters. */
    fun visible(rows: List<HistoryRow>, kind: HistoryKind, card: String?): List<HistoryRow> =
        rows.filter { kind.matches(it.item.kind) && (card == null || it.item.cardLast4 == card) }

    fun toggle(row: HistoryRow) {
        if (row.cancelled) return
        _selection.update { if (row.item.id in it) it - row.item.id else it + row.item.id }
    }

    fun selectAll(rows: List<HistoryRow>) {
        val ids = rows.filter { !it.cancelled }.map { it.item.id }.toSet()
        _selection.update { if (ids.all { id -> id in it }) it - ids else it + ids }
    }

    /** "Which account is the card ending 1111?": remembered for live capture too, and applied to these rows. */
    fun mapCard(card: CardRef, accountId: String) = viewModelScope.launch {
        approver.mapCard(card.bank, card.last4, accountId)
        val ref = ref.value
        _rows.update { rows ->
            rows?.map { row ->
                if (row.item.bank == card.bank && row.item.cardLast4 == card.last4 && row.item.accountId == null) {
                    row.copy(item = suggester.prepare(row.item.copy(accountId = accountId), ref))
                } else row
            }
        }
    }

    /**
     * "Add N transactions": picking counts as approving. Fee presets apply as in bulk approve, with one question per route;
     * rows that still lack an account or category wait in the Review Queue.
     */
    fun add() = viewModelScope.launch {
        val chosen = _rows.value.orEmpty().filter { it.item.id in _selection.value && !it.cancelled }.map { it.item }
        if (chosen.isEmpty()) return@launch
        _adding.value = true
        val stored = dao.items(history.keep(chosen))
        prefs.setSmsHistoryImport(SmsHistoryImport.DONE)
        if (gap) prefs.clearSmsGap()
        added = 0
        toQueue = 0
        val asks = LinkedHashMap<FeeRoute, MutableList<Pair<SmsItemEntity, FeePlan.Ask>>>()
        val prompts = ArrayList<FeePrompt>()
        for (item in stored) {
            if (item.needsDetails) {
                toQueue++
                continue
            }
            when (val plan = approver.feePlan(item)) {
                FeePlan.None -> if (approver.approve(item.id) is ApproveResult.Done) added++
                is FeePlan.Auto -> if (approver.approve(item.id, FeeChoice(plan.amount)) is ApproveResult.Done) added++
                is FeePlan.Ask -> if (plan.fxDifference) prompts += FeePrompt(listOf(item), plan) else asks.getOrPut(plan.route) { ArrayList() } += item to plan
            }
        }
        asks.values.forEach { group -> prompts += FeePrompt(group.map { it.first }, group.first().second) }
        _feePrompts.value = prompts
        if (prompts.isEmpty()) finish()
    }

    /** A fee answer for the batch on screen; Cancel leaves those items in the Review Queue. */
    fun answerFee(choice: FeeChoice?) = viewModelScope.launch {
        val prompt = _feePrompts.value.firstOrNull() ?: return@launch
        _feePrompts.update { it.drop(1) }
        if (choice == null) toQueue += prompt.items.size
        else prompt.items.forEach { if (approver.approve(it.id, choice) is ApproveResult.Done) added++ else toQueue++ }
        if (_feePrompts.value.isEmpty()) finish()
    }

    private suspend fun finish() {
        _adding.value = false
        _done.send(HistoryResult(added, toQueue))
    }

    fun resultText(r: HistoryResult): String = buildList {
        if (r.added > 0) add(context.resources.getQuantityString(R.plurals.sms_history_added, r.added, r.added))
        if (r.toQueue > 0) add(context.resources.getQuantityString(R.plurals.sms_history_to_queue, r.toQueue, r.toQueue))
    }.joinToString(" · ")

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
